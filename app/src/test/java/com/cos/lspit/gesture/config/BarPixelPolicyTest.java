package com.cos.lspit.gesture.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Branch matrix for the hint-bar pixel policy (C3). Every assertion is a real
 * branch check: no test asserts a constant against itself.
 */
public class BarPixelPolicyTest {
    @Test
    public void suppressTruthTable() {
        // 2 x 2 x 2 table; only "a hide source is active AND the shade is closed"
        // may suppress.
        assertFalse(BarPixelPolicy.shouldSuppress(false, false, false));
        assertFalse(BarPixelPolicy.shouldSuppress(false, false, true));
        assertTrue(BarPixelPolicy.shouldSuppress(true, false, false));
        assertFalse(BarPixelPolicy.shouldSuppress(true, false, true));
        assertTrue(BarPixelPolicy.shouldSuppress(false, true, false));
        assertFalse(BarPixelPolicy.shouldSuppress(false, true, true));
        assertTrue(BarPixelPolicy.shouldSuppress(true, true, false));
        assertFalse(BarPixelPolicy.shouldSuppress(true, true, true));
    }

    @Test
    public void admitsSiblingOnlyWhenVisibleAndOpaque() {
        assertTrue(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 320, 24, false));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.GestureBarView", false, 1f, 320, 24, false));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 0f, 320, 24, false));
    }

    @Test
    public void admitsSiblingRejectsZeroArea() {
        assertTrue(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 1, 1, false));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 0, 24, false));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 320, 0, false));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, -3, 24, false));
    }

    @Test
    public void admitsSiblingMatchesKnownNames() {
        // V2: the four known fragments, case-insensitively.
        assertTrue(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 320, 24, false));
        assertTrue(BarPixelPolicy.admitsSibling("com.x.GuideBarView", true, 1f, 320, 24, false));
        assertTrue(BarPixelPolicy.admitsSibling("com.x.HintBarView", true, 1f, 320, 24, false));
        assertTrue(BarPixelPolicy.admitsSibling("com.x.SideGestureBar", true, 1f, 320, 24, false));
        assertTrue(BarPixelPolicy.admitsSibling("com.x.GESTUREBARVIEW", true, 1f, 320, 24, false));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.SomeOtherView", true, 1f, 320, 24, false));
        assertFalse(BarPixelPolicy.admitsSibling(null, true, 1f, 320, 24, false));
    }

    @Test
    public void admitsSiblingRejectsContainerOrViewGroup() {
        // W1: the container flag alone must veto, even for a hint-bar-looking name.
        assertFalse(BarPixelPolicy.admitsSibling(
                "com.x.GestureBarContainer", true, 1f, 320, 24, true));
        assertFalse(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 320, 24, true));
        assertTrue(BarPixelPolicy.admitsSibling("com.x.GestureBarView", true, 1f, 320, 24, false));
    }

    @Test
    public void alphaTargetZeroWhenSuppressed() {
        // API contract case: the production restore path never passes suppress=true
        // (the gate continues before the write-back), see evidence Z9.
        assertEquals(0f, BarPixelPolicy.alphaTarget(true, true, 0.6f), 0f);
        assertEquals(0f, BarPixelPolicy.alphaTarget(true, true, 0f), 0f);
    }

    @Test
    public void alphaTargetRestoresZeroRecorded() {
        assertEquals(0f, BarPixelPolicy.alphaTarget(false, true, 0f), 0f);
    }

    @Test
    public void alphaTargetRestoresRecordedValue() {
        assertEquals(0.6f, BarPixelPolicy.alphaTarget(false, true, 0.6f), 0f);
        assertEquals(1f, BarPixelPolicy.alphaTarget(false, true, 1f), 0f);
    }

    @Test
    public void alphaTargetLeavesUnrecordedViewAlone() {
        // V1/Z6: sentinel -1f means "do not touch"; unreachable from restoreTargets
        // (it only walks the record table), so this is an API contract case.
        assertEquals(-1f, BarPixelPolicy.alphaTarget(true, false, 0.6f), 0f);
        assertEquals(-1f, BarPixelPolicy.alphaTarget(false, false, 0.6f), 0f);
    }

    @Test
    public void restoreKeepsRecordWhileStillSuppressed() {
        assertFalse(BarPixelPolicy.restoreShouldDropRecord(true, true));
        assertTrue(BarPixelPolicy.restoreShouldDropRecord(false, true));
        assertFalse(BarPixelPolicy.restoreShouldDropRecord(false, false));
    }

    @Test
    public void mergeIntoKeepsExistingEntries() {
        Set<String> existing = new HashSet<>();
        existing.add("a");
        existing.add("b");
        Set<String> candidates = new HashSet<>();
        candidates.add("b");
        candidates.add("c");
        Set<String> sameInstance = existing;
        BarPixelPolicy.mergeInto(existing, candidates);
        assertSame(sameInstance, existing);
        assertTrue(existing.contains("a"));
        assertTrue(existing.contains("b"));
        assertTrue(existing.contains("c"));
        assertEquals(3, existing.size());

        // Same call, generic with a different element type.
        Set<Integer> existingNumbers = new HashSet<>();
        existingNumbers.add(1);
        existingNumbers.add(2);
        BarPixelPolicy.mergeInto(existingNumbers, new HashSet<Integer>(java.util.Arrays.asList(2, 3)));
        assertTrue(existingNumbers.contains(1));
        assertTrue(existingNumbers.contains(2));
        assertTrue(existingNumbers.contains(3));
    }
}
