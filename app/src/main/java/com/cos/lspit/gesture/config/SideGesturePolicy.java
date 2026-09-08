package com.cos.lspit.gesture.config;

/**
 * Pure, unit-tested veto policy for the Oplus side-back gate.
 *
 * <p>Reproduces the allow-gate conditions measured live in
 * {@code discovery.json} (outcome MATCH, dynamic evidence t3a/t3c):
 * only a regular touchscreen (source {@code TOUCHSCREEN}) finger
 * {@code ACTION_DOWN} that lands in the left/right side-edge region of the
 * display — and NOT in the status bar or the bottom Home/Recents strip — is
 * vetoed. Everything else (bottom-strip events that feed the nav-handle
 * animation, status-bar touches, stylus/trackpad, non-DOWN actions, center
 * touches) passes through unchanged.
 *
 * <p>Deliberately free of {@code android.*} imports so it runs in plain JVM
 * unit tests. Constants are copied from the measured device geometry.
 */
public final class SideGesturePolicy {
    /** Status-bar height in px below which a DOWN is never a side-back gesture. */
    public static final int STATUS_BAR_H = 120;
    /** Bottom gesture strip height in px; DOWNs in it feed Home/Recents/Quickstep, not side Back. */
    public static final int BOTTOM_STRIP_H = 66;
    /** Side-edge width in px (left/right columns) inside which a DOWN may start side Back. */
    public static final int SIDE_W = 36;

    // android.view.MotionEvent / android.view.InputDevice raw values (kept here to stay android-free).
    public static final int ACTION_DOWN = 0;
    public static final int SOURCE_TOUCHSCREEN = 0x00001002;
    public static final int TOOL_TYPE_FINGER = 1;

    /** Hint-bar length (dp) assumed when the module left the width untouched. */
    public static final int DEFAULT_BAR_DP = 40;
    /** Extra band around the bar (dp) where a DOWN still counts as "on the bar". */
    public static final float BAR_GATE_PADDING_DP = 4f;

    private SideGesturePolicy() {}

    /**
     * @return {@code true} only for a touchscreen-finger ACTION_DOWN inside the
     *         left/right side-edge region and outside the status bar / bottom strip,
     *         with that side's runtime switch enabled (vetoLeft / vetoRight).
     */
    public static boolean shouldVeto(int action, int source, int toolType,
            float x, float y, int displayWidth, int displayHeight,
            boolean vetoLeft, boolean vetoRight) {
        if (action != ACTION_DOWN) return false;
        if (source != SOURCE_TOUCHSCREEN) return false;
        if (toolType != TOOL_TYPE_FINGER) return false;
        if (y <= STATUS_BAR_H) return false;                       // status-bar area
        if (y >= displayHeight - BOTTOM_STRIP_H) return false;     // bottom Home/Recents strip
        boolean left = x < SIDE_W;
        boolean right = x > displayWidth - SIDE_W;
        return (left && vetoLeft) || (right && vetoRight);
    }

    /**
     * Bar-only gate: when armed, a bottom-strip gesture only fires if the finger
     * first lands on the hint bar. A touchscreen-finger ACTION_DOWN in the bottom
     * Home/Recents strip whose x is off the (custom-width) hint-bar band is vetoed
     * so the whole bottom region stops triggering navigation; an on-bar DOWN passes.
     *
     * @return {@code true} to veto the DOWN (drop the bottom gesture entirely).
     */
    public static boolean shouldVetoBarOnly(int action, int source, int toolType,
            float x, float y, int displayWidth, int displayHeight,
            boolean barOnly, int barWidthDp, float density) {
        if (!barOnly) return false;
        if (action != ACTION_DOWN) return false;
        if (source != SOURCE_TOUCHSCREEN) return false;
        if (toolType != TOOL_TYPE_FINGER) return false;
        if (y < displayHeight - BOTTOM_STRIP_H) return false;      // outside the bottom strip
        float center = displayWidth / 2f;
        return Math.abs(x - center) > barHalfWidthPx(barWidthDp, density); // off the hint-bar band
    }

    /**
     * Half-width (px) of the hint-bar band: half the user bar length plus the
     * gate padding. Shared by the SystemUI DOWN veto and the launcher region
     * narrow so the two always agree on what counts as "on the bar".
     */
    public static float barHalfWidthPx(int barWidthDp, float density) {
        return (barWidthDp * density) / 2f + BAR_GATE_PADDING_DP * density;
    }

    /**
     * Narrows a horizontal gesture band {@code [left,right]} symmetrically around
     * its own center down to the hint-bar band. Mutates {@code ltrb} in place.
     *
     * @param ltrb 4 floats {left, top, right, bottom}; only indices 0 and 2 change.
     * @return {@code true} when the band could be narrowed to at least 2px (caller
     *         should apply it), {@code false} when the result is degenerate (caller
     *         must leave the region untouched so gestures are never lost).
     */
    public static boolean narrowBandToBar(float[] ltrb, int barWidthDp, float density) {
        float half = barHalfWidthPx(barWidthDp, density);
        float center = (ltrb[0] + ltrb[2]) / 2f;
        float left = Math.max(ltrb[0], center - half);
        float right = Math.min(ltrb[2], center + half);
        if (right - left < 2f) return false;
        ltrb[0] = left;
        ltrb[2] = right;
        return true;
    }
}
