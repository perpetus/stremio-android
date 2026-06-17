package com.stremio.mobile

import android.app.Application
import android.content.Context
import com.stremio.mobile.di.AppContainer
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import okio.Path.Companion.toPath

class MainApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            android.system.Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        } catch (e: Exception) {
            android.util.Log.e("MainApplication", "Failed to set TMPDIR environment variable", e)
        }
        container = AppContainer(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(1024L * 1024L * 250L) // 250 MB
                    .build()
            }
            .build()
    }
}
