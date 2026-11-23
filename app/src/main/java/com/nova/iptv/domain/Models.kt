package com.nova.iptv.domain

enum class ProfileType { M3U, XTREAM }

/** EPISODE is a directly playable series episode; SERIES may be a container (Xtream) or an episode (M3U). */
enum class ContentKind { LIVE, MOVIE, SERIES, EPISODE }

/**
 * A playable entry. [ref] is the stable per-profile identifier: the full URL for M3U profiles,
 * or a credential-free path such as "live/123.ts" for Xtream profiles.
 */
data class PlayableItem(
    val ref: String,
    val title: String,
    val kind: ContentKind,
    val logo: String? = null,
    val subtitle: String? = null,
)

const val XTREAM_SERIES_PREFIX = "xseries:"

fun String.isXtreamSeries() = startsWith(XTREAM_SERIES_PREFIX)
