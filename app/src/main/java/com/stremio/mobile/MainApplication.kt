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

import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class CrashlyticsTree : Timber.Tree() {
    private val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val passwordRegex = Regex("(?i)(password|token|apiKey|api_key|secret|auth|bearer|pass)\\s*[:=]\\s*[^&\\s]+")
    private val bearerRegex = Regex("(?i)Bearer\\s+[^&\\s]+")

    private fun sanitize(message: String): String {
        var sanitized = message
        sanitized = emailRegex.replace(sanitized, "[EMAIL_REDACTED]")
        sanitized = passwordRegex.replace(sanitized, "$1=[REDACTED]")
        sanitized = bearerRegex.replace(sanitized, "Bearer [REDACTED]")
        return sanitized
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == android.util.Log.VERBOSE || priority == android.util.Log.DEBUG) {
            return
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        val sanitizedMessage = sanitize((tag?.let { "[$it] " } ?: "") + message)
        crashlytics.log(sanitizedMessage)
        if (t != null) {
            val sanitizedThrowable = if (t.message != null) {
                val sanitizedMsg = sanitize(t.message!!)
                if (sanitizedMsg != t.message) {
                    val wrapped = Throwable(sanitizedMsg, t.cause)
                    wrapped.stackTrace = t.stackTrace
                    wrapped
                } else t
            } else t
            crashlytics.recordException(sanitizedThrowable)
        }
    }
}

class MainApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.plant(CrashlyticsTree())
        try {
            android.system.Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set TMPDIR environment variable")
        }
        container = AppContainer(this)
        val analyticsEnabled = container.authRepository.isAnalyticsEnabled()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(analyticsEnabled)

        if (BuildConfig.POSTHOG_API_KEY.isNotEmpty()) {
            val posthogConfig = PostHogAndroidConfig(
                apiKey = BuildConfig.POSTHOG_API_KEY,
                host = BuildConfig.POSTHOG_HOST
            ).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = true
                sessionReplay = true
                sessionReplayConfig.maskAllTextInputs = true
                sessionReplayConfig.maskAllImages = true
                optOut = !analyticsEnabled
            }
            PostHogAndroid.setup(this, posthogConfig)
        }
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
