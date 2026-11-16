package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CloakDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = OnCyberCyan,
    primaryContainer = CyberCyanContainer,
    onPrimaryContainer = CyberCyan,
    secondary = StealthIndigo,
    onSecondary = Color.White,
    secondaryContainer = StealthIndigoContainer,
    onSecondaryContainer = OnStealthIndigo,
    tertiary = VerifiedEmerald,
    onTertiary = Color.White,
    tertiaryContainer = VerifiedEmeraldBg,
    onTertiaryContainer = VerifiedEmerald,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = ElevatedSurface,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder.copy(alpha = 0.55f),
    error = AlertCoral,
    onError = Color.White,
    errorContainer = AlertCoralBg,
    onErrorContainer = AlertCoral
)

private val CloakLightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAF2),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = LightIndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E6FF),
    onSecondaryContainer = Color(0xFF141C66),
    tertiary = VerifiedEmerald,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCard,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightCard,
    surfaceContainerHigh = Color(0xFFE0E9F2),
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.6f),
    error = AlertCoral,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

val CloakDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun CloakDeckTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CloakDarkColorScheme else CloakLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CloakDeckTypography,
        shapes = CloakDeckShapes,
        content = content
    )
}
