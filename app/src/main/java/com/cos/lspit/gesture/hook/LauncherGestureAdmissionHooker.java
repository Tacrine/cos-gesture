package com.cos.lspit.gesture.hook;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import com.cos.lspit.gesture.config.CornerRBandPolicy;
import com.cos.lspit.gesture.config.HookPolicy;
import com.cos.lspit.gesture.config.SideGesturePolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Launcher-side R-corner veto on the swipe-up admission gate.
 *
 * <p>A diagonal swipe that starts at the bottom-right corner of the screen is a
 * mis-touch: it lands inside the launcher's swipe-up region and pops up Recents.
 * {@link LauncherRegionHooker} cannot help — it narrows the window-manager touch
 * region, while the touch interaction service admits the gesture through its own
 * input monitor. The one predicate that decides admission is
 * {@code com.android.quickstep.OplusBaseTouchInteractionService#canRespondGesture(MotionEvent)Z}:
 * when it answers {@code false} the caller installs {@code InputConsumer.NO_OP}
 * instead of building an activity consumer, so no swipe-up handler is ever created.
 *
 * <p><strong>Only</strong> touches inside the bottom-left <strong>or</strong>
 * bottom-right corner band ({@link CornerRBandPolicy}) are vetoed; every other
 * action, coordinate and geometry — including anything unreadable — passes through
 * untouched (<em>fail-open</em>). While {@code barOnly} is off nothing is vetoed at
 * all, so the launcher behaves exactly as if the module were not installed.
 *
 * <p>{@code canRespondGesture} is a pure predicate (it never writes
 * {@code mConsumer}) but its body still resets two state flags before returning,
 * so the hook always runs the original first and only then overrides the returned
 * value: exactly one {@code proceed()} per interception, and never a second one on
 * the failure path (which answers {@code Boolean.TRUE} = "not vetoed").
 *
 * <p>The target is a {@code private final} method, hence
 * {@link Class#getDeclaredMethod} instead of {@code getMethod}; the descriptor is
 * verified before hooking. There is deliberately no APK hash gate: the launcher
 * package is not the firmware build this module pins for SystemUI, and the target
 * signature is verified structurally instead.
 */
public final class LauncherGestureAdmissionHooker {
    private static final String TAG = "COS16-Gesture";

    /** Launcher quickstep touch service that admits or drops the bottom gesture. */
    public static final String TARGET_CLASS = "com.android.quickstep.OplusBaseTouchInteractionService";
    public static final String TARGET_METHOD = "canRespondGesture";
    /** Exact descriptor measured via dexdump: (MotionEvent) boolean. */
    public static final String TARGET_DESCRIPTOR = "(Landroid/view/MotionEvent;)Z";

    /**
     * Registration marker kept as a single literal (not built from the constants) so
     * the exact expected log line is grep-able in the source itself.
     */
    private static final String REGISTERED_MARKER =
            "HOOK_REGISTERED com.android.quickstep.OplusBaseTouchInteractionService#canRespondGesture";

    /** {@code pass} lines per process: enough to see the geometry, never enough to bury a veto. */
    private static final int PASS_LOG_LIMIT = 2000;
    /** {@code skip armed=false} lines per process; an independent allowance. */
    private static final int SKIP_LOG_LIMIT = 20;

    /** Process-wide {@code pass} line budget; the veto and fail-open lines are never throttled. */
    private static final AtomicInteger PASS_LOGGED = new AtomicInteger();
    /** Process-wide {@code skip armed=false} line budget. */
    private static final AtomicInteger SKIP_LOGGED = new AtomicInteger();
    /** Monotone sequence stamped on every logged {@code pass} line, starting at 1. */
    private static final AtomicInteger SEQ = new AtomicInteger();

    private LauncherGestureAdmissionHooker() {}

    public static void onPackageLoaded(XposedModule module, PackageLoadedParam param) {
        try {
            log(module, 4, "MODULE_READY launcher-admission");
            if (!HookPolicy.ENABLED) {
                log(module, 4, "HOOK_DISABLED");
                return;
            }
            Class<?> target = Class.forName(TARGET_CLASS, false, param.getDefaultClassLoader());
            Method method = findTarget(target);
            if (method == null) {
                log(module, 4, "NO_MATCH method-not-found " + TARGET_CLASS + "#" + TARGET_METHOD);
                return;
            }
            String descriptor = HookPolicy.buildDescriptor(method);
            if (!TARGET_DESCRIPTOR.equals(descriptor)) {
                log(module, 4, "NO_MATCH descriptor " + descriptor);
                return;
            }
            // The target is private; libxposed does not require this, but it costs
            // nothing and rules out a visibility-only registration failure.
            method.setAccessible(true);
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> handle(chain, module));
            log(module, 4, REGISTERED_MARKER);
            logMetrics(module);
            GestureConfigClient.init();
            GestureConfigClient.reportStatus(module, "HOOK_REGISTERED",
                    "launcher-" + TARGET_METHOD);
        } catch (Throwable failure) {
            log(module, 6, "HOOK_DISABLED", failure);
        }
    }

    private static Object handle(XposedInterface.Chain chain, XposedModule module) throws Throwable {
        String stage = "arg";
        MotionEvent event = null;
        float x = 0f;
        float y = 0f;
        int action = -1;
        int width = 0;
        int height = 0;
        int bandPx = 0;
        try {
            event = (MotionEvent) chain.getArg(0);
            stage = "armed";
            if (!armed()) {
                // A diagnostic line, not an acceptance marker: it recurs in every
                // logcat window so an "alive" observation cannot be mistaken for a
                // hook that never got registered.
                logSkip(module, "CORNER-NO-RESPOND skip armed=false reason=not-armed");
                return chain.proceed();
            }
            stage = "event";
            if (event == null) return chain.proceed();
            stage = "coords";
            // Deliberately no action filter: the only call site of the target sits in
            // the ACTION_DOWN branch, so a future OEM move to MOVE shows up in the
            // pass log's action field instead of silently disabling the veto.
            x = event.getX();
            y = event.getY();
            action = event.getActionMasked();
            stage = "metrics";
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
            bandPx = CornerRBandPolicy.cornerBandPx(CornerRBandPolicy.CORNER_BAND_DP, metrics.density);
            stage = "policy";
            // Both bands share the same geometry read above; neither call re-reads metrics.
            final boolean inRight = CornerRBandPolicy.inRCornerBand(
                    x, y, width, height, SideGesturePolicy.BOTTOM_STRIP_H, bandPx);
            final boolean inLeft = CornerRBandPolicy.inLCornerBand(
                    x, y, width, height, SideGesturePolicy.BOTTOM_STRIP_H, bandPx);
            final boolean veto = inRight || inLeft;
            // The two bands never overlap on this geometry (300 < 780), so the side is
            // unambiguous; if a tiny display ever made them overlap, "L first" is fixed.
            final String side = inLeft ? "L" : (inRight ? "R" : "-");
            // The veto decision and its log happen before proceed() so that a veto
            // line always means "the predicate said yes", never "the override ran".
            if (veto) {
                log(module, 4, "CORNER-NO-RESPOND veto side=" + side + " x=" + x + " y=" + y
                        + " w=" + width + " h=" + height + " band=" + bandPx);
            } else {
                logPass(module, "CORNER-NO-RESPOND pass x=" + x + " y=" + y
                        + " action=" + action + " band=" + bandPx);
            }
            stage = "proceed";
            Object result = chain.proceed();     // the only proceed() on this path
            if (veto) return Boolean.FALSE;
            return result;
        } catch (Throwable failure) {
            // Fail open in a single direction: answer "do not veto". Never call
            // proceed() here — that would be a second invocation with side effects.
            log(module, 4, "CORNER-NO-RESPOND fail-open x=" + x + " y=" + y
                    + " reason=" + stage + ":" + failure, failure);
            return Boolean.TRUE;
        }
    }

    /** Only a master + barOnly-enabled config arms the R-corner veto. */
    private static boolean armed() {
        return GestureConfigClient.isMasterEnabled()
                && GestureConfigClient.isBarOnlyEnabled();
    }

    private static Method findTarget(Class<?> target) {
        try {
            return target.getDeclaredMethod(TARGET_METHOD, MotionEvent.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Probe-only: prints the metrics this process will judge touches against, once
     * per registration. Logged outside the armed guard on purpose, so an unarmed
     * install still proves which display geometry the decision would use.
     */
    private static void logMetrics(XposedModule module) {
        try {
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            log(module, 4, "CORNER-NO-RESPOND metrics w=" + metrics.widthPixels
                    + " h=" + metrics.heightPixels + " density=" + metrics.density);
        } catch (Throwable ignored) {
            // Probe logging must never disturb hook registration.
        }
    }

    private static void logPass(XposedModule module, String message) {
        if (PASS_LOGGED.getAndIncrement() >= PASS_LOG_LIMIT) return;
        log(module, 4, message + " seq=" + SEQ.incrementAndGet());
    }

    private static void logSkip(XposedModule module, String message) {
        if (SKIP_LOGGED.getAndIncrement() >= SKIP_LOG_LIMIT) return;
        log(module, 4, message);
    }

    private static void log(XposedModule module, int priority, String message) {
        try {
            module.log(priority, TAG, message);
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
    }

    private static void log(XposedModule module, int priority, String message, Throwable failure) {
        try {
            module.log(priority, TAG, message, failure);
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
    }
}
