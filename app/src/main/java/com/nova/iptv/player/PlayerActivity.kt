package com.nova.iptv.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nova.iptv.NovaApp
import com.nova.iptv.data.PlaybackQueue
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.ui.theme.NovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NextUp(val title: String, val subtitle: String?, val logo: String?, val secondsLeft: Int)

data class EndScreen(val nextLabel: String?)

data class PlayerUiState(
    val title: String = "",
    val subtitle: String? = null,
    val kind: ContentKind = ContentKind.LIVE,
    val controlsVisible: Boolean = false,
    val bannerVisible: Boolean = false,
    val error: String? = null,
    val canZap: Boolean = false,
    val resumeFromMs: Long? = null,
    val nextUp: NextUp? = null,
    val endScreen: EndScreen? = null,
    val sleep: SleepMode = SleepMode.Off,
    val sleepPanelVisible: Boolean = false,
    val sleepStopped: Boolean = false,
) {
    val isLive get() = kind == ContentKind.LIVE
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
    fun showSleepPanel(visible: Boolean)
    fun setSleep(mode: SleepChoice)
    fun resumeAfterSleep()
}

sealed interface SleepChoice {
    data object Off : SleepChoice
    data object EndOfItem : SleepChoice
    data class Minutes(val minutes: Int) : SleepChoice
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(), PlayerActions {
    private val container by lazy { (application as NovaApp).container }
    private lateinit var session: PlaybackQueue.Session
    private var urls: List<String> = emptyList()
    private var exo: ExoPlayer? = null
    private var player: Player? = null
    private var playerView: PlayerView? = null
    private val ui = MutableStateFlow(PlayerUiState())

    private var progressJob: Job? = null
    private var monitorJob: Job? = null
    private var bannerJob: Job? = null
    private var nextUpJob: Job? = null
    private var sleepJob: Job? = null

    private var liveRetries = 0
    private val hlsFallbackTried = HashSet<Int>()
    private var autoplay = true
    private var countdownSeconds = PlaybackRules.DEFAULT_COUNTDOWN_SECONDS
    private var cancelledNextFor = -1
    private var endHandledFor = -1

    private val isLiveQueue get() = session.items.firstOrNull()?.kind == ContentKind.LIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = container.playbackQueue.current ?: run {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            NovaTheme {
                val state by ui.collectAsStateWithLifecycle()
                PlayerScreen(state = state, onViewCreated = ::configureView, actions = this@PlayerActivity)
            }
        }

        lifecycleScope.launch {
            container.settings.playback.collect {
                autoplay = it.autoplayNext
                countdownSeconds = it.countdownSeconds
            }
        }

        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { container.db.profileDao().get(session.profileId) }
            if (profile == null) {
                Toast.makeText(this@PlayerActivity, "Profil introuvable.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            urls = withContext(Dispatchers.Default) { container.profiles.streamUrls(profile, session.items.map { it.ref }) }
            startPlayback()
        }
    }

    private suspend fun startPlayback() {
        val dataSource = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(container.http))
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo = exoPlayer

        val wrapped = object : ForwardingPlayer(exoPlayer) {
            // Live streams have no meaningful position: previous/next always change channel.
            override fun seekToPrevious() {
                if (isLiveQueue) seekToPreviousMediaItem() else super.seekToPrevious()
            }

            override fun seekToNext() {
                if (isLiveQueue) seekToNextMediaItem() else super.seekToNext()
            }
        }
        player = wrapped
        playerView?.player = wrapped

        val start = session.startIndex
        var startPosition = 0L
        val startItem = session.items[start]
        if (startItem.kind != ContentKind.LIVE) {
            val history = withContext(Dispatchers.IO) { container.db.libraryDao().history(session.profileId, startItem.ref) }
            if (history != null && history.positionMs > 10_000 &&
                (history.durationMs <= 0 || history.positionMs < history.durationMs * 0.95)
            ) {
                startPosition = history.positionMs
                ui.update { it.copy(resumeFromMs = startPosition) }
                lifecycleScope.launch {
                    delay(10_000)
                    ui.update { it.copy(resumeFromMs = null) }
                }
            }
        }

        exoPlayer.addListener(listener)
        if (isLiveQueue) {
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        } else {
            // We decide ourselves what happens at the end of each item (autoplay, sleep mode, end screen).
            exoPlayer.pauseAtEndOfMediaItems = true
        }
        exoPlayer.setMediaItems(session.items.indices.map { mediaItem(it) }, start, startPosition)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        updateTitle()
        startProgressLoop()
        if (!isLiveQueue) startMonitor()
    }

