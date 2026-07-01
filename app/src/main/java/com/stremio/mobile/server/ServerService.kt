package com.stremio.mobile.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.stremio.mobile.R

class ServerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val useForeground = intent?.getBooleanExtra(EXTRA_FOREGROUND, true) ?: true
        if (useForeground) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.server_notification_title))
                .setContentText(getString(R.string.server_notification_text))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true)
                .build()

            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.server_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }

    companion object {
        const val EXTRA_FOREGROUND = "com.stremio.mobile.server.EXTRA_FOREGROUND"
        private const val CHANNEL_ID = "streaming_server"
        private const val NOTIFICATION_ID = 11470
    }
}

