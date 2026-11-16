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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.browser.AutomationPresets
import com.example.data.local.AutomationFlowEntity
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.RpaActionType
import com.example.data.local.RpaStep
import com.example.data.repository.CloakDeckRepository
import com.example.ui.MainViewModel
import com.example.ui.MassLaunchRequest
import com.example.ui.components.MonoSpecChip
import com.example.ui.components.StatusPill
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.StealthIndigo
import com.example.ui.theme.VerifiedEmerald
import java.util.UUID

@Composable
fun AutomationScreen(
    viewModel: MainViewModel,
    onRunFlowInProfile: (profileId: Long, flowId: Long) -> Unit,
    onOpenMassRunner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val flows by viewModel.automations.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBulkBusy by viewModel.isBulkBusy.collectAsStateWithLifecycle()

    var editingFlow by remember { mutableStateOf<AutomationFlowEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showAdd100ProxiesOrder by remember { mutableStateOf(false) }
    var flowToRun by remember { mutableStateOf<AutomationFlowEntity?>(null) }
    var flowToDelete by remember { mutableStateOf<AutomationFlowEntity?>(null) }

    var bulkKeyword by remember { mutableStateOf("") }
    var bulkDomain by remember { mutableStateOf("") }
    var bulkSecondsSpec by remember { mutableStateOf("20, 45, 60, 90") }
    var bulkPagesPerProfile by remember { mutableIntStateOf(settings.humanizePagesPerProfile) }
    var bulkConcurrency by remember { mutableIntStateOf(1) }
    var bulkClickInternal by remember { mutableStateOf(settings.humanizePagesPerProfile > 1) }
    var bulkAppliedBanner by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(settings.humanizePagesPerProfile) {
        bulkPagesPerProfile = settings.humanizePagesPerProfile
    }

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
                            text = "Humanized RPA",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "100+ proxies -> distinct profiles, comma dwell times & auto-CAPTCHA domain fallback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            editingFlow = null
                            showEditor = true
                        },
                        modifier = Modifier.testTag("new_rpa_flow_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Flow")
                    }
                }
                OutlinedButton(
                    onClick = onOpenMassRunner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rpa_open_mass_fleet_btn")
                ) {
                    Icon(Icons.Rounded.AutoMode, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Live Mass Fleet Runner (${profiles.size} Profiles / ${proxies.size} Proxies)")
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("rpa_bulk_apply_card"),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bulk Keyword, Domain & Open All Profiles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Text(
                                text = "Runs every profile with its own proxy & fingerprint, auto-closes when stay time finishes, and opens the next profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = "${maxOf(profiles.size, proxies.size)} READY",
                            containerColor = CyberCyanContainer,
                            contentColor = CyberCyan
                        )
                    }

                    OutlinedTextField(
                        value = bulkKeyword,
                        onValueChange = { bulkKeyword = it },
                        label = { Text("Bulk Target Google Keyword(s) (comma/line)") },
                        placeholder = { Text("e.g. best cloud hosting 2026") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_rpa_keyword_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bulkDomain,
                            onValueChange = { bulkDomain = it },
                            label = { Text("Bulk Target Website / Domain") },
                            placeholder = { Text("e.g. mybrand.com") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("bulk_rpa_domain_input")
                        )
                        OutlinedTextField(
                            value = bulkSecondsSpec,
                            onValueChange = { raw ->
                                bulkSecondsSpec = raw.filter { it.isDigit() || it == ',' || it == ' ' }
                            },
                            label = { Text("Time (Comma OK)") },
                            placeholder = { Text("20,45,60,90") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("bulk_rpa_seconds_input")
                        )
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("30", "45", "15, 30, 60", "20, 45, 60, 90", "30, 60, 120, 180").forEach { spec ->
                            FilterChip(
                                selected = bulkSecondsSpec.trim() == spec,
                                onClick = { bulkSecondsSpec = spec },
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
                                selected = bulkPagesPerProfile == p,
                                onClick = {
                                    bulkPagesPerProfile = p
                                    bulkClickInternal = p > 1
                                    viewModel.setHumanizePagesPerProfile(p)
                                },
                                label = { Text("$p ${if (p == 1) "Page" else "Pages"}") }
                            )
                        }
                    }

                    Text(
                        text = "Open All Execution Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1 to "1-by-1 (Auto-Close -> Next)",
                            2 to "2× Simultaneous",
                            4 to "4× Simultaneous",
                            8 to "8× Direct Multi"
                        ).forEach { (slots, lbl) ->
                            FilterChip(
                                selected = bulkConcurrency == slots,
                                onClick = { bulkConcurrency = slots },
                                label = { Text(lbl) }
                            )
                        }
                    }

                    SwitchSettingRow(
                        label = "Auto-close profile when stay time finishes & open next",
                        checked = settings.autoCloseProfileAfterHumanize,
                        onCheckedChange = viewModel::setAutoCloseProfileAfterHumanize
                    )

                    SwitchSettingRow(
                        label = "Click internal website link while reading",
                        checked = bulkClickInternal,
                        onCheckedChange = { bulkClickInternal = it }
                    )

                    SwitchSettingRow(
                        label = "Open target domain directly if CAPTCHA appears",
                        checked = settings.directOpenDomainOnCaptcha,
                        onCheckedChange = viewModel::setDirectOpenDomainOnCaptcha
                    )

                    if (!bulkAppliedBanner.isNullOrBlank()) {
                        Text(
                            text = bulkAppliedBanner!!,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VerifiedEmerald
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setHumanizePagesPerProfile(bulkPagesPerProfile)
                                val durations = CloakDeckRepository.parseCommaDurationsSeconds(bulkSecondsSpec, 45)
                                viewModel.bulkApplyHumanizeToAllProxies(
                                    keyword = bulkKeyword,
                                    targetDomain = bulkDomain,
                                    dwellSeconds = durations.first(),
                                    dwellSecondsSpec = bulkSecondsSpec,
                                    clickInternalLink = bulkClickInternal && bulkPagesPerProfile > 1
                                ) { proxyCnt, profCnt ->
                                    bulkAppliedBanner = "Applied $bulkPagesPerProfile pg/profile to $proxyCnt proxies & $profCnt profiles (${durations.joinToString(",")}s)"
                                }
                            },
                            enabled = !isBulkBusy && (proxies.isNotEmpty() || profiles.isNotEmpty()),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bulk_apply_all_proxies_btn")
                        ) {
                            Text(if (isBulkBusy) "Preparing..." else "Apply Settings")
                        }
                        Button(
                            onClick = {
                                viewModel.setHumanizePagesPerProfile(bulkPagesPerProfile)
                                val durations = CloakDeckRepository.parseCommaDurationsSeconds(bulkSecondsSpec, 45)
                                viewModel.bulkApplyHumanizeToAllProxies(
                                    keyword = bulkKeyword,
                                    targetDomain = bulkDomain,
                                    dwellSeconds = durations.first(),
                                    dwellSecondsSpec = bulkSecondsSpec,
                                    clickInternalLink = bulkClickInternal && bulkPagesPerProfile > 1
                                ) { _, _ ->
                                    viewModel.requestMassLaunch(
                                        MassLaunchRequest(
                                            groupTag = "All",
                                            concurrency = bulkConcurrency,
                                            pagesPerProfile = bulkPagesPerProfile,
                                            keywordPool = bulkKeyword.trim(),
                                            targetDomain = bulkDomain.trim(),
                                            dwellSecondsSpec = bulkSecondsSpec.trim(),
                                            syncGeoBeforeEachProfile = true,
                                            autoStart = true
                                        )
                                    )
                                    onOpenMassRunner()
                                }
                            },
                            enabled = !isBulkBusy && (proxies.isNotEmpty() || profiles.isNotEmpty()),
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("open_all_profiles_automation_btn")
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBulkBusy) "Preparing..." else "Open All Profiles (${maxOf(profiles.size, proxies.size)})")
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("rpa_100_proxies_order_card"),
                color = CyberCyanContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Add 100+ Proxies -> 100+ Unique Profiles & Auto-Run",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                    Text(
                        text = "Paste 100+ proxies (up to 2,000), keyword, domain & comma-separated times. Every profile gets a completely unique User-Agent, Device, RAM, ROM, Canvas & WebGL fingerprint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = { showAdd100ProxiesOrder = true },
                        enabled = !isBulkBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rpa_add_100_proxies_btn")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBulkBusy) "Preparing Profiles..." else "Add 100+ Proxies -> Create Unique Profiles")
                    }
                }
            }
        }

        items(flows, key = { it.id }) { flow ->
            AutomationFlowCard(
                flow = flow,
                onRun = {
                    if (profiles.size == 1) {
                        onRunFlowInProfile(profiles.first().id, flow.id)
                    } else {
                        flowToRun = flow
                    }
                },
                onEdit = {
                    editingFlow = flow
                    showEditor = true
                },
                onDelete = { flowToDelete = flow },
                modifier = Modifier.widthIn(max = 760.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showEditor) {
        AutomationFlowEditorDialog(
            initial = editingFlow,
            onDismiss = { showEditor = false },
            onSave = { entity ->
                showEditor = false
                viewModel.saveAutomationFlow(entity)
            }
        )
    }

    flowToRun?.let { targetFlow ->
        ProfilePickerForFlowDialog(
            flow = targetFlow,
            profiles = profiles,
            onDismiss = { flowToRun = null },
            onPickProfile = { profile ->
                flowToRun = null
                onRunFlowInProfile(profile.id, targetFlow.id)
            },
            onCreateQuickAndRun = {
                flowToRun = null
                viewModel.createQuickProfile(
                    name = "RPA Session #${profiles.size + 1}",
                    groupTag = "RPA",
                    proxyId = null,
                    automationFlowId = targetFlow.id
                ) { newId ->
                    onRunFlowInProfile(newId, targetFlow.id)
                }
            }
        )
    }

    flowToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { flowToDelete = null },
            title = { Text("Delete '${target.name}'?") },
            text = { Text("Remove this RPA automation sequence?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAutomationFlow(target.id)
                        flowToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { flowToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showAdd100ProxiesOrder) {
        ProxyOrderCampaignDialog(
            initialPagesPerProfile = settings.humanizePagesPerProfile,
            onDismiss = { showAdd100ProxiesOrder = false },
            onConfirm = { req ->
                showAdd100ProxiesOrder = false
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
                        bulkAppliedBanner = "Created $createdCount distinct profiles in '$createdGroup'"
                        if (req.autoStartCampaign) {
                            viewModel.requestMassLaunch(
                                MassLaunchRequest(
                                    groupTag = createdGroup,
                                    concurrency = req.concurrency,
                                    pagesPerProfile = req.pagesPerProfile,
                                    keywordPool = "",
                                    targetDomain = "",
                                    dwellSecondsSpec = "",
                                    syncGeoBeforeEachProfile = req.syncGeoBeforeLaunch,
                                    autoStart = true
                                )
                            )
                            onOpenMassRunner()
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationFlowCard(
    flow: AutomationFlowEntity,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = remember(flow.stepsJson) { AutomationPresets.decodeSteps(flow.stepsJson) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rpa_card_${flow.id}"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = CyberCyanContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = flow.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (flow.description.isNotBlank()) {
                        Text(
                            text = flow.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StatusPill(
                    text = "${steps.size} STEPS",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MonoSpecChip("Typing", "${flow.humanTypingMinMs}-${flow.humanTypingMaxMs}ms/char")
                MonoSpecChip("Loops", "${flow.loopCount}x", accent = StealthIndigo)
                steps.take(4).forEachIndexed { idx, s ->
                    MonoSpecChip("${idx + 1}", s.type.label)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRun,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("run_rpa_${flow.id}")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Humanized")
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit flow")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete flow", tint = AlertCoral)
                }
            }
        }
    }
}

@Composable
private fun ProfilePickerForFlowDialog(
    flow: AutomationFlowEntity,
    profiles: List<BrowserProfileEntity>,
    onDismiss: () -> Unit,
    onPickProfile: (BrowserProfileEntity) -> Unit,
    onCreateQuickAndRun: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run '${flow.name}'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (profiles.isEmpty()) {
                        "No profiles exist yet. Spin up a new isolated profile to execute this humanized RPA flow."
                    } else {
                        "Select which isolated profile (with its bound proxy & fingerprint) should execute this automation:"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(profiles, key = { it.id }) { prof ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickProfile(prof) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prof.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("${prof.osPlatform} • ${prof.timezone}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CyberCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreateQuickAndRun) {
                Icon(Icons.Rounded.AutoMode, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Profile & Run")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AutomationFlowEditorDialog(
    initial: AutomationFlowEntity?,
    onDismiss: () -> Unit,
    onSave: (AutomationFlowEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "Custom Humanized Flow") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var loopCount by remember { mutableStateOf(initial?.loopCount?.toString() ?: "1") }
    var steps by remember {
        mutableStateOf(
            initial?.let { AutomationPresets.decodeSteps(it.stepsJson) }
                ?: listOf(
                    RpaStep(UUID.randomUUID().toString(), RpaActionType.NAVIGATE, target = "https://myip.link"),
                    RpaStep(UUID.randomUUID().toString(), RpaActionType.HUMAN_WAIT, minDelayMs = 1500, maxDelayMs = 3000),
                    RpaStep(UUID.randomUUID().toString(), RpaActionType.HUMAN_SCROLL, value = "DOWN", scrollCycles = 4)
                )
        )
    }

    var addActionType by remember { mutableStateOf(RpaActionType.GOOGLE_SEARCH_AND_BROWSE) }
    var stepTarget by remember { mutableStateOf("") }
    var stepValue by remember { mutableStateOf("best productivity tools") }
    var stepDurationSec by remember { mutableStateOf("30") }
    var stepClickInternal by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Humanized RPA Flow" else "Edit RPA Flow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Flow Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = loopCount,
                        onValueChange = { loopCount = it.filter(Char::isDigit) },
                        label = { Text("Loops") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Text("Add Step", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RpaActionType.entries.forEach { act ->
                        FilterChip(
                            selected = addActionType == act,
                            onClick = {
                                addActionType = act
                                stepTarget = when (act) {
                                    RpaActionType.NAVIGATE -> "https://"
                                    RpaActionType.HUMAN_CLICK -> "a, button"
                                    RpaActionType.HUMAN_TYPE -> "input[name='q']"
                                    RpaActionType.GOOGLE_SEARCH_AND_BROWSE -> ""
                                    else -> ""
                                }
                                stepValue = when (act) {
                                    RpaActionType.HUMAN_SCROLL -> "DOWN"
                                    RpaActionType.GOOGLE_SEARCH_AND_BROWSE -> "best productivity tools"
                                    else -> ""
                                }
                            },
                            label = { Text(act.label) }
                        )
                    }
                }

                if (addActionType == RpaActionType.GOOGLE_SEARCH_AND_BROWSE ||
                    addActionType == RpaActionType.NAVIGATE ||
                    addActionType == RpaActionType.HUMAN_CLICK ||
                    addActionType == RpaActionType.HUMAN_TYPE
                ) {
                    OutlinedTextField(
                        value = stepTarget,
                        onValueChange = { stepTarget = it },
                        label = {
                            Text(
                                when (addActionType) {
                                    RpaActionType.GOOGLE_SEARCH_AND_BROWSE -> "Target / Suggested Website (optional)"
                                    RpaActionType.NAVIGATE -> "URL"
                                    RpaActionType.HUMAN_CLICK -> "CSS Selector or Button Text"
                                    else -> "Input CSS Selector"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (addActionType == RpaActionType.GOOGLE_SEARCH_AND_BROWSE ||
                    addActionType == RpaActionType.HUMAN_TYPE ||
                    addActionType == RpaActionType.HUMAN_SCROLL
                ) {
                    OutlinedTextField(
                        value = stepValue,
                        onValueChange = { stepValue = it },
                        label = {
                            Text(
                                when (addActionType) {
                                    RpaActionType.GOOGLE_SEARCH_AND_BROWSE -> "Google Search Keyword"
                                    RpaActionType.HUMAN_SCROLL -> "Direction (DOWN / UP)"
                                    else -> "Text to Type"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (addActionType == RpaActionType.GOOGLE_SEARCH_AND_BROWSE || addActionType == RpaActionType.HUMAN_READ_DURATION) {
                    OutlinedTextField(
                        value = stepDurationSec,
                        onValueChange = { stepDurationSec = it.filter(Char::isDigit) },
                        label = { Text("Stay / Human Read Time (Seconds)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5 to "5s", 20 to "20s", 45 to "45s", 60 to "1m", 120 to "2m", 180 to "3m").forEach { (sec, lbl) ->
                            FilterChip(
                                selected = stepDurationSec == sec.toString(),
                                onClick = { stepDurationSec = sec.toString() },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    SwitchSettingRow(
                        label = "Click internal site link while reading",
                        checked = stepClickInternal,
                        onCheckedChange = { stepClickInternal = it }
                    )
                }

                OutlinedButton(
                    onClick = {
                        steps = steps + RpaStep(
                            id = UUID.randomUUID().toString().take(8),
                            type = addActionType,
                            target = stepTarget.trim(),
                            value = stepValue,
                            durationSeconds = (stepDurationSec.toIntOrNull() ?: 30).coerceIn(3, 3600),
                            clickInternalLink = stepClickInternal
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Append Step (${steps.size})")
                }

                LazyColumn(modifier = Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(steps, key = { it.id }) { step ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val secTag = if (step.type == RpaActionType.GOOGLE_SEARCH_AND_BROWSE || step.type == RpaActionType.HUMAN_READ_DURATION) {
                                    "(${step.durationSeconds}s)"
                                } else ""
                                Text(
                                    text = "${step.type.label} ${step.value} ${step.target} $secTag".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { steps = steps.filterNot { it.id == step.id } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove step", tint = AlertCoral, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entity = (initial ?: AutomationFlowEntity(name = name, stepsJson = "")).copy(
                        name = name.ifBlank { "Custom Flow" },
                        description = description,
                        stepsJson = AutomationPresets.encodeSteps(steps),
                        loopCount = (loopCount.toIntOrNull() ?: 1).coerceIn(1, 20)
                    )
                    onSave(entity)
                },
                enabled = steps.isNotEmpty()
            ) {
                Text("Save Flow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
