package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ProxyEntity
import com.example.data.model.FingerprintPresets
import com.example.data.model.GeolocationMode
import com.example.data.model.OsPlatform
import com.example.data.model.ProfileProxyConnectionType
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
fun ProfileEditorScreen(
    profileId: Long?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var draft by remember {
        mutableStateOf(FingerprintPresets.createRandomProfile(name = "Profile #1"))
    }
    var loaded by remember { mutableStateOf(profileId == null) }

    // Accordion expand states (collapsed by default on Create just like video, or user opens any)
    var expandOs by remember { mutableStateOf(true) }
    var expandProxy by remember { mutableStateOf(false) }
    var expandTimezone by remember { mutableStateOf(false) }
    var expandWebRtc by remember { mutableStateOf(false) }
    var expandGeo by remember { mutableStateOf(false) }
    var expandAdvanced by remember { mutableStateOf(false) }
    var expandAutomation by remember { mutableStateOf(false) }

    var inlineCheckLoading by remember { mutableStateOf(false) }
    var inlineCheckStatus by remember { mutableStateOf<String?>(null) }
    var inlineCheckOk by remember { mutableStateOf(true) }

    var showOsMenu by remember { mutableStateOf(false) }
    var showProxyTypeMenu by remember { mutableStateOf(false) }
    var showTimezoneMenu by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        if (profileId != null) {
            viewModel.repository.getProfileOnce(profileId)?.let { existing ->
                draft = existing
            }
            loaded = true
        }
    }

    val currentOs = runCatching { OsPlatform.valueOf(draft.osPlatform) }.getOrDefault(OsPlatform.ANDROID)
    val currentConnType = runCatching {
        ProfileProxyConnectionType.valueOf(draft.proxyConnectionType)
    }.getOrDefault(ProfileProxyConnectionType.WITHOUT_PROXY)
    val currentInlineProxyType = runCatching {
        ProxyType.valueOf(draft.inlineProxyType)
    }.getOrDefault(ProxyType.AUTO)

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
                            .testTag("editor_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = if (profileId == null) "Create profile" else "Edit profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Button(
                    onClick = { viewModel.saveProfile(draft) { id -> onSaved(id) } },
                    enabled = loaded && draft.name.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("save_profile_btn")
                ) {
                    Text(
                        text = if (profileId == null) "CREATE PROFILE" else "SAVE PROFILE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
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
            // Top Profile Name + Group
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_profile_name")
                    )
                    OutlinedTextField(
                        value = draft.groupTag,
                        onValueChange = { draft = draft.copy(groupTag = it) },
                        label = { Text("Workspace / Group tag") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 1. Operating System
            item {
                AccordionCard(
                    title = "Operating System",
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
                                        draft = draft.copy(
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
                            draft = draft.copy(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_fingerprint_btn")
                    ) {
                        Text("NEW FINGERPRINT")
                    }
                }
            }

            // 2. Proxy
            item {
                AccordionCard(
                    title = "Proxy",
                    summary = when (currentConnType) {
                        ProfileProxyConnectionType.WITHOUT_PROXY -> "Without proxy"
                        ProfileProxyConnectionType.YOUR_PROXY -> draft.inlineProxyHost.ifBlank { "Custom proxy" }
                        ProfileProxyConnectionType.VAULT -> proxies.firstOrNull { it.id == draft.proxyId }?.name ?: "Vault proxy"
                    },
                    expanded = expandProxy,
                    onToggle = { expandProxy = !expandProxy }
                ) {
                    Text("Connection type", style = MaterialTheme.typography.labelLarge)
                    Column {
                        ProfileProxyConnectionType.entries.forEach { connType ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { draft = draft.copy(proxyConnectionType = connType.name) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentConnType == connType,
                                    onClick = { draft = draft.copy(proxyConnectionType = connType.name) }
                                )
                                Text(connType.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (currentConnType == ProfileProxyConnectionType.VAULT) {
                        if (proxies.isEmpty()) {
                            Text(
                                text = "No proxies saved in Proxy Vault yet. Switch to 'Your proxy' below or add proxies in the Proxies tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                proxies.forEach { p ->
                                    val flag = FingerprintPresets.countryCodeToFlagEmoji(p.countryCode)
                                    FilterChip(
                                        selected = draft.proxyId == p.id,
                                        onClick = {
                                            var updated = draft.copy(proxyId = p.id)
                                            if (p.lastExitIp.isNotBlank()) {
                                                updated = viewModel.repository.applyProxyGeoToProfile(updated, p)
                                            }
                                            draft = updated
                                        },
                                        label = { Text("$flag ${p.name}") }
                                    )
                                }
                            }
                        }
                    }

                    if (currentConnType == ProfileProxyConnectionType.YOUR_PROXY) {
                        Text("Type", style = MaterialTheme.typography.labelMedium)
                        Box {
                            OutlinedTextField(
                                value = currentInlineProxyType.label,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { showProxyTypeMenu = true })
                            DropdownMenu(expanded = showProxyTypeMenu, onDismissRequest = { showProxyTypeMenu = false }) {
                                listOf(ProxyType.AUTO, ProxyType.HTTP, ProxyType.HTTPS, ProxyType.SOCKS4, ProxyType.SOCKS5).forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.label) },
                                        onClick = {
                                            draft = draft.copy(inlineProxyType = t.name)
                                            showProxyTypeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Text("Proxy host and port", style = MaterialTheme.typography.labelMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = draft.inlineProxyHost,
                                onValueChange = { draft = draft.copy(inlineProxyHost = it.trim()) },
                                placeholder = { Text("IP address") },
                                singleLine = true,
                                modifier = Modifier.weight(1.8f)
                            )
                            OutlinedTextField(
                                value = if (draft.inlineProxyPort == 0) "" else draft.inlineProxyPort.toString(),
                                onValueChange = {
                                    draft = draft.copy(inlineProxyPort = it.filter(Char::isDigit).toIntOrNull() ?: 0)
                                },
                                placeholder = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text.orEmpty()
                                    ProxyChecker.parseSingleProxyLine(clip, currentInlineProxyType)?.let { parsed ->
                                        draft = draft.copy(
                                            inlineProxyType = parsed.type,
                                            inlineProxyHost = parsed.host,
                                            inlineProxyPort = parsed.port,
                                            inlineProxyUsername = parsed.username,
                                            inlineProxyPassword = parsed.password
                                        )
                                        viewModel.showBanner("Parsed proxy from clipboard")
                                    } ?: viewModel.showBanner("Clipboard has no valid proxy string")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste proxy string")
                            }
                        }

                        OutlinedTextField(
                            value = draft.inlineProxyUsername,
                            onValueChange = { draft = draft.copy(inlineProxyUsername = it) },
                            label = { Text("Proxy username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.inlineProxyPassword,
                            onValueChange = { draft = draft.copy(inlineProxyPassword = it) },
                            label = { Text("Proxy password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = {
                                if (draft.inlineProxyHost.isBlank() || draft.inlineProxyPort !in 1..65535) {
                                    inlineCheckOk = false
                                    inlineCheckStatus = "Enter valid IP/host and port first."
                                    return@OutlinedButton
                                }
                                inlineCheckLoading = true
                                inlineCheckStatus = "Probing ${draft.inlineProxyHost}:${draft.inlineProxyPort}..."
                                scope.launch {
                                    val probeTarget = ProxyEntity(
                                        name = "Inline",
                                        type = draft.inlineProxyType,
                                        host = draft.inlineProxyHost,
                                        port = draft.inlineProxyPort,
                                        username = draft.inlineProxyUsername,
                                        password = draft.inlineProxyPassword
                                    )
                                    val res = viewModel.repository.checkInlineProxy(probeTarget)
                                    inlineCheckLoading = false
                                    inlineCheckOk = res.success
                                    if (res.success) {
                                        draft = viewModel.repository.applyProxyCheckResultToProfile(draft, res)
                                        val flag = FingerprintPresets.countryCodeToFlagEmoji(res.countryCode)
                                        inlineCheckStatus = "$flag Live (${res.detectedType?.label}): ${res.exitIp} • ${res.city}, ${res.country} (${res.latencyMs}ms)"
                                    } else {
                                        inlineCheckStatus = "Failed: ${res.errorMessage}"
                                    }
                                }
                            },
                            enabled = !inlineCheckLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inline_check_proxy_btn")
                        ) {
                            Text(if (inlineCheckLoading) "CHECKING PROXY..." else "CHECK PROXY")
                        }

                        inlineCheckStatus?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (inlineCheckOk) VerifiedEmerald else AlertCoral
                            )
                        }
                    }
                }
            }

            // 3. Timezone
            item {
                AccordionCard(
                    title = "Timezone",
                    summary = if (draft.timezoneFillFromIp) "Fill based on IP" else draft.timezone,
                    expanded = expandTimezone,
                    onToggle = { expandTimezone = !expandTimezone }
                ) {
                    SwitchSettingRow(
                        label = "Fill based on IP",
                        checked = draft.timezoneFillFromIp,
                        onCheckedChange = { draft = draft.copy(timezoneFillFromIp = it) }
                    )
                    if (!draft.timezoneFillFromIp) {
                        Box {
                            OutlinedTextField(
                                value = draft.timezone,
                                onValueChange = { draft = draft.copy(timezone = it) },
                                label = { Text("IANA Timezone") },
                                trailingIcon = {
                                    IconButton(onClick = { showTimezoneMenu = true }) {
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick timezone preset")
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
                                            draft = draft.copy(timezone = g.timezone)
                                            showTimezoneMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. WebRTC
            item {
                AccordionCard(
                    title = "WebRTC",
                    summary = runCatching { WebRtcMode.valueOf(draft.webRtcMode).label }.getOrDefault(draft.webRtcMode),
                    expanded = expandWebRtc,
                    onToggle = { expandWebRtc = !expandWebRtc }
                ) {
                    SegmentedOptionRow(
                        title = null,
                        options = listOf(
                            WebRtcMode.BASED_ON_IP.name to "BASED ON IP",
                            WebRtcMode.OFF.name to "OFF"
                        ),
                        selected = draft.webRtcMode,
                        onSelect = { draft = draft.copy(webRtcMode = it) }
                    )
                }
            }

            // 5. Geolocation
            item {
                AccordionCard(
                    title = "Geolocation",
                    summary = "${draft.geolocationMode} • ${if (draft.geolocationBasedOnIp) "Based on IP" else "Custom"}",
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
                        selected = draft.geolocationMode,
                        onSelect = { draft = draft.copy(geolocationMode = it) }
                    )
                    SwitchSettingRow(
                        label = "Geolocation based on IP",
                        checked = draft.geolocationBasedOnIp,
                        onCheckedChange = { draft = draft.copy(geolocationBasedOnIp = it) }
                    )
                    if (!draft.geolocationBasedOnIp && draft.geolocationMode != GeolocationMode.BLOCK.name) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = String.format(java.util.Locale.US, "%.4f", draft.latitude),
                                onValueChange = { it.toDoubleOrNull()?.let { v -> draft = draft.copy(latitude = v) } },
                                label = { Text("Latitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = String.format(java.util.Locale.US, "%.4f", draft.longitude),
                                onValueChange = { it.toDoubleOrNull()?.let { v -> draft = draft.copy(longitude = v) } },
                                label = { Text("Longitude") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 6. Advanced settings
            item {
                AccordionCard(
                    title = "Advanced settings",
                    summary = "${draft.screenWidth}x${draft.screenHeight} • ${draft.hardwareConcurrency}C/${draft.deviceMemory}GB • ${draft.webGlVendor}",
                    expanded = expandAdvanced,
                    onToggle = { expandAdvanced = !expandAdvanced }
                ) {
                    ProfileEditorAdvancedSection(
                        draft = draft,
                        onDraftChange = { draft = it },
                        onCopiedUa = { viewModel.showBanner("Copied User-Agent to clipboard") }
                    )
                }
            }

            // 7. Humanized Automation (RPA)
            item {
                AccordionCard(
                    title = "Humanized RPA Automation",
                    summary = when {
                        draft.humanizeSearchEnabled -> "Google '${draft.humanizeKeyword.ifBlank { "auto" }}' • ${draft.humanizeDwellSeconds}s"
                        draft.autoRunAutomationOnLaunch && draft.automationFlowId != null ->
                            "Auto-Run: " + (automations.firstOrNull { it.id == draft.automationFlowId }?.name ?: "Selected")
                        else -> "Manual / Off"
                    },
                    expanded = expandAutomation,
                    onToggle = { expandAutomation = !expandAutomation }
                ) {
                    SwitchSettingRow(
                        label = "Humanized Google Search & Visit on launch",
                        checked = draft.humanizeSearchEnabled,
                        onCheckedChange = { draft = draft.copy(humanizeSearchEnabled = it) }
                    )
                    if (draft.humanizeSearchEnabled) {
                        OutlinedTextField(
                            value = draft.humanizeKeyword,
                            onValueChange = { draft = draft.copy(humanizeKeyword = it) },
                            label = { Text("Google Search Keyword") },
                            placeholder = { Text("e.g. best cloud hosting tools") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.humanizeTargetDomain,
                            onValueChange = { draft = draft.copy(humanizeTargetDomain = it) },
                            label = { Text("Target / Suggested Website (optional)") },
                            placeholder = { Text("Leave blank to open top suggested result") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.humanizeDwellSeconds.toString(),
                            onValueChange = { raw ->
                                val sec = raw.filter(Char::isDigit).toIntOrNull()
                                if (sec != null) draft = draft.copy(humanizeDwellSeconds = sec.coerceIn(1, 3600))
                                else if (raw.isEmpty()) draft = draft.copy(humanizeDwellSeconds = 5)
                            },
                            label = { Text("Human Read / Stay Duration (Seconds)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5 to "5s", 20 to "20s", 45 to "45s", 60 to "1m", 120 to "2m", 180 to "3m").forEach { (sec, label) ->
                                FilterChip(
                                    selected = draft.humanizeDwellSeconds == sec,
                                    onClick = { draft = draft.copy(humanizeDwellSeconds = sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        SwitchSettingRow(
                            label = "Browse internal page links while reading",
                            checked = draft.humanizeClickInternalLink,
                            onCheckedChange = { draft = draft.copy(humanizeClickInternalLink = it) }
                        )
                        HumanizeScrollStyleSelector(
                            selectedStyleName = draft.humanizeScrollStyle,
                            onSelectStyle = { style -> draft = draft.copy(humanizeScrollStyle = style.name) }
                        )
                    }

                    SwitchSettingRow(
                        label = "Or Auto-run custom RPA Flow when profile opens",
                        checked = draft.autoRunAutomationOnLaunch,
                        onCheckedChange = { draft = draft.copy(autoRunAutomationOnLaunch = it) }
                    )
                    Text("Select Custom Automation Flow", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = draft.automationFlowId == null,
                            onClick = { draft = draft.copy(automationFlowId = null, autoRunAutomationOnLaunch = false) },
                            label = { Text("None") }
                        )
                        automations.forEach { flow ->
                            FilterChip(
                                selected = draft.automationFlowId == flow.id,
                                onClick = { draft = draft.copy(automationFlowId = flow.id) },
                                label = { Text(flow.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccordionCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!expanded && summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}
