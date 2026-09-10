package com.cos.lspit.gesture.hook;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import com.cos.lspit.gesture.config.HookPolicy;
import com.cos.lspit.gesture.config.SideGesturePolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;

/**
 * Policy-gated around-hook on the proven Oplus side-back gate
 * {@code SideGestureDetector#onMotionEventImpl(MotionEvent)V} inside SystemUI.
 *
 * <p>The hook NEVER blanket-vetoes. For every {@code ACTION_DOWN} the
 * {@link SideGesturePolicy} decides whether the event is a touchscreen-finger
 * side-edge DOWN that would start gesture Back; only then is the original
 * method skipped (the veto), so {@code mAllowGesture} is never armed and
 * {@code dispatchToBackAnimation} / final Back never runs for that touch.
 * Status-bar, bottom-strip (Home/Recents / nav-handle animation),
 * non-touchscreen, non-finger and non-DOWN events reach the original method
 * body exactly as before.
 */
public final class SideBackHooker {
    private static final String TAG = "COS16-Gesture";

    /**
     * Set once the background hash check has confirmed the expected SystemUI
     * build. Until then the interceptor passes every event straight through
     * to the original method (fail-closed), mirroring the volatile gate
     * pattern of {@code NavigationHandleHooks.SystemHide.sConvertedHideActive}.
     */
    private static volatile boolean hashVerified;

    private SideBackHooker() {}

