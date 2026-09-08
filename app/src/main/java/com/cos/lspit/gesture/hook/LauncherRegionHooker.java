package com.cos.lspit.gesture.hook;

import android.content.res.Resources;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.cos.lspit.gesture.config.HookPolicy;
import com.cos.lspit.gesture.config.SideGesturePolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Launcher-side barOnly gate. The whole ColorOS bottom gesture region
 * (home/recents/task-switch) is registered by the launcher process
 * ({@code com.android.launcher}) through the Oplus window manager, so the
 * SystemUI veto can never stop those gestures. This hooker runs inside the
 * launcher and narrows the actual registered home-gesture region to the
 * user's hint-bar band: {@code android.view.OplusWindowManager#updateInvalidRegion}
 * receives the per-direction rect list client-side before it is marshalled to
 * the Oplus window manager; whenever {@code barOnly} is armed we shrink each
 * non-empty {@link RectF} horizontally around its own center to the
 * (custom-width) hint-bar band before the original method runs. Off-band
 * touches then never reach the launcher gesture detector at all.
 *
 * <p>The module config (barOnly / barWidthDp / master) is mirrored through
 * {@link GestureConfigClient} in this process; on a config change the hook
 * forces the launcher to re-register by invoking the no-arg
 * {@code NavigationController.updateTouchRegion()} on the Kotlin object
 * singleton, so a toggle applies live without restarting the launcher.
 */
public final class LauncherRegionHooker {
    private static final String TAG = "COS16-Gesture";

    /** Framework class + method ColorOS uses to (re)register invalid gesture regions. */
    public static final String TARGET_CLASS = "android.view.OplusWindowManager";
    public static final String TARGET_METHOD = "updateInvalidRegion";
    /** Exact descriptor measured via dexdump: (String, List, boolean, boolean, Bundle) boolean. */
    public static final String TARGET_DESCRIPTOR =
            "(Ljava/lang/String;Ljava/util/List;ZZLandroid/os/Bundle;)Z";
    /** Only home-gesture regions are narrowed; side/back regions pass through untouched. */
    private static final String REGION_PREFIX = "homegesture_";
    /** Kotlin object singleton holding the region re-computation entry point. */
    private static final String NAV_CONTROLLER = "com.oplus.quickstep.navigation.NavigationController";
    private static final String NAV_REFRESH_METHOD = "updateTouchRegion";
    private static final long REFRESH_DEBOUNCE_MS = 400L;

    private static ClassLoader moduleClassLoader;
    private static final Runnable REGION_REFRESH = new Runnable() {
        @Override
        public void run() {
            refreshLauncherRegion();
        }
    };

    private LauncherRegionHooker() {}

    public static void onPackageLoaded(XposedModule module, PackageLoadedParam param) {
        try {
            log(module, 4, "MODULE_READY launcher");
            if (!HookPolicy.ENABLED) {
                log(module, 4, "HOOK_DISABLED");
                return;
            }
            moduleClassLoader = param.getDefaultClassLoader();
            Class<?> target = Class.forName(TARGET_CLASS, false, moduleClassLoader);
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
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> handle(chain, module));
            log(module, 4, "HOOK_REGISTERED " + TARGET_CLASS + "#" + TARGET_METHOD);
            GestureConfigClient.init();
            GestureConfigClient.addConfigListener(LauncherRegionHooker::scheduleRegionRefresh);
            GestureConfigClient.reportStatus(module, "HOOK_REGISTERED", "launcher-" + TARGET_METHOD);
        } catch (Throwable failure) {
            log(module, 6, "HOOK_DISABLED", failure);
        }
    }

    private static Object handle(XposedInterface.Chain chain, XposedModule module) throws Throwable {
        try {
            Object nameArg = chain.getArg(0);
            if (nameArg instanceof String) {
                String regionName = (String) nameArg;
                if (regionName.startsWith(REGION_PREFIX) && armed()) {
                    Object listArg = chain.getArg(1);
                    if (listArg instanceof List) {
                        int narrowed = 0;
                        for (Object element : (List<?>) listArg) {
                            if (element instanceof RectF && narrow((RectF) element)) narrowed++;
                        }
                        log(module, 4, "REGION_NARROW " + regionName
                                + " rects=" + narrowed + " barDp=" + barWidthDp());
                    }
                }
            }
        } catch (Throwable failure) {
            log(module, 6, "REGION_NARROW_FAILED", failure);
        }
        return chain.proceed();
    }

    /** Only a master + barOnly-enabled config narrows the launcher region. */
    private static boolean armed() {
        return GestureConfigClient.isMasterEnabled()
                && GestureConfigClient.isBarOnlyEnabled();
    }

    private static int barWidthDp() {
        Integer dp = GestureConfigClient.getBarWidthDp();
        return dp == null ? SideGesturePolicy.DEFAULT_BAR_DP : dp;
    }

    /** Narrows a home-gesture rect horizontally to the hint-bar band; true when applied. */
    private static boolean narrow(RectF rect) {
        try {
            float density = Resources.getSystem().getDisplayMetrics().density;
            float[] band = {rect.left, rect.top, rect.right, rect.bottom};
            if (!SideGesturePolicy.narrowBandToBar(band, barWidthDp(), density)) return false;
            rect.left = band[0];
            rect.right = band[2];
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Debounced re-registration trigger: launcher recomputes + re-registers the region. */
    private static void scheduleRegionRefresh() {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.removeCallbacks(REGION_REFRESH);
            handler.postDelayed(REGION_REFRESH, REFRESH_DEBOUNCE_MS);
        } catch (Throwable failure) {
            Log.w(TAG, "REFRESH_SCHEDULE_FAILED " + failure);
        }
    }

    private static void refreshLauncherRegion() {
        try {
            if (moduleClassLoader == null) return;
            Class<?> controller = Class.forName(NAV_CONTROLLER, false, moduleClassLoader);
            Field instanceField = controller.getField("INSTANCE");
            Object singleton = instanceField.get(null);
            Method refresh = controller.getDeclaredMethod(NAV_REFRESH_METHOD);
            refresh.setAccessible(true);
            refresh.invoke(singleton);
            Log.i(TAG, "REGION_REFRESH invoked " + NAV_CONTROLLER + "#" + NAV_REFRESH_METHOD);
        } catch (Throwable failure) {
            Log.w(TAG, "REGION_REFRESH_FAILED " + failure);
        }
    }

    private static Method findTarget(Class<?> target) {
        try {
            return target.getDeclaredMethod(TARGET_METHOD, String.class, List.class,
                    boolean.class, boolean.class, Bundle.class);
        } catch (Throwable ignored) {
            return null;
        }
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
