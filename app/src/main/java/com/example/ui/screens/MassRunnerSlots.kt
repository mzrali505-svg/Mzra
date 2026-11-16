package com.example.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.browser.HumanizedAutomationRunner
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.model.HumanizeBehaviorResolver
import com.example.data.model.ProxyType
import com.example.data.network.ProxyChecker
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class MassSlotUiState(
    val slotIndex: Int,
    val profile: BrowserProfileEntity? = null,
    val proxy: ProxyEntity? = null,
    val progress: Int = 0,
    val currentUrl: String = "https://www.google.com/ncr",
    val note: String = "Idle",
    val rendererGeneration: Int = 0
)

@Composable
internal fun MassSlotWorkerCard(
    slotState: MassSlotUiState,
    runner: HumanizedAutomationRunner,
    blockThirdPartyCookies: Boolean,
    isExpanded: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onUrlChanged: (String) -> Unit,
    onRendererTerminated: () -> Unit,
    onToggleExpand: () -> Unit,
    onSkipSlot: () -> Unit,
    onMainFrameError: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val execState by runner.state.collectAsStateWithLifecycle()
    val activeProfile = slotState.profile

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mass_slot_${slotState.slotIndex}"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (execState.captchaAlertText.isNotBlank()) WarningAmber else CyberCyan.copy(alpha = 0.35f)
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill(
                    text = "SLOT ${slotState.slotIndex + 1}",
                    containerColor = CyberCyanContainer,
                    contentColor = CyberCyan,
                    dotColor = CyberCyan
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeProfile?.name ?: "Standby",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val persona = remember(activeProfile) { HumanizeBehaviorResolver.resolve(activeProfile) }
                    Text(
                        text = "${persona.style.displayName} • ${slotState.proxy?.host ?: "Pool/Direct"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (activeProfile != null) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                            contentDescription = if (isExpanded) "Shrink browser view" else "Expand browser view",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onSkipSlot, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Skip current profile",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            if (slotState.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { slotState.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Single persistent in-place WebView instance (expanding height inline never recreates or destroys WebView)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 520.dp else 230.dp)
            ) {
                if (activeProfile != null) {
                    key("${slotState.slotIndex}_${activeProfile.id}_${slotState.rendererGeneration}") {
                        IsolatedProfileWebView(
                            profile = activeProfile,
                            proxy = slotState.proxy,
                            initialTarget = slotState.currentUrl.ifBlank { "https://www.google.com/ncr" },
                            blockThirdPartyCookies = blockThirdPartyCookies,
                            onWebViewCreated = onWebViewReady,
                            onUrlChanged = { url, _ -> onUrlChanged(url) },
                            onProgress = onProgressChanged,
                            onRendererTerminated = { _, _ -> onRendererTerminated() },
                            onMainFrameError = onMainFrameError
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = slotState.note,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                color = if (execState.captchaAlertText.isNotBlank()) WarningAmber.copy(alpha = 0.16f) else VerifiedEmeraldBg.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = execState.captchaAlertText.ifBlank { execState.statusText.ifBlank { slotState.note } },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (execState.captchaAlertText.isNotBlank()) WarningAmber else VerifiedEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val secLeft = execState.remainingSeconds
                    if (secLeft != null && execState.isRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${secLeft}s -> CLOSE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VerifiedEmerald
                        )
                    }
                    if (execState.captchasSolved > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${execState.captchasSolved} CAP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                }
            }
        }
    }
}

internal data class FleetGenerateRequest(
    val count: Int,
    val namePrefix: String,
    val groupTag: String,
    val keywordsPool: String,
    val targetDomain: String,
    val minSeconds: Int,
    val maxSeconds: Int,
    val bindVaultProxies: Boolean,
    val rotateStickySsid: Boolean
)

@Composable
internal fun GenerateFleetDialog(
    proxyCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (FleetGenerateRequest) -> Unit
) {
    var countText by remember { mutableStateOf("100") }
    var namePrefix by remember { mutableStateOf("Fleet") }
    var groupTag by remember { mutableStateOf("Mass Fleet") }
    var keywordsPool by remember { mutableStateOf("") }
    var targetDomain by remember { mutableStateOf("") }
    var minSec by remember { mutableStateOf("15") }
    var maxSec by remember { mutableStateOf("60") }
    var bindProxies by remember { mutableStateOf(proxyCount > 0) }
    var rotateSsid by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate 100 – 1,000 Isolated Profiles") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Spins up unique User-Agents, Canvas/WebGL/Audio seeds, isolated cookie partitions & distinct scroll personas in 1 tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 50, 100, 500, 1000).forEach { preset ->
                        FilterChip(
                            selected = countText == preset.toString(),
                            onClick = { countText = preset.toString() },
                            label = { Text("$preset") }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit) },
                        label = { Text("Profile Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = groupTag,
                        onValueChange = { groupTag = it },
                        label = { Text("Group Tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }

                OutlinedTextField(
                    value = keywordsPool,
                    onValueChange = { keywordsPool = it },
                    label = { Text("Google Keyword(s) Pool (comma/line)") },
                    placeholder = { Text("e.g. cloud tools, seo audit, best apps") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetDomain,
                    onValueChange = { targetDomain = it },
                    label = { Text("Suggested Target Website / Domain (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minSec,
                        onValueChange = { minSec = it.filter(Char::isDigit) },
                        label = { Text("Min Sec") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxSec,
                        onValueChange = { maxSec = it.filter(Char::isDigit) },
                        label = { Text("Max Sec") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (proxyCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = bindProxies, onCheckedChange = { bindProxies = it })
                        Text("Distribute across $proxyCount Vault proxies", style = MaterialTheme.typography.bodySmall)
                    }
                    if (bindProxies) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = rotateSsid, onCheckedChange = { rotateSsid = it })
                            Text("Generate unique -ssid- IP session per profile (NiceProxy)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = (countText.toIntOrNull() ?: 100).coerceIn(1, 2000)
                    val mn = (minSec.toIntOrNull() ?: 15).coerceIn(5, 3600)
                    val mx = (maxSec.toIntOrNull() ?: 60).coerceAtLeast(mn).coerceAtMost(3600)
                    onConfirm(
                        FleetGenerateRequest(
                            count = count,
                            namePrefix = namePrefix,
                            groupTag = groupTag,
                            keywordsPool = keywordsPool,
                            targetDomain = targetDomain,
                            minSeconds = mn,
                            maxSeconds = mx,
                            bindVaultProxies = bindProxies,
                            rotateStickySsid = rotateSsid
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_generate_fleet_btn")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

internal data class ProxyOrderRequest(
    val rawProxiesText: String,
    val defaultType: ProxyType,
    val keywordsPool: String,
    val targetDomain: String,
    val dwellSecondsSpec: String,
    val pagesPerProfile: Int,
    val concurrency: Int,
    val groupTag: String,
    val syncGeoBeforeLaunch: Boolean,
    val autoStartCampaign: Boolean
)

@Composable
internal fun ProxyOrderCampaignDialog(
    initialPagesPerProfile: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (ProxyOrderRequest) -> Unit
) {
    val defaultTag = remember {
        "Order-" + SimpleDateFormat("HHmmss", Locale.US).format(Date())
    }
    var proxiesText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProxyType.AUTO) }
    var keywordsPool by remember { mutableStateOf("") }
    var targetDomain by remember { mutableStateOf("") }
    var dwellSpecText by remember { mutableStateOf("20, 45, 60, 90") }
    var pagesText by remember { mutableStateOf(initialPagesPerProfile.coerceIn(1, 50).toString()) }
    var selectedConcurrency by remember { mutableStateOf(1) }
    var groupTag by remember { mutableStateOf(defaultTag) }
    var syncGeoBeforeLaunch by remember { mutableStateOf(true) }
    var autoStartCampaign by remember { mutableStateOf(true) }

    val detectedProxyCount = remember(proxiesText) {
        proxiesText.lineSequence().count { line ->
            val trimmed = line.trim()
            trimmed.isNotBlank() &&
                !trimmed.startsWith("#") &&
                (trimmed.contains(":") || trimmed.contains("@"))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("100+ Proxies -> Unique Profiles & Auto-Run") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 490.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Paste 100+ to 2,000 proxies. Every proxy creates a completely distinct profile (unique UA, RAM, ROM, Device Model, Canvas, WebGL & storage).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Proxy Protocol", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                    StatusPill(
                        text = "$detectedProxyCount proxies detected",
                        containerColor = if (detectedProxyCount > 0) VerifiedEmeraldBg else CyberCyanContainer,
                        contentColor = if (detectedProxyCount > 0) VerifiedEmerald else CyberCyan
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ProxyType.AUTO, ProxyType.HTTP, ProxyType.HTTPS, ProxyType.SOCKS5, ProxyType.SOCKS4).forEach { pt ->
                        FilterChip(
                            selected = selectedType == pt,
                            onClick = { selectedType = pt },
                            label = { Text(pt.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = proxiesText,
                    onValueChange = { proxiesText = it },
                    label = { Text("Paste 100+ Proxies (1 per line, up to 2,000)") },
                    placeholder = { Text("ip:port:user:pass\nsocks5://user:pass@host:port") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_proxies_input")
                )

                OutlinedTextField(
                    value = keywordsPool,
                    onValueChange = { keywordsPool = it },
                    label = { Text("Google Search Keyword(s)") },
                    placeholder = { Text("e.g. best cloud software (comma/newline for multiple)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_keywords_input")
                )

                OutlinedTextField(
                    value = targetDomain,
                    onValueChange = { targetDomain = it },
                    label = { Text("Target Website / Domain to Open") },
                    placeholder = { Text("e.g. mybrand.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_domain_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dwellSpecText,
                        onValueChange = { raw ->
                            dwellSpecText = raw.filter { it.isDigit() || it == ',' || it == ' ' }
                        },
                        label = { Text("Stay Sec (Comma OK)") },
                        placeholder = { Text("20, 45, 60, 90") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("order_dwell_spec_input")
                    )
                    OutlinedTextField(
                        value = pagesText,
                        onValueChange = { pagesText = it.filter(Char::isDigit) },
                        label = { Text("Pages") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.75f)
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("30", "45", "15, 30, 60", "20, 45, 60, 90", "30, 60, 120, 180").forEach { spec ->
                        FilterChip(
                            selected = dwellSpecText.trim() == spec,
                            onClick = { dwellSpecText = spec },
                            label = { Text(if (',' in spec) spec else "${spec}s") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 3, 5).forEach { p ->
                        FilterChip(
                            selected = pagesText == p.toString(),
                            onClick = { pagesText = p.toString() },
                            label = { Text("$p ${if (p == 1) "Page" else "Pages"}") }
                        )
                    }
                }

                Text("Open Mode", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        1 to "1-by-1 Sequential",
                        2 to "2× Simultaneous",
                        4 to "4× Simultaneous",
                        8 to "8× Direct Multi"
                    ).forEach { (slots, lbl) ->
                        FilterChip(
                            selected = selectedConcurrency == slots,
                            onClick = { selectedConcurrency = slots },
                            label = { Text(lbl) }
                        )
                    }
                }

                OutlinedTextField(
                    value = groupTag,
                    onValueChange = { groupTag = it },
                    label = { Text("Campaign Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncGeoBeforeLaunch,
                        onCheckedChange = { syncGeoBeforeLaunch = it }
                    )
                    Text(
                        "Sync Exit IP, Timezone, Geo & WebRTC before opening each profile",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoStartCampaign,
                        onCheckedChange = { autoStartCampaign = it }
                    )
                    Text(
                        "Open All Profiles immediately after creation",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = detectedProxyCount > 0,
                onClick = {
                    val pages = (pagesText.toIntOrNull() ?: initialPagesPerProfile).coerceIn(1, 50)
                    onConfirm(
                        ProxyOrderRequest(
                            rawProxiesText = proxiesText,
                            defaultType = selectedType,
                            keywordsPool = keywordsPool,
                            targetDomain = targetDomain,
                            dwellSecondsSpec = dwellSpecText.ifBlank { "45" },
                            pagesPerProfile = pages,
                            concurrency = selectedConcurrency,
                            groupTag = groupTag.ifBlank { defaultTag },
                            syncGeoBeforeLaunch = syncGeoBeforeLaunch,
                            autoStartCampaign = autoStartCampaign
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_order_proxies_btn")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (autoStartCampaign) "Create $detectedProxyCount & Open All" else "Create $detectedProxyCount Profiles")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
