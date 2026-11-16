package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.browser.ProfileWebViewManager
import com.example.data.model.DeviceBlueprint
import com.example.data.model.FingerprintPresets
import com.example.ui.MainViewModel
import com.example.ui.components.MonoSpecChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StealthSectionCard
import com.example.ui.components.osIcon
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.StealthIndigo
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg

private data class LeakAuditTarget(
    val title: String,
    val subtitle: String,
    val url: String
)

private val auditTargets = listOf(
    LeakAuditTarget(
        title = "CloakDeck Built-in Self-Audit",
        subtitle = "Instant offline+online probe for UA, WebGL, Canvas, Audio, WebRTC, Geo & Exit IP",
        url = "cloakdeck://verify"
    ),
    LeakAuditTarget(
        title = "BrowserLeaks JavaScript",
        subtitle = "Industry standard navigator, screen, timezone & client hints check",
        url = "https://browserleaks.com/javascript"
    ),
    LeakAuditTarget(
        title = "PixelScan Fingerprint Check",
        subtitle = "Detects OS/UA mismatches, WebRTC leaks & proxy timezone consistency",
        url = "https://pixelscan.net/"
    ),
    LeakAuditTarget(
        title = "Whoer.net Anonymity Score",
        subtitle = "Evaluates IP, DNS, WebRTC, Language & System Time alignment",
        url = "https://whoer.net/"
    ),
    LeakAuditTarget(
        title = "IPLeak.net",
        subtitle = "Comprehensive WebRTC IPv4/IPv6 & Geolocation leak detector",
        url = "https://ipleak.net/"
    )
)

@Composable
fun StealthLabScreen(
    viewModel: MainViewModel,
    onLaunchProfile: (Long, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()

    val chromiumCaps = remember { ProfileWebViewManager.inspectChromiumCapabilities(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(modifier = Modifier.height(185.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_stealth_header_1794646271931),
                        contentDescription = "CloakDeck Anti-Detect Stealth Engine",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x660B0F14),
                                        Color(0xEE0B0F14)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusPill(
                            text = "ANTI-DETECT CORE v1.0",
                            containerColor = CyberCyan.copy(alpha = 0.16f),
                            contentColor = CyberCyan,
                            dotColor = CyberCyan
                        )
                        Column {
                            Text(
                                text = "Stealth & Fingerprint Lab",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inspect WebView isolation capabilities, spin up 1-tap spoofed identities, or run live leak diagnostics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }

        item {
            StealthSectionCard(
                title = "Engine Capability Telemetry",
                subtitle = "AndroidX Webkit Chromium ${chromiumCaps.versionName} (${chromiumCaps.packageName})",
                icon = Icons.Rounded.Shield,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                CapabilityRow("Multi-Profile Cookie/Storage Isolation", chromiumCaps.multiProfile)
                CapabilityRow("Native WebView Proxy Override", chromiumCaps.proxyOverride)
                CapabilityRow("Pre-DOM Document-Start JS Injection", chromiumCaps.documentStartScript)
                CapabilityRow("HTTP UA Client Hints (Sec-CH-UA)", chromiumCaps.userAgentMetadata)
                CapabilityRow("Renderer Process Crash & Hang Guard", chromiumCaps.rendererClient)
                CapabilityRow("Safe Browsing & WebAuthn Compat", chromiumCaps.safeBrowsing && chromiumCaps.webAuthn)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonoSpecChip("Profiles", "${profiles.size}", modifier = Modifier.weight(1f))
                    MonoSpecChip("Proxies", "${proxies.size}", modifier = Modifier.weight(1f), accent = StealthIndigo)
                }
            }
        }

        item {
            StealthSectionCard(
                title = "1-Tap Blueprint Launch",
                subtitle = "Create & immediately launch a fresh isolated fingerprint",
                icon = Icons.Rounded.Bolt,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FingerprintPresets.deviceBlueprints.take(4).forEach { bp ->
                        BlueprintQuickRow(
                            blueprint = bp,
                            onCreateAndLaunch = {
                                val firstLiveProxy = proxies.firstOrNull { it.lastExitIp.isNotBlank() }?.id
                                viewModel.createQuickProfile(
                                    name = "${bp.osPlatform.displayName} #${profiles.size + 1}",
                                    groupTag = "Quick Lab",
                                    proxyId = firstLiveProxy,
                                    blueprint = bp
                                ) { newId ->
                                    onLaunchProfile(newId, "cloakdeck://verify")
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            StealthSectionCard(
                title = "Live Leak & Fingerprint Auditors",
                subtitle = if (profiles.isNotEmpty()) {
                    "Runs inside '${profiles.first().name}'"
                } else {
                    "Create a profile first to run external leak checks"
                },
                icon = Icons.Rounded.Fingerprint,
                modifier = Modifier.widthIn(max = 720.dp)
            ) {
                // Handled below in items
            }
        }

        items(auditTargets) { target ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        val activeProfile = profiles.firstOrNull()
                        if (activeProfile != null) {
                            onLaunchProfile(activeProfile.id, target.url)
                        } else {
                            viewModel.createQuickProfile(
                                name = "Audit Session #1",
                                groupTag = "Audit",
                                proxyId = proxies.firstOrNull()?.id
                            ) { newId ->
                                onLaunchProfile(newId, target.url)
                            }
                        }
                    }
                    .testTag("audit_target_${target.title.take(10).lowercase().replace(" ", "_")}"),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(9.dp)
                                .size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = target.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = target.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Launch,
                        contentDescription = "Launch audit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, supported: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        StatusPill(
            text = if (supported) "ACTIVE" else "FALLBACK",
            containerColor = if (supported) VerifiedEmeraldBg else WarningAmberBg,
            contentColor = if (supported) VerifiedEmerald else WarningAmber,
            dotColor = if (supported) VerifiedEmerald else WarningAmber
        )
    }
}

@Composable
private fun BlueprintQuickRow(
    blueprint: DeviceBlueprint,
    onCreateAndLaunch: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = osIcon(blueprint.osPlatform.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = blueprint.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${blueprint.screenWidth}x${blueprint.screenHeight} • ${blueprint.cpuCores}C/${blueprint.ramGb}GB • ${blueprint.webGlVendor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onCreateAndLaunch,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("quick_launch_${blueprint.id}")
            ) {
                Text("Spin Up", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
