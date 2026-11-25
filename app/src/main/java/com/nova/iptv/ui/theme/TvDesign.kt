package com.nova.iptv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class TvNavigation { SIDEBAR, RAIL, TOP }
enum class TvCatalogPresentation { POSTERS, LANDSCAPE, LIST }
enum class TvLivePresentation { PREVIEW, PROGRAMMES, MOSAIC }

enum class TvInterface(
    val label: String,
    val description: String,
    val navigation: TvNavigation,
    val catalog: TvCatalogPresentation,
    val live: TvLivePresentation,
    val horizontalCategories: Boolean,
    val cardRadius: Int,
    val cardWidth: Int,
    val heroHeight: Int,
    val defaultPalette: NovaThemeId,
) {
    CLASSIC("NOVA Classique", "L'interface actuelle, conservée comme repère.",
        TvNavigation.SIDEBAR, TvCatalogPresentation.POSTERS, TvLivePresentation.PREVIEW, false, 14, 150, 340, NovaThemeId.NOVA_BLUE),
    CINEMA("NOVA Cinéma", "Grande scène, rail discret et affiches de cinéma.",
        TvNavigation.RAIL, TvCatalogPresentation.POSTERS, TvLivePresentation.MOSAIC, true, 6, 148, 380, NovaThemeId.STREAM_RED),
    STUDIOS("NOVA Univers", "Navigation haute, collections et grandes affiches.",
        TvNavigation.TOP, TvCatalogPresentation.POSTERS, TvLivePresentation.MOSAIC, true, 20, 180, 310, NovaThemeId.UNIVERSE_INDIGO),
    ANTENNA("NOVA Antenne", "Direct prioritaire et cartes de programmes.",
        TvNavigation.TOP, TvCatalogPresentation.LANDSCAPE, TvLivePresentation.PROGRAMMES, false, 12, 240, 260, NovaThemeId.CINEMA_GOLD),
    DISCOVERY("NOVA Horizon", "Reprises en tête et découverte en cartes larges.",
        TvNavigation.RAIL, TvCatalogPresentation.LANDSCAPE, TvLivePresentation.MOSAIC, true, 10, 230, 300, NovaThemeId.HORIZON_OCEAN),
    GUIDE("NOVA Guide", "Navigation latérale et listes IPTV détaillées.",
        TvNavigation.SIDEBAR, TvCatalogPresentation.LIST, TvLivePresentation.PREVIEW, false, 4, 150, 220, NovaThemeId.GUIDE_EMERALD),
    PLUS("NOVA+", "Noir et blanc, navigation haute, direct et collections.",
        TvNavigation.TOP, TvCatalogPresentation.POSTERS, TvLivePresentation.PROGRAMMES, true, 2, 170, 300, NovaThemeId.MONOCHROME),
    ;

    val posterAspectRatio: Float get() = if (catalog == TvCatalogPresentation.LANDSCAPE) 16f / 9f else 2f / 3f
}

object NovaTvInterface {
    var current by mutableStateOf(TvInterface.CLASSIC)
}

val LocalTvInterface = staticCompositionLocalOf { TvInterface.CLASSIC }