    private fun mediaItem(index: Int, mimeType: String? = null): MediaItem {
        val item = session.items[index]
        return MediaItem.Builder()
            .setUri(urls[index])
            .setMediaId(item.ref)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setSubtitle(item.subtitle).build())
            .build()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            liveRetries = 0
            endHandledFor = -1
            nextUpJob?.cancel()
            ui.update { it.copy(error = null, nextUp = null, endScreen = null) }
            updateTitle()
            showBanner()
            if (currentItem()?.kind == ContentKind.LIVE) saveProgress()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) handleItemEnded()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    liveRetries = 0
                    ui.update { it.copy(error = null) }
                }
                Player.STATE_ENDED -> handleItemEnded()
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = exo ?: return
            val index = p.currentMediaItemIndex
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    p.seekToDefaultPosition()
                    p.prepare()
                }
                // Extension-less HLS links (common in M3U playlists) are not detected automatically.
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && hlsFallbackTried.add(index) -> {
                    p.replaceMediaItem(index, mediaItem(index, MimeTypes.APPLICATION_M3U8))
                    p.prepare()
                }
                currentItem()?.kind == ContentKind.LIVE && isNetworkError(error) && liveRetries < MAX_LIVE_RETRIES -> {
                    liveRetries++
                    lifecycleScope.launch {
                        delay(2_000L * liveRetries)
                        exo?.prepare()
                    }
                }
                else -> ui.update { it.copy(error = describe(error)) }
            }
        }
    }

    // region Autoplay / end of item

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch {
            while (isActive) {
                delay(500)
                checkNextUp()
            }
        }
    }

    private fun checkNextUp() {
        val p = exo ?: return
        val item = currentItem() ?: return
        val index = p.currentMediaItemIndex
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val countdownActive = nextUpJob?.isActive == true
        if (countdownActive) {
            // The user seeked back well before the end: hide the proposal.
            if (duration > 0 && duration - p.currentPosition > (countdownSeconds + 5) * 1_000L) {
                nextUpJob?.cancel()
                ui.update { it.copy(nextUp = null) }
            }
            return
        }
        val show = PlaybackRules.shouldShowNextUp(
            kind = item.kind,
            hasNext = p.hasNextMediaItem(),
            autoplay = autoplay,
            sleepAtEndOfItem = ui.value.sleep == SleepMode.EndOfItem,
            cancelled = cancelledNextFor == index,
            durationMs = duration,
            positionMs = p.currentPosition,
            countdownSeconds = countdownSeconds,
        )
        if (show) startCountdown(index)
    }

    private fun startCountdown(index: Int) {
        val next = session.items.getOrNull(index + 1) ?: return
        nextUpJob = lifecycleScope.launch {
            var left = countdownSeconds
            while (left > 0) {
                ui.update { it.copy(nextUp = NextUp(next.title, next.subtitle, next.logo, left)) }
                delay(1_000)
                // The countdown freezes while the user has paused.
                if (exo?.isPlaying == true) left--
            }
            playNextNow()
        }
    }

    private fun handleItemEnded() {
        val p = exo ?: return
        val index = p.currentMediaItemIndex
        if (endHandledFor == index) return
        endHandledFor = index
        val item = currentItem() ?: return
        markFinished(item, p)
        val action = PlaybackRules.onItemEnded(
            sleepAtEndOfItem = ui.value.sleep == SleepMode.EndOfItem,
            hasNext = p.hasNextMediaItem(),
            autoplay = autoplay && item.kind == ContentKind.EPISODE,
            nextCancelled = cancelledNextFor == index,
        )
        when (action) {
            EndAction.SLEEP_STOP -> triggerSleep()
            EndAction.PLAY_NEXT -> playNextNow()
            EndAction.SHOW_END_SCREEN -> {
                nextUpJob?.cancel()
                val next = session.items.getOrNull(index + 1)?.takeIf { p.hasNextMediaItem() }
                ui.update { it.copy(nextUp = null, endScreen = EndScreen(next?.let { n -> n.subtitle ?: n.title })) }
            }
        }
    }

    private fun markFinished(item: PlayableItem, p: Player) {
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        saveHistory(item, duration, duration)
    }

    override fun playNextNow() {
        nextUpJob?.cancel()
        val p = exo ?: return
        if (!p.hasNextMediaItem()) return
        currentItem()?.let { markFinished(it, p) }
        ui.update { it.copy(nextUp = null, endScreen = null) }
        p.seekToNextMediaItem()
        p.playWhenReady = true
    }

    override fun cancelNextUp() {
        cancelledNextFor = exo?.currentMediaItemIndex ?: -1
        nextUpJob?.cancel()
        ui.update { it.copy(nextUp = null) }
    }

    override fun replay() {
        ui.update { it.copy(endScreen = null) }
        endHandledFor = -1
        exo?.seekTo(0)
        exo?.playWhenReady = true
    }

    // endregion

    // region Sleep mode

    override fun showSleepPanel(visible: Boolean) {
        ui.update { it.copy(sleepPanelVisible = visible) }
    }

    override fun setSleep(mode: SleepChoice) {
        sleepJob?.cancel()
        val sleep = when (mode) {
            SleepChoice.Off -> SleepMode.Off
            SleepChoice.EndOfItem -> SleepMode.EndOfItem
            is SleepChoice.Minutes -> SleepMode.Timer(System.currentTimeMillis() + mode.minutes * 60_000L, mode.minutes)
        }
        if (sleep is SleepMode.Timer) {
            sleepJob = lifecycleScope.launch {
                delay(sleep.minutes * 60_000L)
                triggerSleep()
            }
        }
        if (sleep == SleepMode.EndOfItem) nextUpJob?.cancel()
        ui.update {
            it.copy(
                sleep = sleep,
                sleepPanelVisible = false,
                nextUp = if (sleep == SleepMode.EndOfItem) null else it.nextUp,
            )
        }
        val message = when (sleep) {
            SleepMode.Off -> "Minuteur de sommeil désactivé"
            SleepMode.EndOfItem -> if (currentItem()?.kind == ContentKind.MOVIE) "Arrêt à la fin du film" else "Arrêt à la fin de l'épisode"
            is SleepMode.Timer -> "Arrêt dans ${sleep.minutes} minutes"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** Stops playback, saves progress and lets the screen turn off. */
    private fun triggerSleep() {
        nextUpJob?.cancel()
        sleepJob?.cancel()
        exo?.pause()
        saveProgress()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui.update { it.copy(sleep = SleepMode.Off, sleepStopped = true, nextUp = null, endScreen = null, sleepPanelVisible = false) }
    }

    override fun resumeAfterSleep() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui.update { it.copy(sleepStopped = false) }
        val p = exo ?: return
        if (p.playbackState == Player.STATE_ENDED || endHandledFor == p.currentMediaItemIndex) replay() else p.play()
    }

    // endregion

    private fun isNetworkError(error: PlaybackException) = error.errorCode in 2000..2999

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion au flux impossible."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            "Le serveur a refusé le flux" + (code?.let { " (HTTP $it)" } ?: "") + "."
        }
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Flux introuvable."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "Format non pris en charge par le lecteur interne. Essayez un lecteur externe."
        else -> "Lecture impossible (${error.errorCodeName})."
    }

    private fun configureView(view: PlayerView) {
        playerView = view
        view.player = player
        view.setShowSubtitleButton(true)
        view.setShowNextButton(true)
        view.setShowPreviousButton(true)
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        view.controllerShowTimeoutMs = 3_500
        view.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                ui.update { it.copy(controlsVisible = visibility == View.VISIBLE) }
            }
        )
    }

    private fun currentItem(): PlayableItem? = exo?.let { session.items.getOrNull(it.currentMediaItemIndex) }

    private fun updateTitle() {
        val item = currentItem() ?: session.items[session.startIndex]
        ui.update {
            it.copy(title = item.title, subtitle = item.subtitle, kind = item.kind, canZap = session.items.size > 1)
        }
    }

    private fun showBanner() {
        bannerJob?.cancel()
        ui.update { it.copy(bannerVisible = true) }
        bannerJob = lifecycleScope.launch {
            delay(3_000)
            ui.update { it.copy(bannerVisible = false) }
        }
    }

    private fun zap(direction: Int) {
        val p = exo ?: return
        if (session.items.size < 2) return
        if (direction > 0) p.seekToNextMediaItem() else p.seekToPreviousMediaItem()
        p.prepare()
        p.playWhenReady = true
    }

    override fun zapNext() = zap(+1)

    override fun back() = finish()

    override fun restart() {
        exo?.seekTo(0)
        ui.update { it.copy(resumeFromMs = null) }
    }

    override fun retry() {
        ui.update { it.copy(error = null) }
        exo?.prepare()
        exo?.playWhenReady = true
    }

    override fun openExternal() {
        val index = exo?.currentMediaItemIndex ?: session.startIndex
        val url = urls.getOrNull(index) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(url), "video/*")
            .putExtra("title", session.items[index].title)
        exo?.pause()
        try {
            startActivity(Intent.createChooser(intent, "Ouvrir avec un lecteur externe"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Aucun lecteur externe installé (ex. VLC for Android).", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveProgress() {
        val p = exo ?: return
        val item = currentItem() ?: return
        val live = item.kind == ContentKind.LIVE
        val position = if (live) 0L else p.currentPosition
        val duration = if (live || p.duration == C.TIME_UNSET) 0L else p.duration
        if (!live && position < 1_000) return
        saveHistory(item, position, duration)
    }

    /** Values are read on the main thread; the write runs on the app scope so it survives the activity. */
    private fun saveHistory(item: PlayableItem, position: Long, duration: Long) {
        val entry = HistoryEntity(
            profileId = session.profileId,
            itemKey = item.ref,
            kind = item.kind,
            title = item.title,
            subtitle = item.subtitle,
            logo = item.logo,
            positionMs = position,
            durationMs = duration,
        )
        container.appScope.launch { container.db.libraryDao().saveHistory(entry) }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                val p = exo
                if (currentItem()?.kind != ContentKind.LIVE && p != null && p.isPlaying) saveProgress()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                showSleepPanel(true)
                return true
            }
            val state = ui.value
            val overlayOpen = state.error != null || state.nextUp != null || state.endScreen != null || state.sleepStopped
            if (isLiveQueue && !overlayOpen) {
                val controllerVisible = playerView?.isControllerFullyVisible == true
                when (event.keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); return true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
                    KeyEvent.KEYCODE_DPAD_UP -> if (!controllerVisible) { zap(+1); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (!controllerVisible) { zap(-1); return true }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        val state = ui.value
        if (exo != null && !state.sleepStopped && state.endScreen == null) {
            exo?.playWhenReady = true
            startProgressLoop()
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        progressJob?.cancel()
        exo?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        nextUpJob?.cancel()
        sleepJob?.cancel()
        playerView?.player = null
        exo?.removeListener(listener)
        exo?.release()
        exo = null
        player = null
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 30_000L
        const val MAX_LIVE_RETRIES = 3
    }
}
