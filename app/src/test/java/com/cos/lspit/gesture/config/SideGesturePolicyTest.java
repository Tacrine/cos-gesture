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

    // --- Bar-only gate: bottom-strip gestures only fire when the DOWN lands on the hint bar ---

    private static final float DENSITY = 2.75f; // measured device density

    @Test
    public void barOnlyOffNeverVetoesBottomDown() {
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 200f, 2372f,
                DISPLAY_W, DISPLAY_H, false, 78, DENSITY));
    }

    @Test
    public void bottomOffBarDownVetoes() {
        // x=200 (outside the 78dp bar band 422..658) in the bottom strip -> gesture dropped.
        assertTrue(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 200f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void bottomCenterDownNotVetoed() {
        // x=540 is on the hint bar -> bottom gestures still fire.
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 540f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void bottomBarBandEdgeIncludedNotVetoed() {
        // x=658 is exactly the 78dp bar band edge (incl. 4dp padding) -> still on the bar.
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 658f, 2370f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void bottomJustOutsideBarBandVetoes() {
        // x=660 is 2px past the 78dp band edge -> vetoed.
        assertTrue(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 660f, 2370f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void defaultBarWidthGateUses40Dp() {
        // 40dp bar band is 540+-66 (474..606): x=440 off-band vetoes, x=500 in-band does not.
        assertTrue(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 440f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 40, DENSITY));
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 500f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 40, DENSITY));
    }

    @Test
    public void aboveBottomStripDownNotVetoed() {
        // y=2000 is above the 66px bottom strip -> normal app touch, never vetoed.
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 200f, 2000f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void barOnlyNonFingerNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_TOUCHSCREEN, 2, 200f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void barOnlyStylusNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_DOWN, SOURCE_STYLUS, TOOL_TYPE_FINGER, 200f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    @Test
    public void barOnlyNonDownActionNotVetoed() {
        assertFalse(SideGesturePolicy.shouldVetoBarOnly(
                ACTION_MOVE, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 200f, 2372f,
                DISPLAY_W, DISPLAY_H, true, 78, DENSITY));
    }

    // --- Launcher home-gesture region narrow (barOnly) ---

    @Test
    public void narrowFullWidthBandToDefault40Dp() {
        // Full-width bottom strip [0..1080] -> 40dp band is 540 +-66 = 474..606.
        float[] rect = {0f, 2310f, 1080f, 2376f};
        assertTrue(SideGesturePolicy.narrowBandToBar(rect, 40, DENSITY));
        assertEquals(474f, rect[0], 0.5f);
        assertEquals(606f, rect[2], 0.5f);
        // Vertical extent is untouched by the horizontal narrow.
        assertEquals(2310f, rect[1], 0.5f);
        assertEquals(2376f, rect[3], 0.5f);
    }

    @Test
    public void narrowFullWidthBandToWidest160Dp() {
        // 160dp band is 540 +-231 = 309..771.
        float[] rect = {0f, 2310f, 1080f, 2376f};
        assertTrue(SideGesturePolicy.narrowBandToBar(rect, 160, DENSITY));
        assertEquals(309f, rect[0], 0.5f);
        assertEquals(771f, rect[2], 0.5f);
    }

    @Test
    public void narrowOffCenterBandStaysCenteredOnItsOwnWidth() {
        // A region already offset (e.g. another orientation) is centered on itself,
        // not on the display center: 500..900 narrows symmetrically around 700.
        float[] rect = {500f, 0f, 900f, 100f};
        assertTrue(SideGesturePolicy.narrowBandToBar(rect, 40, DENSITY));
        assertEquals(634f, rect[0], 0.5f);
        assertEquals(766f, rect[2], 0.5f);
    }

    @Test
    public void narrowClampsToBandSmallerThanHintBand() {
        // A 100px-wide band can never grow past its own edges.
        float[] rect = {600f, 0f, 700f, 100f};
        assertTrue(SideGesturePolicy.narrowBandToBar(rect, 160, DENSITY));
        assertEquals(600f, rect[0], 0.5f);
        assertEquals(700f, rect[2], 0.5f);
    }

    @Test
    public void narrowDegenerateBandReturnsFalse() {
        // A zero-width region must report "cannot narrow" so the caller leaves it alone.
        float[] rect = {540f, 0f, 540f, 100f};
        assertFalse(SideGesturePolicy.narrowBandToBar(rect, 40, DENSITY));
        assertEquals(540f, rect[0], 0.5f);
        assertEquals(540f, rect[2], 0.5f);
    }

    @Test
    public void barHalfWidthMatchesMeasuredPadding() {
        // 40dp -> (40*2.75)/2 + 4*2.75 = 55 + 11 = 66px.
        assertEquals(66f, SideGesturePolicy.barHalfWidthPx(40, DENSITY), 0.01f);
        // 160dp -> (160*2.75)/2 + 11 = 231px.
        assertEquals(231f, SideGesturePolicy.barHalfWidthPx(160, DENSITY), 0.01f);
    }
}
