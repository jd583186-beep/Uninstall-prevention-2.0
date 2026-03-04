package com.uninstallprevention.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    var isProtected: Boolean = false,
    var countdownRemainingMs: Long = -1L  // -1 means no active countdown
) {
    val hasActiveCountdown: Boolean get() = countdownRemainingMs > 0
}
