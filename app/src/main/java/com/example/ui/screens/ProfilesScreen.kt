package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.BrowserProfileEntity
import com.example.data.local.ProxyEntity
import com.example.data.model.DeviceBlueprint
import com.example.data.model.FingerprintPresets
import com.example.data.model.OsPlatform
import com.example.data.repository.CloakDeckRepository
import com.example.ui.MainViewModel
import com.example.ui.MassLaunchRequest
import com.example.ui.ProfileWithProxy
import com.example.ui.components.MonoSpecChip
import com.example.ui.components.StatusPill
import com.example.ui.components.osIcon
import com.example.ui.components.parseAccentColor
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.StealthIndigo
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: MainViewModel,
    onOpenBrowser: (Long, String?) -> Unit,
    onEditProfile: (Long?) -> Unit,
    onGoToProxies: () -> Unit,
    onOpenMassRunner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items by viewModel.profilesWithProxies.collectAsStateWithLifecycle()
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBulkBusy by viewModel.isBulkBusy.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }
    var showQuickCreate by remember { mutableStateOf(false) }
    var showOrderProxiesDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<BrowserProfileEntity?>(null) }

    var bulkKeyword by remember { mutableStateOf("") }
    var bulkDomain by remember { mutableStateOf("") }
    var bulkSecondsSpec by remember { mutableStateOf("20, 45, 60, 90") }
    var bulkPages by remember { mutableIntStateOf(settings.humanizePagesPerProfile) }
    var bulkConcurrency by remember { mutableIntStateOf(1) }

    androidx.compose.runtime.LaunchedEffect(settings.humanizePagesPerProfile) {
        bulkPages = settings.humanizePagesPerProfile
    }

    val groups = remember(items) {
        listOf("All") + items.map { it.profile.groupTag }.distinct().sorted()
    }

    val proxiedCount = remember(items) {
        items.count { it.proxy != null || (it.profile.proxyConnectionType == "YOUR_PROXY" && it.profile.inlineProxyHost.isNotBlank()) }
    }

    val filtered = remember(items, searchQuery, selectedGroup) {
        items.filter { item ->
            val matchesGroup = selectedGroup == "All" || item.profile.groupTag == selectedGroup
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                item.profile.name.lowercase().contains(q) ||
                item.profile.userAgent.lowercase().contains(q) ||
                item.profile.timezone.lowercase().contains(q) ||
                (item.proxy?.host?.lowercase()?.contains(q) == true) ||
                (item.proxy?.lastExitIp?.lowercase()?.contains(q) == true)
            matchesGroup && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header + Stats Deck
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profile Deck",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Isolated browser containers with unique proxies & fingerprints",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showQuickCreate = true },
                        modifier = Modifier.testTag("quick_new_profile_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New")
                    }
                }

                // Telemetry Summary Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeckStatChip(
                        label = "PROFILES",
                        value = "${items.size}",
                        accent = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    DeckStatChip(
                        label = "PROXIED",
                        value = "$proxiedCount",
                        accent = VerifiedEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    DeckStatChip(
                        label = "PROXY POOL",
                        value = "${proxies.size}",
                        accent = StealthIndigo,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onGoToProxies)
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profiles_bulk_autorun_card"),
                    color = CyberCyanContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bulk Auto-Run All Profiles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            TextButton(
                                onClick = { showOrderProxiesDialog = true },
                                modifier = Modifier.testTag("profiles_add_100_proxies_btn")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ 100+ Proxies")
                            }
                        }

                        OutlinedTextField(
                            value = bulkKeyword,
                            onValueChange = { bulkKeyword = it },
                            label = { Text("Bulk Target Keyword(s) (comma/line)") },
                            placeholder = { Text("e.g. best cloud hosting 2026") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profiles_bulk_keyword_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bulkDomain,
                                onValueChange = { bulkDomain = it },
                                label = { Text("Bulk Target Domain") },
                                placeholder = { Text("e.g. mybrand.com") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.25f)
                                    .testTag("profiles_bulk_domain_input")
                            )
                            OutlinedTextField(
                                value = bulkSecondsSpec,
                                onValueChange = { raw ->
                                    bulkSecondsSpec = raw.filter { it.isDigit() || it == ',' || it == ' ' }
                                },
                                label = { Text("Time (Comma)") },
                                placeholder = { Text("20,45,60,90") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profiles_bulk_seconds_input")
                            )
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                1 to "1-by-1 (Auto-Close -> Next)",
                                2 to "2× Simultaneous",
                                4 to "4× Simultaneous",
                                8 to "8× Direct All"
                            ).forEach { (slots, lbl) ->
                                FilterChip(
                                    selected = bulkConcurrency == slots,
                                    onClick = { bulkConcurrency = slots },
                                    label = { Text(lbl) }
                                )
                            }
                            listOf(1, 2, 3, 5).forEach { p ->
                                FilterChip(
                                    selected = bulkPages == p,
                                    onClick = {
                                        bulkPages = p
                                        viewModel.setHumanizePagesPerProfile(p)
                                    },
                                    label = { Text("$p Pg") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAutoCloseProfileAfterHumanize(!settings.autoCloseProfileAfterHumanize)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Auto-Close profile when time finishes & open next",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = settings.autoCloseProfileAfterHumanize,
                                onCheckedChange = viewModel::setAutoCloseProfileAfterHumanize,
                                modifier = Modifier.testTag("profiles_auto_close_switch")
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDirectOpenDomainOnCaptcha(!settings.directOpenDomainOnCaptcha)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Open target domain directly if CAPTCHA appears",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = settings.directOpenDomainOnCaptcha,
                                onCheckedChange = viewModel::setDirectOpenDomainOnCaptcha,
                                modifier = Modifier.testTag("profiles_direct_captcha_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setHumanizePagesPerProfile(bulkPages)
                                    val durations = CloakDeckRepository.parseCommaDurationsSeconds(bulkSecondsSpec, 45)
                                    viewModel.bulkApplyHumanizeToAllProxies(
                                        keyword = bulkKeyword,
                                        targetDomain = bulkDomain,
                                        dwellSeconds = durations.first(),
                                        dwellSecondsSpec = bulkSecondsSpec,
                                        clickInternalLink = bulkPages > 1
                                    )
                                },
                                enabled = !isBulkBusy && (items.isNotEmpty() || proxies.isNotEmpty()),
                                modifier = Modifier.weight(0.85f)
                            ) {
                                Text(if (isBulkBusy) "Preparing..." else "Apply Bulk")
                            }
                            Button(
                                onClick = {
                                    viewModel.setHumanizePagesPerProfile(bulkPages)
                                    val durations = CloakDeckRepository.parseCommaDurationsSeconds(bulkSecondsSpec, 45)
                                    viewModel.bulkApplyHumanizeToAllProxies(
                                        keyword = bulkKeyword,
                                        targetDomain = bulkDomain,
                                        dwellSeconds = durations.first(),
                                        dwellSecondsSpec = bulkSecondsSpec,
                                        clickInternalLink = bulkPages > 1
                                    ) { _, _ ->
                                        viewModel.requestMassLaunch(
                                            MassLaunchRequest(
                                                groupTag = selectedGroup,
                                                concurrency = bulkConcurrency,
                                                pagesPerProfile = bulkPages,
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
                                enabled = !isBulkBusy && (items.isNotEmpty() || proxies.isNotEmpty()),
                                modifier = Modifier
                                    .weight(1.35f)
                                    .testTag("profiles_open_all_btn")
                            ) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBulkBusy) "Preparing..." else "Open All Profiles (${maxOf(items.size, proxies.size)})")
                            }
                        }
                    }
                }

                if (items.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search profiles, IPs, timezones, or User-Agents...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = { selectedGroup = group },
                                label = { Text(group) }
                            )
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                EmptyProfilesHero(
                    proxyCount = proxies.size,
                    onQuickCreate = { showQuickCreate = true },
                    onFullCreate = { onEditProfile(null) },
                    onGoToProxies = onGoToProxies
                )
            }
        } else if (filtered.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("No matching profiles", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Try clearing your search filter or create a new profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.profile.id }) { item ->
                ProfileDeckCard(
                    item = item,
                    onLaunch = { onOpenBrowser(item.profile.id, null) },
                    onAudit = { onOpenBrowser(item.profile.id, "cloakdeck://verify") },
                    onEdit = { onEditProfile(item.profile.id) },
                    onDuplicate = { viewModel.duplicateProfile(item.profile) },
                    onDelete = { profileToDelete = item.profile },
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showQuickCreate) {
        QuickProfileSheet(
            proxies = proxies,
            profileCount = items.size,
            onDismiss = { showQuickCreate = false },
            onCreateAndLaunch = { name, group, proxyId, blueprint ->
                showQuickCreate = false
                viewModel.createQuickProfile(
                    name = name,
                    groupTag = group,
                    proxyId = proxyId,
                    blueprint = blueprint
                ) { newId ->
                    onOpenBrowser(newId, "cloakdeck://verify")
                }
            },
            onOpenFullEditor = {
                showQuickCreate = false
                onEditProfile(null)
            }
        )
    }

    profileToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete '${target.name}'?") },
            text = { Text("This removes the profile, its fingerprint configuration, and its isolated cookies/history.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProfile(target)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showOrderProxiesDialog) {
        ProxyOrderCampaignDialog(
            initialPagesPerProfile = settings.humanizePagesPerProfile,
            onDismiss = { showOrderProxiesDialog = false },
            onConfirm = { req ->
                showOrderProxiesDialog = false
                viewModel.setHumanizePagesPerProfile(req.pagesPerProfile)
                viewModel.submitProxyOrderCampaign(
                    rawProxiesText = req.rawProxiesText,
                    defaultType = req.defaultType,
                    keywordsPoolRaw = req.keywordsPool,
                    targetDomain = req.targetDomain,
                    dwellSecondsSpec = req.dwellSecondsSpec,
                    groupTag = req.groupTag
                ) { createdGroup, createdCount ->
                    if (createdCount > 0 && req.autoStartCampaign) {
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
        )
    }
}

@Composable
private fun DeckStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDeckCard(
    item: ProfileWithProxy,
    onLaunch: () -> Unit,
    onAudit: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = item.profile
    val proxy = item.proxy
    val accent = parseAccentColor(profile.colorHex)
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.id}"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = osIcon(profile.osPlatform),
                        contentDescription = profile.osPlatform,
                        tint = accent,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusPill(
                            text = profile.groupTag,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${runCatching { OsPlatform.valueOf(profile.osPlatform).displayName }.getOrDefault(profile.osPlatform)} • ${profile.browserEngine} • ${profile.deviceModel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_menu_${profile.id}")
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Profile options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Full Fingerprint") },
                            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clone with Fresh Seeds") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Profile", color = AlertCoral) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = AlertCoral) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Bound Proxy Route Bar (Vault or Inline Your Proxy)
            val hasInlineProxy = profile.proxyConnectionType == "YOUR_PROXY" && profile.inlineProxyHost.isNotBlank()
            val isProxied = proxy != null || hasInlineProxy
            Surface(
                color = if (isProxied) VerifiedEmeraldBg.copy(alpha = 0.65f) else WarningAmberBg.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isProxied) VerifiedEmerald.copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEdit)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (isProxied) VerifiedEmerald else WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    if (proxy != null) {
                        val flag = FingerprintPresets.countryCodeToFlagEmoji(proxy.countryCode)
                        val exitLabel = if (proxy.lastExitIp.isNotBlank()) {
                            "$flag ${proxy.lastExitIp} • ${proxy.city.ifBlank { proxy.country }}"
                        } else {
                            "${proxy.type}://${proxy.host}:${proxy.port}"
                        }
                        Text(
                            text = exitLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (proxy.latencyMs != null) {
                            Text(
                                text = "${proxy.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedEmerald
                            )
                        }
                    } else if (hasInlineProxy) {
                        Text(
                            text = "${profile.inlineProxyType}://${profile.inlineProxyHost}:${profile.inlineProxyPort}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Without proxy (Tap to configure)",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Fingerprint Telemetry Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MonoSpecChip("TZ", profile.timezone)
                MonoSpecChip("RES", "${profile.screenWidth}x${profile.screenHeight}@${profile.devicePixelRatio}x", accent = StealthIndigo)
                MonoSpecChip("HW", "${profile.hardwareConcurrency}C/${profile.deviceMemory}GB")
                MonoSpecChip("WebRTC", profile.webRtcMode, accent = VerifiedEmerald)
                MonoSpecChip("Canvas", "${profile.canvasMode} #${profile.canvasSeed % 10000}")
                MonoSpecChip("GPU", profile.webGlVendor.substringBefore(" ("))
                if (profile.autoRunAutomationOnLaunch && profile.automationFlowId != null) {
                    MonoSpecChip("RPA", "AUTO-RUN", accent = CyberCyan)
                }
            }

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLaunch,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("launch_profile_${profile.id}")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Browser")
                }
                OutlinedButton(
                    onClick = onAudit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("audit_profile_${profile.id}")
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audit")
                }
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.testTag("edit_profile_${profile.id}")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit fingerprint", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyProfilesHero(
    proxyCount: Int,
    onQuickCreate: () -> Unit,
    onFullCreate: () -> Unit,
    onGoToProxies: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(modifier = Modifier.height(170.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.img_stealth_header_1794646271931),
                    contentDescription = "CloakDeck Anti-Detect Hero",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x330B0F14), Color(0xF0121821))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    StatusPill(
                        text = "ISOLATED MULTI-PROFILE ENGINE",
                        containerColor = CyberCyan.copy(alpha = 0.16f),
                        contentColor = CyberCyan,
                        dotColor = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Zero-Leak Identity Isolation",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create isolated browser profiles where every profile gets its own HTTP/SOCKS5 proxy exit IP, User-Agent, Device Model, Timezone, Geolocation, WebRTC policy, Canvas, WebGL, Audio & Font fingerprint.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onQuickCreate,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_quick_create_btn")
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quick Profile")
                    }
                    OutlinedButton(
                        onClick = onFullCreate,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_full_create_btn")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Custom Setup")
                    }
                }

                if (proxyCount == 0) {
                    OutlinedButton(
                        onClick = onGoToProxies,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Step 1: Add Proxies to Your Vault")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickProfileSheet(
    proxies: List<ProxyEntity>,
    profileCount: Int,
    onDismiss: () -> Unit,
    onCreateAndLaunch: (String, String, Long?, DeviceBlueprint) -> Unit,
    onOpenFullEditor: () -> Unit
) {
    var name by remember { mutableStateOf("Identity #${profileCount + 1}") }
    var group by remember { mutableStateOf("Operations") }
    var selectedBlueprint by remember { mutableStateOf(FingerprintPresets.deviceBlueprints.first()) }
    var selectedProxyId by remember { mutableStateOf(proxies.firstOrNull()?.id) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Launch Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onOpenFullEditor) {
                    Text("Advanced Editor")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_profile_name_input")
            )

            OutlinedTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text("Group / Workspace Tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Device & Browser Blueprint", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FingerprintPresets.deviceBlueprints.forEach { bp ->
                    FilterChip(
                        selected = selectedBlueprint.id == bp.id,
                        onClick = { selectedBlueprint = bp },
                        leadingIcon = {
                            Icon(osIcon(bp.osPlatform.name), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(bp.title) }
                    )
                }
            }

            Text("Assign Proxy Node", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedProxyId == null,
                    onClick = { selectedProxyId = null },
                    label = { Text("Direct (No Proxy)") }
                )
                proxies.forEach { proxy ->
                    val flag = FingerprintPresets.countryCodeToFlagEmoji(proxy.countryCode)
                    FilterChip(
                        selected = selectedProxyId == proxy.id,
                        onClick = { selectedProxyId = proxy.id },
                        label = {
                            Text("$flag ${proxy.name.ifBlank { "${proxy.host}:${proxy.port}" }}")
                        }
                    )
                }
            }

            Button(
                onClick = { onCreateAndLaunch(name, group, selectedProxyId, selectedBlueprint) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_create_launch_confirm")
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Fingerprint & Launch")
            }
        }
    }
}
