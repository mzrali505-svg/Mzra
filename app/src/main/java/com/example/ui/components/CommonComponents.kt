package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.model.HumanizeCategory
import com.example.data.model.HumanizeScrollStyle
import com.example.data.model.OsPlatform
import com.example.data.model.ProxyStatus
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AlertCoralBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.StealthIndigo
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg

fun parseAccentColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(CyberCyan)
}

fun osIcon(osPlatform: String): ImageVector {
    return when (runCatching { OsPlatform.valueOf(osPlatform) }.getOrDefault(OsPlatform.WINDOWS)) {
        OsPlatform.WINDOWS, OsPlatform.WINDOWS_11 -> Icons.Rounded.Computer
        OsPlatform.MACOS, OsPlatform.MACOS_M1, OsPlatform.MACOS_M2, OsPlatform.MACOS_M3 -> Icons.Rounded.LaptopMac
        OsPlatform.ANDROID -> Icons.Rounded.Android
        OsPlatform.IOS -> Icons.Rounded.PhoneIphone
        OsPlatform.LINUX -> Icons.Rounded.Terminal
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = null
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProxyStatusPill(
    status: String,
    latencyMs: Long?,
    isChecking: Boolean,
    modifier: Modifier = Modifier
) {
    val parsed = if (isChecking) ProxyStatus.CHECKING else {
        runCatching { ProxyStatus.valueOf(status) }.getOrDefault(ProxyStatus.UNCHECKED)
    }
    val (label, bg, fg) = when (parsed) {
        ProxyStatus.ONLINE -> Triple(
            if (latencyMs != null) "LIVE • ${latencyMs}ms" else "LIVE",
            VerifiedEmeraldBg,
            VerifiedEmerald
        )
        ProxyStatus.CHECKING -> Triple("PROBING...", CyberCyanContainer, CyberCyan)
        ProxyStatus.OFFLINE -> Triple("DEAD", AlertCoralBg, AlertCoral)
        ProxyStatus.UNCHECKED -> Triple("UNCHECKED", WarningAmberBg, WarningAmber)
    }
    StatusPill(
        text = label,
        containerColor = bg,
        contentColor = fg,
        dotColor = fg,
        modifier = modifier
    )
}

@Composable
fun MonoSpecChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StealthSectionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (icon != null) {
                    Surface(
                        color = StealthIndigo.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
        }
    }
}

private val featuredHumanizeStyles = listOf(
    HumanizeScrollStyle.AUTO,
    HumanizeScrollStyle.DEEP_DWELL_ZEN,
    HumanizeScrollStyle.TWO_MINUTE_CONCENTRATOR,
    HumanizeScrollStyle.CAREFUL_READER,
    HumanizeScrollStyle.GENTLE_ONE_HAND_THUMB,
    HumanizeScrollStyle.SECTION_SNAPPER,
    HumanizeScrollStyle.ZIGZAG_EXPLORER,
    HumanizeScrollStyle.TOUCH_MOMENTUM,
    HumanizeScrollStyle.SKIMMER_SCANNER
)

@Composable
fun HumanizeScrollStyleSelector(
    selectedStyleName: String,
    onSelectStyle: (HumanizeScrollStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStyle = HumanizeScrollStyle.fromRaw(selectedStyleName)
    var showBrowserDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Humanize Behavior (${HumanizeScrollStyle.entries.size} Presets)",
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedButton(
                onClick = { showBrowserDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Browse All ${HumanizeScrollStyle.entries.size}")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickList = remember(activeStyle) {
                if (activeStyle in featuredHumanizeStyles) featuredHumanizeStyles
                else listOf(activeStyle) + featuredHumanizeStyles
            }
            quickList.forEach { style ->
                FilterChip(
                    selected = activeStyle == style,
                    onClick = { onSelectStyle(style) },
                    label = { Text(style.shortLabel) }
                )
            }
        }

        Surface(
            color = CyberCyanContainer.copy(alpha = 0.25f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showBrowserDialog = true }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeStyle.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberCyan,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeStyle.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald
                    )
                }
                Text(
                    text = activeStyle.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showBrowserDialog) {
        HumanizeBehaviorPickerDialog(
            selectedStyle = activeStyle,
            onDismiss = { showBrowserDialog = false },
            onSelect = { style ->
                showBrowserDialog = false
                onSelectStyle(style)
            }
        )
    }
}

@Composable
fun HumanizeBehaviorPickerDialog(
    selectedStyle: HumanizeScrollStyle,
    onDismiss: () -> Unit,
    onSelect: (HumanizeScrollStyle) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<HumanizeCategory?>(null) }

    val filteredStyles = remember(search, selectedCategory) {
        val q = search.trim().lowercase()
        HumanizeScrollStyle.entries.filter { style ->
            val catOk = selectedCategory == null || style.category == selectedCategory
            val qOk = q.isBlank() ||
                style.displayName.lowercase().contains(q) ||
                style.shortLabel.lowercase().contains(q) ||
                style.description.lowercase().contains(q) ||
                style.category.label.lowercase().contains(q)
            catOk && qOk
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Humanized Behavior Presets (${HumanizeScrollStyle.entries.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search ${HumanizeScrollStyle.entries.size} reading & scroll styles...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${HumanizeScrollStyle.entries.size})") }
                    )
                    HumanizeCategory.entries.forEach { cat ->
                        val catCount = remember(cat) { HumanizeScrollStyle.entries.count { it.category == cat } }
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.label} ($catCount)") }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 180.dp, max = 330.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredStyles, key = { it.name }) { style ->
                        val isSelected = style == selectedStyle
                        Surface(
                            color = if (isSelected) CyberCyanContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CyberCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(style) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${style.category.label} • Pause ${style.pauseMinMs / 1000}–${style.pauseMaxMs / 1000}s • ${style.description}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

