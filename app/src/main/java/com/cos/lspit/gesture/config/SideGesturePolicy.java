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
}
