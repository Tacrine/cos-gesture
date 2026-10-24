package com.cos.lspit.gesture.config;

/**
 * Pure, unit-tested pixel-suppression policy for the hint bar (C3).
 *
 * <p>Four pure functions plus one in-place merge helper; deliberately free of
 * platform imports so every branch can be asserted in a plain JVM test.
 *
 * <p>Split of responsibility (C3): {@link #admitsSibling} decides who may be
 * recorded, {@link #alphaTarget} decides what alpha a recorded view must get
 * now, {@link #restoreShouldDropRecord} decides whether the record may be
 * dropped, and {@link #mergeInto} merges a candidate set into the existing
 * target set <strong>in place</strong> so the target set keeps its
 * identity/weak-reference semantics.
 */
public final class BarPixelPolicy {
    /** Class-name fragments (lower case) of the painters that may be suppressed. */
    private static final String[] BAR_NAME_FRAGMENTS = {
            "gesturebar", "guidebar", "hintbar", "sidegesturebar"};

    private BarPixelPolicy() {}

    /**
     * C3.1: the module toggle and the converted SystemUI hide both request a hide,
     * and an expanded notification shade always wins (the user is looking at the
     * shade, the bar must be there for them to get back).
     */
    public static boolean shouldSuppress(boolean moduleHide, boolean sysConverted,
            boolean shadeExpanded) {
        return (moduleHide || sysConverted) && !shadeExpanded;
    }

    /**
     * C3.2: a sibling may be admitted (and therefore zeroed) only when it looks
     * like a hint/gesture bar, is visible, is not already transparent, has a real
     * area, and is neither a container nor any {@code ViewGroup}.
     *
     * @param isContainerOrViewGroup caller passes
     *        {@code isContainer || (view instanceof ViewGroup)}; containers are
     *        never admitted (W1).
     */
    public static boolean admitsSibling(String className, boolean visible, float alpha,
            int w, int h, boolean isContainerOrViewGroup) {
        if (className == null) return false;
        if (isContainerOrViewGroup) return false;
        if (!visible) return false;
        if (!(alpha > 0f)) return false;
        if (w <= 0 || h <= 0) return false;
        String lower = className.toLowerCase(java.util.Locale.ROOT);
        for (String fragment : BAR_NAME_FRAGMENTS) {
            if (lower.contains(fragment)) return true;
        }
        return false;
    }

    /**
     * C3.5: alpha a view must have right now.
     *
     * @return {@code -1f} ("do not touch this view") when we have no record of it.
     *         The sentinel test is <strong>first</strong> on purpose (N44):
     *         otherwise {@code suppress && !hasRecord} would return 0f, writing a
     *         zero we never recorded and can never restore.
     */
    public static float alphaTarget(boolean suppress, boolean hasRecord, float recorded) {
        if (!hasRecord) return -1f;
        if (suppress) return 0f;
        if (recorded == 0f) return 0f;
        return recorded;
    }

    /**
     * Z4: a record may only be dropped once suppression is gone; dropping it while
     * still suppressed makes "turn the switch off restores the bar" permanently
     * impossible.
     */
    public static boolean restoreShouldDropRecord(boolean suppress, boolean hasRecord) {
        return !suppress && hasRecord;
    }

    /**
     * N10-a/N43/N48: merge {@code candidates} into {@code existing} <strong>in
     * place</strong>. Returns void so the "reassign the target set" mistake cannot
     * compile (that would replace the weak, identity-based set with a strong one).
     */
    public static <T> void mergeInto(java.util.Set<T> existing, java.util.Set<T> candidates) {
        if (existing == null || candidates == null) return;
        existing.addAll(candidates);
    }
}
