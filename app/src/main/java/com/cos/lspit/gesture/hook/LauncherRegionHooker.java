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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    /** Probe-only cap: REGION_DUMP fires at most this many times per region name. */
    private static final int REGION_DUMP_LIMIT = 3;
    /** Per-name REGION_DUMP counter; a plain map, never a time-based throttle. */
    private static final Map<String, Integer> sRegionDumpCounts = new HashMap<>();

    private static ClassLoader moduleClassLoader;
    /**
     * Shared main-looper handler. Must be a single instance: removeCallbacks
     * only cancels callbacks queued on the same handler, so a fresh Handler
     * per call silently broke the debounce.
     */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    /** Cached singleton handles for the launcher region refresh; valid for the process lifetime. */
    private static volatile Field sInstanceField;
    private static volatile Method sRefreshMethod;
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
                Object listArg = null;
                try {
                    listArg = chain.getArg(1);
                } catch (Throwable ignored) {
                    // Probe logging must never disturb region registration.
                }
                List<?> regionList = listArg instanceof List ? (List<?>) listArg : null;
                boolean armed = armed();
                try {
                    dumpRegion(module, regionName, regionList, armed);
                } catch (Throwable ignored) {
                    // Probe logging must never disturb region registration.
                }
                if (regionName.startsWith(REGION_PREFIX) && armed && regionList != null) {
                    try {
                        narrowRegion(module, regionName, regionList);
                    } catch (Throwable ignored) {
                        // A narrowing failure must never block the original registration.
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

    /**
     * C4: narrows the whole registered region list (never a single rect).
     *
     * <p>The launcher list may hold non-{@code RectF} elements; they are passed to the
     * policy as {@code null} placeholders and are never written back, so the list keeps
     * its length and order (no additions, removals or de-duplication).
     */
    private static void narrowRegion(XposedModule module, String regionName, List<?> regionList) {
        android.util.DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        int size = regionList.size();
        float[][] bands = new float[size][];
        for (int i = 0; i < size; i++) {
            Object element = regionList.get(i);
            if (element instanceof RectF) {
                RectF rect = (RectF) element;
                bands[i] = new float[] {rect.left, rect.top, rect.right, rect.bottom};
            }
        }
        SideGesturePolicy.LauncherBandResult result = SideGesturePolicy.narrowBandList(
                bands, barWidthDp(), metrics.density, metrics.widthPixels, metrics.heightPixels);
        try {
            if (!result.applied) {
                log(module, 4, "REGION_SKIP name=" + regionName + " reason=no-sanity-main-band");
                return;
            }
        } catch (Throwable ignored) {
            // Probe logging must never disturb region registration.
        }
        int written = 0;
        int sizeOut = result.rects == null ? 0 : result.rects.length;
        for (int i = 0; i < sizeOut && i < size; i++) {
            float[] band = result.rects[i];
            if (band == null) continue;                       // null placeholder: skipped
            Object element = regionList.get(i);
            if (!(element instanceof RectF)) continue;
            RectF rect = (RectF) element;
            float[] orig = bands[i];
            rect.left = band[0];
            rect.top = band[1];
            rect.right = band[2];
            rect.bottom = band[3];
            written++;
            try {
                logRect(module, regionName, orig, band, metrics);
            } catch (Throwable ignored) {
                // Probe logging must never change the narrowing decision above.
            }
        }
        try {
            log(module, 4, "REGION_NARROW " + regionName + " rects=" + written
                    + " folded=" + result.folded + " narrowed=" + result.narrowed
                    + " barDp=" + barWidthDp() + " half="
                    + SideGesturePolicy.barHalfWidthPx(barWidthDp(), metrics.density));
            if (written == 0) {
                log(module, 4, "REGION_SKIP name=" + regionName + " reason=no-rect-written");
            }
        } catch (Throwable ignored) {
            // Probe logging must never disturb region registration.
        }
    }

    /**
     * Probe-only: {@code REGION_NARROWED} for a rect that stayed inside the band and
     * {@code REGION_COLLAPSE} for one that sat entirely outside it (the C4 rule-4 fold
     * candidates; after Task 3 they are folded into the main band instead of being
     * re-centred on their own width).
     */
    private static void logRect(XposedModule module, String regionName, float[] orig,
            float[] now, android.util.DisplayMetrics metrics) {
        float half = SideGesturePolicy.barHalfWidthPx(barWidthDp(), metrics.density);
        float center = metrics.widthPixels / 2f;
        String origText = "[" + orig[0] + "," + orig[1] + "," + orig[2] + "," + orig[3] + "]";
        String nowText = "[" + now[0] + "," + now[1] + "," + now[2] + "," + now[3] + "]";
        if (orig[2] <= center - half || orig[0] >= center + half) {
            log(module, 4, "REGION_COLLAPSE name=" + regionName + " orig=" + origText
                    + " new=" + nowText + " usedW=" + (now[2] - now[0])
                    + " usedH=" + (now[3] - now[1]));
        } else {
            log(module, 4, "REGION_NARROWED name=" + regionName + " orig=" + origText
                    + " new=" + nowText);
        }
    }

    /** Probe-only rect list rendering: {@code [l,t,r,b|l,t,r,b]}. */
    private static String rectListText(List<?> regionList) {
        if (regionList == null) return "null";
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (Object element : regionList) {
            if (!first) out.append('|');
            first = false;
            if (element instanceof RectF) {
                RectF rect = (RectF) element;
                out.append(rect.left).append(',').append(rect.top).append(',')
                        .append(rect.right).append(',').append(rect.bottom);
            } else {
                out.append(element == null ? "null" : "?");
            }
        }
        return out.append(']').toString();
    }

    /**
     * Probe-only: logs the region list exactly as the launcher registered it, capped at
     * {@link #REGION_DUMP_LIMIT} lines per region name. Purely diagnostic; the list is
     * never modified and the narrowing decision below is unaffected.
     */
    private static void dumpRegion(XposedModule module, String regionName, List<?> regionList,
            boolean armed) {
        if (regionList == null) return;
        synchronized (sRegionDumpCounts) {
            Integer seen = sRegionDumpCounts.get(regionName);
            if (seen != null && seen >= REGION_DUMP_LIMIT) return;
            sRegionDumpCounts.put(regionName, seen == null ? 1 : seen + 1);
        }
        log(module, 4, "REGION_DUMP name=" + regionName + " armed=" + armed
                + " size=" + regionList.size() + " rects=" + rectListText(regionList));
    }

    /** Debounced re-registration trigger: launcher recomputes + re-registers the region. */
    private static void scheduleRegionRefresh() {
        try {
            MAIN_HANDLER.removeCallbacks(REGION_REFRESH);
            MAIN_HANDLER.postDelayed(REGION_REFRESH, REFRESH_DEBOUNCE_MS);
        } catch (Throwable failure) {
            Log.w(TAG, "REFRESH_SCHEDULE_FAILED " + failure);
        }
    }

    private static void refreshLauncherRegion() {
        try {
            if (moduleClassLoader == null) return;
            Field instanceField = sInstanceField;
            Method refresh = sRefreshMethod;
            if (instanceField == null || refresh == null) {
                Class<?> controller = Class.forName(NAV_CONTROLLER, false, moduleClassLoader);
                instanceField = controller.getField("INSTANCE");
                refresh = controller.getDeclaredMethod(NAV_REFRESH_METHOD);
                refresh.setAccessible(true);
                sInstanceField = instanceField;
                sRefreshMethod = refresh;
            }
            Object singleton = instanceField.get(null);
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
