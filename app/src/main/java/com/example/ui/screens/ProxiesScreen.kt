package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.local.ProxyEntity
import com.example.data.model.FingerprintPresets
import com.example.data.model.GeolocationMode
import com.example.data.model.ProxyType
import com.example.data.model.WebRtcMode
import com.example.data.repository.BulkTimingMode
import com.example.ui.MainViewModel
import com.example.ui.components.MonoSpecChip
import com.example.ui.components.ProxyStatusPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.StealthIndigo
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun ProxiesScreen(
    viewModel: MainViewModel,
    onOpenProxyEditor: (Long?) -> Unit,
    onLaunchProxyProfile: (profileId: Long, batchMode: Boolean) -> Unit,
    onOpenMassRunner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val checkingIds by viewModel.checkingProxyIds.collectAsStateWithLifecycle()
    val directResult by viewModel.directIpResult.collectAsStateWithLifecycle()

    var showBulkDialog by remember { mutableStateOf(false) }
    var proxyToDelete by remember { mutableStateOf<ProxyEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Proxy Vault",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "HTTP, HTTPS, SOCKS4 & SOCKS5 with per-proxy Geo, WebRTC & Humanized Search timers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenProxyEditor(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_proxy_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Proxy")
                    }
                    OutlinedButton(
                        onClick = { showBulkDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bulk_import_proxies_btn")
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bulk Import")
                    }
                    if (proxies.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.checkAllProxies() },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.testTag("check_all_proxies_btn")
                        ) {
                            Icon(Icons.Rounded.NetworkCheck, contentDescription = "Check all proxies", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (proxies.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.startBatchHumanizeQueue(onlyHumanized = true) { firstProfileId ->
                                    onLaunchProxyProfile(firstProfileId, true)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_batch_humanize_btn")
                        ) {
                            Icon(Icons.Rounded.AutoMode, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val humanizedCount = remember(proxies) { proxies.count { it.humanizeEnabled } }
                            Text(
                                if (humanizedCount > 0) "Sequential ($humanizedCount)" else "Run All (${proxies.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onOpenMassRunner,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("proxies_open_mass_fleet_btn")
                        ) {
                            Text("Mass 100/1000 Hub", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Direct Device IP Baseline Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Radar,
                            contentDescription = null,
                            tint = StealthIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Host Baseline IP (Unproxied)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            val desc = when {
                                directResult == null -> "Tap Probe to verify your device's real exit IP for comparison"
                                directResult!!.success -> {
                                    val r = directResult!!
                                    val flag = FingerprintPresets.countryCodeToFlagEmoji(r.countryCode)
                                    "$flag ${r.exitIp} • ${r.city.ifBlank { r.country }} • ${r.timezone} (${r.latencyMs}ms)"
                                }
                                else -> "Check failed: ${directResult!!.errorMessage}"
                            }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.checkDirectIp() },
                            modifier = Modifier.testTag("probe_direct_ip_btn")
                        ) {
                            Text("Probe")
                        }
                    }
                }
            }
        }

        if (proxies.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Proxies Configured Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add single proxies with full Geo-Block, WebRTC, Advanced Fingerprint & Humanized Google Search timers, or bulk-import 100–1,000 proxies with unique per-proxy dwell seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onOpenProxyEditor(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Add Single Proxy")
                            }
                            OutlinedButton(
                                onClick = { showBulkDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Bulk Paste List")
                            }
                        }
                    }
                }
            }
        } else {
            items(proxies, key = { it.id }) { proxy ->
                ProxyVaultCard(
                    proxy = proxy,
                    isChecking = checkingIds.contains(proxy.id),
                    onCheck = { viewModel.checkProxy(proxy.id) },
                    onRun = {
                        viewModel.launchProxySession(proxy.id) { profileId ->
                            onLaunchProxyProfile(profileId, false)
                        }
                    },
                    onEdit = { onOpenProxyEditor(proxy.id) },
                    onDelete = { proxyToDelete = proxy },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showBulkDialog) {
        BulkImportProxyDialog(
            onDismiss = { showBulkDialog = false },
            onImport = { config ->
                showBulkDialog = false
                viewModel.bulkImportProxiesAdvanced(
                    rawText = config.rawText,
                    defaultType = config.defaultType,
                    humanizeEnabled = config.humanizeEnabled,
                    keywordsPoolRaw = config.keywordsPool,
                    targetDomain = config.targetDomain,
                    timingMode = config.timingMode,
                    fixedSeconds = config.fixedSeconds,
                    randomMinSeconds = config.randomMinSeconds,
                    randomMaxSeconds = config.randomMaxSeconds,
                    customSequenceCsv = config.customSequenceCsv,
                    geolocationMode = config.geolocationMode,
                    webRtcMode = config.webRtcMode,
                    createIsolatedProfiles = config.createProfiles
                )
            }
        )
    }

    proxyToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { proxyToDelete = null },
            title = { Text("Delete Proxy?") },
            text = { Text("Remove ${target.host}:${target.port}? Any bound profiles will revert to Direct connection.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProxy(target)
                        proxyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { proxyToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProxyVaultCard(
    proxy: ProxyEntity,
    isChecking: Boolean,
    onCheck: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flag = FingerprintPresets.countryCodeToFlagEmoji(proxy.countryCode)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("proxy_card_${proxy.id}"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = proxy.type,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = proxy.name.ifBlank { "${proxy.host}:${proxy.port}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${proxy.host}:${proxy.port}" + if (proxy.username.isNotBlank()) " • auth" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ProxyStatusPill(
                    status = proxy.status,
                    latencyMs = proxy.latencyMs,
                    isChecking = isChecking
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (proxy.lastExitIp.isNotBlank()) {
                    MonoSpecChip("EXIT IP", "$flag ${proxy.lastExitIp}", accent = VerifiedEmerald)
                }
                if (proxy.city.isNotBlank() || proxy.country.isNotBlank()) {
                    MonoSpecChip("LOC", listOf(proxy.city, proxy.country).filter { it.isNotBlank() }.joinToString(", "))
                }
                if (proxy.timezone.isNotBlank()) {
                    MonoSpecChip("TZ", proxy.timezone, accent = StealthIndigo)
                }
                MonoSpecChip(
                    "GEO",
                    proxy.geolocationMode,
                    accent = if (proxy.geolocationMode == GeolocationMode.BLOCK.name) WarningAmber else CyberCyan
                )
                if (proxy.humanizeEnabled) {
                    val kw = proxy.humanizeKeyword.ifBlank { "auto" }.take(16)
                    MonoSpecChip("HUMANIZE", "${proxy.humanizeDwellSeconds}s • $kw", accent = CyberCyan)
                }
            }

            if (proxy.lastExitIp.isBlank() && proxy.lastError.isNotBlank()) {
                Text(
                    text = "Error: ${proxy.lastError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AlertCoral
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRun,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("run_proxy_${proxy.id}")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (proxy.humanizeEnabled) "Humanize (${proxy.humanizeDwellSeconds}s)" else "Launch")
                }
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !isChecking,
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("check_proxy_${proxy.id}")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isChecking) "Probing..." else "Check")
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_proxy_${proxy.id}")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit proxy & stealth settings")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_proxy_${proxy.id}")
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete proxy", tint = AlertCoral)
                }
            }
        }
    }
}

