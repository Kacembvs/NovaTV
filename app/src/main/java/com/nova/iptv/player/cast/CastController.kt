package com.nova.iptv.player.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import java.util.concurrent.Executor
import com.google.android.gms.cast.MediaMetadata as CastMetadata

/**
 * Google Cast session handling for the player. Everything is optional: without Google Play
 * services (some TVs / devices) the listener is told Cast is unavailable and the button stays hidden.
 */
class CastController(
    context: Context,
    mainExecutor: Executor,
    private val listener: Listener,
) {
    interface Listener {
        fun onCastAvailable(available: Boolean)
        /** A session started (or was already running): the local player should hand over. */
        fun onCastStarted(deviceName: String)
        /** Session ended: resume locally at [positionMs] (0 when unknown). */
        fun onCastEnded(positionMs: Long)
        fun onRemoteStatus(playing: Boolean)
    }

    private var castContext: CastContext? = null
    private var session: CastSession? = null
    val isCasting get() = session?.isConnected == true
    private var lastRemotePosition = 0L

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = session?.remoteMediaClient ?: return
            lastRemotePosition = client.approximateStreamPosition
            listener.onRemoteStatus(client.isPlaying)
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = attach(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attach(s)
        override fun onSessionEnding(s: CastSession) {
            s.remoteMediaClient?.let { lastRemotePosition = it.approximateStreamPosition }
        }
        override fun onSessionEnded(s: CastSession, error: Int) = detach()
        override fun onSessionStarting(s: CastSession) = Unit
        override fun onSessionStartFailed(s: CastSession, error: Int) = Unit
        override fun onSessionResuming(s: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(s: CastSession, error: Int) = Unit
        override fun onSessionSuspended(s: CastSession, reason: Int) = Unit
    }

    init {
        runCatching {
            CastContext.getSharedInstance(context.applicationContext, mainExecutor)
                .addOnSuccessListener(mainExecutor) { ctx ->
                    castContext = ctx
                    ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                    listener.onCastAvailable(true)
                    ctx.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
                }
                .addOnFailureListener(mainExecutor) { listener.onCastAvailable(false) }
        }.onFailure { listener.onCastAvailable(false) }
    }

    private fun attach(s: CastSession) {
        session = s
        s.remoteMediaClient?.registerCallback(remoteCallback)
        listener.onCastStarted(s.castDevice?.friendlyName ?: "Chromecast")
    }

    private fun detach() {
        session?.remoteMediaClient?.unregisterCallback(remoteCallback)
        session = null
        listener.onCastEnded(lastRemotePosition)
        lastRemotePosition = 0
    }

    fun load(item: PlayableItem, url: String, positionMs: Long) {
        val client = session?.remoteMediaClient ?: return
        val live = item.kind == ContentKind.LIVE
        val castUrl = CastUrls.castUrl(url, live)
        val metadata = CastMetadata(CastMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(CastMetadata.KEY_TITLE, item.title)
            item.subtitle?.let { putString(CastMetadata.KEY_SUBTITLE, it) }
            item.logo?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(castUrl)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(CastUrls.mimeType(castUrl, live))
            .setMetadata(metadata)
            .build()
        client.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(if (live) 0 else positionMs)
                .build()
        )
    }

    fun togglePlayback() {
        session?.remoteMediaClient?.togglePlayback()
    }

    fun stop() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    fun release() {
        session?.remoteMediaClient?.unregisterCallback(remoteCallback)
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }
}
