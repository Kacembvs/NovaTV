package com.nova.iptv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.theme.NovaColors

/**
 * Small embedded live preview (Live TV screen on TV / large screens). The player is stopped when
 * the screen goes to the background (e.g. full-screen playback) and released when it leaves composition.
 */
@OptIn(UnstableApi::class)
@Composable
fun PreviewPlayer(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    var failed by remember { mutableStateOf(false) }
    var triedHls by remember(url) { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(container.http))))
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !triedHls && current != null) {
                    triedHls = true
                    player.setMediaItem(MediaItem.Builder().setUri(current).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                    player.prepare()
                } else {
                    failed = true
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) failed = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        failed = false
        if (url == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, url) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> if (url != null && player.playbackState == Player.STATE_IDLE) player.prepare()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                    isFocusable = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            url == null -> Text("Sélectionnez une chaîne", color = NovaColors.TextSecondary, fontSize = 13.sp)
            failed -> Text("Aperçu indisponible", color = NovaColors.TextSecondary, fontSize = 13.sp)
        }
    }
}
