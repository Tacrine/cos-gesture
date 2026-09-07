package com.cos.lspit.gesture.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing matrix for the provider cursor cells: only an explicit 0 disables a
 * switch; null (column missing / provider first launch) and any other value
 * fail back to enabled so the hook keeps its proven default behavior.
 */
class GestureConfigTest {

    @Test
    fun allOnSnapshotParsesOn() {
        val config = GestureConfig.fromValues(1, 1, 1, 1)
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertEquals(1, config.version)
    }

    @Test
    fun allOffSnapshotParsesOff() {
        val config = GestureConfig.fromValues(0, 0, 0, 1)
        assertFalse(config.masterEnabled)
        assertFalse(config.leftEnabled)
        assertFalse(config.rightEnabled)
    }

    @Test
    fun nullCellsFallBackToEnabled() {
        val config = GestureConfig.fromValues(null, null, null, null)
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertEquals(1, config.version)
    }

    @Test
    fun invalidCellsFallBackToEnabled() {
        val config = GestureConfig.fromValues(2, -1, 7, 3)
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertEquals(3, config.version)
    }

    @Test
    fun mixedSnapshotParsesPerSide() {
        val config = GestureConfig.fromValues(1, 0, 1, 2)
        assertTrue(config.masterEnabled)
        assertFalse(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertEquals(2, config.version)
    }

    @Test
    fun defaultsKeepInterceptionEnabled() {
        val config = GestureConfig()
        assertTrue(config.masterEnabled)
        assertTrue(config.leftEnabled)
        assertTrue(config.rightEnabled)
        assertEquals(1, config.version)
    }
}
