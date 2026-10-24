package com.cos.lspit.gesture.config;

/**
 * Pure, unit-tested mBack band policy (C1/C2).
 *
 * <p>Screen space only: the band is centred on {@code displayWidth / 2f} and its
 * half width is the shared hint-bar half width
 * ({@link SideGesturePolicy#barHalfWidthPx(int, float)} = {@code barWidthDp *
 * density / 2 + 4dp * density}), so the SystemUI veto, the launcher region
 * narrow and this gate always agree on what counts as "on the bar".
 *
 * <p>Deliberately free of {@code android.*} imports so it runs in plain JVM
 * unit tests, and free of state: every method is a pure function of its
 * arguments.
 */
public final class BarBandPolicy {
    private BarBandPolicy() {}

    /**
     * Effective hint-bar length in dp: the configured value, or
     * {@link SideGesturePolicy#DEFAULT_BAR_DP} when the module left it unset.
     * Never derived from the live handle width (H5).
     */
    public static int effectiveBarDp(Integer configured) {
        return configured == null ? SideGesturePolicy.DEFAULT_BAR_DP : configured;
    }

    /**
     * Horizontal centre of the bar band in screen pixels.
     *
     * @return {@code displayWidth / 2f}, or {@code -1f} when the display width is
     *         not positive (unresolvable -> callers must fail closed).
     */
    public static float screenCenter(int displayWidth) {
        if (displayWidth <= 0) return -1f;
        return displayWidth / 2f;
    }

    /**
     * Whether a DOWN at {@code rawX} (screen space) lands on the hint-bar band.
     *
     * @param rawUsable {@code false} when {@code MotionEvent.getRawX()} cannot be
     *                  trusted (raw 0 while the view-local x is positive); the
     *                  decision then fails closed.
     */
    public static boolean onBar(float rawX, boolean rawUsable, int displayWidth,
            int barWidthDp, float density) {
        if (!rawUsable) return false;
        float center = screenCenter(displayWidth);
        if (center < 0f) return false;
        float half = SideGesturePolicy.barHalfWidthPx(barWidthDp, density);
        return Math.abs(rawX - center) <= half;
    }
}
