package com.cos.lspit.gesture.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Branch matrix for the launcher swipe-up admission R-corner veto on the measured
 * device geometry (display 1080x2376, density 3.0, bottom strip 66).
 *
 * <p>With {@code CORNER_BAND_DP = 100} the band is {@code x in (780, 1080]} crossed
 * with {@code y >= 2310}; the hint bar sits at {@code x in [423, 657]}, so the two
 * never overlap and everything between them keeps passing.
 */
public class CornerRBandPolicyTest {
    private static final int DISPLAY_W = 1080;
    private static final int DISPLAY_H = 2376;
    private static final int BOTTOM_STRIP_H = SideGesturePolicy.BOTTOM_STRIP_H;
    private static final int BAND_PX = 300;
    private static final float DENSITY = 3.0f;

    @Test
    public void cornerBandPxScalesWithDensity() {
        assertEquals(300, CornerRBandPolicy.cornerBandPx(100, DENSITY));
        assertEquals(200, CornerRBandPolicy.cornerBandPx(100, 2.0f));
        // Unusable density: the caller fails open on any non-positive band width.
        assertEquals(-1, CornerRBandPolicy.cornerBandPx(100, 0f));
        assertTrue(CornerRBandPolicy.cornerBandPx(0, DENSITY) <= 0);
    }

    @Test
    public void inRCornerBandVetoesRightCorner() {
        assertTrue(CornerRBandPolicy.inRCornerBand(
                1050f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void inRCornerBandPassesCenterAndLeft() {
        // Real-device hint-bar-center sample: x=567.42 <= 780 -> the swipe-up stays alive.
        assertFalse(CornerRBandPolicy.inRCornerBand(
                567.42f, 2329.26f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // Left corner: not this predicate's business — inLCornerBand owns it.
        assertFalse(CornerRBandPolicy.inRCornerBand(
                60f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void inRCornerBandPassesAboveBottomStrip() {
        // y=1500 is far above the 66px bottom strip -> normal app touch, never vetoed.
        assertFalse(CornerRBandPolicy.inRCornerBand(
                1050f, 1500f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void inRCornerBandFailOpenOnInvalidMetrics() {
        // Unusable geometry must never veto: every case returns false.
        assertFalse(CornerRBandPolicy.inRCornerBand(1050f, 2330f, 0, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inRCornerBand(1050f, 2330f, DISPLAY_W, 0, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inRCornerBand(1050f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, 0));
    }

    @Test
    public void bandBoundaryIsHalfOpen() {
        // x == displayWidth - bandPx (780) is NOT in the band; 781 is.
        assertFalse(CornerRBandPolicy.inRCornerBand(
                DISPLAY_W - BAND_PX, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertTrue(CornerRBandPolicy.inRCornerBand(
                DISPLAY_W - BAND_PX + 1, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void bottomStripBoundaryMatchesExistingConstant() {
        // x is pinned inside the R corner band so only the strip edge can decide.
        // y == displayHeight - BOTTOM_STRIP_H (2310) is IN the strip; 1px less is out.
        assertTrue(CornerRBandPolicy.inRCornerBand(
                1050f, DISPLAY_H - BOTTOM_STRIP_H, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inRCornerBand(
                1050f, DISPLAY_H - BOTTOM_STRIP_H - 1, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    // ------------------------------------------------------------------
    // L corner: the symmetric band the right corner already had.
    // ------------------------------------------------------------------

    @Test
    public void inLCornerBandVetoesLeftCorner() {
        assertTrue(CornerRBandPolicy.inLCornerBand(
                60f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void inLCornerBandPassesHintBarAndCenterAndRight() {
        // Real-device hint-bar-center sample: x=567.42 is far right of the left band.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                567.42f, 2329.26f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // Hint bar left edge.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                423f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // Hint bar right edge.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                657f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // The right corner is not this predicate's business.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                1050f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void inLCornerBandPassesAboveBottomStrip() {
        // y=1500 is far above the 66px bottom strip -> normal app touch, never vetoed.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                60f, 1500f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // y == 2310 (strip top edge) is IN the strip; 1px less is out.
        assertTrue(CornerRBandPolicy.inLCornerBand(
                60f, DISPLAY_H - BOTTOM_STRIP_H, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inLCornerBand(
                60f, DISPLAY_H - BOTTOM_STRIP_H - 1, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void inLCornerBandFailOpenOnInvalidMetrics() {
        // Unusable geometry must never veto: every case returns false.
        assertFalse(CornerRBandPolicy.inLCornerBand(60f, 2330f, 0, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inLCornerBand(60f, 2330f, DISPLAY_W, 0, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inLCornerBand(60f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, 0));
        assertFalse(CornerRBandPolicy.inLCornerBand(60f, 2330f, DISPLAY_W, DISPLAY_H, 0, BAND_PX));
    }

    @Test
    public void leftBandBoundaryIsHalfOpen() {
        // x in [0, 300): the left end is CLOSED (x == 0 vetoed), the right end is OPEN
        // (x == bandPx passes) — the exact mirror of the right band's (780, 1080].
        assertTrue(CornerRBandPolicy.inLCornerBand(
                0f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertTrue(CornerRBandPolicy.inLCornerBand(
                BAND_PX - 1, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inLCornerBand(
                BAND_PX, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inLCornerBand(
                BAND_PX + 1, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }

    @Test
    public void leftBandNeverOverlapsHintBar() {
        // The hint bar's left edge is outside the left band — the seam is real.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                423f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // The seam's own start point already passes, so the seam is not swallowed.
        assertFalse(CornerRBandPolicy.inLCornerBand(
                300f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        // Documentation of the seam width: left [300, 423) and right (657, 780] are
        // both 123px. This assertion is compile-time constant, so it protects nothing
        // at runtime — the two calls above are the behavioural guards.
        assertEquals(123, 423 - 300);
        assertEquals(123, 780 - 657);
        assertEquals(423 - 300, 780 - 657);
    }

    @Test
    public void leftAndRightBandsAreMirrorSymmetric() {
        // x_R = DISPLAY_W - x_L must flip the verdict for every representative point.
        int[] leftPoints = { 0, 30, 299, 300, 422, 540, 780, 781, 1050, 1080 };
        for (int xLeft : leftPoints) {
            assertEquals("mirror mismatch at x_L=" + xLeft,
                    CornerRBandPolicy.inLCornerBand(
                            (float) xLeft, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX),
                    CornerRBandPolicy.inRCornerBand(
                            (float) (DISPLAY_W - xLeft), 2330f, DISPLAY_W, DISPLAY_H,
                            BOTTOM_STRIP_H, BAND_PX));
        }
        // Pin the four integer boundaries the symmetry depends on.
        assertTrue(CornerRBandPolicy.inLCornerBand(
                0f, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inLCornerBand(
                BAND_PX, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertFalse(CornerRBandPolicy.inRCornerBand(
                DISPLAY_W - BAND_PX, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
        assertTrue(CornerRBandPolicy.inRCornerBand(
                DISPLAY_W - BAND_PX + 1, 2330f, DISPLAY_W, DISPLAY_H, BOTTOM_STRIP_H, BAND_PX));
    }
}
