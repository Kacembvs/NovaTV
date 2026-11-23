package com.nova.iptv.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nova.iptv.AppContainer
import com.nova.iptv.data.PlaybackQueue
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.player.PlayerActivity

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
inline fun <reified VM : ViewModel> novaViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

object Playback {
    fun start(
        context: Context,
        container: AppContainer,
        profileId: Long,
        items: List<PlayableItem>,
        startIndex: Int,
        fromStart: Boolean = false,
    ) {
        if (items.isEmpty()) return
        container.playbackQueue.current = PlaybackQueue.Session(profileId, items, startIndex.coerceIn(items.indices), fromStart)
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }
}
