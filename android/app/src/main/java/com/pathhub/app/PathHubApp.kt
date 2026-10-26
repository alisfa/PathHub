package com.pathhub.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.pathhub.app.data.AntiBlockEngine

class PathHubApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(AntiBlockEngine.client)
            .crossfade(true)
            .build()
    }
}
