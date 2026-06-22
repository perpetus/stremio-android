package com.stremio.mobile.server

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class JniStreamingServerController(
    private val context: Context,
    private val useForegroundService: () -> Boolean
) : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)
    private val startMutex = Mutex()
    override val state: StateFlow<StreamingServerState> = mutableState

    companion object {
        private const val TAG = "JniStreamingServer"

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
        startMutex.withLock {
            val current = mutableState.value
            if (current is StreamingServerState.Ready) {
                if (waitForServerReady(current.baseUrl, timeoutMs = 750)) {
                    return
                }
                Log.w(TAG, "State was Ready but ${current.baseUrl} is not reachable; restarting native server")
                dumpServerLogsToLogcat("ready-state unreachable")
                stopNativeServer()
            }

            mutableState.value = StreamingServerState.Starting
            val useForeground = useForegroundService()
            val serviceIntent = Intent(context, ServerService::class.java).apply {
                putExtra(ServerService.EXTRA_FOREGROUND, useForeground)
            }
            try {
                val url = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && useForeground) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    val configDir = File(context.filesDir, "stream-server").absolutePath
                    val cacheDir = File(context.cacheDir, "stream-server").absolutePath
                    startServerNative(configDir, cacheDir, 11470)
                }

                if (url != null && waitForServerReady(url)) {
                    Log.i(TAG, "Streaming server ready at $url")
                    mutableState.value = StreamingServerState.Ready(url)
                } else {
                    val message = if (url == null) {
                        "Native start returned null"
                    } else {
                        "Native server returned $url but did not become reachable"
                    }
                    Log.e(TAG, message)
                    dumpServerLogsToLogcat(message)
                    stopNativeServer()
                    mutableState.value = StreamingServerState.Failed(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting native server", e)
                dumpServerLogsToLogcat("start exception")
                mutableState.value = StreamingServerState.Failed(e.message ?: "Unknown native error")
                stopNativeServer()
            }
        }
    }

    override suspend fun stop() {
        stopNativeServer()
        mutableState.value = StreamingServerState.Stopped
    }

    private suspend fun stopNativeServer() {
        withContext(Dispatchers.IO) {
            runCatching { stopServerNative() }
            try {
                context.stopService(Intent(context, ServerService::class.java))
            } catch (ignored: Exception) {}
        }
    }

    private suspend fun waitForServerReady(baseUrl: String, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isServerReachable(baseUrl)) {
                return true
            }
            delay(150)
        }
        return false
    }

    private suspend fun isServerReachable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("$baseUrl/settings").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.setRequestProperty("Accept", "application/json")
            try {
                connection.responseCode in 200..499
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun dumpServerLogsToLogcat(reason: String) {
        val logDir = File(context.filesDir, "stream-server/logs")
        Log.e(TAG, "Checking server logs after $reason: ${logDir.absolutePath}")
        if (!logDir.exists()) {
            Log.e(TAG, "Server log directory does not exist")
            return
        }

        val candidates = logDir.listFiles()
            ?.filter { it.isFile && (it.name == "server_current.log" || it.extension == "log" || it.extension == "jsonl") }
            ?.sortedByDescending { it.lastModified() }
            ?.let { files ->
                val current = files.firstOrNull { it.name == "server_current.log" }
                listOfNotNull(current) + files.filter { it != current }.take(1)
            }
            .orEmpty()

        if (candidates.isEmpty()) {
            Log.e(TAG, "No server log files found in ${logDir.absolutePath}")
            return
        }

        candidates.forEach { file ->
            Log.e(TAG, "---- ${file.name} (${file.length()} bytes) ----")
            runCatching {
                file.readLines().takeLast(120).forEach { line ->
                    Log.e(TAG, line.take(3500))
                }
            }.onFailure {
                Log.e(TAG, "Failed to read ${file.absolutePath}", it)
            }
        }
    }
}
