package com.cos.lspit.gesture.config

import android.content.Context

/**
 * App-side persistence for [GestureConfig]. Saving also notifies the
 * ConfigProvider URI so the SystemUI-side observer refreshes immediately.
 */
object ConfigStore {
    private const val PREFS_NAME = "gesture_config"
    private const val KEY_MASTER = "master"
    private const val KEY_LEFT = "left"
    private const val KEY_RIGHT = "right"
    private const val KEY_VERSION = "version"

    fun load(context: Context): GestureConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GestureConfig.fromValues(
            if (prefs.contains(KEY_MASTER)) prefs.getInt(KEY_MASTER, 1) else null,
            if (prefs.contains(KEY_LEFT)) prefs.getInt(KEY_LEFT, 1) else null,
            if (prefs.contains(KEY_RIGHT)) prefs.getInt(KEY_RIGHT, 1) else null,
            if (prefs.contains(KEY_VERSION)) prefs.getInt(KEY_VERSION, 1) else null,
        )
    }

    fun save(context: Context, config: GestureConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MASTER, if (config.masterEnabled) 1 else 0)
            .putInt(KEY_LEFT, if (config.leftEnabled) 1 else 0)
            .putInt(KEY_RIGHT, if (config.rightEnabled) 1 else 0)
            .putInt(KEY_VERSION, config.version)
            .apply()
        context.contentResolver.notifyChange(ConfigProvider.CONFIG_URI, null)
    }
}
