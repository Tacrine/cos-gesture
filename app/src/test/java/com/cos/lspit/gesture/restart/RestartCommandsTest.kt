package com.cos.lspit.gesture.restart

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Command arrays are asserted verbatim: a typo or shell metacharacter here
 * would run with root/Shell privileges on the device, so no construction
 * logic is allowed to drift silently.
 */
class RestartCommandsTest {

    @Test
    fun rootKillRunsSuKillPidof() {
        assertArrayEquals(
            arrayOf("su", "-c", "kill \$(pidof com.android.systemui)"),
            RestartCommands.rootKill(),
        )
    }

    @Test
    fun shizukuKillRunsShellKillPidof() {
        assertArrayEquals(
            arrayOf("sh", "-c", "kill \$(pidof com.android.systemui)"),
            RestartCommands.shizukuKill(),
        )
    }

    @Test
    fun rootProbeRunsSuId() {
        assertArrayEquals(arrayOf("su", "-c", "id"), RestartCommands.rootProbe())
    }

    @Test
    fun targetPackageIsSystemUi() {
        assertEquals("com.android.systemui", RestartCommands.TARGET_PACKAGE)
    }
}
