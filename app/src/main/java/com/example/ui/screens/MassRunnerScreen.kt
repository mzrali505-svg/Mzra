package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.browser.HumanizedAutomationRunner
import com.example.browser.ProfileWebViewManager
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.network.LocalProxyBridge
import com.example.data.repository.CloakDeckRepository
import com.example.service.CampaignForegroundService
import com.example.ui.MainViewModel
import com.example.ui.components.MonoSpecChip
import com.example.ui.components.StatusPill
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.StealthIndigo
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private enum class ScopeOption(val label: String, val limit: Int?) {
    ALL("All Profiles", null),
    FIRST_10("First 10", 10),
    FIRST_100("First 100", 100),
    FIRST_1000("First 1,000", 1000)
}

@Composable
fun MassRunnerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onGoToCaptchaSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profilesWithProxies by viewModel.profilesWithProxies.collectAsStateWithLifecycle()
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingLaunch by viewModel.pendingMassLaunch.collectAsStateWithLifecycle()
    val isBulkBusy by viewModel.isBulkBusy.collectAsStateWithLifecycle()

    var selectedGroup by remember { mutableStateOf("All") }
    var scopeOption by remember { mutableStateOf(ScopeOption.ALL) }
    var concurrency by remember { mutableIntStateOf(1) }
    var pagesPerProfileText by remember { mutableStateOf(settings.humanizePagesPerProfile.coerceIn(1, 50).toString()) }
    var overrideKeywordPool by remember { mutableStateOf("") }
    var overrideDomain by remember { mutableStateOf("") }
    var overrideDwellText by remember { mutableStateOf("") }
    var syncGeoBeforeEachProfile by remember { mutableStateOf(true) }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var pendingAutoLaunchGroup by remember { mutableStateOf<String?>(null) }
    var pendingAutoLaunchPages by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(settings.humanizePagesPerProfile) {
        val persisted = settings.humanizePagesPerProfile.coerceIn(1, 50).toString()
        if (pagesPerProfileText.isNotEmpty() && pagesPerProfileText != persisted) {
            pagesPerProfileText = persisted
        }
    }

    // Live Campaign Runtime State
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var totalQueuedProfiles by remember { mutableIntStateOf(0) }
    var completedProfiles by remember { mutableIntStateOf(0) }
    var targetTotalPages by remember { mutableIntStateOf(0) }
    var pagesScrolledTotal by remember { mutableIntStateOf(0) }
    var captchasSolvedTotal by remember { mutableIntStateOf(0) }
    var activeRouteLabel by remember { mutableStateOf("Idle") }
    var expandedSlotIdx by remember { mutableStateOf<Int?>(null) }

    val slotStates = remember { mutableStateMapOf<Int, MassSlotUiState>() }
    val slotRunners = remember { mutableMapOf<Int, HumanizedAutomationRunner>() }
    val slotWebViews = remember { mutableMapOf<Int, WebView?>() }
    val slotJobs = remember { mutableMapOf<Int, Job?>() }
    val activityLog = remember { mutableStateListOf<String>() }
    var masterJob by remember { mutableStateOf<Job?>(null) }
    val localBridge = remember { LocalProxyBridge() }

    val groups = remember(profilesWithProxies) {
        listOf("All") + profilesWithProxies.map { it.profile.groupTag }.distinct().sorted()
    }

    val selectedProfiles = remember(profilesWithProxies, selectedGroup, scopeOption) {
        val byGroup = if (selectedGroup == "All") {
            profilesWithProxies
        } else {
            profilesWithProxies.filter { it.profile.groupTag == selectedGroup }
        }
        val limit = scopeOption.limit
        if (limit != null) byGroup.take(limit) else byGroup
    }

    val pagesPerProfile = (pagesPerProfileText.toIntOrNull() ?: 1).coerceIn(1, 50)
    val previewTotalPages = selectedProfiles.size * pagesPerProfile

    fun appendLog(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        activityLog.add(0, "[$ts] $message")
        if (activityLog.size > 120) {
            activityLog.removeAt(activityLog.lastIndex)
        }
    }

    fun syncForegroundNotification() {
        CampaignForegroundService.updateProgress(
            context = context,
            title = "CloakDeck Fleet • $completedProfiles/$totalQueuedProfiles Profiles",
            body = "Pages: $pagesScrolledTotal/$targetTotalPages • CAPTCHAs Solved: $captchasSolvedTotal",
            progress = completedProfiles,
            max = totalQueuedProfiles.coerceAtLeast(1)
        )
    }

    fun destroySlotWebView(slotIdx: Int) {
        slotWebViews.remove(slotIdx)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            runCatching {
                wv.stopLoading()
                wv.destroy()
            }
        }
    }

    fun stopCampaign(stopService: Boolean = true, clearProxy: Boolean = true) {
        masterJob?.cancel()
        masterJob = null
        slotJobs.values.forEach { it?.cancel() }
        slotJobs.clear()
        slotRunners.values.forEach { it.stop() }
        slotWebViews.keys.toList().forEach { destroySlotWebView(it) }
        slotStates.clear()
        isRunning = false
        isPaused = false
        expandedSlotIdx = null
        if (clearProxy) {
            ProfileWebViewManager.clearProxyRoute(localBridge)
        }
        if (stopService) {
            CampaignForegroundService.stop(context)
        }
        appendLog("Campaign stopped.")
    }

    fun startCampaignNow(explicitPagesPerProfile: Int? = null) {
        val queue = selectedProfiles
        if (queue.isEmpty()) {
            viewModel.showBanner("No profiles selected — tap Generate 100 / 1,000 Profiles first")
            return
        }

        val activePagesPerProfile = (explicitPagesPerProfile ?: pagesPerProfileText.toIntOrNull() ?: settings.humanizePagesPerProfile).coerceIn(1, 50)
        stopCampaign(stopService = false, clearProxy = false)
        val workerCount = concurrency.coerceIn(1, queue.size.coerceAtMost(8))
        val overrideKeywords = overrideKeywordPool
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val hasCustomDwellSpec = overrideDwellText.any { it.isDigit() }
        val overrideDwellList = if (hasCustomDwellSpec) {
            CloakDeckRepository.parseCommaDurationsSeconds(overrideDwellText, fallbackSec = 45)
        } else {
            emptyList()
        }
        val safeDomainOverride = overrideDomain.trim()

        totalQueuedProfiles = queue.size
        completedProfiles = 0
        pagesScrolledTotal = 0
        captchasSolvedTotal = 0
        targetTotalPages = queue.size * activePagesPerProfile
        isRunning = true
        isPaused = false
        activityLog.clear()
        appendLog("Launched $workerCount live slot(s) for ${queue.size} profiles ($activePagesPerProfile pg/profile • $targetTotalPages total pages)")

        // Pre-populate runners on Main thread before spawning worker coroutines
        slotRunners.clear()
        for (slotIdx in 0 until workerCount) {
            slotRunners[slotIdx] = HumanizedAutomationRunner()
        }

        syncForegroundNotification()
        val queueCursor = AtomicInteger(0)

        masterJob = scope.launch(Dispatchers.Default) {
            if (workerCount > 1) {
                val allEffectiveProxies = queue.mapNotNull { item ->
                    ProfileWebViewManager.resolveEffectiveProxy(item.profile, item.proxy)
                }.ifEmpty { proxies }
                val poolLabel = ProfileWebViewManager.applyPooledProxyRouteAwait(allEffectiveProxies, localBridge)
                withContext(Dispatchers.Main) {
                    activeRouteLabel = poolLabel
                }
            }

            val workerJobs = (0 until workerCount).map { slotIdx ->
                val runner = slotRunners.getValue(slotIdx)

                val job = launch {
                    var firstSlotRun = true
                    while (isActive) {
                        val itemIndex = queueCursor.getAndIncrement()
                        if (itemIndex >= queue.size) break

                        // Stagger initial simultaneous Chromium WebView creation to prevent UI-thread freeze
                        if (firstSlotRun) {
                            firstSlotRun = false
                            if (workerCount > 1 && slotIdx > 0) {
                                delay(slotIdx * 450L)
                            }
                        } else {
                            delay(150L)
                        }

                        val item = queue[itemIndex]
                        var prof = item.profile
                        var resolvedProxy = ProfileWebViewManager.resolveEffectiveProxy(prof, item.proxy)

                        // In 1-by-1 Strict IP Lock mode: sync Proxy Exit IP -> Timezone/Locale/Geo/WebRTC BEFORE WebView opens
                        if (workerCount == 1) {
                            if (syncGeoBeforeEachProfile && resolvedProxy != null &&
                                (resolvedProxy.lastExitIp.isBlank() || prof.timezoneFillFromIp)
                            ) {
                                withContext(Dispatchers.Main) {
                                    slotStates[slotIdx] = MassSlotUiState(
                                        slotIndex = slotIdx,
                                        profile = null,
                                        proxy = resolvedProxy,
                                        note = "Verifying Proxy #${itemIndex + 1} (${resolvedProxy.host}) & locking Geo/TZ..."
                                    )
                                }
                                val checkRes = withContext(Dispatchers.IO) {
                                    com.example.data.network.ProxyChecker.checkProxy(resolvedProxy)
                                }
                                if (checkRes.success) {
                                    resolvedProxy = resolvedProxy.copy(
                                        type = checkRes.detectedType?.name ?: resolvedProxy.type,
                                        lastExitIp = checkRes.exitIp,
                                        country = checkRes.country,
                                        countryCode = checkRes.countryCode,
                                        city = checkRes.city,
                                        isp = checkRes.isp,
                                        timezone = checkRes.timezone,
                                        latitude = checkRes.latitude,
                                        longitude = checkRes.longitude,
                                        latencyMs = checkRes.latencyMs,
                                        status = com.example.data.model.ProxyStatus.ONLINE.name,
                                        lastCheckedAt = System.currentTimeMillis()
                                    )
                                    prof = viewModel.repository.applyProxyCheckResultToProfile(prof, checkRes)
                                    viewModel.repository.persistSyncedProxyAndProfile(resolvedProxy, prof)
                                    withContext(Dispatchers.Main) {
                                        appendLog("[#${itemIndex + 1} ${prof.name}] IP synced: ${checkRes.exitIp} (${checkRes.city.ifBlank { checkRes.country }}) • TZ=${prof.timezone}")
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        appendLog("[#${itemIndex + 1} ${prof.name}] Direct lookup skipped (${checkRes.errorMessage.take(36)}), routing via live bridge")
                                    }
                                }
                            }

                            val routeLabel = ProfileWebViewManager.applyProxyRouteAwait(resolvedProxy, localBridge)
                            withContext(Dispatchers.Main) {
                                activeRouteLabel = routeLabel
                            }
                        }

                        val assignedKw = when {
                            overrideKeywords.isNotEmpty() -> overrideKeywords[itemIndex % overrideKeywords.size]
                            prof.humanizeKeyword.isNotBlank() -> prof.humanizeKeyword
                            else -> "top cloud tools 2026"
                        }
                        val assignedDomain = safeDomainOverride.ifBlank { prof.humanizeTargetDomain }
                        val assignedDwell = if (overrideDwellList.isNotEmpty()) {
                            overrideDwellList[itemIndex % overrideDwellList.size]
                        } else {
                            prof.humanizeDwellSeconds.coerceIn(5, 3600)
                        }

                        val proxyTag = resolvedProxy?.let { "${it.host}:${it.port}" } ?: "DIRECT"
                        withContext(Dispatchers.Main) {
                            slotStates[slotIdx] = MassSlotUiState(
                                slotIndex = slotIdx,
                                profile = prof,
                                proxy = resolvedProxy,
                                progress = 0,
                                currentUrl = "https://www.google.com/ncr",
                                note = "Starting ${prof.name}..."
                            )
                            appendLog("[#${itemIndex + 1} ${prof.name}] Proxy=$proxyTag • $assignedKw • ${assignedDwell}s • $activePagesPerProfile pg")
                        }

                        // Wait cleanly until Compose mounts the slot's Chromium WebView (up to 4s)
                        var mountWaitMs = 0L
                        while (slotWebViews[slotIdx] == null && mountWaitMs < 4000L && isActive) {
                            delay(100)
                            mountWaitMs += 100
                        }

                        runner.runDirectHumanizeTask(
                            keyword = assignedKw,
                            targetDomain = assignedDomain,
                            dwellSeconds = assignedDwell,
                            clickInternalLink = activePagesPerProfile > 1 && prof.humanizeClickInternalLink,
                            profile = prof,
                            pagesPerProfile = activePagesPerProfile,
                            captchaConfig = settings.toCaptchaConfig(),
                            activeProxy = resolvedProxy,
                            onRotateProxySession = { rotatedProxy, newSsid ->
                                resolvedProxy = rotatedProxy
                                withContext(Dispatchers.Main) {
                                    slotStates[slotIdx]?.let { st ->
                                        slotStates[slotIdx] = st.copy(proxy = rotatedProxy)
                                    }
                                }
                                if (workerCount == 1) {
                                    val newLabel = ProfileWebViewManager.applyProxyRouteAwait(rotatedProxy, localBridge)
                                    withContext(Dispatchers.Main) {
                                        activeRouteLabel = newLabel
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    appendLog("[${prof.name}] Rotated proxy session -> $newSsid")
                                }
                                true
                            },
                            onPageScrolled = {
                                withContext(Dispatchers.Main) {
                                    pagesScrolledTotal++
                                    syncForegroundNotification()
                                }
                            },
                            onCaptchaSolved = { method ->
                                withContext(Dispatchers.Main) {
                                    captchasSolvedTotal++
                                    appendLog("[${prof.name}] CAPTCHA solved ($method)")
                                    syncForegroundNotification()
                                }
                            },
                            onLogEvent = { msg ->
                                withContext(Dispatchers.Main) {
                                    appendLog("[${prof.name}] $msg")
                                }
                            },
                            webViewProvider = { slotWebViews[slotIdx] },
                            onNavigate = { targetUrl ->
                                val wv = slotWebViews[slotIdx] ?: return@runDirectHumanizeTask
                                wv.loadUrl(targetUrl, ProfileWebViewManager.buildRequestHeaders(prof))
                            }
                        )

                        withContext(Dispatchers.Main) {
                            completedProfiles++
                            destroySlotWebView(slotIdx)
                            slotStates[slotIdx] = MassSlotUiState(
                                slotIndex = slotIdx,
                                profile = null,
                                note = "Auto-closed (${prof.name}) -> Next proxy..."
                            )
                            appendLog("[#${itemIndex + 1} ${prof.name}] Stay time complete -> Auto-closed cleanly & picking next proxy")
                            syncForegroundNotification()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        slotStates[slotIdx] = MassSlotUiState(
                            slotIndex = slotIdx,
                            profile = null,
                            note = "Auto-closed & queue finished"
                        )
                    }
                }
                slotJobs[slotIdx] = job
                job
            }

            try {
                workerJobs.joinAll()
                withContext(Dispatchers.Main) {
                    if (isRunning) {
                        isRunning = false
                        slotStates.clear()
                        ProfileWebViewManager.clearProxyRoute(localBridge)
                        CampaignForegroundService.stop(context)
                        appendLog("All $completedProfiles profiles auto-closed • $pagesScrolledTotal pages • $captchasSolvedTotal CAPTCHAs solved.")
                        viewModel.showBanner("Mass campaign complete: all $completedProfiles profiles auto-closed cleanly!")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun handleLaunchClick() {
        startCampaignNow()
    }

    LaunchedEffect(pendingLaunch) {
        val req = pendingLaunch ?: return@LaunchedEffect
        viewModel.consumePendingMassLaunch()
        selectedGroup = req.groupTag.ifBlank { "All" }
        scopeOption = ScopeOption.ALL
        concurrency = req.concurrency.coerceIn(1, 8)
        val reqPages = req.pagesPerProfile.coerceIn(1, 50)
        pagesPerProfileText = reqPages.toString()
        viewModel.setHumanizePagesPerProfile(reqPages)
        overrideKeywordPool = req.keywordPool
        overrideDomain = req.targetDomain
        overrideDwellText = req.dwellSecondsSpec
        syncGeoBeforeEachProfile = req.syncGeoBeforeEachProfile
        if (req.autoStart) {
            pendingAutoLaunchPages = reqPages
            pendingAutoLaunchGroup = selectedGroup
        }
    }

    LaunchedEffect(profilesWithProxies, pendingAutoLaunchGroup) {
        val targetGroup = pendingAutoLaunchGroup ?: return@LaunchedEffect
        val readyItems = if (targetGroup == "All") {
            profilesWithProxies
        } else {
            profilesWithProxies.filter { it.profile.groupTag == targetGroup }
        }
        if (readyItems.isNotEmpty() && selectedGroup == targetGroup && !isRunning) {
            val autoPages = pendingAutoLaunchPages
            pendingAutoLaunchGroup = null
            pendingAutoLaunchPages = null
            startCampaignNow(explicitPagesPerProfile = autoPages)
        }
    }

    BackHandler {
        stopCampaign()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopCampaign()
            localBridge.shutdown()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            stopCampaign()
                            onBack()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mass Fleet & CAPTCHA Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Run 100 – 1,000+ profiles simultaneously or IP-locked with auto-CAPTCHA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { showGenerateDialog = true },
                        enabled = !isRunning && !isBulkBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_generate_fleet_btn")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBulkBusy) "Preparing..." else "100/1000")
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isRunning) {
                        Button(
                            onClick = { handleLaunchClick() },
                            enabled = !isBulkBusy && selectedProfiles.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_mass_campaign_btn")
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isBulkBusy) "Preparing Fleet..." else "Launch ${selectedProfiles.size} Profiles ($concurrency×)")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                isPaused = !isPaused
                                slotRunners.values.forEach { it.togglePause() }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPaused) "Resume All" else "Pause All")
                        }
                        Button(
                            onClick = { stopCampaign() },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertCoral),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Fleet")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val gridColumns = if (maxWidth >= 700.dp) 3 else if (concurrency >= 2) 2 else 1

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Telemetry Stats Strip
                run {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MonoSpecChip(
                                    "PROFILES",
                                    if (isRunning || totalQueuedProfiles > 0) "$completedProfiles / $totalQueuedProfiles" else "${selectedProfiles.size} Ready",
                                    accent = CyberCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                MonoSpecChip(
                                    "PAGES SCROLLED",
                                    if (isRunning || targetTotalPages > 0) "$pagesScrolledTotal / $targetTotalPages" else "$previewTotalPages Planned",
                                    accent = VerifiedEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                                MonoSpecChip(
                                    "CAPTCHAs SOLVED",
                                    "$captchasSolvedTotal",
                                    accent = StealthIndigo,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (isRunning && totalQueuedProfiles > 0) {
                                val pct = completedProfiles.toFloat() / totalQueuedProfiles.toFloat()
                                LinearProgressIndicator(
                                    progress = { pct.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Security, contentDescription = null, tint = VerifiedEmerald, modifier = Modifier.size(16.dp))
                                    val solverDesc = buildString {
                                        append(settings.captchaSolverProvider.label)
                                        if (settings.rotateProxySessionOnCaptcha) append(" + Auto SSID Rotate")
                                    }
                                    Text(
                                        text = solverDesc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                StatusPill(
                                    text = if (isRunning) activeRouteLabel else "${proxies.size} Proxies",
                                    containerColor = CyberCyanContainer,
                                    contentColor = CyberCyan
                                )
                            }
                        }
                    }
                }

                // 1-Tap Order Card + Campaign Configuration Card (shown when idle)
                if (!isRunning) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp),
                        color = CyberCyanContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1-Tap 1,000 Proxies Order -> Distinct Profiles -> Run 1-by-1",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Text(
                                text = "Paste your proxies list, target domain, keyword, site time & pages (2–3). CloakDeck creates unique UA/RAM/ROM/Canvas/WebGL profiles and opens & closes them 1-by-1 automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = { showOrderDialog = true },
                                enabled = !isBulkBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_order_proxies_btn")
                            ) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isBulkBusy) "Preparing Profiles..." else "Paste Proxies & Launch 1-by-1 Order")
                            }
                        }
                    }

                    run {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 920.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Bulk Target, Comma Time & Auto-Close",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        onClick = onGoToCaptchaSettings,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("CAPTCHA Keys")
                                    }
                                }

                                OutlinedTextField(
                                    value = overrideKeywordPool,
                                    onValueChange = { overrideKeywordPool = it },
                                    label = { Text("Bulk Target Keyword(s) (comma/line)") },
                                    placeholder = { Text("e.g. best cloud hosting, seo tools (blank = profile default)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("mass_bulk_keyword_input")
                                )
                                OutlinedTextField(
                                    value = overrideDomain,
                                    onValueChange = { overrideDomain = it },
                                    label = { Text("Bulk Target Website / Domain") },
                                    placeholder = { Text("e.g. mybrand.com (blank = profile default)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("mass_bulk_domain_input")
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pagesPerProfileText,
                                        onValueChange = { raw ->
                                            val digits = raw.filter(Char::isDigit)
                                            pagesPerProfileText = digits
                                            digits.toIntOrNull()?.let { parsed ->
                                                viewModel.setHumanizePagesPerProfile(parsed.coerceIn(1, 50))
                                            }
                                        },
                                        label = { Text("Pages / Profile") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    OutlinedTextField(
                                        value = overrideDwellText,
                                        onValueChange = { raw ->
                                            overrideDwellText = raw.filter { it.isDigit() || it == ',' || it == ' ' }
                                        },
                                        label = { Text("Bulk Stay Sec (or 20,45,60)") },
                                        placeholder = { Text("20, 45, 60, 90") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .testTag("mass_bulk_dwell_input")
                                    )
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1 to "1 pg", 2 to "2 pgs", 3 to "3 pgs", 5 to "5 pgs").forEach { (p, lbl) ->
                                        FilterChip(
                                            selected = pagesPerProfileText == p.toString(),
                                            onClick = {
                                                pagesPerProfileText = p.toString()
                                                viewModel.setHumanizePagesPerProfile(p)
                                            },
                                            label = { Text(lbl) }
                                        )
                                    }
                                    listOf("30", "20, 45, 60, 90").forEach { spec ->
                                        FilterChip(
                                            selected = overrideDwellText.trim() == spec,
                                            onClick = { overrideDwellText = spec },
                                            label = { Text(if (',' in spec) spec else "${spec}s") }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Close Profile & Open Next When Time Completes",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Closes #1 automatically as soon as its humanized timer ends, switches to the next proxy & opens #2.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = settings.autoCloseProfileAfterHumanize,
                                        onCheckedChange = viewModel::setAutoCloseProfileAfterHumanize,
                                        modifier = Modifier.testTag("mass_auto_close_switch")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Open Target Domain Directly on CAPTCHA",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Skips waiting when CAPTCHA appears and immediately visits target website directly.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = settings.directOpenDomainOnCaptcha,
                                        onCheckedChange = viewModel::setDirectOpenDomainOnCaptcha,
                                        modifier = Modifier.testTag("mass_direct_captcha_switch")
                                    )
                                }

                                Text("Execution Mode (Sequential Auto-Close vs Parallel)", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        1 to "1× Sequential (Auto-Close -> Next)",
                                        2 to "2× Simultaneous",
                                        4 to "4× Simultaneous",
                                        6 to "6× Simultaneous",
                                        8 to "8× Simultaneous"
                                    ).forEach { (slots, lbl) ->
                                        FilterChip(
                                            selected = concurrency == slots,
                                            onClick = { concurrency = slots },
                                            label = { Text(lbl) }
                                        )
                                    }
                                }

                                Text("Profile Group Filter", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    groups.forEach { g ->
                                        FilterChip(
                                            selected = selectedGroup == g,
                                            onClick = { selectedGroup = g },
                                            label = { Text(g) }
                                        )
                                    }
                                }

                                Text("Queue Size", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ScopeOption.entries.forEach { opt ->
                                        FilterChip(
                                            selected = scopeOption == opt,
                                            onClick = { scopeOption = opt },
                                            label = { Text(opt.label) }
                                        )
                                    }
                                }

                                if (concurrency == 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Auto-Sync Proxy IP, Timezone & Location",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Resolves proxy exit IP before opening each profile so WebRTC, Timezone & GPS never leak real device location.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = syncGeoBeforeEachProfile,
                                            onCheckedChange = { syncGeoBeforeEachProfile = it }
                                        )
                                    }
                                }

                                if (profilesWithProxies.size < 20) {
                                    OutlinedButton(
                                        onClick = { showGenerateDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Need 100 or 1,000 Profiles? Generate Fleet in 1 Tap")
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Persistent WebView Slot Grid while running (non-recycled so scrolling never disposes WebViews)
                if (isRunning && slotStates.isNotEmpty()) {
                    val activeSlots = slotStates.values.sortedBy { it.slotIndex }
                    val rows = activeSlots.chunked(gridColumns)

                    rows.forEach { rowSlots ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 920.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowSlots.forEach { slot ->
                                val runner = slotRunners[slot.slotIndex]
                                if (runner != null) {
                                    MassSlotWorkerCard(
                                        slotState = slot,
                                        runner = runner,
                                        blockThirdPartyCookies = settings.blockThirdPartyCookies,
                                        isExpanded = expandedSlotIdx == slot.slotIndex,
                                        onWebViewReady = { wv -> slotWebViews[slot.slotIndex] = wv },
                                        onProgressChanged = { p ->
                                            slotStates[slot.slotIndex]?.let { cur ->
                                                if (p == 0 || p == 100 || kotlin.math.abs(p - cur.progress) >= 8) {
                                                    slotStates[slot.slotIndex] = cur.copy(progress = p)
                                                }
                                            }
                                        },
                                        onUrlChanged = { url ->
                                            slotStates[slot.slotIndex]?.let { cur ->
                                                if (cur.currentUrl != url) {
                                                    slotStates[slot.slotIndex] = cur.copy(currentUrl = url)
                                                }
                                            }
                                        },
                                        onRendererTerminated = {
                                            slotWebViews[slot.slotIndex] = null
                                            slotStates[slot.slotIndex]?.let { cur ->
                                                if (cur.rendererGeneration < 3) {
                                                    slotStates[slot.slotIndex] = cur.copy(
                                                        rendererGeneration = cur.rendererGeneration + 1,
                                                        note = "Recovering Chromium renderer..."
                                                    )
                                                    appendLog("[Slot ${slot.slotIndex + 1}] Chromium renderer reclaimed — recreating cleanly")
                                                }
                                            }
                                        },
                                        onToggleExpand = {
                                            expandedSlotIdx = if (expandedSlotIdx == slot.slotIndex) null else slot.slotIndex
                                        },
                                        onSkipSlot = {
                                            runner.stop()
                                        },
                                        onMainFrameError = { _, desc ->
                                            val diag = localBridge.lastFailureReason
                                            if (desc.contains("PROXY", ignoreCase = true) || diag.isNotBlank()) {
                                                val errMsg = diag.ifBlank { desc }
                                                slotStates[slot.slotIndex]?.let { cur ->
                                                    val noteText = "Proxy Error: $errMsg"
                                                    if (cur.note != noteText) {
                                                        slotStates[slot.slotIndex] = cur.copy(note = noteText)
                                                        appendLog("[Slot ${slot.slotIndex + 1}] $noteText")
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            repeat(gridColumns - rowSlots.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Live Event Feed
                if (activityLog.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Public, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                Text("Live Execution & CAPTCHA Feed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            activityLog.take(16).forEach { entry ->
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showGenerateDialog) {
        GenerateFleetDialog(
            proxyCount = proxies.size,
            onDismiss = { showGenerateDialog = false },
            onConfirm = { req ->
                showGenerateDialog = false
                viewModel.generateProfileFleet(
                    count = req.count,
                    namePrefix = req.namePrefix,
                    groupTag = req.groupTag,
                    keywordsPoolRaw = req.keywordsPool,
                    targetDomain = req.targetDomain,
                    minSeconds = req.minSeconds,
                    maxSeconds = req.maxSeconds,
                    bindVaultProxies = req.bindVaultProxies,
                    rotateStickySsidPerProfile = req.rotateStickySsid
                ) { created ->
                    selectedGroup = req.groupTag.ifBlank { "All" }
                    scopeOption = ScopeOption.ALL
                    appendLog("Generated $created isolated profiles in group '${req.groupTag}'")
                }
            }
        )
    }

    if (showOrderDialog) {
        ProxyOrderCampaignDialog(
            initialPagesPerProfile = pagesPerProfile,
            onDismiss = { showOrderDialog = false },
            onConfirm = { req ->
                showOrderDialog = false
                viewModel.setHumanizePagesPerProfile(req.pagesPerProfile)
                viewModel.submitProxyOrderCampaign(
                    rawProxiesText = req.rawProxiesText,
                    defaultType = req.defaultType,
                    keywordsPoolRaw = req.keywordsPool,
                    targetDomain = req.targetDomain,
                    dwellSecondsSpec = req.dwellSecondsSpec,
                    groupTag = req.groupTag
                ) { createdGroup, createdCount ->
                    if (createdCount > 0) {
                        selectedGroup = createdGroup
                        scopeOption = ScopeOption.ALL
                        concurrency = req.concurrency.coerceIn(1, 8)
                        val safeOrderPages = req.pagesPerProfile.coerceIn(1, 50)
                        pagesPerProfileText = safeOrderPages.toString()
                        overrideKeywordPool = ""
                        overrideDomain = ""
                        overrideDwellText = ""
                        syncGeoBeforeEachProfile = req.syncGeoBeforeLaunch
                        appendLog("Order created: $createdCount unique profiles in '$createdGroup' (${concurrency}× mode, $safeOrderPages pgs, times=${req.dwellSecondsSpec})")
                        if (req.autoStartCampaign) {
                            pendingAutoLaunchPages = safeOrderPages
                            pendingAutoLaunchGroup = createdGroup
                        }
                    }
                }
            }
        )
    }
}
