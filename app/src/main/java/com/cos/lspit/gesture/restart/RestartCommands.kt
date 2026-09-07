package com.cos.lspit.gesture.restart

/**
 * Verbatim command arrays for restarting the SystemUI scope. SystemUI is a
 * persistent process, so killing it makes the system respawn it — no explicit
 * start command is needed.
 */
object RestartCommands {
    const val TARGET_PACKAGE = "com.android.systemui"

    fun rootKill(): Array<String> = arrayOf("su", "-c", "kill \$(pidof $TARGET_PACKAGE)")

    fun shizukuKill(): Array<String> = arrayOf("sh", "-c", "kill \$(pidof $TARGET_PACKAGE)")

    fun rootProbe(): Array<String> = arrayOf("su", "-c", "id")
}
