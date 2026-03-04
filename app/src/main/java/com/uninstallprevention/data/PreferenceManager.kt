package com.uninstallprevention.data

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {

    private const val PREFS_NAME = "uninstall_prevention_prefs"
    private const val KEY_PROTECTED_APPS = "protected_apps"
    private const val KEY_COUNTDOWN_END_TIME = "countdown_end_time"
    private const val KEY_COUNTDOWN_PACKAGE = "countdown_package"
    private const val KEY_PROTECTION_ACTIVE = "protection_active"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Protected Apps ---
    fun getProtectedApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_PROTECTED_APPS, emptySet()) ?: emptySet()
    }

    fun addProtectedApp(context: Context, packageName: String) {
        val current = getProtectedApps(context).toMutableSet()
        current.add(packageName)
        getPrefs(context).edit().putStringSet(KEY_PROTECTED_APPS, current).apply()
    }

    fun removeProtectedApp(context: Context, packageName: String) {
        val current = getProtectedApps(context).toMutableSet()
        current.remove(packageName)
        getPrefs(context).edit().putStringSet(KEY_PROTECTED_APPS, current).apply()
    }

    fun isAppProtected(context: Context, packageName: String): Boolean {
        return getProtectedApps(context).contains(packageName)
    }

    // --- Countdown Timer ---
    fun setCountdownEndTime(context: Context, packageName: String, endTimeMillis: Long) {
        getPrefs(context).edit()
            .putLong(KEY_COUNTDOWN_END_TIME + "_$packageName", endTimeMillis)
            .apply()
    }

    fun getCountdownEndTime(context: Context, packageName: String): Long {
        return getPrefs(context).getLong(KEY_COUNTDOWN_END_TIME + "_$packageName", -1L)
    }

    fun clearCountdown(context: Context, packageName: String) {
        getPrefs(context).edit()
            .remove(KEY_COUNTDOWN_END_TIME + "_$packageName")
            .apply()
    }

    fun hasActiveCountdown(context: Context, packageName: String): Boolean {
        val endTime = getCountdownEndTime(context, packageName)
        return endTime > System.currentTimeMillis()
    }

    fun getRemainingMillis(context: Context, packageName: String): Long {
        val endTime = getCountdownEndTime(context, packageName)
        return if (endTime <= 0) -1L else maxOf(0L, endTime - System.currentTimeMillis())
    }

    // --- All active countdowns ---
    fun getAppsWithActiveCountdown(context: Context): List<String> {
        val prefs = getPrefs(context)
        val all = prefs.all
        val now = System.currentTimeMillis()
        return all.keys
            .filter { it.startsWith(KEY_COUNTDOWN_END_TIME + "_") }
            .mapNotNull { key ->
                val endTime = prefs.getLong(key, -1L)
                if (endTime > now) key.removePrefix(KEY_COUNTDOWN_END_TIME + "_") else null
            }
    }
}
