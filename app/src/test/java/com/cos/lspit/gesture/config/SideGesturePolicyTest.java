package com.cos.lspit.gesture.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Full branch matrix for the side-back veto policy on the measured device
 * geometry (display 1080x2376, status bar 120, bottom strip 66, side 36).
 */
public class SideGesturePolicyTest {
    private static final int DISPLAY_W = 1080;
    private static final int DISPLAY_H = 2376;
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;
    private static final int ACTION_MOVE = 2;
    private static final int SOURCE_TOUCHSCREEN = 0x1002;
    private static final int SOURCE_STYLUS = 0x2002;
    private static final int TOOL_TYPE_FINGER = 1;

    @Test
    public void leftEdgeDownTouchFingerVetoes() {
        assertTrue(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void rightEdgeDownTouchFingerVetoes() {
        assertTrue(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 1078f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void bottomStripDownIsNotVetoed() {
        // y=2372 is inside the bottom Home/Recents strip (>= 2376-66) -> must pass through.
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 540f, 2372f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void statusBarDownIsNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 50f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void nonTouchscreenSourceIsNotVetoed() {
        // 0x2002 STYLUS source must never be vetoed.
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_STYLUS, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void nonFingerToolTypeIsNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, 2, 2f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void actionMoveWithSideCoordsIsNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_MOVE, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void actionUpWithSideCoordsIsNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_UP, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void centerDownIsNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 540f, 1300f, DISPLAY_W, DISPLAY_H, true, true));
    }

    @Test
    public void thresholdsMatchMeasuredInterceptionRegions() {
        assertEquals(120, SideGesturePolicy.STATUS_BAR_H);
        assertEquals(66, SideGesturePolicy.BOTTOM_STRIP_H);
        assertEquals(36, SideGesturePolicy.SIDE_W);
    }

    @Test
    public void leftOnlyVetoesLeftEdge() {
        // Right side disabled: a LEFT-edge DOWN must still be vetoed.
        assertTrue(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, true, false));
    }

    @Test
    public void rightOnlyVetoesRightEdge() {
        // Left side disabled: a RIGHT-edge DOWN must still be vetoed.
        assertTrue(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 1078f, 1300f, DISPLAY_W, DISPLAY_H, false, true));
    }

    @Test
    public void leftDisabledLeftEdgePasses() {
        // Mirror of leftOnlyVetoesLeftEdge: left disabled + LEFT-edge DOWN -> system gesture restored.
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, false, true));
    }

    @Test
    public void bothDisabledNeverVetoes() {
        // Both sides disabled: nothing is ever vetoed (system gestures fully restored).
        assertFalse(SideGesturePolicy.shouldVeto(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, DISPLAY_W, DISPLAY_H, false, false));
    }
}
