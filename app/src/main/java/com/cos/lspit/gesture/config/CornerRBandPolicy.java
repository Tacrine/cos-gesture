package com.cos.lspit.gesture.config;

/**
 * Pure, unit-tested veto policy for the launcher swipe-up admission gate.
 *
 * <p>The whole ColorOS bottom gesture (home/recents/task-switch) is admitted by
 * the launcher process in the ACTION_DOWN branch of
 * {@code OplusBaseTouchInteractionService#onDispatchInputEvent}, which asks
 * {@code canRespondGesture(MotionEvent)} whether the gesture is allowed at all.
 * This policy answers the one geometric question that gate needs: did the finger
 * come down inside the bottom <em>corner expansion</em> of the gesture strip,
 * i.e. a thumb resting in the bottom-left or bottom-right corner before it swipes
 * up-and-away?
 *
 * <p>There are two symmetric bands, each the bottom strip (the same
 * {@link SideGesturePolicy#BOTTOM_STRIP_H} tall region the rest of the module
 * uses) intersected with one {@code CORNER_BAND_DP}-wide column:
 * {@link #inLCornerBand} covers the left corner ({@code x in [0, bandPx)}) and
 * {@link #inRCornerBand} the right corner ({@code x in (W - bandPx, W]}).
 * Anything else — the hint bar itself, the two 123px seams beside it, the center,
 * the whole rest of the screen — passes, so a missed verdict never costs a
 * legitimate gesture.
 *
 * <p>The class name is historical: it was introduced for the right corner only
 * and now owns both sides. It is deliberately <em>not</em> renamed, because the
 * name is referenced by the hooker, the test class and several accepted audit
 * anchors, and a rename would rewrite already-verified material for no
 * behavioural gain.
 *
 * <p>Every unusable input (non-positive display size, non-positive band or strip
 * height) returns {@code false} = "do not veto": geometry that cannot be trusted
 * must never suppress a gesture.
 *
 * <p>Deliberately free of {@code android.*} imports so it runs in plain JVM unit
 * tests.
 */
public final class CornerRBandPolicy {
    /**
     * R-corner band width in dp. A product trade-off (roughly the width a thumb
     * occupies when it starts a diagonal swipe from the bottom-right corner), not
     * a firmware constant, and deliberately independent of {@code barWidthDp}.
     */
    public static final int CORNER_BAND_DP = 100;

    private CornerRBandPolicy() {}

    /**
     * Converts the band width from dp to px, rounding to the nearest px.
     *
     * @return the band width in px, or {@code -1} when {@code density <= 0}
     *         (callers fail open on any non-positive result)
     */
    public static int cornerBandPx(int bandDp, float density) {
        if (!(density > 0f)) return -1;
        return Math.round(bandDp * density);
    }

    /**
     * @return {@code true} only when ({@code x}, {@code y}) lies in the bottom-right
     *         corner band, i.e. below the bottom strip top edge AND right of the band
     *         left edge. Both edges are inclusive at the strip top and exclusive at
     *         the band left edge ({@code x == displayWidth - bandPx} still passes).
     *         Any unusable metric returns {@code false} (= do not veto).
     */
    public static boolean inRCornerBand(float x, float y, int displayWidth, int displayHeight,
            int bottomStripH, int bandPx) {
        return inBand(x, y, displayWidth, displayHeight, bottomStripH, bandPx, true);
    }

    /**
     * @return {@code true} only when ({@code x}, {@code y}) lies in the bottom-left
     *         corner band, the exact mirror of {@link #inRCornerBand}: below the bottom
     *         strip top edge AND left of the band right edge. Both edges are inclusive
     *         at the strip top and exclusive at the band right edge, so {@code x == 0}
     *         and {@code x == bandPx - 1} are vetoed while {@code x == bandPx} passes.
     *         Any unusable metric returns {@code false} (= do not veto).
     */
    public static boolean inLCornerBand(float x, float y, int displayWidth, int displayHeight,
            int bottomStripH, int bandPx) {
        return inBand(x, y, displayWidth, displayHeight, bottomStripH, bandPx, false);
    }

    /**
     * The single shared four-step decision both corner bands use; {@code rightSide}
     * only picks which column the third step compares against, so the fail-open
     * guards exist exactly once.
     */
    private static boolean inBand(float x, float y, int displayWidth, int displayHeight,
            int bottomStripH, int bandPx, boolean rightSide) {
        // Fail open: without trusted geometry a gesture is never vetoed.
        if (displayWidth <= 0 || displayHeight <= 0 || bandPx <= 0 || bottomStripH <= 0) {
            return false;
        }
        // Above the bottom strip the swipe-up gesture does not start in the corner.
        if (y < displayHeight - bottomStripH) return false;
        if (rightSide) {
            // Right band: x in (displayWidth - bandPx, displayWidth]; the right end is closed.
            if (x <= displayWidth - bandPx) return false;
        } else {
            // Left band: x in [0, bandPx); the left end is closed.
            if (x >= bandPx) return false;
        }
        return true;
    }
}
