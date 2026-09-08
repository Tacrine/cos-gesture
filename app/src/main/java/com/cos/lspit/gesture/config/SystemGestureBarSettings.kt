package com.cos.lspit.gesture.config

import android.content.Context
import android.provider.Settings

object SystemGestureBarSettings {
    // ColorOS user toggle: writing 0 = show, 1 = hide.
    // Previously the module wrote hide_gesture_bar_enable which is the unrelated
    // "swipe-up gesture bar type" key — SystemUI kept the bar truly hidden
    // (window alpha 0 + guide bar GONE) regardless of that key, so mBack died.
    // gesture_side_hide_bar_prevention_enable is the one SystemUI actually reads
    // when routing touch and rendering the bar.
    private const val KEY_HIDE_GESTURE_BAR = "gesture_side_hide_bar_prevention_enable"

    fun isHintBarVisible(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, KEY_HIDE_GESTURE_BAR, 0) == 0
        } catch (_: Throwable) {
            true
        }
    }

    fun setHintBarVisible(context: Context, visible: Boolean) {
        val value = if (visible) 0 else 1
        try {
            Settings.Secure.putInt(context.contentResolver, KEY_HIDE_GESTURE_BAR, value)
        } catch (_: Throwable) {
            // The app may not have the secure-settings permission on older devices.
            // The caller can still surface a UI warning instead of crashing.
        }
    }
}