private data class BulkImportConfig(
    val rawText: String,
    val defaultType: ProxyType,
    val humanizeEnabled: Boolean,
    val keywordsPool: String,
    val targetDomain: String,
    val timingMode: BulkTimingMode,
    val fixedSeconds: Int,
    val randomMinSeconds: Int,
    val randomMaxSeconds: Int,
    val customSequenceCsv: String,
    val geolocationMode: String,
    val webRtcMode: String,
    val createProfiles: Boolean
)

@Composable
private fun BulkImportProxyDialog(
    onDismiss: () -> Unit,
    onImport: (BulkImportConfig) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var defaultType by remember { mutableStateOf(ProxyType.SOCKS5) }
    var humanizeEnabled by remember { mutableStateOf(true) }
    var keywordsPool by remember { mutableStateOf("") }
    var targetDomain by remember { mutableStateOf("") }
    var timingMode by remember { mutableStateOf(BulkTimingMode.RANDOM_RANGE) }
    var fixedSeconds by remember { mutableStateOf("30") }
    var randomMinSeconds by remember { mutableStateOf("10") }
    var randomMaxSeconds by remember { mutableStateOf("180") }
    var customSequenceCsv by remember { mutableStateOf("5, 20, 45, 90, 120, 180") }
    var geolocationMode by remember { mutableStateOf(GeolocationMode.BLOCK.name) }
    var webRtcMode by remember { mutableStateOf(WebRtcMode.BASED_ON_IP.name) }
    var createProfiles by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Import 100–1,000 Proxies") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "One proxy per line. Optional per-line format: host:port|seconds|keyword|domain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ProxyType.AUTO, ProxyType.HTTP, ProxyType.HTTPS, ProxyType.SOCKS5, ProxyType.SOCKS4).forEach { t ->
                        FilterChip(
                            selected = defaultType == t,
                            onClick = { defaultType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("socks5://user:pass@192.0.2.10:1080|20|best laptops\n198.51.100.4:8080:user:pass|120") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_proxy_textarea")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { humanizeEnabled = !humanizeEnabled },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = humanizeEnabled, onCheckedChange = { humanizeEnabled = it })
                    Text("Enable Humanized Google Search & Browse", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                if (humanizeEnabled) {
                    OutlinedTextField(
                        value = keywordsPool,
                        onValueChange = { keywordsPool = it },
                        label = { Text("Google Keyword(s) (comma/line separated)") },
                        placeholder = { Text("e.g. cloud hosting, seo tools, ai workflow") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetDomain,
                        onValueChange = { targetDomain = it },
                        label = { Text("Suggested Website / Domain Hint (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Per-Proxy Stay Time Assignment", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BulkTimingMode.entries.forEach { mode ->
                            FilterChip(
                                selected = timingMode == mode,
                                onClick = { timingMode = mode },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    when (timingMode) {
                        BulkTimingMode.PER_LINE_OR_FIXED -> {
                            OutlinedTextField(
                                value = fixedSeconds,
                                onValueChange = { fixedSeconds = it.filter(Char::isDigit) },
                                label = { Text("Default Seconds (when line omits |sec)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        BulkTimingMode.RANDOM_RANGE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = randomMinSeconds,
                                    onValueChange = { randomMinSeconds = it.filter(Char::isDigit) },
                                    label = { Text("Min Sec") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = randomMaxSeconds,
                                    onValueChange = { randomMaxSeconds = it.filter(Char::isDigit) },
                                    label = { Text("Max Sec") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        BulkTimingMode.CUSTOM_SEQUENCE -> {
                            OutlinedTextField(
                                value = customSequenceCsv,
                                onValueChange = { customSequenceCsv = it },
                                label = { Text("Cycle Seconds (e.g. 5, 20, 60, 120, 180)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Text("Default Geolocation Mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(GeolocationMode.BLOCK.name, GeolocationMode.PROMPT.name, GeolocationMode.ALLOW.name).forEach { g ->
                        FilterChip(
                            selected = geolocationMode == g,
                            onClick = { geolocationMode = g },
                            label = { Text(g) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createProfiles = !createProfiles },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = createProfiles, onCheckedChange = { createProfiles = it })
                    Text("Auto-create isolated Browser Profile for each proxy", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImport(
                        BulkImportConfig(
                            rawText = rawText,
                            defaultType = defaultType,
                            humanizeEnabled = humanizeEnabled,
                            keywordsPool = keywordsPool,
                            targetDomain = targetDomain,
                            timingMode = timingMode,
                            fixedSeconds = (fixedSeconds.toIntOrNull() ?: 30).coerceIn(3, 3600),
                            randomMinSeconds = (randomMinSeconds.toIntOrNull() ?: 10).coerceIn(3, 3600),
                            randomMaxSeconds = (randomMaxSeconds.toIntOrNull() ?: 180).coerceIn(3, 3600),
                            customSequenceCsv = customSequenceCsv,
                            geolocationMode = geolocationMode,
                            webRtcMode = webRtcMode,
                            createProfiles = createProfiles
                        )
                    )
                },
                enabled = rawText.isNotBlank(),
                modifier = Modifier.testTag("bulk_import_confirm_btn")
            ) {
                Text("Import All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
