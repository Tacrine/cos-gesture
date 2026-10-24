package com.cos.lspit.gesture.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Full branch matrix for the mBack band policy (C1/C2) on the measured device
 * geometry: display width 1080, bar length 40dp, density 1 -> half width
 * 40 * 1 / 2 + 4 * 1 = 24px around the centre 540.
 */
public class BarBandPolicyTest {
    private static final int DISPLAY_W = 1080;
    private static final int BAR_DP = 40;
    private static final float DENSITY = 1f;
    /** barHalfWidthPx(40, 1f) = 40 * 1 / 2 + 4 * 1 = 24. */
    private static final float HALF = 24f;

    @Test
    public void onBarAcceptsCenterTap() {
        assertTrue(BarBandPolicy.onBar(540f, true, DISPLAY_W, BAR_DP, DENSITY));
    }

    @Test
    public void onBarAcceptsInsideBand() {
        // Both edges of the band are inclusive.
        assertTrue(BarBandPolicy.onBar(540f - HALF, true, DISPLAY_W, BAR_DP, DENSITY));
        assertTrue(BarBandPolicy.onBar(540f + HALF, true, DISPLAY_W, BAR_DP, DENSITY));
        assertTrue(BarBandPolicy.onBar(540f + HALF - 1f, true, DISPLAY_W, BAR_DP, DENSITY));
    }

    @Test
    public void onBarRejectsOutsideBand() {
        assertFalse(BarBandPolicy.onBar(540f + HALF + 1f, true, DISPLAY_W, BAR_DP, DENSITY));
        assertFalse(BarBandPolicy.onBar(540f - HALF - 1f, true, DISPLAY_W, BAR_DP, DENSITY));
        // The old fail-open range started at the view's own left edge; a corner
        // touch far off the centred band must now be rejected.
        assertFalse(BarBandPolicy.onBar(0f, true, DISPLAY_W, BAR_DP, DENSITY));
        assertFalse(BarBandPolicy.onBar(DISPLAY_W, true, DISPLAY_W, BAR_DP, DENSITY));
    }

    @Test
    public void onBarRejectsWhenCenterUnresolvable() {
        assertFalse(BarBandPolicy.onBar(0f, true, 0, BAR_DP, DENSITY));
        assertFalse(BarBandPolicy.onBar(540f, true, -1, BAR_DP, DENSITY));
    }

    @Test
    public void onBarRejectsWhenRawUnusable() {
        // displayWidth 2 -> centre 1, so rawX 0 is inside the band and only the
        // rawUsable flag can reject it.
        assertTrue(BarBandPolicy.onBar(0f, true, 2, BAR_DP, DENSITY));
        assertFalse(BarBandPolicy.onBar(0f, false, 2, BAR_DP, DENSITY));
        assertFalse(BarBandPolicy.onBar(0f, false, DISPLAY_W, BAR_DP, DENSITY));
    }

    @Test
    public void effectiveBarDpUsesConfigured() {
        assertEquals(120, BarBandPolicy.effectiveBarDp(120));
        assertEquals(40, BarBandPolicy.effectiveBarDp(40));
        assertEquals(0, BarBandPolicy.effectiveBarDp(0));
    }

    @Test
    public void effectiveBarDpDefaultsWhenNull() {
        assertEquals(SideGesturePolicy.DEFAULT_BAR_DP, BarBandPolicy.effectiveBarDp(null));
        assertEquals(40, BarBandPolicy.effectiveBarDp(null));
    }

    @Test
    public void screenCenterIsHalfDisplayWidth() {
        assertEquals(540f, BarBandPolicy.screenCenter(DISPLAY_W), 0f);
        assertEquals(1f, BarBandPolicy.screenCenter(2), 0f);
    }

    @Test
    public void screenCenterInvalidWhenDisplayWidthNotPositive() {
        assertEquals(-1f, BarBandPolicy.screenCenter(0), 0f);
        assertEquals(-1f, BarBandPolicy.screenCenter(-1080), 0f);
    }
}
