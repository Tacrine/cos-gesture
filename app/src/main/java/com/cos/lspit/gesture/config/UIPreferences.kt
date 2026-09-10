package com.cos.lspit.gesture.config

import android.content.Context

/**
 * UI-only preferences (theme mode). Kept separate from [ConfigStore] so
 * changing dark/light mode does not wake the SystemUI-side config observer.
 */
object UIPreferences {
    private const val PREFS_NAME = "ui_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    fun loadThemeMode(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun saveThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }
}