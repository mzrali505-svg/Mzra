package com.example.data.network

import com.example.data.local.ProxyEntity
import com.example.data.model.ProxyStatus
import com.example.data.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class ProxyCheckResult(
    val success: Boolean,
    val detectedType: ProxyType? = null,
    val exitIp: String = "",
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
    val isp: String = "",
    val timezone: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val latencyMs: Long? = null,
    val errorMessage: String = ""
)

object ProxyChecker {
    private val socksAuthMutex = Mutex()

    suspend fun checkProxy(proxy: ProxyEntity?): ProxyCheckResult = withContext(Dispatchers.IO) {
        val type = proxy?.let {
            runCatching { ProxyType.valueOf(it.type) }.getOrDefault(ProxyType.AUTO)
        } ?: ProxyType.DIRECT

        if (proxy != null && type == ProxyType.AUTO) {
            // Auto mode: test HTTP, SOCKS5, HTTPS, then SOCKS4 with fast-fail
            val httpResult = checkWithExplicitType(proxy, ProxyType.HTTP)
            if (httpResult.success) return@withContext httpResult.copy(detectedType = ProxyType.HTTP)

            val socks5Result = checkWithExplicitType(proxy, ProxyType.SOCKS5)
            if (socks5Result.success) return@withContext socks5Result.copy(detectedType = ProxyType.SOCKS5)

            val httpsResult = checkWithExplicitType(proxy, ProxyType.HTTPS)
            if (httpsResult.success) return@withContext httpsResult.copy(detectedType = ProxyType.HTTPS)

            val socks4Result = checkWithExplicitType(proxy, ProxyType.SOCKS4)
            if (socks4Result.success) return@withContext socks4Result.copy(detectedType = ProxyType.SOCKS4)

            return@withContext ProxyCheckResult(
                success = false,
                errorMessage = "Proxy connection failed (HTTP: ${httpResult.errorMessage}; SOCKS5: ${socks5Result.errorMessage})"
            )
        }

        checkWithExplicitType(proxy, type)
    }

    private fun checkWithExplicitType(proxy: ProxyEntity?, type: ProxyType): ProxyCheckResult {
        val startNs = System.nanoTime()

        if (proxy == null || type == ProxyType.DIRECT || type == ProxyType.HTTP) {
            return executeIpCheck(proxy, type, startNs).copy(detectedType = type)
        }

        // Route SOCKS5, HTTPS (TLS-to-proxy), and SOCKS4 via LocalProxyBridge so auth & tunneling match WebView cleanly
        val bridge = LocalProxyBridge()
        return try {
            val localPort = bridge.start(proxy.copy(type = type.name))
            if (localPort <= 0) {
                ProxyCheckResult(false, detectedType = type, errorMessage = "Local loopback bridge unavailable")
            } else {
                val bridgeProxy = proxy.copy(
                    type = ProxyType.HTTP.name,
                    host = "127.0.0.1",
                    port = localPort,
                    username = "",
                    password = ""
                )
                val res = executeIpCheck(bridgeProxy, ProxyType.HTTP, startNs, bridge)
                val diag = bridge.lastFailureReason
                if (!res.success && diag.isNotBlank()) {
                    res.copy(detectedType = type, errorMessage = diag)
                } else {
                    res.copy(detectedType = type)
                }
            }
        } finally {
            bridge.shutdown()
        }
    }

    private fun isProxyTransportFailure(err: String): Boolean {
        val lower = err.lowercase()
        return lower.contains("407") ||
            lower.contains("502") ||
            lower.contains("auth") ||
            lower.contains("refused") ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("reset") ||
            lower.contains("unreachable") ||
            lower.contains("socks") ||
            lower.contains("unknownhost") ||
            lower.contains("connect")
    }