    public static void onPackageLoaded(XposedModule module, PackageLoadedParam param) {
        try {
            log(module, 4, "MODULE_READY");
            log(module, 4, "HOST_API " + module.getApiVersion());
            if (!HookPolicy.ENABLED) {
                log(module, 4, "HOOK_DISABLED");
                GestureConfigClient.reportStatus(module, "HOOK_DISABLED", "policy-disabled");
                return;
            }
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> target;
            try {
                target = Class.forName(HookPolicy.TARGET_CLASS, false, loader);
            } catch (Throwable ignored) {
                log(module, 4, "NO_MATCH");
                GestureConfigClient.reportStatus(module, "NO_MATCH", "class-not-found");
                return;
            }
            Method method = findTarget(target, loader);
            if (method == null) {
                log(module, 4, "NO_MATCH");
                GestureConfigClient.reportStatus(module, "NO_MATCH", "method-not-found");
                return;
            }
            // DESCRIPTOR_MATCH guard: wrong parameter list or return type (e.g. a
            // "Z" AOSP-style method) must fail closed to NO_MATCH, never a hook.
            if (!HookPolicy.descriptorMatches(method)) {
                log(module, 4, "NO_MATCH");
                GestureConfigClient.reportStatus(module, "NO_MATCH", "descriptor-mismatch");
                return;
            }
            // Register first, verify second: hashing a multi-hundred-MB APK
            // inline would stall the SystemUI main thread at startup. The
            // interceptor stays a pure passthrough until the background
            // check below flips hashVerified.
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> handle(chain, module));
            log(module, 4, "HOOK_PENDING_VERIFY " + HookPolicy.TARGET_CLASS + "#"
                    + HookPolicy.TARGET_METHOD + HookPolicy.TARGET_DESCRIPTOR);
            GestureConfigClient.reportStatus(module, "HOOK_PENDING_VERIFY",
                    HookPolicy.TARGET_CLASS + "#" + HookPolicy.TARGET_METHOD);
            Thread verifier = new Thread(new Runnable() {
                @Override
                public void run() {
                    verifyHash(module, param);
                }
            }, "cos16-sideback-verify");
            verifier.setDaemon(true);
            verifier.start();
        } catch (Throwable failure) {
            try {
                module.log(6, TAG, "HOOK_DISABLED", failure);
            } catch (Throwable ignored) {
                // Logging must never make SystemUI fail.
            }
        }
    }

    /**
     * Background hash verification. Any failure keeps {@code hashVerified}
     * false, which means a permanent passthrough (fail-closed). Config init
     * and the HOOK_REGISTERED report live in the success branch only: on an
     * unverified ROM the fail-closed config defaults must stay armed, per
     * the "never act on an unproven build" contract in HookPolicy.
     */
    private static void verifyHash(XposedModule module, PackageLoadedParam param) {
        try {
            if (!hashMatches(param)) {
                log(module, 4, "HASH_MISMATCH");
                GestureConfigClient.reportStatus(module, "HASH_MISMATCH", "systemui-sha256");
                return;
            }
            hashVerified = true;
            log(module, 4, "HOOK_REGISTERED " + HookPolicy.TARGET_CLASS + "#"
                    + HookPolicy.TARGET_METHOD + HookPolicy.TARGET_DESCRIPTOR);
            GestureConfigClient.init();
            GestureConfigClient.reportStatus(module, "HOOK_REGISTERED",
                    HookPolicy.TARGET_CLASS + "#" + HookPolicy.TARGET_METHOD);
        } catch (Throwable failure) {
            log(module, 6, "HASH_VERIFY_FAILED", failure);
            GestureConfigClient.reportStatus(module, "HASH_VERIFY_FAILED", "verify-thread");
        }
    }

    /**
     * Interceptor body. A veto skips the original method by returning its void value
     * ({@code null}) without calling {@code chain.proceed()}; every other event is
     * forwarded unchanged via {@code chain.proceed()}.
     */
    private static Object handle(io.github.libxposed.api.XposedInterface.Chain chain,
            XposedModule module) throws Throwable {
        if (!hashVerified) {
            // Unverified build: never veto and never skip - a return without
            // chain.proceed() would silently drop the original method and kill
            // side Back entirely.
            return chain.proceed();
        }
        Object argument = null;
        try {
            argument = chain.getArg(0);
        } catch (Throwable ignored) {
            // No argument readable -> let the original method run untouched.
        }
        if (argument instanceof MotionEvent) {
            MotionEvent event = (MotionEvent) argument;
            try {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (shouldVeto(event)) {
                        log(module, 4, "VETO side ACTION_DOWN x=" + event.getX(0)
                                + " y=" + event.getY(0) + " source=" + event.getSource()
                                + " toolType=" + event.getToolType(0));
                        return null; // skip original onMotionEventImpl -> no side Back
                    }
                    if (shouldVetoBarOnly(event)) {
                        log(module, 4, "VETO barOnly down x=" + Math.round(event.getX(0))
                                + " y=" + Math.round(event.getY(0))
                                + " barOnly=" + GestureConfigClient.isBarOnlyEnabled());
                        return null; // skip original -> no bottom Home/Recents off the bar
                    }
                }
                // Non-DOWN events pass through silently (no per-move log flood).
            } catch (Throwable failure) {
                log(module, 6, "PASS veto-eval-failed", failure);
            }
        }
        return chain.proceed();
    }

    /** Decides veto using live default-display metrics; unknown bounds fail open (no veto). */
    private static boolean shouldVeto(MotionEvent event) {
        int width;
        int height;
        try {
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        } catch (Throwable ignored) {
            return false; // cannot bound the display -> never drop an event blindly
        }
        boolean vetoLeft = GestureConfigClient.isMasterEnabled()
                && GestureConfigClient.isLeftEnabled();
        boolean vetoRight = GestureConfigClient.isMasterEnabled()
                && GestureConfigClient.isRightEnabled();
        return SideGesturePolicy.shouldVeto(
                event.getActionMasked(),
                event.getSource(),
                event.getToolType(0),
                event.getX(0),
                event.getY(0),
                width,
                height,
                vetoLeft,
                vetoRight);
    }

    /** Bar-only gate: veto a bottom-strip DOWN that started off the hint bar. */
    private static boolean shouldVetoBarOnly(MotionEvent event) {
        try {
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            Integer dp = GestureConfigClient.getBarWidthDp();
            int barDp = dp == null ? SideGesturePolicy.DEFAULT_BAR_DP : dp;
            return SideGesturePolicy.shouldVetoBarOnly(
                    event.getActionMasked(),
                    event.getSource(),
                    event.getToolType(0),
                    event.getX(0),
                    event.getY(0),
                    metrics.widthPixels,
                    metrics.heightPixels,
                    GestureConfigClient.isMasterEnabled()
                            && GestureConfigClient.isBarOnlyEnabled(),
                    barDp,
                    metrics.density);
        } catch (Throwable ignored) {
            return false; // cannot bound the display -> never drop an event blindly
        }
    }

    private static void log(XposedModule module, int priority, String message) {
        try {
            module.log(priority, TAG, message);
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
    }

    private static void log(XposedModule module, int priority, String message,
            Throwable failure) {
        try {
            module.log(priority, TAG, message, failure);
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
    }

    /** Resolves the method by name + MotionEvent parameter; return type checked via descriptor. */
    private static Method findTarget(Class<?> target, ClassLoader loader) {
        try {
            Class<?> motionEvent = Class.forName("android.view.MotionEvent", false, loader);
            return target.getDeclaredMethod(HookPolicy.TARGET_METHOD, motionEvent);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hashMatches(PackageLoadedParam param) {
        if (HookPolicy.EXPECTED_SYSTEMUI_SHA256.isEmpty()) return true;
        try (InputStream input = new java.io.FileInputStream(
                param.getApplicationInfo().sourceDir)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
            return HookPolicy.EXPECTED_SYSTEMUI_SHA256.equalsIgnoreCase(toHex(digest.digest()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
