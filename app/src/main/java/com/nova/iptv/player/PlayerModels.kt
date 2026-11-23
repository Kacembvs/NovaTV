package com.nova.iptv.player

import com.nova.iptv.domain.ContentKind

data class NextUp(val title: String, val subtitle: String?, val logo: String?, val secondsLeft: Int)

data class EndScreen(val nextLabel: String?)

data class TrackOption(val label: String, val selected: Boolean, val groupIndex: Int, val trackIndex: Int)

enum class PlayerPanel { OPTIONS, AUDIO, SUBTITLES, SPEED, QUALITY, SLEEP, INFO }

enum class GestureKind { BRIGHTNESS, VOLUME }

data class GestureIndicator(val kind: GestureKind, val level: Float)

data class PlayerUiState(
    val title: String = "",
    val subtitle: String? = null,
    val kind: ContentKind = ContentKind.LIVE,
    val isTv: Boolean = false,
    val controlsVisible: Boolean = false,
    val bannerVisible: Boolean = false,
    val error: String? = null,
    val canZap: Boolean = false,
    val resumeFromMs: Long? = null,
    val nextUp: NextUp? = null,
    val endScreen: EndScreen? = null,
    val sleep: SleepMode = SleepMode.Off,
    val sleepStopped: Boolean = false,
    /** Live only: current and next programme from the XMLTV guide. */
    val programme: String? = null,
    val nextProgramme: String? = null,
    val panel: PlayerPanel? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val videoTracks: List<TrackOption> = emptyList(),
    val qualityAuto: Boolean = true,
    val speed: Float = 1f,
    val techInfo: List<Pair<String, String>> = emptyList(),
    val locked: Boolean = false,
    val gesturesEnabled: Boolean = true,
    val gesture: GestureIndicator? = null,
    val seekFeedback: String? = null,
    /** Incremented to move the remote's focus to the top bar. */
    val topBarFocusRequest: Int = 0,
) {
    val isLive get() = kind == ContentKind.LIVE
}

sealed interface SleepChoice {
    data object Off : SleepChoice
    data object EndOfItem : SleepChoice
    data class Minutes(val minutes: Int) : SleepChoice
}

interface PlayerActions {
    fun back()
    fun retry()
    fun openExternal()
    fun zapNext()
    fun restart()
    fun playNextNow()
    fun cancelNextUp()
    fun replay()
    fun openPanel(panel: PlayerPanel?)
    fun setSleep(mode: SleepChoice)
    fun resumeAfterSleep()
    fun selectAudio(option: TrackOption)
    fun selectSubtitles(option: TrackOption?)
    fun selectQuality(option: TrackOption?)
    fun setSpeed(speed: Float)
    fun toggleLock()
    fun showControls()
    fun seekBy(deltaMs: Long)
    fun adjustBrightness(delta: Float)
    fun adjustVolume(delta: Float)
    fun endGesture()
    fun onTopBarFocused(focused: Boolean)
}
