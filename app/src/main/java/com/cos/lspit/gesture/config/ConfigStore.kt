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
    private const val KEY_MBACK = "mback"
    private const val KEY_BAR_WIDTH = "bar_width_dp"
    private const val KEY_BAR_ONLY = "bar_only"
    private const val KEY_HINT_TAP_SHIELD = "hint_tap_shield"
        private const val KEY_BAR_HIDDEN = "bar_hidden"
        private const val KEY_VERSION = "version"

        fun load(context: Context): GestureConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return GestureConfig.fromValues(
                if (prefs.contains(KEY_MASTER)) prefs.getInt(KEY_MASTER, 1) else null,
                if (prefs.contains(KEY_LEFT)) prefs.getInt(KEY_LEFT, 1) else null,
                if (prefs.contains(KEY_RIGHT)) prefs.getInt(KEY_RIGHT, 1) else null,
                if (prefs.contains(KEY_MBACK)) prefs.getInt(KEY_MBACK, 0) else null,
                if (prefs.contains(KEY_BAR_WIDTH)) prefs.getInt(KEY_BAR_WIDTH, 0) else null,
                if (prefs.contains(KEY_BAR_ONLY)) prefs.getInt(KEY_BAR_ONLY, 0) else null,
                if (prefs.contains(KEY_HINT_TAP_SHIELD)) prefs.getInt(KEY_HINT_TAP_SHIELD, 0) else null,
                                if (prefs.contains(KEY_BAR_HIDDEN)) prefs.getInt(KEY_BAR_HIDDEN, 0) else null,
                                if (prefs.contains(KEY_VERSION)) prefs.getInt(KEY_VERSION, 4) else null,
            )
        }

        fun save(context: Context, config: GestureConfig) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MASTER, if (config.masterEnabled) 1 else 0)
                .putInt(KEY_LEFT, if (config.leftEnabled) 1 else 0)
                .putInt(KEY_RIGHT, if (config.rightEnabled) 1 else 0)
                .putInt(KEY_MBACK, if (config.mbackEnabled) 1 else 0)
                .putInt(KEY_BAR_ONLY, if (config.barOnlyEnabled) 1 else 0)
                .putInt(KEY_HINT_TAP_SHIELD, if (config.hintTapShieldEnabled) 1 else 0)
                .putInt(KEY_BAR_HIDDEN, if (config.barHiddenEnabled) 1 else 0)
                .apply {
                    val w = config.barWidthDp
                    if (w == null) remove(KEY_BAR_WIDTH) else putInt(KEY_BAR_WIDTH, w)
                }
                .putInt(KEY_VERSION, config.version)
                .apply()
            context.contentResolver.notifyChange(ConfigProvider.CONFIG_URI, null)
        }
}
