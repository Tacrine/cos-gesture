package com.cos.lspit.gesture.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.cos.lspit.gesture.hook.NavigationHandleHooks.SystemHide.shouldConvert;

import org.junit.Test;

/**
 * Decision matrix for the system-hide funnel converter.
 *
 * SystemUI can ask the bar to hide via two entry points we hook:
 *   - updateSideGestureBarVisible(int): arg=1 -> hide
 *   - updateWindowAlpha(int):           arg=1 or arg=8 (View.GONE) -> hide
 *   (View.GONE == 8 is what updateViewVisible$1 uses when the gesture guide
 *    bar is also hidden -- so any non-zero request is a hide.)
 *
 * We clamp non-zero to 0 only when a feature actually needs the bar:
 *   - mBack: relies on the handle's onTouch for tap/long-press -> Home/Back
 *   - barOnly: relies on the handle starting gestures from its hit region
 *
 * When both are off, we respect the user's request and let SystemUI truly hide
 * the bar (window alpha 0 + guide bar GONE) -- that's exactly what users
 * without mBack expect (apps get the extra bottom inset and content shifts up).
 */
public class SystemHideTest {

    private static boolean convert(int requested, boolean mback, boolean barOnly) {
        return shouldConvert(requested, mback, barOnly);
    }

    // Hide requests (1 from updateSideGestureBarVisible, 8/View.GONE from
    // updateWindowAlpha): we may need to clamp.

    @Test public void hideRequest_one_mbackOn_clamp() {
        assertTrue(convert(1, true, false));
    }

    @Test public void hideRequest_one_barOnlyOn_clamp() {
        assertTrue(convert(1, false, true));
    }

    @Test public void hideRequest_one_bothOn_clamp() {
        assertTrue(convert(1, true, true));
    }

    @Test public void hideRequest_eight_mbackOn_clamp() {
        // View.GONE == 8 -- passed by updateViewVisible$1 when guide bar hidden.
        assertTrue(convert(8, true, false));
    }

    @Test public void hideRequest_eight_barOnlyOn_clamp() {
        assertTrue(convert(8, false, true));
    }

    @Test public void hideRequest_eight_bothOn_clamp() {
        assertTrue(convert(8, true, true));
    }

    @Test public void hideRequest_one_bothOff_respectSystem() {
        // No feature needs the bar -- let SystemUI truly hide it.
        assertFalse(convert(1, false, false));
    }

    @Test public void hideRequest_eight_bothOff_respectSystem() {
        assertFalse(convert(8, false, false));
    }

    // Show request (0): funnel asked to show -- we must not interfere

    @Test public void showRequest_anyCombo_noClamp() {
        // Showing should never be intercepted, regardless of feature state.
        assertFalse(convert(0, true, true));
        assertFalse(convert(0, true, false));
        assertFalse(convert(0, false, true));
        assertFalse(convert(0, false, false));
    }

    // Upstream proxy chokepoint: NavBarSettingsValueProxy.getSwipeSideGestureBarType
    // returns 0 = shown, non-zero = hidden. We report "shown" when a feature
    // needs the bar so the whole SystemUI pipeline keeps the handle touchable.

    private static boolean overrideProxy(int realType, boolean mback, boolean barOnly) {
        return shouldConvert(realType, mback, barOnly);
    }

    @Test public void proxyHide_typeOne_mbackOn() {
        // Core scenario: system hide requested + mBack needs the handle.
        assertTrue(overrideProxy(1, true, false));
    }

    @Test public void proxyHide_typeOne_barOnlyOn() {
        assertTrue(overrideProxy(1, false, true));
    }

    @Test public void proxyHide_typeOne_bothOff_respectSystem() {
        assertFalse(overrideProxy(1, false, false));
    }

    @Test public void proxyHide_typeZero_mbackOn_noOverride() {
        // System already shows the bar -- never interfere.
        assertFalse(overrideProxy(0, true, true));
    }

    @Test public void proxyHide_unexpectedValue_mbackOn() {
        // Any non-zero type is treated as a hide request, matching shouldConvert.
        assertTrue(overrideProxy(2, true, false));
    }
}
