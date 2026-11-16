package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.HumanizeScrollStyleSelector
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.ProxyEntity
import com.example.data.model.FingerprintPresets
import com.example.data.model.GeolocationMode
import com.example.data.model.OsPlatform
import com.example.data.model.ProxyType
import com.example.data.model.WebRtcMode
import com.example.data.network.ProxyChecker
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.VerifiedEmerald
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun ProxyEditorScreen(
    proxyId: Long?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSaveAndLaunch: (profileId: Long) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var proxyDraft by remember {
        mutableStateOf(
            ProxyEntity(
                name = "",
                type = ProxyType.SOCKS5.name,
                host = "",
                port = 1080
            )
        )
    }
    var profileDraft by remember {
        mutableStateOf(FingerprintPresets.createRandomProfile(name = "Proxy Session"))
    }
    var loaded by remember { mutableStateOf(proxyId == null) }
    var syncProfileOnSave by remember { mutableStateOf(true) }

    var expandHumanize by remember { mutableStateOf(true) }
    var expandGeo by remember { mutableStateOf(true) }
    var expandWebRtc by remember { mutableStateOf(false) }
    var expandTimezone by remember { mutableStateOf(false) }
    var expandOs by remember { mutableStateOf(false) }
    var expandAdvanced by remember { mutableStateOf(false) }

    var checkLoading by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    var checkOk by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showTypeMenu by remember { mutableStateOf(false) }
    var showOsMenu by remember { mutableStateOf(false) }
    var showTimezoneMenu by remember { mutableStateOf(false) }

    LaunchedEffect(proxyId) {
        if (proxyId != null) {
            viewModel.repository.getProxyOnce(proxyId)?.let { existingProxy ->
                proxyDraft = existingProxy
            }
            viewModel.repository.getFirstProfileForProxy(proxyId)?.let { existingProfile ->
                profileDraft = existingProfile
            }
            loaded = true
        }
    }

    val currentProxyType = runCatching { ProxyType.valueOf(proxyDraft.type) }.getOrDefault(ProxyType.SOCKS5)
    val currentOs = runCatching { OsPlatform.valueOf(profileDraft.osPlatform) }.getOrDefault(OsPlatform.ANDROID)

    fun saveInternal(launchAfter: Boolean) {
        if (proxyDraft.host.isBlank()) {
            validationError = "Proxy IP / host is required."
            return
        }
        if (proxyDraft.port !in 1..65535) {
            validationError = "Port must be between 1 and 65535."
            return
        }
        validationError = null

        val finalProxy = proxyDraft.copy(
            name = proxyDraft.name.ifBlank { "${currentProxyType.label} (${proxyDraft.host})" },
            host = proxyDraft.host.trim(),
            username = proxyDraft.username.trim(),
            geolocationMode = profileDraft.geolocationMode,
            geolocationBasedOnIp = profileDraft.geolocationBasedOnIp,
            timezoneFillFromIp = profileDraft.timezoneFillFromIp,
            webRtcMode = profileDraft.webRtcMode,
            humanizeDwellSeconds = proxyDraft.humanizeDwellSeconds.coerceIn(3, 3600)
        )
        viewModel.saveProxyWithProfileTemplate(
            proxy = finalProxy,
            profileTemplate = profileDraft,
            syncToProfile = syncProfileOnSave || launchAfter,
            checkImmediately = true
        ) { _, savedProfileId ->
            if (launchAfter && savedProfileId != null) {
                onSaveAndLaunch(savedProfileId)
            } else {
                onSaved()
            }
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("proxy_editor_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (proxyId == null) "Add Proxy & Stealth Config" else "Edit Proxy & Stealth Config",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Proxy tunnel, Geo Block, WebRTC, Advanced Fingerprint & Humanized Search",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { saveInternal(launchAfter = false) },
                        enabled = loaded,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_proxy_confirm_btn")
                    ) {
                        Text("SAVE PROXY", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { saveInternal(launchAfter = true) },
                        enabled = loaded,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_and_launch_proxy_btn")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE & RUN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Proxy Node Credentials & Probe
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Proxy Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyberCyan)

                        OutlinedTextField(
                            value = proxyDraft.name,
                            onValueChange = { proxyDraft = proxyDraft.copy(name = it) },
                            label = { Text("Proxy label (optional, e.g. US-NYC Residential)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Box {
                            OutlinedTextField(
                                value = currentProxyType.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Type") },
                                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showTypeMenu = true })
                            DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                                listOf(ProxyType.AUTO, ProxyType.HTTP, ProxyType.HTTPS, ProxyType.SOCKS4, ProxyType.SOCKS5).forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.label) },
                                        onClick = {
                                            val newPort = if (proxyDraft.port == 0 || proxyDraft.port == currentProxyType.defaultPort) t.defaultPort else proxyDraft.port
                                            proxyDraft = proxyDraft.copy(type = t.name, port = newPort)
                                            showTypeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = proxyDraft.host,
                                onValueChange = { proxyDraft = proxyDraft.copy(host = it.trim()) },
                                label = { Text("IP address / Host") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.8f)
                                    .testTag("proxy_host_input")
                            )
                            OutlinedTextField(
                                value = if (proxyDraft.port == 0) "" else proxyDraft.port.toString(),
                                onValueChange = {
                                    proxyDraft = proxyDraft.copy(port = it.filter(Char::isDigit).toIntOrNull() ?: 0)
                                },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("proxy_port_input")
                            )
                            IconButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text.orEmpty()
                                    ProxyChecker.parseSingleProxyLine(clip, currentProxyType)?.let { parsed ->
                                        proxyDraft = proxyDraft.copy(
                                            type = parsed.type,
                                            host = parsed.host,
                                            port = parsed.port,
                                            username = parsed.username,
                                            password = parsed.password,
                                            humanizeEnabled = parsed.humanizeEnabled || proxyDraft.humanizeEnabled,
                                            humanizeDwellSeconds = if (parsed.humanizeEnabled) parsed.humanizeDwellSeconds else proxyDraft.humanizeDwellSeconds,
                                            humanizeKeyword = parsed.humanizeKeyword.ifBlank { proxyDraft.humanizeKeyword },
                                            humanizeTargetDomain = parsed.humanizeTargetDomain.ifBlank { proxyDraft.humanizeTargetDomain }
                                        )
                                        viewModel.showBanner("Parsed proxy from clipboard")
                                    } ?: viewModel.showBanner("Clipboard has no valid proxy string")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste proxy string")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = proxyDraft.username,
                                onValueChange = { proxyDraft = proxyDraft.copy(username = it) },
                                label = { Text("Proxy username") },
                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = proxyDraft.password,
                                onValueChange = { proxyDraft = proxyDraft.copy(password = it) },
                                label = { Text("Proxy password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (proxyDraft.host.isBlank() || proxyDraft.port !in 1..65535) {
                                    checkOk = false
                                    checkMessage = "Enter a valid IP/host and port first."
                                    return@OutlinedButton
                                }
                                checkLoading = true
                                checkMessage = "Probing ${proxyDraft.host}:${proxyDraft.port}..."
                                scope.launch {
                                    val res = viewModel.repository.checkInlineProxy(proxyDraft)
                                    checkLoading = false
                                    checkOk = res.success
                                    if (res.success) {
                                        proxyDraft = proxyDraft.copy(
                                            type = res.detectedType?.name ?: proxyDraft.type,
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
                                        profileDraft = viewModel.repository.applyProxyCheckResultToProfile(profileDraft, res)
                                        val flag = FingerprintPresets.countryCodeToFlagEmoji(res.countryCode)
                                        checkMessage = "$flag Live (${res.detectedType?.label}): ${res.exitIp} • ${res.city}, ${res.country} (${res.latencyMs}ms)"
                                    } else {
                                        checkMessage = "Failed: ${res.errorMessage}"
                                    }
                                }
                            },
                            enabled = !checkLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (checkLoading) "CHECKING PROXY..." else "CHECK PROXY")
                        }

                        checkMessage?.let { msg ->
                            Text(msg, style = MaterialTheme.typography.labelSmall, color = if (checkOk) VerifiedEmerald else AlertCoral)
                        }
                        validationError?.let { err ->
                            Text(err, style = MaterialTheme.typography.labelSmall, color = AlertCoral)
                        }
                        SwitchSettingRow(
                            label = "Auto-sync & maintain isolated profile for this proxy",
                            checked = syncProfileOnSave,
                            onCheckedChange = { syncProfileOnSave = it }
                        )
                    }
                }
            }

            // 2. Humanized Google Search & Exact Dwell Duration (Seconds)
            item {
                AccordionCard(
                    title = "Humanized Google Search & Dwell Timer",
                    summary = if (proxyDraft.humanizeEnabled) {
                        "Google '${proxyDraft.humanizeKeyword.ifBlank { "auto" }}' • ${proxyDraft.humanizeDwellSeconds}s"
                    } else {
                        "Disabled"
                    },
                    expanded = expandHumanize,
                    onToggle = { expandHumanize = !expandHumanize }
                ) {
                    SwitchSettingRow(
                        label = "Enable Humanized Google Search & Visit on launch",
                        checked = proxyDraft.humanizeEnabled,
                        onCheckedChange = { proxyDraft = proxyDraft.copy(humanizeEnabled = it) }
                    )
                    OutlinedTextField(
                        value = proxyDraft.humanizeKeyword,
                        onValueChange = { proxyDraft = proxyDraft.copy(humanizeKeyword = it) },
                        label = { Text("Google Search Keyword") },
                        placeholder = { Text("e.g. best wireless earbuds 2026") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_humanize_keyword")
                    )
                    OutlinedTextField(
                        value = proxyDraft.humanizeTargetDomain,
                        onValueChange = { proxyDraft = proxyDraft.copy(humanizeTargetDomain = it) },
                        label = { Text("Suggested Website / Domain Hint (optional)") },
                        placeholder = { Text("Leave blank to click top suggested organic site") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = proxyDraft.humanizeDwellSeconds.toString(),
                        onValueChange = { raw ->
                            val sec = raw.filter(Char::isDigit).toIntOrNull()
                            if (sec != null) proxyDraft = proxyDraft.copy(humanizeDwellSeconds = sec.coerceIn(1, 3600))
                            else if (raw.isEmpty()) proxyDraft = proxyDraft.copy(humanizeDwellSeconds = 5)
                        },
                        label = { Text("Human Reading / Work Time (Exact Seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_humanize_seconds")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5 to "5s", 20 to "20s", 45 to "45s", 60 to "1m (60s)", 120 to "2m (120s)", 180 to "3m (180s)").forEach { (sec, label) ->
                            FilterChip(
                                selected = proxyDraft.humanizeDwellSeconds == sec,
                                onClick = { proxyDraft = proxyDraft.copy(humanizeDwellSeconds = sec, humanizeEnabled = true) },
                                label = { Text(label) }
                            )
                        }
                    }
                    SwitchSettingRow(
                        label = "Click internal site link mid-session & continue reading",
                        checked = proxyDraft.humanizeClickInternalLink,
                        onCheckedChange = { proxyDraft = proxyDraft.copy(humanizeClickInternalLink = it) }
                    )
                    HumanizeScrollStyleSelector(
                        selectedStyleName = proxyDraft.humanizeScrollStyle,
                        onSelectStyle = { style -> proxyDraft = proxyDraft.copy(humanizeScrollStyle = style.name) }
                    )
                }
            }

            // 3. Geolocation (Directly below Proxy & Humanize!)
            item {
                AccordionCard(
                    title = "Geolocation",
                    summary = "${profileDraft.geolocationMode} • ${if (profileDraft.geolocationBasedOnIp) "Based on IP" else "Custom"}",
                    expanded = expandGeo,
                    onToggle = { expandGeo = !expandGeo }
                ) {
                    SegmentedOptionRow(
                        title = null,
                        options = listOf(
                            GeolocationMode.PROMPT.name to "PROMPT",
                            GeolocationMode.ALLOW.name to "ALLOW",
                            GeolocationMode.BLOCK.name to "BLOCK"
                        ),
                        selected = profileDraft.geolocationMode,
                        onSelect = { profileDraft = profileDraft.copy(geolocationMode = it) }
                    )
                    SwitchSettingRow(
                        label = "Geolocation based on IP",
                        checked = profileDraft.geolocationBasedOnIp,
                        onCheckedChange = { profileDraft = profileDraft.copy(geolocationBasedOnIp = it) }
                    )
                    if (!profileDraft.geolocationBasedOnIp && profileDraft.geolocationMode != GeolocationMode.BLOCK.name) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = String.format(java.util.Locale.US, "%.4f", profileDraft.latitude),
                                onValueChange = { it.toDoubleOrNull()?.let { v -> profileDraft = profileDraft.copy(latitude = v) } },
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = String.format(java.util.Locale.US, "%.4f", profileDraft.longitude),
                                onValueChange = { it.toDoubleOrNull()?.let { v -> profileDraft = profileDraft.copy(longitude = v) } },
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. WebRTC
            item {
                AccordionCard(
                    title = "WebRTC",
                    summary = runCatching { WebRtcMode.valueOf(profileDraft.webRtcMode).label }.getOrDefault(profileDraft.webRtcMode),
                    expanded = expandWebRtc,
                    onToggle = { expandWebRtc = !expandWebRtc }
                ) {
                    SegmentedOptionRow(
                        title = null,
                        options = listOf(
                            WebRtcMode.BASED_ON_IP.name to "BASED ON IP",
                            WebRtcMode.OFF.name to "OFF"
                        ),
                        selected = profileDraft.webRtcMode,
                        onSelect = { profileDraft = profileDraft.copy(webRtcMode = it) }
                    )
                }
            }

            // 5. Timezone
            item {
                AccordionCard(
                    title = "Timezone",
                    summary = if (profileDraft.timezoneFillFromIp) "Fill based on IP" else profileDraft.timezone,
                    expanded = expandTimezone,
                    onToggle = { expandTimezone = !expandTimezone }
                ) {
                    SwitchSettingRow(
                        label = "Fill based on IP",
                        checked = profileDraft.timezoneFillFromIp,
                        onCheckedChange = { profileDraft = profileDraft.copy(timezoneFillFromIp = it) }
                    )
                    if (!profileDraft.timezoneFillFromIp) {
                        Box {
                            OutlinedTextField(
                                value = profileDraft.timezone,
                                onValueChange = { profileDraft = profileDraft.copy(timezone = it) },
                                label = { Text("IANA Timezone") },
                                trailingIcon = {
                                    IconButton(onClick = { showTimezoneMenu = true }) {
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick timezone")
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = showTimezoneMenu, onDismissRequest = { showTimezoneMenu = false }) {
                                FingerprintPresets.geoPresets.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text("${g.label} (${g.timezone})") },
                                        onClick = {
                                            profileDraft = profileDraft.copy(timezone = g.timezone)
                                            showTimezoneMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Operating System & Fingerprint
            item {
                AccordionCard(
                    title = "Operating System & Fingerprint",
                    summary = currentOs.displayName,
                    expanded = expandOs,
                    onToggle = { expandOs = !expandOs }
                ) {
                    Box {
                        OutlinedTextField(
                            value = currentOs.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showOsMenu = true })
                        DropdownMenu(expanded = showOsMenu, onDismissRequest = { showOsMenu = false }) {
                            OsPlatform.entries.forEach { os ->
                                DropdownMenuItem(
                                    text = { Text(os.displayName) },
                                    onClick = {
                                        val bp = FingerprintPresets.blueprintForOs(os)
                                        profileDraft = profileDraft.copy(
                                            osPlatform = os.name,
                                            browserEngine = bp.browserEngine.name,
                                            userAgent = bp.userAgent,
                                            platformString = os.navigatorPlatform,
                                            vendorString = bp.vendor,
                                            deviceModel = bp.deviceModel,
                                            screenWidth = bp.screenWidth,
                                            screenHeight = bp.screenHeight,
                                            devicePixelRatio = bp.dpr,
                                            hardwareConcurrency = bp.cpuCores,
                                            deviceMemory = bp.ramGb,
                                            maxTouchPoints = bp.maxTouchPoints,
                                            webGlVendor = bp.webGlVendor,
                                            webGlRenderer = bp.webGlRenderer,
                                            fontsListCsv = bp.fontsCsv,
                                            batterySpoofEnabled = os.isMobile
                                        )
                                        showOsMenu = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val bp = FingerprintPresets.blueprintForOs(currentOs)
                            val rng = Random(System.nanoTime())
                            profileDraft = profileDraft.copy(
                                userAgent = bp.userAgent,
                                platformString = currentOs.navigatorPlatform,
                                vendorString = bp.vendor,
                                deviceModel = bp.deviceModel,
                                screenWidth = bp.screenWidth,
                                screenHeight = bp.screenHeight,
                                devicePixelRatio = bp.dpr,
                                hardwareConcurrency = bp.cpuCores,
                                deviceMemory = bp.ramGb,
                                maxTouchPoints = bp.maxTouchPoints,
                                webGlVendor = bp.webGlVendor,
                                webGlRenderer = bp.webGlRenderer,
                                canvasSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
                                audioSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
                                webGlSeed = abs(rng.nextLong(100_000L, 999_999_999L)),
                                clientRectsSeed = abs(rng.nextLong(100_000L, 999_999_999L))
                            )
                            viewModel.showBanner("Generated fresh fingerprint for ${currentOs.displayName}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("NEW FINGERPRINT")
                    }
                }
            }

            // 7. Advanced Settings (Full video suite right under Proxy!)
            item {
                AccordionCard(
                    title = "Advanced settings",
                    summary = "${profileDraft.screenWidth}x${profileDraft.screenHeight} • ${profileDraft.hardwareConcurrency}C/${profileDraft.deviceMemory}GB • ${profileDraft.webGlVendor}",
                    expanded = expandAdvanced,
                    onToggle = { expandAdvanced = !expandAdvanced }
                ) {
                    ProfileEditorAdvancedSection(
                        draft = profileDraft,
                        onDraftChange = { profileDraft = it },
                        onCopiedUa = { viewModel.showBanner("Copied User-Agent to clipboard") }
                    )
                }
            }
        }
    }
}