    private fun executeIpCheck(
        proxy: ProxyEntity?,
        type: ProxyType,
        startNs: Long,
        bridge: LocalProxyBridge? = null
    ): ProxyCheckResult {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .writeTimeout(7, TimeUnit.SECONDS)

        if (proxy != null && type != ProxyType.DIRECT) {
            val javaProxyType = when (type) {
                ProxyType.HTTP, ProxyType.HTTPS -> Proxy.Type.HTTP
                ProxyType.SOCKS4, ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                ProxyType.DIRECT, ProxyType.AUTO -> Proxy.Type.DIRECT
            }
            clientBuilder.proxy(Proxy(javaProxyType, InetSocketAddress(proxy.host, proxy.port)))
            if ((proxy.username.isNotEmpty() || proxy.password.isNotEmpty()) && (type == ProxyType.HTTP || type == ProxyType.HTTPS)) {
                clientBuilder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(proxy.username, proxy.password)
                    if (response.request.header("Proxy-Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }
        }

        val client = clientBuilder.build()

        val primary = queryIpWhoIs(client, startNs)
        if (primary.success) return primary
        if (proxy != null && type != ProxyType.DIRECT &&
            (isProxyTransportFailure(primary.errorMessage) || !bridge?.lastFailureReason.isNullOrBlank())
        ) {
            return primary
        }

        val secondary = queryIpApiCo(client, startNs)
        if (secondary.success) return secondary
        if (proxy != null && type != ProxyType.DIRECT && isProxyTransportFailure(secondary.errorMessage)) {
            return secondary
        }

        val tertiary = queryIpify(client, startNs)
        if (tertiary.success) return tertiary

        val quaternary = queryIcanhazip(client, startNs)
        if (quaternary.success) return quaternary

        return ProxyCheckResult(
            success = false,
            errorMessage = primary.errorMessage.ifBlank { "Proxy connection timed out or refused" }
        )
    }

    private fun queryIpWhoIs(client: OkHttpClient, startNs: Long): ProxyCheckResult {
        return try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    return ProxyCheckResult(false, errorMessage = "HTTP ${resp.code}")
                }
                val json = JSONObject(body)
                if (!json.optBoolean("success", true)) {
                    return ProxyCheckResult(false, errorMessage = json.optString("message", "Lookup failed"))
                }
                val latency = (System.nanoTime() - startNs) / 1_000_000L
                val conn = json.optJSONObject("connection")
                val tz = json.optJSONObject("timezone")
                ProxyCheckResult(
                    success = true,
                    exitIp = json.optString("ip"),
                    country = json.optString("country"),
                    countryCode = json.optString("country_code"),
                    city = json.optString("city"),
                    isp = conn?.optString("isp").orEmpty().ifBlank { conn?.optString("org").orEmpty() },
                    timezone = tz?.optString("id").orEmpty(),
                    latitude = json.optDouble("latitude").takeIf { !it.isNaN() },
                    longitude = json.optDouble("longitude").takeIf { !it.isNaN() },
                    latencyMs = latency.coerceAtLeast(1L)
                )
            }
        } catch (e: Exception) {
            ProxyCheckResult(false, errorMessage = e.localizedMessage ?: "Connection error")
        }
    }

