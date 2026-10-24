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

    /** C4 rule 6 / N37: a fold or intersection narrower than this px is degenerate. */
    private static final float MIN_BAND_PX = 2f;

    /** C4: result of a list-level narrow; index-aligned with the input array. */
    public static final class LauncherBandResult {
        /** Same length as the input; {@code null} positions stay {@code null}. */
        public final float[][] rects;
        /** True when a sanity main band was found and the list was processed. */
        public final boolean applied;
        /** Rects folded into the main band (rules 4 and 6). */
        public final int folded;
        /** Rects matched by the horizontal narrow rules (rules 1 and 2). */
        public final int narrowed;

        LauncherBandResult(float[][] rects, boolean applied, int folded, int narrowed) {
            this.rects = rects;
            this.applied = applied;
            this.folded = folded;
            this.narrowed = narrowed;
        }
    }

    /**
     * C4: narrows a whole registered home-gesture list around the display centre.
     *
     * <p>Index alignment is the contract: the returned array has the same length as
     * the input and every {@code null} position stays {@code null} (the launcher list
     * may hold non-{@code RectF} placeholders), so the caller writes back by index and
     * skips nulls. The list is never reordered, filtered or de-duplicated: identical
     * bands are legitimate, and the region is treated as a <strong>union</strong> of
     * rects (C4 / N21).
     *
     * <p>Pass 1 picks the main band: a rect that contains {@code displayWidth / 2f}
     * horizontally, reaches the bottom strip ({@code bottom >= displayHeight - 3 *
     * BOTTOM_STRIP_H}) and is at most {@code 3 * BOTTOM_STRIP_H} tall. Tie-break:
     * strictly widest, then greatest bottom, then first in the list. No candidate
     * means {@code applied == false} (the caller logs {@code REGION_SKIP}).
     *
     * <p>Pass 2 applies the per-rect rules documented in C4: narrow the main band and
     * the horizontally intersecting overlapping rects, fold the corner rects that
     * overlap the main band vertically <em>unconditionally</em> (even when the target
     * is byte-identical to the main band), leave everything else untouched.
     *
     * @param rects non-null array of {@code {left, top, right, bottom}}, null elements
     *        allowed
     * @return never null; {@code rects} is a fresh copy, so the input is never mutated
     */
    public static LauncherBandResult narrowBandList(float[][] rects, int barWidthDp,
            float density, int displayWidth, int displayHeight) {
        float[][] out = rects == null ? null : copyRects(rects);
        float half = barHalfWidthPx(barWidthDp, density);
        // C4 invariant: a degenerate band or an unusable display size skips the region.
        if (out == null || barWidthDp <= 0 || !(half > 0f)
                || displayWidth <= 0 || displayHeight <= 0) {
            return new LauncherBandResult(out, false, 0, 0);
        }
        float center = displayWidth / 2f;
        int mainIndex = findMainBand(out, center, displayHeight);
        if (mainIndex < 0) return new LauncherBandResult(out, false, 0, 0);
        float[] mainBand = out[mainIndex];
        float bandLeft = center - half;
        float bandRight = center + half;
        int folded = 0;
        int narrowed = 0;
        for (int i = 0; i < out.length; i++) {
            float[] rect = out[i];
            if (rect == null) continue;                 // null placeholder: not written back
            if (i == mainIndex) {
                // Rule 1: the main band is only ever narrowed, never widened.
                float left = Math.max(rect[0], bandLeft);
                float right = Math.min(rect[2], bandRight);
                if (right - left < rect[2] - rect[0]) {
                    rect[0] = left;
                    rect[2] = right;
                }
                narrowed++;
                continue;
            }
            boolean horizontal = intersectsX(rect[0], rect[2], bandLeft, bandRight);
            boolean vertical = overlapsY(rect[1], rect[3], mainBand[1], mainBand[3]);
            if (horizontal && vertical) {
                // Rule 2: two-axis intersection. Rule 6: under 2px folds to the main band.
                float left = Math.max(rect[0], bandLeft);
                float right = Math.min(rect[2], bandRight);
                if (right - left < MIN_BAND_PX) {
                    foldIntoMainBand(rect, mainBand, bandLeft, bandRight);
                    folded++;
                } else {
                    rect[0] = left;
                    rect[2] = right;
                    rect[1] = Math.max(rect[1], mainBand[1]);
                    rect[3] = Math.min(rect[3], mainBand[3]);
                    narrowed++;
                }
                continue;
            }
            if (horizontal) continue;                   // Rule 3: kept as-is
            if (!vertical) continue;                    // Rule 5: kept as-is
            // Rule 4: corner rect overlapping the main band vertically -> always folded,
            // even when the fold target equals the current rect byte for byte (N21).
            foldIntoMainBand(rect, mainBand, bandLeft, bandRight);
            folded++;
        }
        return new LauncherBandResult(out, true, folded, narrowed);
    }

    /**
     * C5: regression detector, never the positive proof of coverage.
     *
     * <p>True when every rect in {@code resultRects} that overlaps {@code mainBand}
     * vertically does not intersect the R-corner interval
     * {@code [cx + half, displayWidth]} with a strictly positive width (C2 / V3:
     * touching endpoints do not count). Under the C4 invariant this is always true,
     * so a {@code false} means the narrow silently stopped covering the corner.
     */
    public static boolean coversCorner(float[][] resultRects, float[] mainBand, float half,
            int displayWidth) {
        if (resultRects == null || mainBand == null || displayWidth <= 0) return false;
        float cornerLeft = displayWidth / 2f + half;
        for (float[] rect : resultRects) {
            if (rect == null) continue;
            if (!overlapsY(rect[1], rect[3], mainBand[1], mainBand[3])) continue;
            float overlap = Math.min(rect[2], (float) displayWidth)
                    - Math.max(rect[0], cornerLeft);
            if (overlap > 0f) return false;
        }
        return true;
    }

    /** Pass 1 of C4: index of the sanity main band, or {@code -1}. */
    private static int findMainBand(float[][] rects, float center, int displayHeight) {
        int best = -1;
        float bestWidth = -1f;
        float bestBottom = -1f;
        for (int i = 0; i < rects.length; i++) {
            float[] rect = rects[i];
            if (rect == null) continue;
            if (!(rect[0] <= center && center <= rect[2])) continue;
            if (rect[3] < displayHeight - 3 * BOTTOM_STRIP_H) continue;
            if (rect[3] - rect[1] > 3 * BOTTOM_STRIP_H) continue;
            float width = rect[2] - rect[0];
            if (width > bestWidth || (width == bestWidth && rect[3] > bestBottom)) {
                best = i;
                bestWidth = width;
                bestBottom = rect[3];
            }
        }
        return best;
    }

    /**
     * C4 rules 4 and 6: folds {@code rect} into the main band extent, clamping the
     * horizontal extent to the band; a degenerate target collapses to the main band
     * itself (not "keep the original").
     */
    private static void foldIntoMainBand(float[] rect, float[] mainBand, float bandLeft,
            float bandRight) {
        float left = Math.max(mainBand[0], bandLeft);
        float right = Math.min(mainBand[2], bandRight);
        if (right - left < MIN_BAND_PX) {
            rect[0] = mainBand[0];
            rect[1] = mainBand[1];
            rect[2] = mainBand[2];
            rect[3] = mainBand[3];
            return;
        }
        rect[0] = left;
        rect[1] = mainBand[1];
        rect[2] = right;
        rect[3] = mainBand[3];
    }

    /** C2 intersection: strictly positive width only (touching endpoints do not count). */
    private static boolean intersectsX(float leftA, float rightA, float leftB, float rightB) {
        return Math.min(rightA, rightB) - Math.max(leftA, leftB) > 0f;
    }

    /** C2 vertical overlap: strictly positive height only. */
    private static boolean overlapsY(float topA, float bottomA, float topB, float bottomB) {
        return Math.min(bottomA, bottomB) - Math.max(topA, topB) > 0f;
    }

    private static float[][] copyRects(float[][] rects) {
        float[][] out = new float[rects.length][];
        for (int i = 0; i < rects.length; i++) {
            float[] rect = rects[i];
            if (rect == null) continue;
            out[i] = new float[] {rect[0], rect[1], rect[2], rect[3]};
        }
        return out;
    }
}
