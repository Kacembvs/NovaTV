package com.nova.iptv.player

import com.nova.iptv.domain.ContentKind

enum class EndAction { SLEEP_STOP, PLAY_NEXT, SHOW_END_SCREEN }

sealed interface SleepMode {
    data object Off : SleepMode
    /** Stop at the end of the current episode / film. */
    data object EndOfItem : SleepMode
    data class Timer(val deadlineMs: Long, val minutes: Int) : SleepMode
}

/** Pure decision rules for autoplay and sleep mode (sleep mode always wins). */
object PlaybackRules {
    const val DEFAULT_COUNTDOWN_SECONDS = 20
    val COUNTDOWN_CHOICES = listOf(5, 10, 15, 20)
    val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60)

    fun onItemEnded(
        sleepAtEndOfItem: Boolean,
        hasNext: Boolean,
        autoplay: Boolean,
        nextCancelled: Boolean,
    ): EndAction = when {
        sleepAtEndOfItem -> EndAction.SLEEP_STOP
        hasNext && autoplay && !nextCancelled -> EndAction.PLAY_NEXT
        else -> EndAction.SHOW_END_SCREEN
    }

    fun shouldShowNextUp(
        kind: ContentKind,
        hasNext: Boolean,
        autoplay: Boolean,
        sleepAtEndOfItem: Boolean,
        cancelled: Boolean,
        durationMs: Long,
        positionMs: Long,
        countdownSeconds: Int,
    ): Boolean {
        val countdownMs = countdownSeconds * 1_000L
        return kind == ContentKind.EPISODE &&
            hasNext &&
            autoplay &&
            !sleepAtEndOfItem &&
            !cancelled &&
            durationMs > countdownMs * 2 &&
            durationMs - positionMs in 0..countdownMs
    }
}
