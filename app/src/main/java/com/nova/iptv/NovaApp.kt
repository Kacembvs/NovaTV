package com.nova.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nova.iptv.data.RefreshWorker
import com.nova.iptv.data.ThemeSettings
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaPalettes
import com.nova.iptv.ui.theme.NovaTvInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NovaApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The first value is read synchronously (small local file) so the first frame already has the right theme.
        applyTheme(runBlocking { container.settings.theme.first() })
        container.appScope.launch(Dispatchers.Main) {
            container.settings.theme.collect(::applyTheme)
        }
        container.appScope.launch {
            container.settings.autoRefresh.distinctUntilChanged().collect { RefreshWorker.schedule(this@NovaApp, it) }
        }
        container.downloads.start()
    }

    private fun applyTheme(settings: ThemeSettings) {
        NovaColors.palette = NovaPalettes.of(settings.theme, settings.customAccent)
        NovaTvInterface.current = settings.tvInterface
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.http)
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
