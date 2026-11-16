package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BrowserProfileEntity
import com.example.data.model.FingerprintPresets
import com.example.data.model.SpoofMode
import com.example.data.model.WebGlMode
import com.example.ui.theme.CyberCyan
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun ProfileEditorAdvancedSection(
    draft: BrowserProfileEntity,
    onDraftChange: (BrowserProfileEntity) -> Unit,
    onCopiedUa: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showFontsDialog by remember { mutableStateOf(false) }
    var showWebGlParamsDialog by remember { mutableStateOf(false) }
    var showResolutionMenu by remember { mutableStateOf(false) }
    var showCpuMenu by remember { mutableStateOf(false) }
    var showRamMenu by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // --- Navigator ---
        Text("Navigator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)

        OutlinedTextField(
            value = draft.userAgent,
            onValueChange = { onDraftChange(draft.copy(userAgent = it)) },
            label = { Text("User-agent") },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(draft.userAgent))
                    onCopiedUa()
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy User-Agent")
                }
            },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_ua_input")
        )

        // Screen Resolution Dropdown
        Box {
            OutlinedTextField(
                value = "${draft.screenWidth}x${draft.screenHeight}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Screen resolution") },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResolutionMenu = true }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showResolutionMenu = true }
            )
            DropdownMenu(
                expanded = showResolutionMenu,
                onDismissRequest = { showResolutionMenu = false }
            ) {
                FingerprintPresets.resolutionPresets.forEach { res ->
                    DropdownMenuItem(
                        text = { Text(res) },
                        onClick = {
                            val parts = res.split("x")
                            val w = parts.getOrNull(0)?.toIntOrNull() ?: draft.screenWidth
                            val h = parts.getOrNull(1)?.toIntOrNull() ?: draft.screenHeight
                            onDraftChange(draft.copy(screenWidth = w, screenHeight = h))
                            showResolutionMenu = false
                        }
                    )
                }
            }
        }

        // Languages
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Languages", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fill based on IP", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = draft.languagesFillFromIp,
                    onCheckedChange = { onDraftChange(draft.copy(languagesFillFromIp = it)) }
                )
            }
            if (!draft.languagesFillFromIp) {
                OutlinedTextField(
                    value = draft.languagesCsv,
                    onValueChange = {
                        val primary = it.split(",").firstOrNull()?.trim().orEmpty().ifBlank { "en-US" }
                        onDraftChange(draft.copy(languagesCsv = it, language = primary))
                    },
                    label = { Text("Languages (comma-separated, e.g. en-US,en)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        OutlinedTextField(
            value = draft.platformString,
            onValueChange = { onDraftChange(draft.copy(platformString = it)) },
            label = { Text("Platform") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // CPU Threads
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = draft.hardwareConcurrency.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("CPU threads") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { showCpuMenu = true })
                DropdownMenu(expanded = showCpuMenu, onDismissRequest = { showCpuMenu = false }) {
                    listOf(2, 4, 6, 8, 12, 16, 24).forEach { cores ->
                        DropdownMenuItem(
                            text = { Text("$cores") },
                            onClick = {
                                onDraftChange(draft.copy(hardwareConcurrency = cores))
                                showCpuMenu = false
                            }
                        )
                    }
                }
            }

            // RAM
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = draft.deviceMemory.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("RAM (GB)") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { showRamMenu = true })
                DropdownMenu(expanded = showRamMenu, onDismissRequest = { showRamMenu = false }) {
                    listOf(2, 4, 6, 8, 12, 16, 32).forEach { ram ->
                        DropdownMenuItem(
                            text = { Text("$ram") },
                            onClick = {
                                onDraftChange(draft.copy(deviceMemory = ram))
                                showRamMenu = false
                            }
                        )
                    }
                }
            }
        }

        SwitchSettingRow(
            label = "DoNotTrack",
            checked = draft.doNotTrack,
            onCheckedChange = { onDraftChange(draft.copy(doNotTrack = it)) }
        )

        HorizontalDivider()

        // --- Fonts ---
        Text("Fonts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)
        SwitchSettingRow(
            label = "Font masking",
            checked = draft.fontMasking,
            onCheckedChange = { onDraftChange(draft.copy(fontMasking = it)) }
        )
        SwitchSettingRow(
            label = "Enable unicode glyphs and DOMRect",
            checked = draft.enableUnicodeGlyphsAndDomRect,
            onCheckedChange = { onDraftChange(draft.copy(enableUnicodeGlyphsAndDomRect = it)) }
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val activeFonts = draft.fontsListCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                activeFonts.forEach { f ->
                    Text(f, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(
            onClick = { showFontsDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_fonts_btn")
        ) {
            Text("EDIT")
        }

        HorizontalDivider()

        // --- Media Devices ---
        Text("Media devices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)
        SwitchSettingRow(
            label = "Mask media devices",
            checked = draft.maskMediaDevices,
            onCheckedChange = { onDraftChange(draft.copy(maskMediaDevices = it)) }
        )
        MediaCounterRow(
            label = "Video inputs:",
            count = draft.videoInputsCount,
            onChange = { onDraftChange(draft.copy(videoInputsCount = it)) }
        )
        MediaCounterRow(
            label = "Audio inputs:",
            count = draft.audioInputsCount,
            onChange = { onDraftChange(draft.copy(audioInputsCount = it)) }
        )
        MediaCounterRow(
            label = "Audio outputs:",
            count = draft.audioOutputsCount,
            onChange = { onDraftChange(draft.copy(audioOutputsCount = it)) }
        )

        HorizontalDivider()

        // --- Hardware (Canvas, Audio, WebGL) ---
        Text("Hardware", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)

        SegmentedOptionRow(
            title = "Canvas",
            options = listOf(SpoofMode.NOISE.name to "NOISE", SpoofMode.OFF.name to "OFF", SpoofMode.BLOCK.name to "BLOCK"),
            selected = draft.canvasMode,
            onSelect = { onDraftChange(draft.copy(canvasMode = it)) }
        )

        SegmentedOptionRow(
            title = "Audio context",
            options = listOf(SpoofMode.NOISE.name to "NOISE", SpoofMode.OFF.name to "OFF"),
            selected = draft.audioMode,
            onSelect = { onDraftChange(draft.copy(audioMode = it)) }
        )

        SegmentedOptionRow(
            title = "WebGL image",
            options = listOf(SpoofMode.NOISE.name to "NOISE", SpoofMode.OFF.name to "OFF"),
            selected = draft.webGlImageMode,
            onSelect = { onDraftChange(draft.copy(webGlImageMode = it)) }
        )

        SegmentedOptionRow(
            title = "WebGL metadata",
            options = listOf(WebGlMode.MASK.name to "MASK", WebGlMode.OFF.name to "OFF"),
            selected = draft.webGlMetadataMode,
            onSelect = { onDraftChange(draft.copy(webGlMetadataMode = it)) }
        )

        OutlinedTextField(
            value = draft.webGlVendor,
            onValueChange = { onDraftChange(draft.copy(webGlVendor = it)) },
            label = { Text("WebGL vendor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.webGlRenderer,
            onValueChange = { onDraftChange(draft.copy(webGlRenderer = it)) },
            label = { Text("WebGL renderer") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { showWebGlParamsDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("other_webgl_params_btn")
        ) {
            Text("OTHER WEBGL PARAMETERS")
        }

        HorizontalDivider()

        // --- Storage options ---
        Text("Storage options", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)
        SwitchSettingRow(
            label = "Local storage and indexedDB",
            checked = draft.localStorageAndIndexedDb,
            onCheckedChange = { onDraftChange(draft.copy(localStorageAndIndexedDb = it)) }
        )
        SwitchSettingRow(
            label = "Enable extension storage",
            checked = draft.enableExtensionStorage,
            onCheckedChange = { onDraftChange(draft.copy(enableExtensionStorage = it)) }
        )

        HorizontalDivider()

        // --- Browser plugins ---
        Text("Browser plugins", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)
        SwitchSettingRow(
            label = "Potentially vulnerable plugins",
            checked = draft.allowVulnerablePlugins,
            onCheckedChange = { onDraftChange(draft.copy(allowVulnerablePlugins = it)) }
        )

        HorizontalDivider()

        // --- Other ---
        Text("Other", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CyberCyan)
        SwitchSettingRow(
            label = "Active session lock",
            checked = draft.activeSessionLock,
            onCheckedChange = { onDraftChange(draft.copy(activeSessionLock = it)) }
        )
        SwitchSettingRow(
            label = "Enable Google services",
            checked = draft.enableGoogleServices,
            onCheckedChange = { onDraftChange(draft.copy(enableGoogleServices = it)) }
        )
        SwitchSettingRow(
            label = "Browser bookmark saving",
            checked = draft.bookmarkSaving,
            onCheckedChange = { onDraftChange(draft.copy(bookmarkSaving = it)) }
        )
        SwitchSettingRow(
            label = "Browser history saving",
            checked = draft.historySaving,
            onCheckedChange = { onDraftChange(draft.copy(historySaving = it)) }
        )
        SwitchSettingRow(
            label = "Enable password saving",
            checked = draft.passwordSaving,
            onCheckedChange = { onDraftChange(draft.copy(passwordSaving = it)) }
        )
        SwitchSettingRow(
            label = "Enable session saving",
            checked = draft.sessionSaving,
            onCheckedChange = { onDraftChange(draft.copy(sessionSaving = it)) }
        )
        SwitchSettingRow(
            label = "Debug mode",
            checked = draft.debugMode,
            onCheckedChange = { onDraftChange(draft.copy(debugMode = it)) }
        )

        OutlinedTextField(
            value = draft.startUrl,
            onValueChange = { onDraftChange(draft.copy(startUrl = it)) },
            label = { Text("Start URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.customDns,
            onValueChange = { onDraftChange(draft.copy(customDns = it)) },
            label = { Text("Custom DNS (e.g. 1.1.1.1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showFontsDialog) {
        val currentSet = remember(draft.fontsListCsv) {
            draft.fontsListCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
        }
        var selectedFonts by remember { mutableStateOf(currentSet.toSet()) }

        AlertDialog(
            onDismissRequest = { showFontsDialog = false },
            title = { Text("Masked Fonts List") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(FingerprintPresets.allFontsCatalog) { font ->
                        val checked = selectedFonts.contains(font)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFonts = if (checked) selectedFonts - font else selectedFonts + font
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedFonts = if (it) selectedFonts + font else selectedFonts - font
                                }
                            )
                            Text(font, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onDraftChange(draft.copy(fontsListCsv = selectedFonts.joinToString(",")))
                    showFontsDialog = false
                }) {
                    Text("Apply (${selectedFonts.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFontsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWebGlParamsDialog) {
        var maxTex by remember { mutableStateOf(draft.webGlMaxTextureSize.toString()) }
        var maxAttr by remember { mutableStateOf(draft.webGlMaxVertexAttribs.toString()) }
        var shading by remember { mutableStateOf(draft.webGlShadingLang) }

        AlertDialog(
            onDismissRequest = { showWebGlParamsDialog = false },
            title = { Text("Other WebGL Parameters") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = maxTex,
                        onValueChange = { maxTex = it.filter(Char::isDigit) },
                        label = { Text("MAX_TEXTURE_SIZE (3379)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxAttr,
                        onValueChange = { maxAttr = it.filter(Char::isDigit) },
                        label = { Text("MAX_VERTEX_ATTRIBS (34921)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shading,
                        onValueChange = { shading = it },
                        label = { Text("SHADING_LANGUAGE_VERSION (35724)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            onDraftChange(
                                draft.copy(
                                    canvasSeed = abs(Random.nextLong(100_000L, 999_999_999L)),
                                    audioSeed = abs(Random.nextLong(100_000L, 999_999_999L)),
                                    webGlSeed = abs(Random.nextLong(100_000L, 999_999_999L)),
                                    clientRectsSeed = abs(Random.nextLong(100_000L, 999_999_999L))
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Regenerate Hardware Noise Seeds")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onDraftChange(
                        draft.copy(
                            webGlMaxTextureSize = maxTex.toIntOrNull() ?: draft.webGlMaxTextureSize,
                            webGlMaxVertexAttribs = maxAttr.toIntOrNull() ?: draft.webGlMaxVertexAttribs,
                            webGlShadingLang = shading
                        )
                    )
                    showWebGlParamsDialog = false
                }) {
                    Text("Save Parameters")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebGlParamsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SegmentedOptionRow(
    title: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (key, label) ->
                    val isSelected = selected == key
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clickable { onSelect(key) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCounterRow(
    label: String,
    count: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        IconButton(
            onClick = { onChange((count - 1).coerceAtLeast(0)) },
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(18.dp))
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(
            onClick = { onChange((count + 1).coerceAtMost(4)) },
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label", modifier = Modifier.size(18.dp))
        }
    }
}
