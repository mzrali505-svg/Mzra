package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SearchEngine
import com.example.data.network.CaptchaSolverProvider
import com.example.ui.MainViewModel
import com.example.ui.components.StealthSectionCard
import com.example.ui.theme.AlertCoral

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmClearSessions by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                Text(
                    text = "Engine Preferences",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure proxy geo-sync, session hygiene, and browser defaults",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            StealthSectionCard(
                title = "Appearance & Workspace",
                icon = Icons.Rounded.DarkMode,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                ToggleRow(
                    title = "Obsidian Dark Theme",
                    subtitle = "High-contrast cyber-stealth dark interface",
                    checked = settings.darkMode,
                    onCheckedChange = viewModel::setDarkMode,
                    testTag = "toggle_dark_mode"
                )
            }
        }

        item {
            StealthSectionCard(
                title = "Proxy & Geolocation Automation",
                icon = Icons.Rounded.Public,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                ToggleRow(
                    title = "Auto-Sync Timezone & Geo with Proxy",
                    subtitle = "Automatically update bound profiles' timezone, language & GPS coordinates when a proxy check resolves",
                    checked = settings.autoSyncGeoWithProxy,
                    onCheckedChange = viewModel::setAutoSyncGeo,
                    testTag = "toggle_auto_sync_geo"
                )
                ToggleRow(
                    title = "Verify Proxy Before Browser Launch",
                    subtitle = "Ping bound proxy and refresh live exit IP on browser startup",
                    checked = settings.autoVerifyProxyOnLaunch,
                    onCheckedChange = viewModel::setAutoVerifyProxy,
                    testTag = "toggle_auto_verify_proxy"
                )
            }
        }

        item {
            StealthSectionCard(
                title = "Privacy & Search Engine",
                icon = Icons.Rounded.Search,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                ToggleRow(
                    title = "Block Third-Party Cookies",
                    subtitle = "Strict cross-site cookie isolation inside every profile container",
                    checked = settings.blockThirdPartyCookies,
                    onCheckedChange = viewModel::setBlockThirdPartyCookies,
                    testTag = "toggle_block_3p_cookies"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Omnibox Search Engine",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSearchEngine(engine) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.searchEngine == engine,
                                onClick = { viewModel.setSearchEngine(engine) }
                            )
                            Text(
                                text = engine.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            var apiKeyDraft by remember(settings.captchaApiKey) { mutableStateOf(settings.captchaApiKey) }
            var showKey by remember { mutableStateOf(false) }

            StealthSectionCard(
                title = "CAPTCHA Auto-Solver & Evasion",
                subtitle = "Automatically solves reCAPTCHA, Cloudflare Turnstile & Google /sorry challenges",
                icon = Icons.Rounded.Security,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                ToggleRow(
                    title = "Enable CAPTCHA Auto-Solver",
                    subtitle = "Humanized MotionEvent checkbox tap, optional solver API & interactive watchdog",
                    checked = settings.autoSolveCaptcha,
                    onCheckedChange = viewModel::setAutoSolveCaptcha,
                    testTag = "toggle_auto_solve_captcha"
                )
                ToggleRow(
                    title = "Open Target Domain Directly on CAPTCHA",
                    subtitle = "If CAPTCHA cannot be solved automatically, immediately navigate to your target website without waiting",
                    checked = settings.directOpenDomainOnCaptcha,
                    onCheckedChange = viewModel::setDirectOpenDomainOnCaptcha,
                    testTag = "toggle_direct_domain_captcha"
                )
                ToggleRow(
                    title = "Auto-Close Profile & Open Next After Time Completes",
                    subtitle = "Automatically closes each profile once its humanized stay timer finishes and launches the next queued profile",
                    checked = settings.autoCloseProfileAfterHumanize,
                    onCheckedChange = viewModel::setAutoCloseProfileAfterHumanize,
                    testTag = "toggle_auto_close_profile"
                )
                ToggleRow(
                    title = "Rotate Sticky Session (-ssid-) on Google Block",
                    subtitle = "Automatically pulls a fresh exit IP on sticky proxies like NiceProxy if Google /sorry appears",
                    checked = settings.rotateProxySessionOnCaptcha,
                    onCheckedChange = viewModel::setRotateProxyOnCaptcha,
                    testTag = "toggle_rotate_ssid_captcha"
                )

                Text(
                    text = "Solver Strategy / Provider",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CaptchaSolverProvider.entries.forEach { prov ->
                        FilterChip(
                            selected = settings.captchaSolverProvider == prov,
                            onClick = { viewModel.setCaptchaProvider(prov) },
                            label = { Text(prov.label) }
                        )
                    }
                }

                if (settings.captchaSolverProvider != CaptchaSolverProvider.TOUCH_ONLY) {
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = {
                            apiKeyDraft = it
                            viewModel.setCaptchaApiKey(it)
                        },
                        label = { Text("${settings.captchaSolverProvider.label} Key") },
                        placeholder = { Text("Paste API key for 100% unattended image/token solving") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("captcha_api_key_input")
                    )
                }

                Text(
                    text = "Manual Tap Grace Window (before auto-opening target website)",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 8, 12, 15).forEach { sec ->
                        FilterChip(
                            selected = settings.captchaInteractiveWaitSeconds == sec,
                            onClick = { viewModel.setCaptchaWaitSeconds(sec) },
                            label = { Text("${sec}s") }
                        )
                    }
                }
            }
        }

        item {
            StealthSectionCard(
                title = "Session Storage Hygiene",
                subtitle = "Reset all isolated WebView cookies, cache, and IndexedDB partitions",
                icon = Icons.Rounded.Cookie,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                Button(
                    onClick = { confirmClearSessions = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertCoral.copy(alpha = 0.16f),
                        contentColor = AlertCoral
                    ),
                    border = BorderStroke(1.dp, AlertCoral.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_sessions_button")
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Text("  Wipe All Browser Sessions & Cookies")
                }
            }
        }
    }

    if (confirmClearSessions) {
        AlertDialog(
            onDismissRequest = { confirmClearSessions = false },
            title = { Text("Wipe All Isolated Sessions?") },
            text = {
                Text("This will delete cookies, local storage, and cache across all browser profiles. Your saved profile fingerprints and proxies will remain intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllSessions()
                        confirmClearSessions = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
                ) {
                    Text("Wipe Sessions")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSessions = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
