package com.example.data.network

import android.os.Build
import android.util.Base64
import com.example.data.local.ProxyEntity
import com.example.data.model.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Local loopback HTTP/CONNECT bridge on 127.0.0.1 that tunnels Android WebView proxy traffic
 * into upstream HTTP, HTTPS (TLS), SOCKS4, or SOCKS5 proxies with full RFC 1929 / Basic auth support,
 * fast AUTO protocol detection, and automatic pool failover across live nodes.
 */
class LocalProxyBridge {
    private val bridgeLock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val threadId = AtomicInteger(1)
    private val bridgeExecutor = ThreadPoolExecutor(
        4,
        512,
        30L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, "CloakProxyBridge-${threadId.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
    )
    private val bridgeDispatcher = bridgeExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + bridgeDispatcher)

    private val activeUpstreams = AtomicReference<List<ProxyEntity>>(emptyList())
    private val activeClientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val poolIndex = AtomicInteger(0)
    private val detectedProtocolCache = ConcurrentHashMap<String, ProxyType>()

    @Volatile
    private var lastSingleUpstreamKey: String? = null

    @Volatile
    var localPort: Int = -1
        private set

    @Volatile
    var lastFailureReason: String = ""
        private set

    fun start(upstream: ProxyEntity): Int {
        val newKey = cacheKey(upstream)
        synchronized(bridgeLock) {
            val prevKey = lastSingleUpstreamKey
            lastSingleUpstreamKey = newKey
            if (prevKey != null && prevKey != newKey) {
                closeActiveClientSockets()
            }
        }
        return startPoolInternal(listOf(upstream), clearSingleKey = false)
    }

    fun startPool(upstreams: List<ProxyEntity>): Int {
        return startPoolInternal(upstreams, clearSingleKey = true)
    }

    private fun startPoolInternal(upstreams: List<ProxyEntity>, clearSingleKey: Boolean): Int = synchronized(bridgeLock) {
        if (clearSingleKey) {
            lastSingleUpstreamKey = null
        }
        val snapshot = upstreams
            .filter { it.host.isNotBlank() && it.port in 1..65535 }
            .toList()
        if (snapshot.isEmpty()) {
            stop()
            return -1
        }

        activeUpstreams.set(snapshot)
        lastFailureReason = ""

        val existing = serverSocket
        if (existing != null && !existing.isClosed && localPort > 0 && acceptJob?.isActive == true) {
            // Reuse the already-bound IPv4 loopback port cleanly without disrupting WebView's ProxyController
            return localPort
        }

        stopInternal(clearCache = false)
        activeUpstreams.set(snapshot)

        val ipv4Loopback = InetAddress.getByAddress("127.0.0.1", byteArrayOf(127, 0, 0, 1))
        val server = ServerSocket(0, 128, ipv4Loopback)
        serverSocket = server
        localPort = server.localPort

        acceptJob = scope.launch {
            while (isActive && !server.isClosed) {
                try {
                    val client = server.accept()
                    val currentList = activeUpstreams.get()
                    if (currentList.isEmpty()) {
                        runCatching { client.close() }
                        continue
                    }
                    val startIdx = if (currentList.size <= 1) {
                        0
                    } else {
                        (poolIndex.getAndIncrement() and Int.MAX_VALUE) % currentList.size
                    }
                    launch { handleClient(client, currentList, startIdx) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        localPort
    }

    fun stop() = synchronized(bridgeLock) {
        stopInternal(clearCache = false)
    }

    private fun closeActiveClientSockets() {
        val iterator = activeClientSockets.iterator()
        while (iterator.hasNext()) {
            val sock = iterator.next()
            iterator.remove()
            runCatching { sock.close() }
        }
    }

    private fun stopInternal(clearCache: Boolean) {
        localPort = -1
        lastSingleUpstreamKey = null
        activeUpstreams.set(emptyList())
        acceptJob?.cancel()
        acceptJob = null
        closeActiveClientSockets()
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (clearCache) {
            detectedProtocolCache.clear()
        }
    }

    fun shutdown() {
        synchronized(bridgeLock) {
            stopInternal(clearCache = true)
        }
        scope.cancel()
        runCatching { bridgeExecutor.shutdownNow() }
    }

    private fun cacheKey(upstream: ProxyEntity): String =
        "${upstream.host}:${upstream.port}:${upstream.username}"

    private suspend fun handleClient(
        client: Socket,
        upstreams: List<ProxyEntity>,
        startIdx: Int
    ) {
        activeClientSockets.add(client)
        client.soTimeout = 60_000
        client.tcpNoDelay = true
        var upstreamSocket: Socket? = null
        try {
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            val headerBytes = readHttpHeaderBlock(clientIn, rejectNonHttpFirstByte = false) ?: return
            val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            val parts = requestLine.split(" ")
            if (parts.size < 3) return

            val method = parts[0].uppercase()
            val target = parts[1]
            val maxAttempts = upstreams.size.coerceAtMost(5)

            if (method == "CONNECT") {
                val (destHost, destPort) = parseHostPort(target, 443)
                for (attempt in 0 until maxAttempts) {
                    val upstream = upstreams[(startIdx + attempt) % upstreams.size]
                    val configuredType = runCatching { ProxyType.valueOf(upstream.type) }.getOrDefault(ProxyType.AUTO)
                    val effectiveType = if (configuredType == ProxyType.AUTO) {
                        detectedProtocolCache[cacheKey(upstream)] ?: ProxyType.AUTO
                    } else {
                        configuredType
                    }
                    val candidateSock = openUpstreamTunnelWithFallback(upstream, effectiveType, destHost, destPort)
                    if (candidateSock != null) {
                        upstreamSocket = candidateSock
                        break
                    }
                }
                if (upstreamSocket == null) {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray())
                    clientOut.flush()
                    return
                }
                lastFailureReason = ""
                upstreamSocket.soTimeout = 60_000
                upstreamSocket.tcpNoDelay = true
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOut.flush()
                relayBidirectional(client, upstreamSocket)
            } else {
                val (destHost, destPort) = extractHttpHostPort(target, lines)
                for (attempt in 0 until maxAttempts) {
                    val upstream = upstreams[(startIdx + attempt) % upstreams.size]
                    val configuredType = runCatching { ProxyType.valueOf(upstream.type) }.getOrDefault(ProxyType.AUTO)
                    val effectiveType = if (configuredType == ProxyType.AUTO) {
                        detectedProtocolCache[cacheKey(upstream)] ?: ProxyType.AUTO
                    } else {
                        configuredType
                    }

                    val served = handlePlainHttpWithUpstream(
                        client = client,
                        clientOut = clientOut,
                        headerText = headerText,
                        upstream = upstream,
                        effectiveType = effectiveType,
                        destHost = destHost,
                        destPort = destPort
                    ) { bound -> upstreamSocket = bound }
                    if (served) {
                        lastFailureReason = ""
                        return
                    }
                }
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
            }
        } catch (_: Exception) {
            // Connection closed or aborted cleanly
        } finally {
            activeClientSockets.remove(client)
            runCatching { client.close() }
            runCatching { upstreamSocket?.close() }
        }
    }

    private suspend fun handlePlainHttpWithUpstream(
        client: Socket,
        clientOut: java.io.OutputStream,
        headerText: String,
        upstream: ProxyEntity,
        effectiveType: ProxyType,
        destHost: String,
        destPort: Int,
        onUpstreamBound: (Socket) -> Unit
    ): Boolean {
        return when (effectiveType) {
            ProxyType.HTTP, ProxyType.HTTPS -> {
                val sock = connectRawUpstream(upstream, effectiveType) ?: return false
                onUpstreamBound(sock)
                sock.soTimeout = 60_000
                val modifiedHeaders = injectProxyAuthHeader(forceCloseConnectionHeaders(headerText, isProxyRequest = true), upstream)
                val upOut = sock.getOutputStream()
                upOut.write(modifiedHeaders.toByteArray(StandardCharsets.ISO_8859_1))
                upOut.flush()
                relayBidirectional(client, sock)
                true
            }

            ProxyType.AUTO -> {
                val httpTry = connectRawUpstream(upstream, ProxyType.HTTP, handshakeTimeoutMs = 4_500)
                if (httpTry != null) {
                    val modifiedHeaders = injectProxyAuthHeader(forceCloseConnectionHeaders(headerText, isProxyRequest = true), upstream)
                    val upOut = httpTry.getOutputStream()
                    upOut.write(modifiedHeaders.toByteArray(StandardCharsets.ISO_8859_1))
                    upOut.flush()
                    val probe = ByteArray(5)
                    val n = runCatching { httpTry.getInputStream().read(probe) }.getOrDefault(-1)
                    if (n > 0 && String(probe, 0, n, StandardCharsets.ISO_8859_1).startsWith("HTTP/", ignoreCase = true)) {
                        detectedProtocolCache[cacheKey(upstream)] = ProxyType.HTTP
                        onUpstreamBound(httpTry)
                        httpTry.soTimeout = 60_000
                        clientOut.write(probe, 0, n)
                        clientOut.flush()
                        relayBidirectional(client, httpTry)
                        return true
                    } else {
                        runCatching { httpTry.close() }
                    }
                }

                val tunnelSock = openUpstreamTunnelWithFallback(upstream, ProxyType.SOCKS5, destHost, destPort)
                    ?: openUpstreamTunnelWithFallback(upstream, ProxyType.HTTPS, destHost, destPort)
                    ?: openUpstreamTunnelWithFallback(upstream, ProxyType.SOCKS4, destHost, destPort)
                    ?: return false

                onUpstreamBound(tunnelSock)
                tunnelSock.soTimeout = 60_000
                val rewritten = forceCloseConnectionHeaders(rewriteAbsoluteUrlToRelative(headerText), isProxyRequest = false)
                val upOut = tunnelSock.getOutputStream()
                upOut.write(rewritten.toByteArray(StandardCharsets.ISO_8859_1))
                upOut.flush()
                relayBidirectional(client, tunnelSock)
                true
            }

            ProxyType.SOCKS5, ProxyType.SOCKS4, ProxyType.DIRECT -> {
                val tunnelSock = openUpstreamTunnelWithFallback(upstream, effectiveType, destHost, destPort)
                    ?: return false
                onUpstreamBound(tunnelSock)
                tunnelSock.soTimeout = 60_000
                val rewritten = forceCloseConnectionHeaders(rewriteAbsoluteUrlToRelative(headerText), isProxyRequest = false)
                val upOut = tunnelSock.getOutputStream()
                upOut.write(rewritten.toByteArray(StandardCharsets.ISO_8859_1))
                upOut.flush()
                relayBidirectional(client, tunnelSock)
                true
            }
        }
    }

    private fun connectRawUpstream(
        upstream: ProxyEntity,
        type: ProxyType,
        handshakeTimeoutMs: Int = 5_000
    ): Socket? {
        val addresses = runCatching {
            InetAddress.getAllByName(upstream.host)
                .sortedBy { if (it is Inet4Address) 0 else 1 }
        }.getOrElse {
            lastFailureReason = "DNS lookup failed for ${upstream.host}"
            return null
        }

        for (addr in addresses.take(2)) {
            val base = Socket()
            try {
                base.connect(InetSocketAddress(addr, upstream.port), 5_500)
                base.soTimeout = handshakeTimeoutMs
                base.tcpNoDelay = true
                if (type == ProxyType.HTTPS) {
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSock = factory.createSocket(base, upstream.host, upstream.port, true) as SSLSocket
                    sslSock.soTimeout = handshakeTimeoutMs
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val params = sslSock.sslParameters
                        params.serverNames = listOf(SNIHostName(upstream.host))
                        sslSock.sslParameters = params
                    }
                    sslSock.startHandshake()
                    return sslSock
                }
                return base
            } catch (e: Exception) {
                runCatching { base.close() }
                lastFailureReason = if (type == ProxyType.HTTPS) {
                    "TLS proxy handshake failed (${upstream.host}:${upstream.port})"
                } else {
                    "Proxy unreachable (${upstream.host}:${upstream.port}): ${e.javaClass.simpleName}"
                }
            }
        }
        return null
    }

    private fun openUpstreamTunnelWithFallback(
        upstream: ProxyEntity,
        proxyType: ProxyType,
        destHost: String,
        destPort: Int
    ): Socket? {
        if (proxyType != ProxyType.AUTO) {
            val sock = openUpstreamTunnel(upstream, proxyType, destHost, destPort)
            if (sock != null) {
                detectedProtocolCache[cacheKey(upstream)] = proxyType
                return sock
            }
            return null
        }

        val key = cacheKey(upstream)
        val cached = detectedProtocolCache[key]
        val candidates = buildList {
            if (cached != null) add(cached)
            listOf(ProxyType.HTTP, ProxyType.SOCKS5, ProxyType.HTTPS, ProxyType.SOCKS4).forEach {
                if (it != cached) add(it)
            }
        }

        for (candidate in candidates) {
            val sock = openUpstreamTunnel(upstream, candidate, destHost, destPort)
            if (sock != null) {
                detectedProtocolCache[key] = candidate
                return sock
            }
        }
        return null
    }

    private fun openUpstreamTunnel(
        upstream: ProxyEntity,
        proxyType: ProxyType,
        destHost: String,
        destPort: Int
    ): Socket? {
        if (proxyType == ProxyType.DIRECT) {
            return runCatching {
                Socket().apply {
                    connect(InetSocketAddress(destHost, destPort), 8_000)
                    soTimeout = 15_000
                    tcpNoDelay = true
                }
            }.getOrNull()
        }

        val sock = connectRawUpstream(upstream, proxyType, handshakeTimeoutMs = 4_500) ?: return null
        return try {
            when (proxyType) {
                ProxyType.HTTP, ProxyType.HTTPS, ProxyType.AUTO -> {
                    val out = sock.getOutputStream()
                    val hasCreds = upstream.username.isNotEmpty() || upstream.password.isNotEmpty()
                    val req = buildString {
                        append("CONNECT ").append(destHost).append(':').append(destPort).append(" HTTP/1.1\r\n")
                        append("Host: ").append(destHost).append(':').append(destPort).append("\r\n")
                        append("User-Agent: Mozilla/5.0\r\n")
                        if (hasCreds) {
                            val token = Base64.encodeToString(
                                "${upstream.username}:${upstream.password}".toByteArray(StandardCharsets.UTF_8),
                                Base64.NO_WRAP
                            )
                            append("Proxy-Authorization: Basic ").append(token).append("\r\n")
                        }
                        append("Proxy-Connection: keep-alive\r\n\r\n")
                    }
                    out.write(req.toByteArray(StandardCharsets.ISO_8859_1))
                    out.flush()
                    val respBytes = readHttpHeaderBlock(sock.getInputStream(), rejectNonHttpFirstByte = true)
                    val statusLine = respBytes?.let { String(it, StandardCharsets.ISO_8859_1).lineSequence().firstOrNull() }.orEmpty()
                    if (!Regex("""^HTTP/\d\.\d\s+200\b""", RegexOption.IGNORE_CASE).containsMatchIn(statusLine)) {
                        if (statusLine.contains("407")) {
                            lastFailureReason = "Proxy Auth Rejected (HTTP 407) on ${upstream.host}:${upstream.port}"
                        } else if (statusLine.isNotBlank()) {
                            lastFailureReason = "Proxy returned $statusLine (${upstream.host}:${upstream.port})"
                        }
                        sock.close()
                        return null
                    }
                    sock
                }

                ProxyType.SOCKS5 -> {
                    if (!performSocks5Handshake(sock, upstream, destHost, destPort)) {
                        sock.close()
                        return null
                    }
                    sock
                }

                ProxyType.SOCKS4 -> {
                    if (!performSocks4Handshake(sock, destHost, destPort)) {
                        sock.close()
                        return null
                    }
                    sock
                }

                ProxyType.DIRECT -> sock
            }
        } catch (e: Exception) {
            lastFailureReason = "Proxy tunnel error (${upstream.host}:${upstream.port}): ${e.javaClass.simpleName}"
            runCatching { sock.close() }
            null
        }
    }

    private fun performSocks5Handshake(
        sock: Socket,
        upstream: ProxyEntity,
        destHost: String,
        destPort: Int
    ): Boolean {
        val input = sock.getInputStream()
        val out = sock.getOutputStream()
        val hasAuth = upstream.username.isNotEmpty() || upstream.password.isNotEmpty()

        if (hasAuth) {
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            out.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        out.flush()

        val greeting = ByteArray(2)
        if (!readExact(input, greeting) || greeting[0] != 0x05.toByte()) {
            lastFailureReason = "Not a SOCKS5 endpoint (${upstream.host}:${upstream.port})"
            return false
        }

        when (greeting[1]) {
            0x00.toByte() -> Unit
            0x02.toByte() -> {
                if (!hasAuth) {
                    lastFailureReason = "SOCKS5 proxy requires username/password (${upstream.host}:${upstream.port})"
                    return false
                }
                val userBytes = upstream.username.toByteArray(StandardCharsets.UTF_8)
                val passBytes = upstream.password.toByteArray(StandardCharsets.UTF_8)
                val authPacket = ByteArrayOutputStream().apply {
                    write(0x01)
                    write(userBytes.size.coerceAtMost(255))
                    write(userBytes, 0, userBytes.size.coerceAtMost(255))
                    write(passBytes.size.coerceAtMost(255))
                    write(passBytes, 0, passBytes.size.coerceAtMost(255))
                }.toByteArray()
                out.write(authPacket)
                out.flush()
                val authResp = ByteArray(2)
                if (!readExact(input, authResp) || authResp[1] != 0x00.toByte()) {
                    lastFailureReason = "SOCKS5 Auth Rejected (${upstream.host}:${upstream.port})"
                    return false
                }
            }
            else -> {
                lastFailureReason = "SOCKS5 auth method unsupported (${greeting[1]})"
                return false
            }
        }

        val ipv4Bytes = parseIpv4Literal(destHost)
        val connectReq = ByteArrayOutputStream().apply {
            write(0x05) // VER
            write(0x01) // CMD = CONNECT
            write(0x00) // RSV
            if (ipv4Bytes != null) {
                write(0x01) // ATYP = IPv4
                write(ipv4Bytes)
            } else {
                val domainBytes = destHost.toByteArray(StandardCharsets.UTF_8)
                write(0x03) // ATYP = DOMAINNAME
                write(domainBytes.size.coerceAtMost(255))
                write(domainBytes, 0, domainBytes.size.coerceAtMost(255))
            }
            write((destPort shr 8) and 0xFF)
            write(destPort and 0xFF)
        }.toByteArray()
        out.write(connectReq)
        out.flush()

        val replyHead = ByteArray(4)
        if (!readExact(input, replyHead) || replyHead[1] != 0x00.toByte()) {
            lastFailureReason = "SOCKS5 target connect refused (rep=${replyHead[1].toInt() and 0xFF})"
            return false
        }

        val addrLen = when (replyHead[3].toInt() and 0xFF) {
            1 -> 4
            3 -> input.read().coerceAtLeast(0)
            4 -> 16
            else -> return false
        }
        val skip = ByteArray(addrLen + 2)
        return readExact(input, skip)
    }

    private fun parseIpv4Literal(host: String): ByteArray? {
        val parts = host.split(".")
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for (i in 0 until 4) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255) return null
            out[i] = n.toByte()
        }
        return out
    }

    private fun performSocks4Handshake(sock: Socket, destHost: String, destPort: Int): Boolean {
        val addr = runCatching { InetAddress.getByName(destHost).address }.getOrNull() ?: return false
        if (addr.size != 4) return false
        val out = sock.getOutputStream()
        val input = sock.getInputStream()
        val req = ByteArrayOutputStream().apply {
            write(0x04)
            write(0x01)
            write((destPort shr 8) and 0xFF)
            write(destPort and 0xFF)
            write(addr)
            write(0x00)
        }.toByteArray()
        out.write(req)
        out.flush()
        val resp = ByteArray(8)
        return readExact(input, resp) && resp[1] == 0x5A.toByte()
    }

    private suspend fun relayBidirectional(a: Socket, b: Socket) {
        val job1 = scope.launch { copyStream(a, b) }
        val job2 = scope.launch { copyStream(b, a) }
        job1.join()
        job2.join()
    }

    private fun copyStream(src: Socket, dst: Socket) {
        val buffer = ByteArray(16 * 1024)
        try {
            val input = src.getInputStream()
            val output = dst.getOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
            }
            runCatching { dst.shutdownOutput() }
        } catch (_: Exception) {
        }
    }

    private fun readHttpHeaderBlock(
        input: InputStream,
        rejectNonHttpFirstByte: Boolean
    ): ByteArray? {
        val baos = ByteArrayOutputStream()
        var matched = 0
        while (baos.size() < 32 * 1024) {
            val b = input.read()
            if (b == -1) return null
            if (rejectNonHttpFirstByte && baos.size() == 0) {
                // If upstream responds with SOCKS5 (0x05), SOCKS4 (0x00/0x04), or TLS (0x15/0x16), abort instantly
                if (b != 'H'.code && b != 'h'.code) {
                    return null
                }
            }
            baos.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                matched == 1 && b == '\n'.code -> 2
                matched == 3 && b == '\n'.code -> return baos.toByteArray()
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return null
    }

    private fun readExact(input: InputStream, buf: ByteArray): Boolean {
        var offset = 0
        while (offset < buf.size) {
            val count = input.read(buf, offset, buf.size - offset)
            if (count <= 0) return false
            offset += count
        }
        return true
    }

    private fun parseHostPort(authority: String, defaultPort: Int): Pair<String, Int> {
        val idx = authority.lastIndexOf(':')
        return if (idx > 0 && idx < authority.length - 1) {
            val host = authority.substring(0, idx).removeSurrounding("[", "]")
            val port = authority.substring(idx + 1).toIntOrNull() ?: defaultPort
            host to port
        } else {
            authority to defaultPort
        }
    }

    private fun extractHttpHostPort(target: String, lines: List<String>): Pair<String, Int> {
        val hostHeader = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
        if (!hostHeader.isNullOrBlank()) {
            return parseHostPort(hostHeader, 80)
        }
        val clean = target.removePrefix("http://").substringBefore("/")
        return parseHostPort(clean, 80)
    }

    private fun injectProxyAuthHeader(headerText: String, upstream: ProxyEntity): String {
        if (upstream.username.isEmpty() && upstream.password.isEmpty()) return headerText
        if (headerText.contains("Proxy-Authorization:", ignoreCase = true)) return headerText
        val token = Base64.encodeToString(
            "${upstream.username}:${upstream.password}".toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return headerText.replaceFirst(
            "\r\n\r\n",
            "\r\nProxy-Authorization: Basic $token\r\n\r\n"
        )
    }

    private fun forceCloseConnectionHeaders(headerText: String, isProxyRequest: Boolean): String {
        var updated = headerText
            .replace(Regex("(?im)^Proxy-Connection:.*\\r\\n"), "")
            .replace(Regex("(?im)^Connection:.*\\r\\n"), "")
        val addition = if (isProxyRequest) {
            "\r\nProxy-Connection: close\r\nConnection: close\r\n\r\n"
        } else {
            "\r\nConnection: close\r\n\r\n"
        }
        return updated.replaceFirst("\r\n\r\n", addition)
    }

    private fun rewriteAbsoluteUrlToRelative(headerText: String): String {
        val firstBreak = headerText.indexOf("\r\n")
        if (firstBreak <= 0) return headerText
        val firstLine = headerText.substring(0, firstBreak)
        val parts = firstLine.split(" ")
        if (parts.size < 3) return headerText
        val uri = parts[1]
        if (!uri.startsWith("http://", ignoreCase = true)) return headerText
        val pathStart = uri.indexOf('/', startIndex = 7)
        val relative = if (pathStart >= 0) uri.substring(pathStart) else "/"
        val newFirstLine = "${parts[0]} $relative ${parts[2]}"
        return newFirstLine + headerText.substring(firstBreak)
    }
}
