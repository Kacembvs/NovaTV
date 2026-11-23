package com.nova.iptv.player

import com.nova.iptv.domain.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRulesTest {

    @Test
    fun sleepModeHasPriorityOverAutoplay() {
        assertEquals(
            EndAction.SLEEP_STOP,
            PlaybackRules.onItemEnded(sleepAtEndOfItem = true, hasNext = true, autoplay = true, nextCancelled = false),
        )
    }

    @Test
    fun autoplayChainsOnlyWhenAllowedAndNotCancelled() {
        assertEquals(EndAction.PLAY_NEXT, PlaybackRules.onItemEnded(false, hasNext = true, autoplay = true, nextCancelled = false))
        assertEquals(EndAction.SHOW_END_SCREEN, PlaybackRules.onItemEnded(false, hasNext = true, autoplay = false, nextCancelled = false))
        assertEquals(EndAction.SHOW_END_SCREEN, PlaybackRules.onItemEnded(false, hasNext = true, autoplay = true, nextCancelled = true))
        assertEquals(EndAction.SHOW_END_SCREEN, PlaybackRules.onItemEnded(false, hasNext = false, autoplay = true, nextCancelled = false))
    }

    @Test
    fun nextUpAppearsInTheLastCountdownSecondsOfAnEpisode() {
        fun show(position: Long, kind: ContentKind = ContentKind.EPISODE, sleep: Boolean = false, cancelled: Boolean = false) =
            PlaybackRules.shouldShowNextUp(kind, true, true, sleep, cancelled, durationMs = 1_200_000, positionMs = position, countdownSeconds = 20)

        assertFalse(show(1_100_000))
        assertTrue(show(1_180_000))
        assertTrue(show(1_195_000))
        assertFalse(show(1_185_000, kind = ContentKind.MOVIE))
        assertFalse(show(1_185_000, sleep = true))
        assertFalse(show(1_185_000, cancelled = true))
    }

    @Test
    fun nextUpIsNotShownForVeryShortItems() {
        assertFalse(PlaybackRules.shouldShowNextUp(ContentKind.EPISODE, true, true, false, false, 30_000, 25_000, 20))
    }
}
