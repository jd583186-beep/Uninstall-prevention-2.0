package com.uninstallprevention.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.uninstallprevention.data.PreferenceManager
import com.uninstallprevention.ui.MainActivity
import java.util.concurrent.TimeUnit

class CountdownService : Service() {

    companion object {
        const val CHANNEL_ID = "countdown_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_COUNTDOWN"
        const val ACTION_CANCEL = "ACTION_CANCEL_COUNTDOWN"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        // Broadcast sent to UI
        const val BROADCAST_TICK = "com.uninstallprevention.COUNTDOWN_TICK"
        const val BROADCAST_DONE = "com.uninstallprevention.COUNTDOWN_DONE"
        const val BROADCAST_CANCELLED = "com.uninstallprevention.COUNTDOWN_CANCELLED"
        const val EXTRA_REMAINING_MS = "extra_remaining_ms"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val activeCountdowns = mutableMapOf<String, Runnable>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
                startCountdown(packageName, appName)
            }
            ACTION_CANCEL -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
                cancelCountdown(packageName)
            }
        }
        return START_STICKY
    }

    private fun startCountdown(packageName: String, appName: String) {
        // Cancel any existing countdown for this package
        activeCountdowns[packageName]?.let { handler.removeCallbacks(it) }

        val endTime = PreferenceManager.getCountdownEndTime(this, packageName)
        if (endTime <= 0 || endTime <= System.currentTimeMillis()) return

        startForeground(NOTIFICATION_ID, buildNotification(appName, 24 * 60 * 60 * 1000L))

        val runnable = object : Runnable {
            override fun run() {
                val remaining = PreferenceManager.getRemainingMillis(this@CountdownService, packageName)
                if (remaining <= 0) {
                    // Timer done â€” remove protection
                    PreferenceManager.removeProtectedApp(this@CountdownService, packageName)
                    PreferenceManager.clearCountdown(this@CountdownService, packageName)
                    activeCountdowns.remove(packageName)

                    sendBroadcast(Intent(BROADCAST_DONE).apply {
                        putExtra(EXTRA_PACKAGE_NAME, packageName)
                    })

                    if (activeCountdowns.isEmpty()) stopSelf()
                } else {
                    // Update notification
                    val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notifManager.notify(NOTIFICATION_ID, buildNotification(appName, remaining))

                    sendBroadcast(Intent(BROADCAST_TICK).apply {
                        putExtra(EXTRA_PACKAGE_NAME, packageName)
                        putExtra(EXTRA_REMAINING_MS, remaining)
                    })

                    handler.postDelayed(this, 1000)
                }
            }
        }

        activeCountdowns[packageName] = runnable
        handler.post(runnable)
    }

    private fun cancelCountdown(packageName: String) {
        activeCountdowns[packageName]?.let {
            handler.removeCallbacks(it)
            activeCountdowns.remove(packageName)
        }

        PreferenceManager.clearCountdown(this, packageName)

        sendBroadcast(Intent(BROADCAST_CANCELLED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        })

        if (activeCountdowns.isEmpty()) stopSelf()
    }

    private fun buildNotification(appName: String, remainingMs: Long): Notification {
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
        val timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ðŸ”“ Removing protection from $appName")
            .setContentText("Time remaining: $timeStr")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Countdown Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active 24-hour unlock countdowns"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
