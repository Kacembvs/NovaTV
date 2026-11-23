package com.nova.iptv.data

import com.nova.iptv.domain.PlayableItem

/**
 * Hands the play queue to the player screen in memory (a channel list can be far larger than the
 * Binder limit of an Intent).
 */
class PlaybackQueue {
    data class Session(
        val profileId: Long,
        val items: List<PlayableItem>,
        val startIndex: Int,
        /** "Recommencer": ignore the saved position of the first item. */
        val fromStart: Boolean = false,
    )

    @Volatile
    var current: Session? = null
}
