package com.nova.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class NovaApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.http)
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