    private fun queryIpApiCo(client: OkHttpClient, startNs: Long): ProxyCheckResult {
        return try {
            val req = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "CloakDeck/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    return ProxyCheckResult(false, errorMessage = "HTTP ${resp.code}")
                }
                val json = JSONObject(body)
                val ip = json.optString("ip")
                if (ip.isBlank()) return ProxyCheckResult(false, errorMessage = "Empty IP response")
                val latency = (System.nanoTime() - startNs) / 1_000_000L
                ProxyCheckResult(
                    success = true,
                    exitIp = ip,
                    country = json.optString("country_name"),
                    countryCode = json.optString("country_code"),
                    city = json.optString("city"),
                    isp = json.optString("org"),
                    timezone = json.optString("timezone"),
                    latitude = json.optDouble("latitude").takeIf { !it.isNaN() },
                    longitude = json.optDouble("longitude").takeIf { !it.isNaN() },
                    latencyMs = latency.coerceAtLeast(1L)
                )
            }
        } catch (e: Exception) {
            ProxyCheckResult(false, errorMessage = e.localizedMessage ?: "Connection error")
        }
    }

    private fun queryIpify(client: OkHttpClient, startNs: Long): ProxyCheckResult {
        return try {
            val req = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    return ProxyCheckResult(false, errorMessage = "HTTP ${resp.code}")
                }
                val ip = JSONObject(body).optString("ip")
                if (ip.isBlank()) return ProxyCheckResult(false, errorMessage = "Empty IP")
                val latency = (System.nanoTime() - startNs) / 1_000_000L
                ProxyCheckResult(
                    success = true,
                    exitIp = ip,
                    latencyMs = latency.coerceAtLeast(1L)
                )
            }
        } catch (e: Exception) {
            ProxyCheckResult(false, errorMessage = e.localizedMessage ?: "Connection error")
        }
    }

    private fun queryIcanhazip(client: OkHttpClient, startNs: Long): ProxyCheckResult {
        return try {
            val req = Request.Builder()
                .url("https://icanhazip.com/")
                .build()
            client.newCall(req).execute().use { resp ->
                val ip = resp.body?.string()?.trim().orEmpty()
                if (!resp.isSuccessful || ip.isBlank() || ip.contains("<")) {
                    return ProxyCheckResult(false, errorMessage = "HTTP ${resp.code}")
                }
                val latency = (System.nanoTime() - startNs) / 1_000_000L
                ProxyCheckResult(
                    success = true,
                    exitIp = ip,
                    latencyMs = latency.coerceAtLeast(1L)
                )
            }
        } catch (e: Exception) {
            ProxyCheckResult(false, errorMessage = e.localizedMessage ?: "Connection error")
        }
    }

    fun parseBulkProxies(rawText: String, defaultType: ProxyType = ProxyType.HTTP): List<ProxyEntity> {
        val results = mutableListOf<ProxyEntity>()
        rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEachIndexed { idx, line ->
                parseSingleProxyLine(line, defaultType, idx + 1)?.let { results.add(it) }
            }
        return results
    }

    fun parseSingleProxyLine(
        rawLine: String,
        defaultType: ProxyType = ProxyType.HTTP,
        indexHint: Int = 1
    ): ProxyEntity? {
        val trimmed = rawLine.trim()
        if (trimmed.isBlank()) return null

        // Optional pipe metadata: <proxy_string>|<dwellSeconds>|<keyword>|<targetDomain>
        val metaParts = trimmed.split("|")
        var remaining = metaParts[0].trim()
        val pipeDwell = metaParts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(3, 3600)
        val pipeKeyword = metaParts.getOrNull(2)?.trim().orEmpty()
        val pipeDomain = metaParts.getOrNull(3)?.trim().orEmpty()

        var detectedType = defaultType
        val schemeSplit = remaining.split("://", limit = 2)
        if (schemeSplit.size == 2) {
            detectedType = when (schemeSplit[0].lowercase()) {
                "http" -> ProxyType.HTTP
                "https" -> ProxyType.HTTPS
                "socks4" -> ProxyType.SOCKS4
                "socks5", "socks5h", "socks" -> ProxyType.SOCKS5
                else -> defaultType
            }
            remaining = schemeSplit[1].trimEnd('/')
        }

        val atIdx = remaining.lastIndexOf('@')
        if (atIdx > 0) {
            val authStr = remaining.substring(0, atIdx)
            val hostStr = remaining.substring(atIdx + 1).substringBefore('/')
            val authParts = authStr.split(":", limit = 2)
            val hostParts = hostStr.split(":", limit = 2)
            val host = hostParts.getOrNull(0)?.trim().orEmpty()
            val port = hostParts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535) return null
            return ProxyEntity(
                name = "${detectedType.label} #$indexHint ($host)",
                type = detectedType.name,
                host = host,
                port = port,
                username = authParts.getOrNull(0).orEmpty().trim(),
                password = authParts.getOrNull(1).orEmpty().trim(),
                status = ProxyStatus.UNCHECKED.name,
                humanizeEnabled = pipeDwell != null || pipeKeyword.isNotBlank(),
                humanizeDwellSeconds = pipeDwell ?: 30,
                humanizeKeyword = pipeKeyword,
                humanizeTargetDomain = pipeDomain
            )
        }

        val colonParts = remaining.substringBefore('/').split(":", limit = 4)
        if (colonParts.size >= 2) {
            val host = colonParts[0].trim()
            val port = colonParts[1].trim().toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535) return null
            val user = colonParts.getOrNull(2)?.trim().orEmpty()
            val pass = colonParts.getOrNull(3)?.trim().orEmpty()
            return ProxyEntity(
                name = "${detectedType.label} #$indexHint ($host)",
                type = detectedType.name,
                host = host,
                port = port,
                username = user,
                password = pass,
                status = ProxyStatus.UNCHECKED.name,
                humanizeEnabled = pipeDwell != null || pipeKeyword.isNotBlank(),
                humanizeDwellSeconds = pipeDwell ?: 30,
                humanizeKeyword = pipeKeyword,
                humanizeTargetDomain = pipeDomain
            )
        }
        return null
    }
}
