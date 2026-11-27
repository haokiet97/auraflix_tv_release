package com.auraflix.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Bảng màu thương hiệu AuraFlix, dùng chung tinh thần với bản TV. */
object AuraColors {
    val Background = Color(0xFF14151C)
    val BackgroundElevated = Color(0xFF1B1D26)
    val Surface = Color(0xFF23252F)
    val SurfaceVariant = Color(0xFF2D303C)
    val Accent = Color(0xFFFF5A5F)
    val AccentSoft = Color(0xFFFF9A3D)
    val TextPrimary = Color(0xFFF5F6FA)
    val TextSecondary = Color(0xFFA6AAB8)
    val TextMuted = Color(0xFF74798A)
    val Scrim = Color(0xE6000000)
}

private val AuraColorScheme = darkColorScheme(
    primary = AuraColors.Accent,
    onPrimary = Color.White,
    primaryContainer = AuraColors.Accent,
    onPrimaryContainer = Color.White,
    secondary = AuraColors.AccentSoft,
    onSecondary = Color.Black,
    background = AuraColors.Background,
    onBackground = AuraColors.TextPrimary,
    surface = AuraColors.Surface,
    onSurface = AuraColors.TextPrimary,
    surfaceVariant = AuraColors.SurfaceVariant,
    onSurfaceVariant = AuraColors.TextSecondary,
    outline = AuraColors.TextMuted,
    error = AuraColors.Accent,
)

@Composable
fun AuraMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        typography = Typography(),
        content = content,
    )
}
