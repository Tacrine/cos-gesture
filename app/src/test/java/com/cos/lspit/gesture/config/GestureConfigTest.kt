package com.cos.lspit.gesture.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing matrix for the provider cursor cells.
 *
 * The three veto switches (master/left/right) keep the proven fail-open
 * semantic: only an explicit 0 disables; null/any-other fails back to enabled.
 *
 * mback is fail-closed: only an explicit 1 arms it; null (column missing /
 * provider first launch) and any other value stay disabled, because mBack
 * actively takes over Back/Home injection and must never enable by accident.
 *
 * barWidthDp is nullable and only binds within the accepted width range
 * (40-160); null means "leave the system hint-bar width untouched".
 */
class GestureConfigTest {

    private fun v(
        master: Int? = 1,
        left: Int? = 1,
        right: Int? = 1,
        mback: Int? = 0,
        barWidth: Int? = null,
        barOnly: Int? = 0,
        version: Int? = 2,
        ) = GestureConfig.fromValues(master, left, right, mback, barWidth, barOnly, null, null, version)

    // --- veto switches (fail-open, unchanged) ---

    @Test
    fun allOnSnapshotParsesOn() {
        val config = v(1, 1, 1, 0, null, 2)
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertFalse(config.mbackEnabled)
        assertEquals(2, config.version)
    }

    @Test
    fun allOffSnapshotParsesOff() {
        val config = v(0, 0, 0, 0, null, 2)
        assertFalse(config.masterEnabled)
        assertFalse(config.leftEnabled)
        assertFalse(config.rightEnabled)
    }

    @Test
    fun nullVetoCellsFallBackToEnabled() {
        val config = v(null, null, null, 0, null, 2)
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
    }

    @Test
    fun invalidVetoCellsFallBackToEnabled() {
        val config = v(2, -1, 7, 0, null, 2)
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
    }

    // --- mback (fail-closed) ---

    @Test
    fun mbackOnParsesOn_whenExplicit1() {
        val config = v(1, 1, 1, 1, null, 2)
        assertTrue(config.mbackEnabled)
    }

    @Test
    fun mbackOff_whenExplicit0() {
        val config = v(1, 1, 1, 0, null, 2)
        assertFalse(config.mbackEnabled)
    }

    @Test
    fun mbackDisabled_whenColumnMissing() {
        val config = v(1, 1, 1, null, null, 2)
        assertFalse(config.mbackEnabled)
    }

    @Test
    fun mbackDisabled_whenUnparseable() {
        val config = v(1, 1, 1, 7, null, 2)
        assertFalse(config.mbackEnabled)
    }

    // --- barOnly (fail-closed) ---

    @Test
    fun barOnlyOnParsesOn_whenExplicit1() {
        val config = v(1, 1, 1, 0, null, 1)
        assertTrue(config.barOnlyEnabled)
    }

    @Test
    fun barOnlyOff_whenExplicit0() {
        val config = v(1, 1, 1, 0, null, 0)
        assertFalse(config.barOnlyEnabled)
    }

    @Test
    fun barOnlyDisabled_whenColumnMissing() {
        val config = v(1, 1, 1, 0, null, null)
        assertFalse(config.barOnlyEnabled)
    }

    // --- hintTapShield (fail-closed) ---

    @Test
    fun hintTapShieldOnParsesOn_whenExplicit1() {
            val config = GestureConfig.fromValues(1, 1, 1, 0, null, 0, 1, null, 4)
        assertTrue(config.hintTapShieldEnabled)
    }

    @Test
    fun hintTapShieldOff_whenColumnMissingOrZero() {
            assertFalse(GestureConfig.fromValues(1, 1, 1, 0, null, 0, null, null, 4).hintTapShieldEnabled)
            assertFalse(GestureConfig.fromValues(1, 1, 1, 0, null, 0, 0, null, 4).hintTapShieldEnabled)
    }

        // --- barHidden (fail-closed) ---

        @Test
        fun barHiddenOnParsesOn_whenExplicit1() {
            val config = GestureConfig.fromValues(1, 1, 1, 0, null, 0, 0, 1, 4)
            assertTrue(config.barHiddenEnabled)
        }

        @Test
        fun barHiddenOff_whenColumnMissingOrZero() {
            assertFalse(GestureConfig.fromValues(1, 1, 1, 0, null, 0, 0, null, 4).barHiddenEnabled)
            assertFalse(GestureConfig.fromValues(1, 1, 1, 0, null, 0, 0, 0, 4).barHiddenEnabled)
        }

    // --- barWidthDp (fail-closed to null / untouched) ---

    @Test
    fun barWidthParsesInRange() {
        val config = v(1, 1, 1, 0, 100, 2)
        assertEquals(100, config.barWidthDp)
    }

    @Test
    fun barWidthNull_whenColumnMissing() {
        val config = v(1, 1, 1, 0, null, 2)
        assertNull(config.barWidthDp)
    }

    @Test
    fun barWidthNull_whenBelowRange() {
        assertNull(v(1, 1, 1, 0, 39, 2).barWidthDp)
    }

    @Test
    fun barWidthNull_whenAboveRange() {
        assertNull(v(1, 1, 1, 0, 161, 2).barWidthDp)
    }

    @Test
    fun barWidthBoundary_inRange() {
        assertEquals(40, v(1, 1, 1, 0, 40, 2).barWidthDp)
        assertEquals(160, v(1, 1, 1, 0, 160, 2).barWidthDp)
    }

    // --- defaults ---

    @Test
    fun defaultsKeepInterceptionEnabled_mbackOff_widthNull() {
        val config = GestureConfig()
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertFalse(config.mbackEnabled)
        assertNull(config.barWidthDp)
        assertEquals(4, config.version)
    }

    // --- legacy compatibility ---
    // A pre-upgrade provider snapshot only has the 4 original columns. The
    // new columns arrive as null, which must keep all three veto switches on
    // and the new features safely off/untouched.

    @Test
    fun legacyFourColumnSnapshotStaysCompatible() {
            // A pre-upgrade cursor only carried master,left,right,version columns.
            // Simulate reading just those four (version cell = 1); the new mback
            // and barWidthDp cells decode as null via their readNullableColumn
            // paths, leaving the new features safely off/untouched.
            val config = GestureConfig.fromValues(1, 1, 1, null, null, null, null, null, 1)
            assertTrue(config.masterEnabled)
            assertTrue(config.leftEnabled)
            assertTrue(config.rightEnabled)
            assertFalse(config.mbackEnabled)
            assertNull(config.barWidthDp)
            assertEquals(1, config.version)
        }
}
