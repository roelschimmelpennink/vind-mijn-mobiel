package com.vindmijnmobiel

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RingService : Service() {

    private var server: PhoneRingServer? = null
    private var ntfyListener: NtfyListener? = null
    private lateinit var ringController: RingController

    override fun onCreate() {
        super.onCreate()
        ringController = RingController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server == null) {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            server = PhoneRingServer(PORT, ringController).also { it.start() }
        }

        if (ntfyListener == null) {
            val topic = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getString("ntfy_topic", null)
            if (topic != null) {
                ntfyListener = NtfyListener(topic, ringController).also { it.start() }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        ntfyListener?.stop()
        ringController.stopRinging()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        const val PORT = 5000
        const val NOTIFICATION_ID = 1
    }
}
