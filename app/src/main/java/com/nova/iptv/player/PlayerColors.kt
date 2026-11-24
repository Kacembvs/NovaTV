package com.nova.iptv.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaPalettes
import com.nova.iptv.ui.theme.NovaThemeId

internal object PlayerColors {
    private val palette
        get() = NovaColors.palette.let {
            if (it.isLight) NovaPalettes.of(NovaThemeId.MONOCHROME) else it
        }

    val Panel get() = palette.background2.copy(alpha = 0.96f)
    val Control get() = palette.card
    val Focused get() = lerp(palette.card, Color.White, 0.12f)
    val Border get() = palette.border
    val Accent get() = palette.accent
    val Secondary get() = palette.textSecondary
    val Error get() = palette.error
}
