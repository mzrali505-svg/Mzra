package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import androidx.webkit.WebViewStartUpConfig
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.model.BrowserEngine
import com.example.data.model.OsPlatform
import com.example.data.model.ProfileProxyConnectionType
import com.example.data.model.ProxyType
import com.example.data.network.LocalProxyBridge
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class ChromiumCapabilitySnapshot(
    val packageName: String,
    val versionName: String,
    val multiProfile: Boolean,
    val proxyOverride: Boolean,
    val documentStartScript: Boolean,
    val userAgentMetadata: Boolean,
    val rendererClient: Boolean,
    val safeBrowsing: Boolean,
    val webAuthn: Boolean
)

data class OsClientHintSpec(
    val platform: String,
    val platformVersion: String,
    val architecture: String,
    val bitness: Int,
    val isMobile: Boolean
)

object ProfileWebViewManager {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val mainExecutor = Executor { runnable ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    private val bridgeControlExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CloakBridgeControl").apply { isDaemon = true }
    }
    private val cookieFlushExecutor = Executors.newCachedThreadPool()

    private val routeGeneration = AtomicLong(0L)

    @Volatile
    private var activeOverridePort: Int = -1

    @Volatile
    private var warmUpRequested = false

    fun isMultiProfileSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun isProxyOverrideSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    fun isDocumentStartScriptSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun isUserAgentMetadataSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)

    fun isRendererClientSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)

    fun isSafeBrowsingSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)

    fun isWebAuthnSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)

    fun inspectChromiumCapabilities(context: Context): ChromiumCapabilitySnapshot {
        val pkg = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
        return ChromiumCapabilitySnapshot(
            packageName = pkg?.packageName ?: "com.google.android.webview",
            versionName = pkg?.versionName ?: "System Default",
            multiProfile = isMultiProfileSupported(),
            proxyOverride = isProxyOverrideSupported(),
            documentStartScript = isDocumentStartScriptSupported(),
            userAgentMetadata = isUserAgentMetadataSupported(),
            rendererClient = isRendererClientSupported(),
            safeBrowsing = isSafeBrowsingSupported(),
            webAuthn = isWebAuthnSupported()
        )
    }

    fun warmUpChromium(context: Context) {
        if (warmUpRequested) return
        warmUpRequested = true
        runCatching {
            // Inspect package first cleanly and trigger asynchronous Chromium initialization on a background executor
            WebViewCompat.getCurrentWebViewPackage(context.applicationContext)
            val config = WebViewStartUpConfig.Builder(Executors.newSingleThreadExecutor()).build()
            WebViewCompat.startUpWebView(
                config,
                WebViewCompat.WebViewStartUpCallback { _ ->
                    // Chromium engine libraries and ICU data initialized asynchronously before first tab opens.
                }
            )
        }
    }

    fun resolveEffectiveProxy(
        profile: BrowserProfileEntity,
        vaultProxy: ProxyEntity?
    ): ProxyEntity? {
        val connType = runCatching {
            ProfileProxyConnectionType.valueOf(profile.proxyConnectionType)
        }.getOrDefault(ProfileProxyConnectionType.WITHOUT_PROXY)

        return when (connType) {
            ProfileProxyConnectionType.WITHOUT_PROXY -> null
            ProfileProxyConnectionType.VAULT -> vaultProxy
            ProfileProxyConnectionType.YOUR_PROXY -> {
                if (profile.inlineProxyHost.isBlank() || profile.inlineProxyPort !in 1..65535) {
                    null
                } else {
                    ProxyEntity(
                        id = -1L,
                        name = "Inline (${profile.inlineProxyHost})",
                        type = profile.inlineProxyType,
                        host = profile.inlineProxyHost.trim(),
                        port = profile.inlineProxyPort,
                        username = profile.inlineProxyUsername.trim(),
                        password = profile.inlineProxyPassword
                    )
                }
            }
        }
    }

    fun getProfileCookieManager(webView: WebView?): CookieManager {
        if (webView != null && isMultiProfileSupported()) {
            val profileCm = runCatching { WebViewCompat.getProfile(webView).cookieManager }.getOrNull()
            if (profileCm != null) return profileCm
        }
        return CookieManager.getInstance()
    }

    fun flushProfileCookies(webView: WebView?, profile: BrowserProfileEntity?) {
        if (profile?.sessionSaving != true) return
        val cm = runCatching { getProfileCookieManager(webView) }.getOrNull() ?: return
        cookieFlushExecutor.execute {
            runCatching { cm.flush() }
        }
    }

    fun extractMajorChromeVersion(userAgent: String): String {
        val regex = Regex("""(?:Chrome|Edg|CriOS)/(\d{2,3})""")
        return regex.find(userAgent)?.groupValues?.getOrNull(1) ?: "132"
    }

    fun resolveOsClientHintSpec(profile: BrowserProfileEntity): OsClientHintSpec {
        val os = runCatching { OsPlatform.valueOf(profile.osPlatform) }.getOrDefault(OsPlatform.ANDROID)
        return when (os) {
            OsPlatform.WINDOWS, OsPlatform.WINDOWS_11 -> OsClientHintSpec(
                platform = "Windows",
                platformVersion = if (os == OsPlatform.WINDOWS_11) "15.0.0" else "10.0.0",
                architecture = "x86",
                bitness = 64,
                isMobile = false
            )
            OsPlatform.MACOS, OsPlatform.MACOS_M1, OsPlatform.MACOS_M2, OsPlatform.MACOS_M3 -> OsClientHintSpec(
                platform = "macOS",
                platformVersion = "14.5.0",
                architecture = if (os == OsPlatform.MACOS) "x86" else "arm",
                bitness = 64,
                isMobile = false
            )
            OsPlatform.LINUX -> OsClientHintSpec(
                platform = "Linux",
                platformVersion = "6.5.0",
                architecture = "x86",
                bitness = 64,
                isMobile = false
            )
            OsPlatform.IOS -> OsClientHintSpec(
                platform = "iOS",
                platformVersion = "18.0.0",
                architecture = "arm",
                bitness = 64,
                isMobile = true
            )
            OsPlatform.ANDROID -> OsClientHintSpec(
                platform = "Android",
                platformVersion = "15.0.0",
                architecture = "arm",
                bitness = 64,
                isMobile = true
            )
        }
    }

    fun buildUserAgentMetadata(profile: BrowserProfileEntity): UserAgentMetadata {
        val major = extractMajorChromeVersion(profile.userAgent)
        val fullVer = "$major.0.0.0"
        val osSpec = resolveOsClientHintSpec(profile)
        val engine = runCatching { BrowserEngine.valueOf(profile.browserEngine) }.getOrDefault(BrowserEngine.CHROME)

        val primaryBrandName = when (engine) {
            BrowserEngine.EDGE -> "Microsoft Edge"
            else -> "Google Chrome"
        }
        val brands = listOf(
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Not A(Brand")
                .setMajorVersion("8")
                .setFullVersion("8.0.0.0")
                .build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Chromium")
                .setMajorVersion(major)
                .setFullVersion(fullVer)
                .build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(primaryBrandName)
                .setMajorVersion(major)
                .setFullVersion(fullVer)
                .build()
        )

        return UserAgentMetadata.Builder()
            .setBrandVersionList(brands)
            .setFullVersion(fullVer)
            .setPlatform(osSpec.platform)
            .setPlatformVersion(osSpec.platformVersion)
            .setArchitecture(osSpec.architecture)
            .setBitness(osSpec.bitness)
            .setMobile(osSpec.isMobile)
            .setModel(if (osSpec.isMobile) profile.deviceModel else "")
            .setWow64(false)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureIsolatedWebView(
        webView: WebView,
        profile: BrowserProfileEntity,
        proxy: ProxyEntity?,
        blockThirdPartyCookies: Boolean,
        onRendererUnresponsive: (() -> Unit)? = null
    ): ScriptHandler? {
        if (isMultiProfileSupported()) {
            runCatching {
                WebViewCompat.setProfile(webView, profile.webViewProfileName)
            }
        }

        val cookieManager = getProfileCookieManager(webView)
        cookieManager.setAcceptCookie(profile.sessionSaving)
        cookieManager.setAcceptThirdPartyCookies(webView, !blockThirdPartyCookies && profile.sessionSaving)

        val isMobileProfile = runCatching {
            OsPlatform.valueOf(profile.osPlatform).isMobile
        }.getOrDefault(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = profile.localStorageAndIndexedDb
            databaseEnabled = profile.localStorageAndIndexedDb
            userAgentString = profile.userAgent
            useWideViewPort = true
            loadWithOverviewMode = !isMobileProfile
            layoutAlgorithm = if (isMobileProfile) {
                WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            } else {
                WebSettings.LayoutAlgorithm.NORMAL
            }
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(profile.geolocationMode != "BLOCK")

            if (isSafeBrowsingSupported()) {
                runCatching {
                    WebSettingsCompat.setSafeBrowsingEnabled(this, profile.enableGoogleServices)
                }
            }
            if (isWebAuthnSupported()) {
                runCatching {
                    WebSettingsCompat.setWebAuthenticationSupport(
                        this,
                        WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                    )
                }
            }
            if (isUserAgentMetadataSupported()) {
                runCatching {
                    WebSettingsCompat.setUserAgentMetadata(this, buildUserAgentMetadata(profile))
                }
            }
        }

        if (isRendererClientSupported()) {
            runCatching {
                WebViewCompat.setWebViewRenderProcessClient(
                    webView,
                    mainExecutor,
                    object : WebViewRenderProcessClient() {
                        override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                            onRendererUnresponsive?.invoke()
                            runCatching { renderer?.terminate() }
                        }

                        override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) = Unit
                    }
                )
            }
        }

        val script = FingerprintScriptBuilder.buildDocumentStartScript(
            profile = profile,
            activeExitIp = proxy?.lastExitIp
        )
        return if (isDocumentStartScriptSupported()) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            }.getOrNull()
        } else {
            null
        }
    }

    private fun buildLoopbackProxyConfig(localPort: Int): ProxyConfig {
        return ProxyConfig.Builder()
            .addProxyRule("http://127.0.0.1:$localPort")
            .addBypassRule("cloakdeck.local")
            .addBypassRule("localhost")
            .addBypassRule("127.0.0.1")
            .build()
    }

    fun applyProxyRoute(
        proxy: ProxyEntity?,
        bridge: LocalProxyBridge,
        onApplied: (activeRuleLabel: String) -> Unit
    ) {
        val gen = routeGeneration.incrementAndGet()
        val completed = AtomicBoolean(false)
        fun completeOnce(label: String) {
            if (completed.compareAndSet(false, true)) {
                mainExecutor.execute { onApplied(label) }
            }
        }

        val proxyType = proxy?.let {
            runCatching { ProxyType.valueOf(it.type) }.getOrDefault(ProxyType.AUTO)
        } ?: ProxyType.DIRECT

        if (!isProxyOverrideSupported()) {
            completeOnce("Proxy Override unsupported on this WebView")
            return
        }

        if (proxy == null || proxyType == ProxyType.DIRECT) {
            bridgeControlExecutor.execute {
                if (gen != routeGeneration.get()) {
                    completeOnce("Superseded")
                    return@execute
                }
                bridge.stop()
                activeOverridePort = -1
                mainExecutor.execute {
                    if (gen != routeGeneration.get()) {
                        completeOnce("Superseded")
                        return@execute
                    }
                    runCatching {
                        ProxyController.getInstance().clearProxyOverride(mainExecutor) {
                            completeOnce("DIRECT")
                        }
                    }.onFailure {
                        completeOnce("DIRECT")
                    }
                }
            }
            return
        }

        val effectiveProxy = proxy.copy(type = proxyType.name)
        bridgeControlExecutor.execute {
            if (gen != routeGeneration.get()) {
                completeOnce("Superseded")
                return@execute
            }
            val localPort = runCatching { bridge.start(effectiveProxy) }.getOrDefault(-1)
            if (localPort <= 0) {
                completeOnce("Bridge Fallback")
                return@execute
            }
            val label = "${proxyType.label}://${proxy.host}:${proxy.port}"
            if (activeOverridePort == localPort) {
                completeOnce(label)
                return@execute
            }
            val proxyConfig = buildLoopbackProxyConfig(localPort)
            mainExecutor.execute {
                if (gen != routeGeneration.get()) {
                    completeOnce("Superseded")
                    return@execute
                }
                runCatching {
                    ProxyController.getInstance().setProxyOverride(proxyConfig, mainExecutor) {
                        activeOverridePort = localPort
                        completeOnce(label)
                    }
                }.onFailure {
                    completeOnce("Bridge Fallback")
                }
            }
        }
    }

    suspend fun applyProxyRouteAwait(
        proxy: ProxyEntity?,
        bridge: LocalProxyBridge,
        timeoutMs: Long = 3_000L
    ): String {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                applyProxyRoute(proxy, bridge) { label ->
                    if (cont.isActive) cont.resume(label)
                }
            }
        } ?: if (proxy == null) "DIRECT" else "${proxy.type}://${proxy.host}:${proxy.port}"
    }

    fun applyPooledProxyRoute(
        proxies: List<ProxyEntity>,
        bridge: LocalProxyBridge,
        onApplied: (activeRuleLabel: String) -> Unit
    ) {
        val valid = proxies.filter {
            val pt = runCatching { ProxyType.valueOf(it.type) }.getOrDefault(ProxyType.AUTO)
            pt != ProxyType.DIRECT && it.host.isNotBlank() && it.port in 1..65535
        }

        if (valid.isEmpty() || !isProxyOverrideSupported()) {
            applyProxyRoute(valid.firstOrNull(), bridge, onApplied)
            return
        }

        val gen = routeGeneration.incrementAndGet()
        val completed = AtomicBoolean(false)
        fun completeOnce(label: String) {
            if (completed.compareAndSet(false, true)) {
                mainExecutor.execute { onApplied(label) }
            }
        }

        bridgeControlExecutor.execute {
            if (gen != routeGeneration.get()) {
                completeOnce("Superseded")
                return@execute
            }
            val localPort = runCatching { bridge.startPool(valid) }.getOrDefault(-1)
            if (localPort <= 0) {
                completeOnce("Pool Bridge Fallback")
                return@execute
            }
            val label = "POOL (${valid.size} nodes)"
            if (activeOverridePort == localPort) {
                completeOnce(label)
                return@execute
            }
            val proxyConfig = buildLoopbackProxyConfig(localPort)
            mainExecutor.execute {
                if (gen != routeGeneration.get()) {
                    completeOnce("Superseded")
                    return@execute
                }
                runCatching {
                    ProxyController.getInstance().setProxyOverride(proxyConfig, mainExecutor) {
                        activeOverridePort = localPort
                        completeOnce(label)
                    }
                }.onFailure {
                    completeOnce("Pool Bridge Fallback")
                }
            }
        }
    }

    suspend fun applyPooledProxyRouteAwait(
        proxies: List<ProxyEntity>,
        bridge: LocalProxyBridge,
        timeoutMs: Long = 3_000L
    ): String {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                applyPooledProxyRoute(proxies, bridge) { label ->
                    if (cont.isActive) cont.resume(label)
                }
            }
        } ?: "POOL (${proxies.size} nodes)"
    }

    fun clearProxyRoute(bridge: LocalProxyBridge) {
        val gen = routeGeneration.incrementAndGet()
        bridgeControlExecutor.execute {
            if (gen != routeGeneration.get()) return@execute
            bridge.stop()
            activeOverridePort = -1
            if (isProxyOverrideSupported()) {
                mainExecutor.execute {
                    if (gen != routeGeneration.get()) return@execute
                    runCatching {
                        ProxyController.getInstance().clearProxyOverride(mainExecutor) {}
                    }
                }
            }
        }
    }

    fun clearProfileSessionData(webView: WebView?, profileName: String) {
        if (isMultiProfileSupported()) {
            if (webView != null) {
                runCatching {
                    val activeProfile = WebViewCompat.getProfile(webView)
                    activeProfile.cookieManager.removeAllCookies(null)
                    activeProfile.cookieManager.flush()
                    activeProfile.webStorage.deleteAllData()
                    webView.clearCache(true)
                }
            } else {
                runCatching {
                    ProfileStore.getInstance().deleteProfile(profileName)
                }
            }
        } else {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                webView?.clearCache(true)
            }
        }
    }

    fun buildRequestHeaders(profile: BrowserProfileEntity): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val langs = profile.languagesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (langs.isNotEmpty()) {
            val acceptLang = langs.mapIndexed { idx, lang ->
                if (idx == 0) lang else "$lang;q=${String.format(Locale.US, "%.1f", (0.9 - idx * 0.1).coerceAtLeast(0.1))}"
            }.joinToString(",")
            headers["Accept-Language"] = acceptLang
        }
        if (profile.doNotTrack) {
            headers["DNT"] = "1"
        }
        return headers
    }
}
