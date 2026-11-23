package com.nova.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** "NOVA Blue" palette from docs/SPECIFICATIONS_UX_UI.md. */
object NovaColors {
    val Background = Color(0xFF080D18)
    val Background2 = Color(0xFF101829)
    val Card = Color(0xFF172238)
    val Blue = Color(0xFF258BFF)
    val Cyan = Color(0xFF43D5FF)
    val Violet = Color(0xFF8257FF)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFA5B1C5)
    val Border = Color(0xFF27344A)
    val Error = Color(0xFFFF6B7A)
}

private val NovaBlueScheme = darkColorScheme(
    primary = NovaColors.Cyan,
    onPrimary = Color(0xFF00131A),
    primaryContainer = NovaColors.Blue,
    onPrimaryContainer = Color.White,
    secondary = NovaColors.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2350),
    onSecondaryContainer = Color.White,
    tertiary = NovaColors.Blue,
    background = NovaColors.Background,
    onBackground = NovaColors.TextPrimary,
    surface = NovaColors.Background2,
    onSurface = NovaColors.TextPrimary,
    surfaceVariant = NovaColors.Card,
    onSurfaceVariant = NovaColors.TextSecondary,
    outline = NovaColors.Border,
    outlineVariant = NovaColors.Border,
    error = NovaColors.Error,
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NovaBlueScheme, content = content)
}
