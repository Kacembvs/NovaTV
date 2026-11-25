package com.nova.iptv.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.nova.iptv.core.LocalTvLayout
import com.nova.iptv.core.resolveTvLayout

enum class NovaThemeId(val label: String) {
    NOVA_BLUE("NOVA Blue"),
    CINEMA_GOLD("Cinema Gold"),
    STREAM_RED("Stream Red"),
    PURE_LIGHT("Pure Light"),
    CUSTOM("Personnalisé"),
    MONOCHROME("NOVA+ Noir et blanc"),
    UNIVERSE_INDIGO("Univers Indigo"),
    HORIZON_OCEAN("Horizon Océan"),
    GUIDE_EMERALD("Guide Émeraude"),
}

data class NovaPalette(
    val background: Color,
    val background2: Color,
    val card: Color,
    val accent: Color,
    val accent2: Color,
    val accentStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val error: Color,
    val isLight: Boolean = false,
)

object NovaPalettes {
    /** Accent colours offered for the "Personnalisé" theme. */
    val CUSTOM_ACCENTS = listOf(
        0xFF43D5FF, 0xFF8257FF, 0xFF2ED47A, 0xFFFF8A3D, 0xFFFF4FA3, 0xFFE8C45C, 0xFFFF4D5A, 0xFFB0BEC5,
    ).map { it.toInt() }

    private val dark = NovaPalette(
        background = Color(0xFF080D18),
        background2 = Color(0xFF101829),
        card = Color(0xFF172238),
        accent = Color(0xFF43D5FF),
        accent2 = Color(0xFF8257FF),
        accentStrong = Color(0xFF258BFF),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA5B1C5),
        border = Color(0xFF27344A),
        error = Color(0xFFFF6B7A),
    )

    fun of(id: NovaThemeId, customAccent: Int? = null): NovaPalette = when (id) {
        NovaThemeId.NOVA_BLUE -> dark
        NovaThemeId.CINEMA_GOLD -> NovaPalette(
            background = Color(0xFF0B0A08),
            background2 = Color(0xFF15130F),
            card = Color(0xFF221E17),
            accent = Color(0xFFE8C45C),
            accent2 = Color(0xFFB88A2E),
            accentStrong = Color(0xFFD4A63A),
            textPrimary = Color(0xFFFFF8E8),
            textSecondary = Color(0xFFBDB29A),
            border = Color(0xFF3A3326),
            error = Color(0xFFFF6B6B),
        )
        NovaThemeId.STREAM_RED -> NovaPalette(
            background = Color(0xFF0A0A0B),
            background2 = Color(0xFF141416),
            card = Color(0xFF202024),
            accent = Color(0xFFFF4D5A),
            accent2 = Color(0xFFB3122E),
            accentStrong = Color(0xFFE50914),
            textPrimary = Color.White,
            textSecondary = Color(0xFFB3B3B8),
            border = Color(0xFF34343A),
            error = Color(0xFFFF8A65),
        )
        NovaThemeId.MONOCHROME -> NovaPalette(
            background = Color(0xFF080808),
            background2 = Color(0xFF141414),
            card = Color(0xFF202020),
            accent = Color.White,
            accent2 = Color(0xFFD0D0D0),
            accentStrong = Color.White,
            textPrimary = Color.White,
            textSecondary = Color(0xFFB5B5B5),
            border = Color(0xFF404040),
            error = Color(0xFFFF6B6B),
        )
        NovaThemeId.PURE_LIGHT -> NovaPalette(
            background = Color(0xFFF5F7FB),
            background2 = Color(0xFFFFFFFF),
            card = Color(0xFFE9EEF6),
            accent = Color(0xFF0A72D9),
            accent2 = Color(0xFF6A3FE0),
            accentStrong = Color(0xFF0A72D9),
            textPrimary = Color(0xFF0E1525),
            textSecondary = Color(0xFF55627A),
            border = Color(0xFFCCD5E3),
            error = Color(0xFFD32F45),
            isLight = true,
        )
        NovaThemeId.UNIVERSE_INDIGO -> NovaPalette(
            background = Color(0xFF070718),
            background2 = Color(0xFF11112E),
            card = Color(0xFF1C1B45),
            accent = Color(0xFFA78BFF),
            accent2 = Color(0xFF6C4DF5),
            accentStrong = Color(0xFF7B5CFF),
            textPrimary = Color.White,
            textSecondary = Color(0xFFB4AFD6),
            border = Color(0xFF2E2B66),
            error = Color(0xFFFF6B7A),
        )
        NovaThemeId.HORIZON_OCEAN -> NovaPalette(
            background = Color(0xFF0F171E),
            background2 = Color(0xFF16222C),
            card = Color(0xFF1F2E3B),
            accent = Color(0xFF00A8E1),
            accent2 = Color(0xFF7FD9FF),
            accentStrong = Color(0xFF0090C8),
            textPrimary = Color.White,
            textSecondary = Color(0xFF9FB3C4),
            border = Color(0xFF2C3F50),
            error = Color(0xFFFF6B7A),
        )
        NovaThemeId.GUIDE_EMERALD -> NovaPalette(
            background = Color(0xFF0B100E),
            background2 = Color(0xFF131A17),
            card = Color(0xFF1C2622),
            accent = Color(0xFF2ED47A),
            accent2 = Color(0xFF8BE8B5),
            accentStrong = Color(0xFF1FB866),
            textPrimary = Color.White,
            textSecondary = Color(0xFFA3B8AD),
            border = Color(0xFF2A3A33),
            error = Color(0xFFFF6B7A),
        )
        NovaThemeId.CUSTOM -> {
            val accent = Color(customAccent ?: CUSTOM_ACCENTS.first())
            dark.copy(accent = accent, accentStrong = accent)
        }
    }
}

