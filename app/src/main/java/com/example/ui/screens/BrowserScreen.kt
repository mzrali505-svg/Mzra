package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.key
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.browser.FingerprintScriptBuilder
import com.example.browser.HumanizedAutomationRunner
import com.example.browser.LocalAuditPage
import com.example.browser.ProfileWebViewManager
import com.example.data.local.AutomationFlowEntity
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.model.FingerprintPresets
import com.example.data.model.HumanizeBehaviorResolver
import com.example.data.model.ProfileProxyConnectionType
import com.example.data.network.LocalProxyBridge
import com.example.ui.MainViewModel
import com.example.ui.components.MonoSpecChip
import com.example.ui.components.StatusPill
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    profileId: Long,
    initialUrl: String?,
    initialFlowId: Long?,
    batchMode: Boolean = false,
    viewModel: MainViewModel,
    onCloseBrowser: () -> Unit
) {
    var activeProfileId by remember(profileId) { mutableStateOf(profileId) }
    val batchQueue by viewModel.batchQueueProfileIds.collectAsStateWithLifecycle()
    val batchIndex by viewModel.batchQueueIndex.collectAsStateWithLifecycle()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val bookmarks by viewModel.repository.observeBookmarks(activeProfileId).collectAsStateWithLifecycle(emptyList())
    val history by viewModel.repository.observeHistory(activeProfileId).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var profile by remember { mutableStateOf<BrowserProfileEntity?>(null) }
    var proxy by remember { mutableStateOf<ProxyEntity?>(null) }
    var activeRouteLabel by remember { mutableStateOf("Initializing...") }
    var isReady by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var rendererGeneration by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(initialUrl ?: "https://myip.link") }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var showFingerprintSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showRpaSheet by remember { mutableStateOf(false) }
    var consumedInitialFlow by remember { mutableStateOf(false) }
    var userStoppedBatch by remember { mutableStateOf(false) }

    val localBridge = remember { LocalProxyBridge() }
    val rpaRunner = remember { HumanizedAutomationRunner() }
    val rpaState by rpaRunner.state.collectAsStateWithLifecycle()
    var rpaJob by remember { mutableStateOf<Job?>(null) }
    var proxyErrorBanner by remember { mutableStateOf<String?>(null) }

    fun navigateTo(raw: String) {
        val wv = webViewRef ?: return
        val prof = profile ?: return
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return

        val resolved = when {
            trimmed.equals("cloakdeck://verify", ignoreCase = true) -> "cloakdeck://verify"
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> settings.searchEngine.queryUrlTemplate.format(Uri.encode(trimmed))
        }

        proxyErrorBanner = null
        currentUrl = resolved
        urlInput = resolved
        focusManager.clearFocus()

        if (resolved == "cloakdeck://verify") {
            val html = LocalAuditPage.buildHtml(prof, proxy)
            wv.loadDataWithBaseURL("https://cloakdeck.local/", html, "text/html", "UTF-8", null)
        } else {
            wv.loadUrl(resolved, ProfileWebViewManager.buildRequestHeaders(prof))
        }
    }

    fun destroyActiveWebView() {
        webViewRef?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            runCatching {
                wv.stopLoading()
                wv.destroy()
            }
        }
        webViewRef = null
    }

    fun launchAutomationFlow(
        flow: AutomationFlowEntity,
        targetProfile: BrowserProfileEntity? = profile,
        targetProxy: ProxyEntity? = proxy
    ) {
        rpaJob?.cancel()
        rpaJob = scope.launch(Dispatchers.Default) {
            rpaRunner.runFlow(
                flow = flow,
                profile = targetProfile,
                pagesPerProfile = settings.humanizePagesPerProfile,
                captchaConfig = settings.toCaptchaConfig(),
                activeProxy = targetProxy,
                onRotateProxySession = { rotatedProxy, _ ->
                    withContext(Dispatchers.Main) { proxy = rotatedProxy }
                    val label = ProfileWebViewManager.applyProxyRouteAwait(rotatedProxy, localBridge)
                    withContext(Dispatchers.Main) { activeRouteLabel = label }
                    true
                },
                webViewProvider = { webViewRef },
                onNavigate = { targetUrl -> navigateTo(targetUrl) }
            )
        }
    }

    fun launchDirectHumanize(
        keyword: String,
        targetDomain: String,
        dwellSeconds: Int,
        clickInternal: Boolean,
        advanceBatchWhenDone: Boolean,
        targetProfile: BrowserProfileEntity? = profile,
        targetProxy: ProxyEntity? = proxy
    ) {
        rpaJob?.cancel()
        rpaJob = scope.launch(Dispatchers.Default) {
            rpaRunner.runDirectHumanizeTask(
                keyword = keyword,
                targetDomain = targetDomain,
                dwellSeconds = dwellSeconds,
                clickInternalLink = clickInternal && settings.humanizePagesPerProfile > 1,
                profile = targetProfile,
                pagesPerProfile = settings.humanizePagesPerProfile,
                captchaConfig = settings.toCaptchaConfig(),
                activeProxy = targetProxy,
                onRotateProxySession = { rotatedProxy, _ ->
                    withContext(Dispatchers.Main) { proxy = rotatedProxy }
                    val label = ProfileWebViewManager.applyProxyRouteAwait(rotatedProxy, localBridge)
                    withContext(Dispatchers.Main) { activeRouteLabel = label }
                    true
                },
                webViewProvider = { webViewRef },
                onNavigate = { targetUrl -> navigateTo(targetUrl) }
            )
            withContext(Dispatchers.Main) {
                if (advanceBatchWhenDone && !userStoppedBatch) {
                    val nextProfileId = viewModel.advanceBatchQueue()
                    if (nextProfileId != null) {
                        activeProfileId = nextProfileId
                    } else {
                        viewModel.showBanner("All profiles completed — auto-closing session")
                        if (settings.autoCloseProfileAfterHumanize) {
                            onCloseBrowser()
                        }
                    }
                } else if (!userStoppedBatch && settings.autoCloseProfileAfterHumanize && (targetProfile?.humanizeSearchEnabled == true || keyword.isNotBlank())) {
                    viewModel.showBanner("Humanized time (${dwellSeconds}s) finished — profile auto-closed")
                    onCloseBrowser()
                }
            }
        }
    }

    LaunchedEffect(activeProfileId) {
        isReady = false
        proxyErrorBanner = null
        rendererGeneration = 0
        rpaJob?.cancel()
        rpaRunner.stop()
        destroyActiveWebView()

        val loadedProfile = viewModel.repository.getProfileOnce(activeProfileId)
        if (loadedProfile == null) {
            onCloseBrowser()
            return@LaunchedEffect
        }
        viewModel.repository.markProfileLaunched(activeProfileId)

        val connType = runCatching {
            ProfileProxyConnectionType.valueOf(loadedProfile.proxyConnectionType)
        }.getOrDefault(ProfileProxyConnectionType.WITHOUT_PROXY)

        val vaultProxy = if (connType == ProfileProxyConnectionType.VAULT && loadedProfile.proxyId != null) {
            if (settings.autoVerifyProxyOnLaunch) {
                viewModel.repository.checkAndUpdateProxy(loadedProfile.proxyId)
            }
            viewModel.repository.getProxyOnce(loadedProfile.proxyId)
        } else null

        var effectiveProxy = ProfileWebViewManager.resolveEffectiveProxy(loadedProfile, vaultProxy)
        var effectiveProfile = loadedProfile

        if (connType == ProfileProxyConnectionType.YOUR_PROXY && effectiveProxy != null && settings.autoVerifyProxyOnLaunch) {
            val res = viewModel.repository.checkInlineProxy(effectiveProxy)
            if (res.success) {
                effectiveProxy = effectiveProxy.copy(
                    type = res.detectedType?.name ?: effectiveProxy.type,
                    lastExitIp = res.exitIp,
                    country = res.country,
                    countryCode = res.countryCode,
                    city = res.city,
                    isp = res.isp,
                    timezone = res.timezone,
                    latitude = res.latitude,
                    longitude = res.longitude,
                    latencyMs = res.latencyMs
                )
                effectiveProfile = viewModel.repository.applyProxyCheckResultToProfile(effectiveProfile, res)
                viewModel.repository.saveProfile(effectiveProfile)
            }
        } else if (connType == ProfileProxyConnectionType.VAULT && effectiveProxy != null && effectiveProxy.lastExitIp.isNotBlank()) {
            effectiveProfile = viewModel.repository.applyProxyGeoToProfile(effectiveProfile, effectiveProxy)
            viewModel.repository.saveProfile(effectiveProfile)
        }

        profile = effectiveProfile
        proxy = effectiveProxy

        val startTarget = if (!consumedInitialFlow && !initialUrl.isNullOrBlank()) {
            initialUrl
        } else {
            effectiveProfile.startUrl
        }
        currentUrl = startTarget
        urlInput = startTarget

        activeRouteLabel = ProfileWebViewManager.applyProxyRouteAwait(effectiveProxy, localBridge)
        isReady = true

        // Wait briefly for WebView mount, then trigger automation / humanize task
        delay(900)
        if (!consumedInitialFlow && initialFlowId != null) {
            consumedInitialFlow = true
            val flow = viewModel.repository.getAutomationOnce(initialFlowId)
            if (flow != null) {
                launchAutomationFlow(flow, effectiveProfile, effectiveProxy)
            }
        } else if (effectiveProfile.humanizeSearchEnabled || (batchMode && batchQueue.isNotEmpty())) {
            launchDirectHumanize(
                keyword = effectiveProfile.humanizeKeyword,
                targetDomain = effectiveProfile.humanizeTargetDomain,
                dwellSeconds = effectiveProfile.humanizeDwellSeconds,
                clickInternal = effectiveProfile.humanizeClickInternalLink,
                advanceBatchWhenDone = batchMode && batchQueue.isNotEmpty(),
                targetProfile = effectiveProfile,
                targetProxy = effectiveProxy
            )
        } else if (effectiveProfile.autoRunAutomationOnLaunch && effectiveProfile.automationFlowId != null) {
            val flow = viewModel.repository.getAutomationOnce(effectiveProfile.automationFlowId)
            if (flow != null) {
                launchAutomationFlow(flow, effectiveProfile, effectiveProxy)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            rpaJob?.cancel()
            rpaRunner.stop()
            viewModel.clearBatchQueue()
            ProfileWebViewManager.clearProxyRoute(localBridge)
            localBridge.shutdown()
            webViewRef?.let { wv ->
                if (profile?.sessionSaving == false) {
                    ProfileWebViewManager.clearProfileSessionData(wv, profile!!.webViewProfileName)
                }
            }
            destroyActiveWebView()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onCloseBrowser()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPill(
                            text = profile?.name ?: "Loading...",
                            containerColor = CyberCyanContainer,
                            contentColor = CyberCyan,
                            dotColor = CyberCyan,
                            modifier = Modifier
                                .clickable { showFingerprintSheet = true }
                                .testTag("browser_profile_pill")
                        )

                        val hasProxy = proxy != null
                        val flag = proxy?.countryCode?.let { FingerprintPresets.countryCodeToFlagEmoji(it) }.orEmpty()
                        val proxyChipText = if (hasProxy) {
                            val ip = proxy?.lastExitIp?.takeIf { it.isNotBlank() } ?: "${proxy?.host}:${proxy?.port}"
                            "$flag $ip"
                        } else {
                            "DIRECT"
                        }
                        StatusPill(
                            text = proxyChipText,
                            containerColor = if (hasProxy) VerifiedEmeraldBg else WarningAmberBg,
                            contentColor = if (hasProxy) VerifiedEmerald else WarningAmber,
                            dotColor = if (hasProxy) VerifiedEmerald else WarningAmber,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { showFingerprintSheet = true }
                                .testTag("browser_proxy_pill")
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { showRpaSheet = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("browser_rpa_btn")
                        ) {
                            Icon(
                                Icons.Rounded.SmartToy,
                                contentDescription = "Humanized RPA Automation",
                                tint = if (rpaState.isRunning) CyberCyan else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = onCloseBrowser,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("close_browser_btn")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close browser session")
                        }
                    }

                    // Live RPA & Batch Queue Execution Telemetry Banner
                    if (rpaState.isRunning || (batchMode && batchQueue.isNotEmpty())) {
                        Surface(
                            color = CyberCyanContainer.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val queueTag = if (batchMode && batchQueue.isNotEmpty()) {
                                        "Batch ${batchIndex + 1}/${batchQueue.size} • "
                                    } else ""
                                    val capTag = if (rpaState.captchasSolved > 0) " • ${rpaState.captchasSolved} CAPTCHA" else ""
                                    val secLeftTag = rpaState.remainingSeconds?.takeIf { rpaState.isRunning }?.let { sec ->
                                        if (settings.autoCloseProfileAfterHumanize) " • Auto-Close ${sec}s" else " • ${sec}s"
                                    }.orEmpty()
                                    Text(
                                        text = "$queueTag${rpaState.flowName.ifBlank { profile?.name ?: "Session" }}$capTag$secLeftTag",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (rpaState.captchaAlertText.isNotBlank()) WarningAmber else CyberCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = rpaState.captchaAlertText.ifBlank { rpaState.statusText.ifBlank { "Proxy active" } },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (rpaState.isRunning) {
                                    IconButton(onClick = { rpaRunner.togglePause() }, modifier = Modifier.size(30.dp)) {
                                        Icon(
                                            imageVector = if (rpaState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                            contentDescription = "Pause/Resume RPA",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (batchMode && batchQueue.size > 1) {
                                    IconButton(
                                        onClick = {
                                            rpaJob?.cancel()
                                            rpaRunner.stop()
                                            val nextId = viewModel.advanceBatchQueue()
                                            if (nextId != null) {
                                                activeProfileId = nextId
                                            } else {
                                                viewModel.showBanner("Reached end of proxy batch")
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = "Next Proxy in Batch",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        userStoppedBatch = true
                                        rpaJob?.cancel()
                                        rpaRunner.stop()
                                        viewModel.clearBatchQueue()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "Stop RPA", tint = AlertCoral, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Omnibox URL Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (currentUrl.startsWith("cloakdeck://")) Icons.Rounded.Shield else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(CyberCyan),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { navigateTo(urlInput) }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("browser_url_input")
                            )
                            IconButton(
                                onClick = {
                                    if (isLoading) webViewRef?.stopLoading() else navigateTo(urlInput)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                    contentDescription = if (isLoading) "Stop loading" else "Go or reload",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Quick Test & RPA Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = currentUrl == "cloakdeck://verify",
                            onClick = { navigateTo("cloakdeck://verify") },
                            leadingIcon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Self-Audit") }
                        )
                        FilterChip(
                            selected = currentUrl.contains("myip.link"),
                            onClick = { navigateTo("https://myip.link") },
                            label = { Text("MyIP.link") }
                        )
                        FilterChip(
                            selected = currentUrl.contains("browserleaks"),
                            onClick = { navigateTo("https://browserleaks.com/javascript") },
                            label = { Text("BrowserLeaks") }
                        )
                        FilterChip(
                            selected = currentUrl.contains("pixelscan"),
                            onClick = { navigateTo("https://pixelscan.net/") },
                            label = { Text("PixelScan") }
                        )
                        FilterChip(
                            selected = currentUrl.contains("whoer"),
                            onClick = { navigateTo("https://whoer.net/") },
                            label = { Text("Whoer.net") }
                        )
                    }

                    if (isLoading && progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(
                        onClick = { navigateTo("cloakdeck://verify") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("browser_home_audit_btn")
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = "Audit Home")
                    }
                    IconButton(
                        onClick = { showFingerprintSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("browser_fp_inspector_btn")
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = "Inspect active fingerprint", tint = CyberCyan)
                    }
                    IconButton(
                        onClick = { showBookmarksSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("browser_bookmarks_btn")
                    ) {
                        Icon(Icons.Rounded.Bookmarks, contentDescription = "Bookmarks & History")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val activeProfile = profile
            if (isReady && activeProfile != null) {
                key(activeProfileId, rendererGeneration) {
                    IsolatedProfileWebView(
                        profile = activeProfile,
                        proxy = proxy,
                        initialTarget = if (rendererGeneration > 0 && currentUrl.startsWith("http")) "about:blank" else currentUrl,
                        blockThirdPartyCookies = settings.blockThirdPartyCookies,
                        onWebViewCreated = { webViewRef = it },
                        onUrlChanged = { url, title ->
                            proxyErrorBanner = null
                            currentUrl = url
                            urlInput = if (url.startsWith("https://cloakdeck.local/")) "cloakdeck://verify" else url
                            pageTitle = title
                            canGoBack = webViewRef?.canGoBack() == true
                            canGoForward = webViewRef?.canGoForward() == true
                            if (activeProfile.historySaving && !url.startsWith("https://cloakdeck.local/") && !url.startsWith("cloakdeck://") && url != "about:blank") {
                                scope.launch { viewModel.repository.addHistory(activeProfileId, title, url) }
                            }
                        },
                        onProgress = { p ->
                            val nextLoading = p < 100
                            if (p == 0 || p == 100 || isLoading != nextLoading || kotlin.math.abs(p - progress) >= 5) {
                                progress = p
                                isLoading = nextLoading
                            }
                        },
                        onRendererTerminated = { didCrash, _ ->
                            webViewRef = null
                            if (rendererGeneration < 3) {
                                rendererGeneration++
                                viewModel.showBanner(
                                    if (didCrash) "Chromium renderer recovered cleanly" else "Low-memory Chromium renderer restored"
                                )
                            } else {
                                viewModel.showBanner("Renderer stopped after repeated crashes — tap Reload")
                            }
                        },
                        onMainFrameError = { _, desc ->
                            val bridgeReason = localBridge.lastFailureReason
                            if (desc.contains("PROXY", ignoreCase = true) || bridgeReason.isNotBlank()) {
                                proxyErrorBanner = bridgeReason.ifBlank { desc }
                            }
                        }
                    )
                }

                proxyErrorBanner?.let { errText ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        color = WarningAmberBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, WarningAmber)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Proxy Connection Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                            Text(
                                text = "$errText — check proxy host/port/auth or retry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        proxyErrorBanner = null
                                        scope.launch {
                                            activeRouteLabel = ProfileWebViewManager.applyProxyRouteAwait(proxy, localBridge)
                                            webViewRef?.reload()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry Proxy")
                                }
                                Button(
                                    onClick = {
                                        proxyErrorBanner = null
                                        proxy = null
                                        scope.launch {
                                            activeRouteLabel = ProfileWebViewManager.applyProxyRouteAwait(null, localBridge)
                                            navigateTo(currentUrl)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Direct")
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(40.dp)
                    )
                    Text("Isolating Container & Proxy Bridge...", style = MaterialTheme.typography.titleMedium)
                    Text(activeRouteLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showRpaSheet) {
        var quickKeyword by remember { mutableStateOf(profile?.humanizeKeyword ?: "") }
        var quickDomain by remember { mutableStateOf(profile?.humanizeTargetDomain ?: "") }
        var quickSeconds by remember { mutableStateOf((profile?.humanizeDwellSeconds ?: 30).toString()) }

        ModalBottomSheet(onDismissRequest = { showRpaSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Humanized Google Search & RPA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val activePersona = remember(profile) { HumanizeBehaviorResolver.resolve(profile) }
                Text(
                    "Active Scroll Persona: ${activePersona.style.displayName} (${String.format(java.util.Locale.US, "%.2fx", activePersona.paceMultiplier)} pace)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )
                Text(
                    "Search Google for a keyword, open a suggested site, and read it like a human for exact seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = quickKeyword,
                    onValueChange = { quickKeyword = it },
                    label = { Text("Google Search Keyword") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quickDomain,
                        onValueChange = { quickDomain = it },
                        label = { Text("Target Site (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                    OutlinedTextField(
                        value = quickSeconds,
                        onValueChange = { quickSeconds = it.filter(Char::isDigit) },
                        label = { Text("Seconds") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5 to "5s", 20 to "20s", 60 to "1m", 120 to "2m", 180 to "3m").forEach { (sec, lbl) ->
                        FilterChip(
                            selected = quickSeconds == sec.toString(),
                            onClick = { quickSeconds = sec.toString() },
                            label = { Text(lbl) }
                        )
                    }
                }
                Button(
                    onClick = {
                        showRpaSheet = false
                        launchDirectHumanize(
                            keyword = quickKeyword,
                            targetDomain = quickDomain,
                            dwellSeconds = (quickSeconds.toIntOrNull() ?: 30).coerceIn(3, 3600),
                            clickInternal = true,
                            advanceBatchWhenDone = false
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Humanized Search Now")
                }

                HorizontalDivider()
                Text("Saved RPA Flows", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                automations.forEach { flow ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRpaSheet = false
                                launchAutomationFlow(flow)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(flow.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(flow.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Run flow", tint = CyberCyan)
                        }
                    }
                }
            }
        }
    }

    if (showFingerprintSheet && profile != null) {
        val prof = profile!!
        val chromiumCaps = remember { ProfileWebViewManager.inspectChromiumCapabilities(context) }
        ModalBottomSheet(onDismissRequest = { showFingerprintSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Active Anti-Detect Telemetry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                MonoSpecChip("Chromium Engine", "${chromiumCaps.versionName} (${chromiumCaps.packageName})", accent = CyberCyan)
                val webkitFeatures = buildList {
                    if (chromiumCaps.multiProfile) add("Multi-Profile")
                    if (chromiumCaps.proxyOverride) add("Proxy Override")
                    if (chromiumCaps.documentStartScript) add("Doc-Start JS")
                    if (chromiumCaps.userAgentMetadata) add("UA Client Hints")
                    if (chromiumCaps.rendererClient) add("Renderer Guard")
                }.joinToString(" • ").ifBlank { "Compatibility Fallback" }
                MonoSpecChip("AndroidX Webkit Hooks", webkitFeatures, accent = VerifiedEmerald)
                MonoSpecChip("Proxy Route", activeRouteLabel)
                if (proxy != null && proxy!!.lastExitIp.isNotBlank()) {
                    MonoSpecChip("Exit IP", "${proxy!!.lastExitIp} (${proxy!!.city}, ${proxy!!.country})", accent = VerifiedEmerald)
                }
                MonoSpecChip("Profile Partition", prof.webViewProfileName)
                MonoSpecChip("Timezone / Locale", "${prof.timezone} • ${prof.language}")
                MonoSpecChip("Geolocation", "${String.format("%.4f", prof.latitude)}, ${String.format("%.4f", prof.longitude)}")
                MonoSpecChip("Screen / Hardware", "${prof.screenWidth}x${prof.screenHeight}@${prof.devicePixelRatio}x • ${prof.hardwareConcurrency}C/${prof.deviceMemory}GB")
                MonoSpecChip("MediaDevices", "Vid=${prof.videoInputsCount}, Mic=${prof.audioInputsCount}, Spk=${prof.audioOutputsCount}")
                MonoSpecChip("WebRTC / Canvas / Audio", "${prof.webRtcMode} • ${prof.canvasMode} • ${prof.audioMode}")
                Text(
                    text = "UA: ${prof.userAgent}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        ProfileWebViewManager.clearProfileSessionData(webViewRef, prof.webViewProfileName)
                        showFingerprintSheet = false
                        webViewRef?.reload()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Cookies & Cache for This Profile Only")
                }
            }
        }
    }

    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Profile Bookmarks & History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (profile?.bookmarkSaving != false) {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.repository.addBookmark(activeProfileId, pageTitle.ifBlank { currentUrl }, currentUrl)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Current")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (bookmarks.isNotEmpty()) {
                        item {
                            Text("Bookmarks", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                        }
                        items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showBookmarksSheet = false
                                        navigateTo(bm.url)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bm.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    TextButton(onClick = { scope.launch { viewModel.repository.deleteBookmark(bm.id) } }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("History (Isolated)", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                            if (history.isNotEmpty()) {
                                TextButton(onClick = { scope.launch { viewModel.repository.clearHistory(activeProfileId) } }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }

                    items(history, key = { "hist_${it.id}" }) { h ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBookmarksSheet = false
                                    navigateTo(h.url)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(h.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(h.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun IsolatedProfileWebView(
    profile: BrowserProfileEntity,
    proxy: ProxyEntity?,
    initialTarget: String,
    blockThirdPartyCookies: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onUrlChanged: (url: String, title: String) -> Unit,
    onProgress: (Int) -> Unit,
    onRendererTerminated: (didCrash: Boolean, lastUrl: String) -> Unit = { _, _ -> },
    onMainFrameError: (errorCode: Int, description: String) -> Unit = { _, _ -> }
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                ProfileWebViewManager.configureIsolatedWebView(
                    webView = this,
                    profile = profile,
                    proxy = proxy,
                    blockThirdPartyCookies = blockThirdPartyCookies
                )

                val fallbackJs = FingerprintScriptBuilder.buildDocumentStartScript(
                    profile = profile,
                    activeExitIp = proxy?.lastExitIp
                )
                var lastSeenUrl = initialTarget

                webViewClient = object : WebViewClientCompat() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url?.toString().orEmpty()
                        if (u.startsWith("cloakdeck://verify")) {
                            val html = LocalAuditPage.buildHtml(profile, proxy)
                            view.loadDataWithBaseURL("https://cloakdeck.local/", html, "text/html", "UTF-8", null)
                            return true
                        }
                        val lower = u.lowercase()
                        val isWebScheme = lower.startsWith("http://") ||
                            lower.startsWith("https://") ||
                            lower.startsWith("about:") ||
                            lower.startsWith("data:") ||
                            lower.startsWith("blob:")
                        return !isWebScheme
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (!url.isNullOrBlank()) {
                            lastSeenUrl = url
                        }
                        if (!ProfileWebViewManager.isDocumentStartScriptSupported()) {
                            view?.evaluateJavascript(fallbackJs, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!url.isNullOrBlank()) {
                            lastSeenUrl = url
                            onUrlChanged(url, view?.title.orEmpty())
                        }
                        ProfileWebViewManager.flushProfileCookies(view, profile)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceErrorCompat
                    ) {
                        if (request.isForMainFrame) {
                            val code = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) {
                                error.errorCode
                            } else {
                                -1
                            }
                            val desc = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
                                error.description?.toString().orEmpty()
                            } else {
                                ""
                            }
                            onMainFrameError(code, desc)
                        }
                    }

                    override fun onReceivedHttpAuthRequest(
                        view: WebView?,
                        handler: HttpAuthHandler?,
                        host: String?,
                        realm: String?
                    ) {
                        if (proxy != null && proxy.username.isNotBlank()) {
                            handler?.proceed(proxy.username, proxy.password)
                        } else {
                            super.onReceivedHttpAuthRequest(view, handler, host, realm)
                        }
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        (view?.parent as? ViewGroup)?.removeView(view)
                        runCatching { view?.destroy() }
                        onRendererTerminated(detail?.didCrash() == true, lastSeenUrl)
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        val allow = profile.geolocationMode != "BLOCK"
                        callback?.invoke(origin, allow, false)
                    }
                }

                onWebViewCreated(this)

                if (initialTarget == "cloakdeck://verify") {
                    val html = LocalAuditPage.buildHtml(profile, proxy)
                    loadDataWithBaseURL("https://cloakdeck.local/", html, "text/html", "UTF-8", null)
                } else {
                    loadUrl(initialTarget, ProfileWebViewManager.buildRequestHeaders(profile))
                }
            }
        },
        onRelease = { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            runCatching {
                wv.stopLoading()
                wv.destroy()
            }
        }
    )
}
