package com.cos.lspit.gesture.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    // --- Launcher home-gesture region narrow (barOnly, C4 list-level) ---
    //
    // Geometry used below (measured device): display 1080x2376, density 2.75,
    // BOTTOM_STRIP_H 66.  A sanity main band must contain cx=540 and satisfy
    // bottom >= 2376-198 = 2178 with height <= 198.
    // 40dp -> half = (40*2.75)/2 + 4*2.75 = 66 -> band [474, 606].
    // 160dp -> half = 231 -> band [309, 771].

    @Test
    public void narrowFullWidthBandToDefault40Dp() {
        // Main band [0,2310,1080,2376] contains the centre and sits on the bottom
        // strip -> rule 1 narrows it to cx +-66. Vertical extent is untouched.
        float[][] input = {{0f, 2310f, 1080f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(1, result.rects.length);
        assertEquals(474f, result.rects[0][0], 0.5f);
        assertEquals(606f, result.rects[0][2], 0.5f);
        assertEquals(2310f, result.rects[0][1], 0.5f);
        assertEquals(2376f, result.rects[0][3], 0.5f);
        // The input array is never mutated (the result carries a fresh copy).
        assertEquals(0f, input[0][0], 0.5f);
        assertEquals(1080f, input[0][2], 0.5f);
    }

    @Test
    public void narrowFullWidthBandToWidest160Dp() {
        float[][] input = {{0f, 2310f, 1080f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 160, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(309f, result.rects[0][0], 0.5f);
        assertEquals(771f, result.rects[0][2], 0.5f);
    }

    @Test
    public void narrowOffCenterRectWithoutMainBandIsUntouched() {
        // {500,0,900,100}: bottom 100 < 2178 -> NOT a sanity main band, so the whole
        // region is skipped and every rect keeps its original coordinates.
        float[][] input = {{500f, 0f, 900f, 100f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertFalse(result.applied);
        assertEquals(0, result.narrowed);
        assertEquals(500f, result.rects[0][0], 0.5f);
        assertEquals(900f, result.rects[0][2], 0.5f);
    }

    @Test
    public void narrowSmallRectWithoutMainBandIsUntouched() {
        float[][] input = {{600f, 0f, 700f, 100f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 160, DENSITY, DISPLAY_W, DISPLAY_H);
        assertFalse(result.applied);
        assertEquals(600f, result.rects[0][0], 0.5f);
        assertEquals(700f, result.rects[0][2], 0.5f);
    }

    @Test
    public void narrowZeroWidthRectWithoutMainBandIsUntouched() {
        float[][] input = {{540f, 0f, 540f, 100f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertFalse(result.applied);
        assertEquals(540f, result.rects[0][0], 0.5f);
        assertEquals(540f, result.rects[0][2], 0.5f);
    }

    @Test
    public void narrowListNeverWidensCenteredRect() {
        // A main band already narrower than the hint band (500..580) must NOT be
        // widened to 474..606: the narrow only ever shrinks.
        float[][] input = {{500f, 2310f, 580f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(500f, result.rects[0][0], 0.5f);
        assertEquals(580f, result.rects[0][2], 0.5f);
    }

    @Test
    public void narrowListAppliesToMainBandOnly() {
        // The main band is narrowed; the unrelated top rect (no vertical overlap) is
        // kept byte for byte, so exactly one rect changed.
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {600f, 0f, 700f, 100f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(474f, result.rects[0][0], 0.5f);
        assertEquals(606f, result.rects[0][2], 0.5f);
        assertEquals(600f, result.rects[1][0], 0.5f);
        assertEquals(700f, result.rects[1][2], 0.5f);
        assertEquals(0, result.folded);
    }

    @Test
    public void narrowListKeepsNonOverlappingVerticalRect() {
        // A wide rect that crosses the band horizontally but sits far above the main
        // band (rule 3) is preserved untouched.
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {0f, 300f, 1080f, 336f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(0f, result.rects[1][0], 0.5f);
        assertEquals(1080f, result.rects[1][2], 0.5f);
        assertEquals(300f, result.rects[1][1], 0.5f);
        assertEquals(336f, result.rects[1][3], 0.5f);
    }

    @Test
    public void narrowListFoldsCornerRectIntoMainBandExtent() {
        // Right-corner rect (no horizontal intersection with the band) is folded into
        // the main band extent: [max(main.left,cx-half), main.top, min(main.right,cx+half), main.bottom].
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {700f, 2310f, 1080f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(1, result.folded);
        assertEquals(474f, result.rects[1][0], 0.5f);
        assertEquals(2310f, result.rects[1][1], 0.5f);
        assertEquals(606f, result.rects[1][2], 0.5f);
        assertEquals(2376f, result.rects[1][3], 0.5f);
        // The fold is unconditional: with a main band already clamped to the band, the
        // fold target is byte-identical to that main band and is STILL written back
        // (N21), not "de-duplicated away" and not left in the corner.
        float[][] identical = {
                {474f, 2310f, 606f, 2376f},
                {100f, 2310f, 200f, 2376f}};
        SideGesturePolicy.LauncherBandResult again =
                SideGesturePolicy.narrowBandList(identical, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertEquals(1, again.folded);
        assertEquals(474f, again.rects[1][0], 0.5f);
        assertEquals(606f, again.rects[1][2], 0.5f);
        assertEquals(2, again.rects.length);
    }

    @Test
    public void narrowListAllRectsOverlappingMainBandStayWithinBand() {
        // C4 invariant over a mixed list: every rect that overlaps the main band
        // vertically ends up horizontally inside [cx-half, cx+half].
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {700f, 2310f, 1080f, 2376f},
                {0f, 2310f, 380f, 2376f},
                {300f, 2300f, 800f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        float half = SideGesturePolicy.barHalfWidthPx(40, DENSITY);
        float center = DISPLAY_W / 2f;
        for (float[] rect : result.rects) {
            assertEquals(center - half, rect[0], 0.5f);
            assertEquals(center + half, rect[2], 0.5f);
        }
    }

    @Test
    public void narrowListAllowsDuplicateBands() {
        // Two identical full-width bands: the list is never de-duplicated, so the
        // length is preserved and BOTH get narrowed.
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {0f, 2310f, 1080f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(2, result.rects.length);
        assertEquals(474f, result.rects[0][0], 0.5f);
        assertEquals(606f, result.rects[0][2], 0.5f);
        assertEquals(474f, result.rects[1][0], 0.5f);
        assertEquals(606f, result.rects[1][2], 0.5f);
        assertEquals(2, result.narrowed);
    }

    @Test
    public void narrowListThinCornerRectLeavesBand() {
        // N8-d synthetic geometry: density 0.1 / barWidthDp 1 -> half = 0.45, i.e. the
        // whole band is narrower than MIN_BAND_PX. Rule 6 then collapses the corner
        // rect onto the main band itself instead of leaving it outside the band.
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {900f, 2310f, 1080f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 1, 0.1f, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(1, result.folded);
        // The corner rect is folded to the main band, never left in the corner.
        assertEquals(result.rects[0][0], result.rects[1][0], 0f);
        assertEquals(result.rects[0][2], result.rects[1][2], 0f);
        assertEquals(2310f, result.rects[1][1], 0.5f);
        assertEquals(2376f, result.rects[1][3], 0.5f);
        // The degenerate fold target is not zero-width (it collapses to the main band).
        assertTrue(result.rects[1][2] - result.rects[1][0] > 0f);
    }

    @Test
    public void narrowListSkipsWhenNoSanityMainBand() {
        // No rect contains the centre on the bottom strip -> applied=false, nothing moves.
        float[][] input = {
                {0f, 300f, 1080f, 336f},
                {0f, 2310f, 200f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertFalse(result.applied);
        assertEquals(0, result.folded);
        assertEquals(0, result.narrowed);
        assertEquals(2, result.rects.length);
        assertEquals(0f, result.rects[0][0], 0.5f);
        assertEquals(1080f, result.rects[0][2], 0.5f);
        assertEquals(200f, result.rects[1][2], 0.5f);
        // Same skip for a null list, a zero bar width and a non-positive half.
        assertFalse(SideGesturePolicy.narrowBandList(null, 40, DENSITY, DISPLAY_W, DISPLAY_H)
                .applied);
        float[][] band = {{0f, 2310f, 1080f, 2376f}};
        assertFalse(SideGesturePolicy.narrowBandList(band, 0, DENSITY, DISPLAY_W, DISPLAY_H)
                .applied);
        assertFalse(SideGesturePolicy.narrowBandList(band, 40, 0f, DISPLAY_W, DISPLAY_H)
                .applied);
        assertFalse(SideGesturePolicy.narrowBandList(band, 40, DENSITY, 0, DISPLAY_H)
                .applied);
    }

    @Test
    public void narrowListNeverTurnsPositiveWidthIntoZero() {
        // A main band narrower than the hint band keeps its width (rule 1 guard) and a
        // thin intersecting rect keeps a positive width too.
        float[][] input = {
                {530f, 2310f, 570f, 2376f},
                {520f, 2310f, 560f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(530f, result.rects[0][0], 0.5f);
        assertEquals(570f, result.rects[0][2], 0.5f);
        for (float[] rect : result.rects) {
            assertTrue(rect[2] - rect[0] > 0f);
        }
    }

    @Test
    public void narrowListHandlesNullPlaceholders() {
        // Non-RectF list elements arrive as null: same length, same order, null kept.
        float[][] input = {
                null,
                {0f, 2310f, 1080f, 2376f},
                null,
                {700f, 2310f, 1080f, 2376f}};
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertEquals(4, result.rects.length);
        assertNull(result.rects[0]);
        assertNull(result.rects[2]);
        assertEquals(474f, result.rects[1][0], 0.5f);
        assertEquals(606f, result.rects[1][2], 0.5f);
        assertEquals(474f, result.rects[3][0], 0.5f);
        assertEquals(1, result.folded);
    }

    @Test
    public void resultRectsDoNotCoverCorner() {
        // Regression detector for S3: the realistic list has both R-corner and L-corner
        // rects. Before the narrow the corner is covered with a positive width
        // (coversCorner == false); after C4 it is not (coversCorner == true).
        float[][] input = {
                {0f, 2310f, 1080f, 2376f},
                {900f, 2310f, 1080f, 2376f},
                {0f, 2310f, 180f, 2376f}};
        float half = SideGesturePolicy.barHalfWidthPx(40, DENSITY);
        float[] originalMain = {0f, 2310f, 1080f, 2376f};
        assertFalse(SideGesturePolicy.coversCorner(input, originalMain, half, DISPLAY_W));
        SideGesturePolicy.LauncherBandResult result =
                SideGesturePolicy.narrowBandList(input, 40, DENSITY, DISPLAY_W, DISPLAY_H);
        assertTrue(result.applied);
        assertTrue(SideGesturePolicy.coversCorner(
                result.rects, result.rects[0], half, DISPLAY_W));
    }

    @Test
    public void cornerCoverageIgnoresZeroWidthTouch() {
        // V3: touching endpoints is NOT an intersection, so a zero-width rect at the
        // corner boundary is still "covered"; a positive-width overlap is not.
        float half = SideGesturePolicy.barHalfWidthPx(40, DENSITY);
        float[] mainBand = {474f, 2310f, 606f, 2376f};
        float[][] zeroWidth = {
                {474f, 2310f, 606f, 2376f},
                {606f, 2310f, 606f, 2376f}};
        assertTrue(SideGesturePolicy.coversCorner(zeroWidth, mainBand, half, DISPLAY_W));
        float[][] positiveWidth = {
                {474f, 2310f, 606f, 2376f},
                {606f, 2310f, 700f, 2376f}};
        assertFalse(SideGesturePolicy.coversCorner(positiveWidth, mainBand, half, DISPLAY_W));
        // A corner rect that does not overlap the main band vertically is irrelevant.
        float[][] otherRow = {
                {474f, 2310f, 606f, 2376f},
                {900f, 300f, 1080f, 336f}};
        assertTrue(SideGesturePolicy.coversCorner(otherRow, mainBand, half, DISPLAY_W));
    }

    @Test
    public void barHalfWidthMatchesMeasuredPadding() {
        // 40dp -> (40*2.75)/2 + 4*2.75 = 55 + 11 = 66px.
        assertEquals(66f, SideGesturePolicy.barHalfWidthPx(40, DENSITY), 0.01f);
        // 160dp -> (160*2.75)/2 + 11 = 231px.
        assertEquals(231f, SideGesturePolicy.barHalfWidthPx(160, DENSITY), 0.01f);
    }
}
