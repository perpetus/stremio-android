package com.stremio.mobile.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import com.stremio.mobile.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? MainApplication ?: return
            val prefs = app.getSharedPreferences("stremio_account", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_on_boot", false)
            if (autoStart) {
                Timber.d("Auto-start on boot is enabled, starting server...")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.container.serverController.start()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to auto-start server on boot")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