/**
 * Current palette. Backed by Compose state, so changing the theme recomposes every screen
 * immediately, without restarting the app.
 */
object NovaColors {
    var palette by mutableStateOf(NovaPalettes.of(NovaThemeId.NOVA_BLUE))

    val Background get() = palette.background
    val Background2 get() = palette.background2
    val Card get() = palette.card
    val Blue get() = palette.accentStrong
    val Cyan get() = palette.accent
    val Violet get() = palette.accent2
    val TextPrimary get() = palette.textPrimary
    val TextSecondary get() = palette.textSecondary
    val Border get() = palette.border
    val Error get() = palette.error
}

private fun NovaPalette.toScheme(): ColorScheme {
    val onAccent = if (isLight) Color.White else Color(0xFF00131A)
    val onContainer = if (accentStrong.luminance() > 0.4f) Color(0xFF00131A) else Color.White
    val onSecondaryAccent = if (accent2.luminance() > 0.4f) Color(0xFF00131A) else Color.White
    return if (isLight) {
        lightColorScheme(
            primary = accent, onPrimary = onAccent, primaryContainer = accentStrong, onPrimaryContainer = onContainer,
            secondary = accent2, onSecondary = onSecondaryAccent, secondaryContainer = card, onSecondaryContainer = textPrimary,
            tertiary = accentStrong, background = background, onBackground = textPrimary, surface = background2,
            onSurface = textPrimary, surfaceVariant = card, onSurfaceVariant = textSecondary, outline = border,
            outlineVariant = border, error = error,
        )
    } else {
        darkColorScheme(
            primary = accent, onPrimary = onAccent, primaryContainer = accentStrong, onPrimaryContainer = onContainer,
            secondary = accent2, onSecondary = onSecondaryAccent, secondaryContainer = card, onSecondaryContainer = Color.White,
            tertiary = accentStrong, background = background, onBackground = textPrimary, surface = background2,
            onSurface = textPrimary, surfaceVariant = card, onSurfaceVariant = textSecondary, outline = border,
            outlineVariant = border, error = error,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun tvRippleConfiguration(isLight: Boolean) = RippleConfiguration(
    color = if (isLight) Color.Black else Color.White,
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0.12f,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    val tvLayout = resolveTvLayout()
    if (tvLayout) {
        CompositionLocalProvider(
            LocalTvLayout provides true,
            LocalRippleConfiguration provides tvRippleConfiguration(NovaColors.palette.isLight),
            LocalTvInterface provides NovaTvInterface.current,
        ) {
            MaterialTheme(colorScheme = NovaColors.palette.toScheme(), content = content)
        }
    } else {
        MaterialTheme(colorScheme = NovaColors.palette.toScheme(), content = content)
    }
}
