package com.uninstallprevention.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uninstallprevention.data.PreferenceManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart countdown service if there are active countdowns after reboot
        val activeCountdowns = PreferenceManager.getAppsWithActiveCountdown(context)
        if (activeCountdowns.isNotEmpty()) {
            val serviceIntent = Intent(context, CountdownService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
