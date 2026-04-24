package com.solovision.openclawagents.data

import android.content.Context
import android.content.SharedPreferences
import com.solovision.openclawagents.model.AppThemeMode

private const val APP_THEME_PREFS = "app_theme_prefs"
private const val APP_THEME_MODE_KEY = "app_theme_mode"

class AppThemeStore(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.applicationContext
        ?.getSharedPreferences(APP_THEME_PREFS, Context.MODE_PRIVATE)

    fun read(): AppThemeMode {
        val stored = prefs?.getString(APP_THEME_MODE_KEY, null)
        return when (stored) {
            null -> AppThemeMode.Midnight
            "System", "Dark" -> AppThemeMode.Midnight
            "Light" -> AppThemeMode.Snow
            else -> AppThemeMode.entries.firstOrNull { it.name == stored } ?: AppThemeMode.Midnight
        }
    }

    fun write(mode: AppThemeMode) {
        prefs?.edit()?.putString(APP_THEME_MODE_KEY, mode.name)?.apply()
    }
}
