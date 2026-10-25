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
        // Left corner: the symmetric case must never be vetoed.
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
}
