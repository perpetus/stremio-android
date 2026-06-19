package com.stremio.mobile.server

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class JniStreamingServerController(
    private val context: Context,
    private val useForegroundService: () -> Boolean
) : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)
    override val state: StateFlow<StreamingServerState> = mutableState

    companion object {
        init {
            System.loadLibrary("stream_server")
        }

        @JvmStatic
        private external fun startServerNative(configDir: String, cacheDir: String, port: Int): String?

        @JvmStatic
        private external fun stopServerNative()

        @JvmStatic
        private external fun getServerUrlNative(): String?
    }

    override suspend fun start() {
        if (mutableState.value is StreamingServerState.Ready) {
            return
        }
        mutableState.value = StreamingServerState.Starting
        withContext(Dispatchers.IO) {
            val useForeground = useForegroundService()
            val serviceIntent = Intent(context, ServerService::class.java).apply {
                putExtra(ServerService.EXTRA_FOREGROUND, useForeground)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && useForeground) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }


                val configDir = File(context.filesDir, "stream-server").absolutePath
                val cacheDir = File(context.cacheDir, "stream-server").absolutePath
                val url = startServerNative(configDir, cacheDir, 11470)
                if (url != null) {
                    mutableState.value = StreamingServerState.Ready(url)
                } else {
                    android.util.Log.e("JniStreamingServerController", "Native start returned null")
                    mutableState.value = StreamingServerState.Failed("Native start returned null")
                    try {
                        context.stopService(serviceIntent)
                    } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.e("JniStreamingServerController", "Error starting native server", e)
                mutableState.value = StreamingServerState.Failed(e.message ?: "Unknown native error")
                try {
                    context.stopService(serviceIntent)
                } catch (ignored: Exception) {}
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            stopServerNative()
            try {
                context.stopService(Intent(context, ServerService::class.java))
            } catch (ignored: Exception) {}
        }
        mutableState.value = StreamingServerState.Stopped
    }
}
