package com.cos.lspit.gesture.hook;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Coordinator hook on the Oplus navigation handle (the gesture hint bar).
 *
 * <p>One entry point ({@link #register}) owns every hook site on this view so
 * the mBack touch handler, the custom-width writer and the hint-bar
 * visibility enforcer can never fight over the same method. Ported from
 * rikumi/coloros-mod (MIT) and translated from classic Xposed to the
 * libxposed API used by this module.
 *
 * <p>Failure is self-limiting: every stage is wrapped so a missing class or
 * method logs {@code NO_MATCH} and never crashes SystemUI; a miss on any
 * apply call is logged and the original method body still runs via
 * {@code chain.proceed()}.
 */
public final class NavigationHandleHooks {
    private static final String TAG = "COS16-Gesture";
    private static final String HANDLE_CLASS =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle";
    private static final String HANDLE_METHOD = "handleValidTouchEvent";
    /** Width clamp range (dp) shared with the UI slider. */
    private static final int BAR_WIDTH_MIN_DP = 40;
    private static final int BAR_WIDTH_MAX_DP = 160;
    /** Swipe that abandons mBack (px threshold = dp x density), from coloros-mod. */
    private static final int MBACK_SWIPE_DP = 20;
    /** Extra band around the bar where mBack still responds, in dp. */
    private static final float MBACK_BAND_PADDING_DP = 4f;
    private static final long MBACK_RIPPLE_HIDE_DELAY_MS = 280L;
    /** Secure setting the module UI writes; 1 = hidden. The real ColorOS user toggle
     *  is gesture_side_hide_bar_prevention_enable; hide_gesture_bar_enable was a
     *  sibling key that does not gate the bar's touch routing. The module writes the
     *  real key, observes it for visibility enforcement, and additionally converts
     *  SystemUI's true-hide (window alpha 0) into pixel-only hide so mBack survives. */
    private static final String SETTING_HIDE_GESTURE_BAR = "gesture_side_hide_bar_prevention_enable";

    private static volatile XposedModule sModule;
    private static volatile View sLiveHandle;
    private static volatile boolean sObserverAdded;
    private static volatile boolean sListenerAdded;
    /** All live handles; appearance is re-applied to every instance, not just the last. */
    private static final java.util.Set<View> sHandles =
            java.util.Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    /** Orientation per live handle, recorded by the setVertical hook. */
    private static final java.util.Map<View, Boolean> sVertical =
            new WeakHashMap<View, Boolean>();

    private NavigationHandleHooks() {}

    private static int handleCount() {
        synchronized (sHandles) {
            return sHandles.size();
        }
    }

    /** Applies appearance + mBack surface to every currently live handle. */
    private static void applyAllHandles() {
        View[] snapshot;
        synchronized (sHandles) {
            snapshot = sHandles.toArray(new View[0]);
        }
        for (View v : snapshot) {
            if (v == null) continue;
            try {
                Appearance.applyAll(v);
            } catch (Throwable ignored) {
            }
            try {
                MBack.syncSurface(v);
            } catch (Throwable ignored) {
            }
            try {
                TapShield.sync(v);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Applies only the hint-bar visibility rule to every live handle. */
    private static void applyVisibilityHandles() {
        View[] snapshot;
        synchronized (sHandles) {
            snapshot = sHandles.toArray(new View[0]);
        }
        for (View v : snapshot) {
            if (v == null) continue;
            try {
                Appearance.applyVisibility(v);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Walks up the parent chain looking for a NavigationBarView and asks its
     * panel interactor whether the notification shade / quick settings is
     * fully open. Failures (no interactor, missing class) default to false,
     * meaning the consumer treats itself as still needed.
     */
    private static boolean isShadeExpanded(View view) {
        View current = view;
        while (current != null) {
            if (current.getClass().getName().endsWith("NavigationBarView")) {
                try {
                    Object interactor = getObjectField(current, "mPanelExpansionInteractor");
                    if (interactor == null) return false;
                    Method full = findMethod(interactor.getClass(), "isFullyExpanded");
                    Method panel = findMethod(interactor.getClass(), "isPanelExpanded");
                    return (full != null && Boolean.TRUE.equals(full.invoke(interactor)))
                            || (panel != null && Boolean.TRUE.equals(panel.invoke(interactor)));
                } catch (Throwable failure) {
                    return false;
                }
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    public static void register(XposedModule module, PackageLoadedParam param) {
        sModule = module;
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> handle;
            try {
                handle = Class.forName(HANDLE_CLASS, false, loader);
            } catch (Throwable missing) {
                report(module, "NO_MATCH", "class-not-found " + HANDLE_CLASS);
                return;
            }
            hookTouch(module, handle);
            hookAttach(module, handle);
            hookLayout(module, handle);
            hookDetach(module, handle);
            hookSetVertical(module, handle);
            hookConfigListener(module);
            hookSecureSettingObserver(module);
            TapShield.register(module, loader);
            HiddenBar.register(module, handle);
            SystemHide.register(module, loader);
            report(module, "HOOK_REGISTERED", HANDLE_CLASS + " coordinator");
        } catch (Throwable failure) {
            try {
                module.log(6, TAG, "HOOK_DISABLED", failure);
            } catch (Throwable ignored) {
                // Logging must never make SystemUI fail.
            }
        }
    }

    private static void hookTouch(XposedModule module, Class<?> handle) {
        try {
            Class<?> motionEvent =
                    Class.forName("android.view.MotionEvent", false, handle.getClassLoader());
            Method method = handle.getDeclaredMethod(HANDLE_METHOD, motionEvent);
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object receiver;
                        try {
                            receiver = chain.getThisObject();
                        } catch (Throwable ignored) {
                            return chain.proceed();
                        }
                        Object arg;
                        try {
                            arg = chain.getArg(0);
                        } catch (Throwable ignored) {
                            arg = null;
                        }
                        if (receiver instanceof View
                                && arg instanceof MotionEvent) {
                            View rv = (View) receiver;
                            MotionEvent me = (MotionEvent) arg;
                            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                log(5, "TOUCHDOWN cls=" + rv.getClass().getSimpleName()
                                        + " x=" + Math.round(me.getX())
                                        + " rawX=" + Math.round(me.getRawX())
                                        + " y=" + Math.round(me.getY()));
                            }
                            if (GestureConfigClient.isMbackEnabled()
                                    && MBack.onTouch(rv, me)) {
                                return null; // swallowed: handled inside the mBack band
                            }
                        }
                        return chain.proceed();
                    });
            log(module, 4, "HOOK_OK touch " + HANDLE_METHOD);
        } catch (Throwable failure) {
            report(module, "NO_MATCH", "touch " + HANDLE_METHOD + " " + failure);
        }
    }

    // Lifecycle callbacks are hooked declared-only on the handle class on purpose:
    // hooking inherited android.view.View methods would fire for every View in the
    // process and recurse (addView -> dispatchAttachedToWindow -> re-enter the hook),
    // overflowing the stack. Each site is independent so an old ROM that does not
    // override a method simply skips it.

    private static void hookAttach(XposedModule module, Class<?> handle) {
        try {
            Method method = handle.getDeclaredMethod("onAttachedToWindow");
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object thiz = chain.getThisObject();
                        if (thiz instanceof View) {
                            View v = (View) thiz;
                            sLiveHandle = v;
                            synchronized (sHandles) {
                                sHandles.add(v);
                            }
                            try {
                                Appearance.applyAll(v);
                            } catch (Throwable ignored) {
                            }
                            try {
                                MBack.syncSurface(v);
                            } catch (Throwable ignored) {
                            }
                            try {
                                TapShield.sync(v);
                            } catch (Throwable ignored) {
                            }
                            log(module, 4, "HANDLE_ATTACHED vis=" + v.getVisibility()
                                    + " id=" + System.identityHashCode(v)
                                    + " count=" + handleCount());
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
            report(module, "NO_MATCH", "attach hook skipped");
        }
    }

    private static void hookLayout(XposedModule module, Class<?> handle) {
        try {
            Method method = handle.getDeclaredMethod(
                    "onLayout", boolean.class, int.class, int.class, int.class, int.class);
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object thiz = chain.getThisObject();
                        if (thiz instanceof View) {
                            try {
                                MBack.positionSurface((View) thiz);
                            } catch (Throwable ignored) {
                            }
                            try {
                                TapShield.position((View) thiz);
                            } catch (Throwable ignored) {
                            }
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
            report(module, "NO_MATCH", "layout hook skipped");
        }
    }

    private static void hookDetach(XposedModule module, Class<?> handle) {
        try {
            Method method = handle.getDeclaredMethod("onDetachedFromWindow");
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object thiz = chain.getThisObject();
                        if (thiz instanceof View) {
                            View v = (View) thiz;
                            if (thiz == sLiveHandle) sLiveHandle = null;
                            synchronized (sHandles) {
                                sHandles.remove(v);
                            }
                            synchronized (sVertical) {
                                sVertical.remove(v);
                            }
                            try {
                                MBack.detach((View) thiz);
                            } catch (Throwable ignored) {
                            }
                            try {
                                TapShield.remove(v);
                            } catch (Throwable ignored) {
                            }
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
            report(module, "NO_MATCH", "detach hook skipped");
        }
    }

    private static void hookSetVertical(XposedModule module, Class<?> handle) {
        try {
            Method method = handle.getDeclaredMethod("setVertical", boolean.class);
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object thiz = chain.getThisObject();
                        if (thiz instanceof View) {
                            View v = (View) thiz;
                            try {
                                Object arg = chain.getArg(0);
                                if (arg instanceof Boolean) {
                                    synchronized (sVertical) {
                                        sVertical.put(v, (Boolean) arg);
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                            try {
                                Appearance.applyAll(v);
                            } catch (Throwable ignored) {
                            }
                        }
                        return result;
                    });
        } catch (Throwable ignored) {
            report(module, "NO_MATCH", "setVertical hook skipped");
        }
    }

    private static void hookConfigListener(XposedModule module) {
        if (sListenerAdded) return;
        sListenerAdded = true;
        GestureConfigClient.addConfigListener(() -> applyAllHandles());
    }

    private static void hookSecureSettingObserver(XposedModule module) {
        if (sObserverAdded) return;
        sObserverAdded = true;
        GestureConfigClient.postOnMain(() -> {
            try {
                Context ctx = GestureConfigClient.systemContext();
                if (ctx == null) return;
                ContentResolver resolver = ctx.getContentResolver();
                resolver.registerContentObserver(
                        Settings.Secure.getUriFor(SETTING_HIDE_GESTURE_BAR), false,
                        new ContentObserver(new Handler(Looper.getMainLooper())) {
                            @Override
                            public void onChange(boolean selfChange) {
                                applyVisibilityHandles();
                            }
                        });
                log(module, 4, "HOOK_OK secure-observer " + SETTING_HIDE_GESTURE_BAR);
            } catch (Throwable ignored) {
                report(module, "NO_MATCH", "secure-observer skipped");
            }
        });
    }

    // ---------------------------------------------------------------------
    // Hint-bar appearance: custom width + show/hide enforcement at view level.
    // ---------------------------------------------------------------------

    static final class TapShield {
        private static final String SURFACE_FIELD = "cos16_tap_shield";
        private static boolean hooksRegistered;
        private static final WeakHashMap<View, GestureBlockSurface> surfaces =
                new WeakHashMap<View, GestureBlockSurface>();

        static void register(final XposedModule module, ClassLoader loader) {
            if (hooksRegistered) return;
            hooksRegistered = true;
            try {
                Class<?> info = Class.forName(
                        "android.view.ViewTreeObserver$InternalInsetsInfo", false, loader);
                Class<?> listener = Class.forName(
                        "com.android.systemui.navigationbar.views.NavigationBar$$ExternalSyntheticLambda10",
                        false, loader);
                Method method = listener.getDeclaredMethod("onComputeInternalInsets", info);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object lambda = chain.getThisObject();
                                Object navBar = getObjectField(lambda, "f$0");
                                Object viewObject = getObjectField(navBar, "mView");
                                if (viewObject instanceof View) {
                                    applyInsets((View) viewObject, chain.getArg(0));
                                }
                            } catch (Throwable failure) {
                                log(5, "TAPSHIELD_INSETS_FAILED " + failure);
                            }
                            return result;
                        });
                log(module, 4, "TAPSHIELD_HOOK_OK insets");
            } catch (Throwable failure) {
                log(module, 5, "TAPSHIELD_NO_MATCH insets " + failure);
            }
            try {
                Class<?> nav = Class.forName(
                        "com.oplusos.systemui.navigationbar.OplusNavigationBarView",
                        false, loader);
                Method method = nav.getDeclaredMethod("updateSlippery");
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object value = chain.getThisObject();
                                if (value instanceof View) {
                                    View handle = findHandleInTree((View) value);
                                    if (handle != null) {
                                        sync(handle);
                                        ((View) value).requestLayout();
                                    }
                                }
                            } catch (Throwable failure) {
                                log(5, "TAPSHIELD_SHADE_SYNC_FAILED " + failure);
                            }
                            return result;
                        });
            } catch (Throwable failure) {
                log(module, 5, "TAPSHIELD_NO_MATCH slippery " + failure);
            }
        }

        static void sync(View handle) {
            GestureBlockSurface surface;
            synchronized (surfaces) {
                surface = surfaces.get(handle);
            }
            if (!active(handle)) {
                if (surface != null) remove(handle);
                return;
            }
            if (surface == null) {
                ViewGroup host = MBack.findHost(handle);
                if (host == null) return;
                surface = new GestureBlockSurface(handle, host);
                surface.setClickable(true);
                final GestureBlockSurface target = surface;
                surface.setOnTouchListener((view, event) -> {
                    if (GestureConfigClient.isMbackEnabled()) {
                        target.dispatchToSource(event);
                    }
                    return true;
                });
                host.addView(surface, 0, new FrameLayout.LayoutParams(1, 1));
                synchronized (surfaces) {
                    surfaces.put(handle, surface);
                }
                log(4, "TAPSHIELD_ON");
            }
            surface.update();
        }

        static void position(View handle) {
            synchronized (surfaces) {
                GestureBlockSurface surface = surfaces.get(handle);
                if (surface != null) surface.update();
            }
        }

        static void remove(View handle) {
            GestureBlockSurface surface;
            synchronized (surfaces) {
                surface = surfaces.remove(handle);
            }
            if (surface == null) return;
            ViewParent parent = surface.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(surface);
            log(4, "TAPSHIELD_OFF");
        }

        private static boolean active(View handle) {
            return GestureConfigClient.isMasterEnabled()
                    && GestureConfigClient.isHintTapShieldEnabled()
                    && !isShadeExpanded(handle);
        }

        private static void applyInsets(View navView, Object info) {
            if (!active(navView)) return;
            View handle = findHandleInTree(navView);
            if (handle == null || !(getObjectField(info, "touchableRegion") instanceof android.graphics.Region)) {
                return;
            }
            Rect rect = barRect(handle, navView);
            android.graphics.Region region =
                    (android.graphics.Region) getObjectField(info, "touchableRegion");
            region.set(rect);
            Method setter = findMethod(info.getClass(), "setTouchableInsets", int.class);
            if (setter != null) {
                try {
                    setter.invoke(info, 3);
                } catch (Throwable failure) {
                    log(5, "TAPSHIELD_INSETS_SET_FAILED " + failure);
                }
            }
        }

        private static Rect barRect(View handle, View host) {
            int[] handleLocation = new int[2];
            int[] hostLocation = new int[2];
            handle.getLocationInWindow(handleLocation);
            host.getLocationInWindow(hostLocation);
            int density = Math.max(1, Math.round(density(handle)));
            int pad = Math.round(MBACK_BAND_PADDING_DP * density);
            int width = handle.getWidth() + pad * 2;
            int barHeight = Math.max(1, getIntField(handle, "mHeight", handle.getHeight()));
            int bottom = getIntField(handle, "mHandleBottom", 0);
            int centerX = handleLocation[0] - hostLocation[0] + handle.getWidth() / 2;
            int centerY = handleLocation[1] - hostLocation[1]
                    + handle.getHeight() - bottom - barHeight / 2;
            int top = centerY - barHeight / 2 - pad;
            return new Rect(centerX - width / 2, top, centerX + width / 2,
                    centerY + barHeight / 2 + pad);
        }

        private static View findHandleInTree(View view) {
            if (view == null) return null;
            if (view.getClass().getName().contains("OplusNavigationHandle")) return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View found = findHandleInTree(group.getChildAt(i));
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static boolean isShadeExpanded(View view) {
            return NavigationHandleHooks.isShadeExpanded(view);
        }

        private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
            return NavigationHandleHooks.findMethod(type, name, parameters);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    static final class GestureBlockSurface extends View {
        private final View source;
        private final ViewGroup host;

        GestureBlockSurface(View source, ViewGroup host) {
            super(source.getContext());
            this.source = source;
            this.host = host;
            setWillNotDraw(true);
        }

        void update() {
            if (getParent() != host) return;
            Rect rect = TapShield.barRect(source, host);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            lp.width = rect.width();
            lp.height = rect.height();
            setLayoutParams(lp);
            setX(rect.left);
            setY(rect.top);
        }

        void dispatchToSource(MotionEvent event) {
            MotionEvent forwarded = MotionEvent.obtain(event);
            try {
                int[] sourceLocation = new int[2];
                int[] surfaceLocation = new int[2];
                source.getLocationInWindow(sourceLocation);
                getLocationInWindow(surfaceLocation);
                forwarded.offsetLocation(
                        sourceLocation[0] - surfaceLocation[0],
                        sourceLocation[1] - surfaceLocation[1]);
                source.dispatchTouchEvent(forwarded);
            } finally {
                forwarded.recycle();
            }
        }
    }

    // ---------------------------------------------------------------------
    // OLED burn-in defence: hide the hint bar pixels without changing
    // layout or touch dispatch. Swallowing onDraw keeps the handle
    // VISIBLE and full-sized, so mBack/barOnly/TapShield/width geometry
    // stay intact; only the pixels stop being emitted. When the OS draws
    // the bar from a parent layer instead (no onDraw override), the
    // setAlpha fallback is registered to push the alpha to 0.
    // ---------------------------------------------------------------------

    static final class HiddenBar {
        private static volatile boolean onDrawHooked;
        private static volatile boolean alphaHooked;

        static void register(XposedModule module, Class<?> handle) {
            try {
                Class<?> canvas = Class.forName("android.graphics.Canvas", false,
                        handle.getClassLoader());
                Method method = handle.getDeclaredMethod("onDraw", canvas);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                Object thiz = chain.getThisObject();
                                if (thiz instanceof View && !isShadeExpanded((View) thiz)) {
                                    boolean moduleHide = GestureConfigClient.isBarHiddenEnabled();
                                    boolean sysConverted = SystemHide.sConvertedHideActive;
                                    if (moduleHide) {
                                        log(module, 5, "HIDDENBAR_SKIP_DRAW");
                                        return null;
                                    }
                                    if (sysConverted) {
                                        log(module, 5, "HIDDENBAR_SKIP_DRAW_SYSHIDE");
                                        return null;
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                            return chain.proceed();
                        });
                onDrawHooked = true;
                log(module, 4, "HIDDENBAR_HOOK_OK onDraw");
            } catch (Throwable failure) {
                log(module, 5, "HIDDENBAR_NO_MATCH onDraw " + failure);
                registerAlphaFallback(module, handle);
            }
        }

        private static void registerAlphaFallback(XposedModule module, Class<?> handle) {
            try {
                Class<?> view = Class.forName("android.view.View", false,
                        handle.getClassLoader());
                Method method = view.getDeclaredMethod("setAlpha", float.class);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            // Guard order matters: fast volatile read first,
                            // then instanceof on the (already loaded) handle
                            // class, both before any reflection.
                            boolean moduleHide = GestureConfigClient.isBarHiddenEnabled();
                            boolean sysConverted = SystemHide.sConvertedHideActive;
                            if (!moduleHide && !sysConverted) {
                                return chain.proceed();
                            }
                            Object thiz = chain.getThisObject();
                            if (thiz instanceof View
                                    && !isShadeExpanded((View) thiz)
                                    && handle.isInstance(thiz)) {
                                log(5, moduleHide
                                        ? "HIDDENBAR_SKIP_ALPHA"
                                        : "HIDDENBAR_SKIP_ALPHA_SYSHIDE");
                                return null;
                            }
                            return chain.proceed();
                        });
                alphaHooked = true;
                log(module, 4, "HIDDENBAR_HOOK_OK setAlpha");
            } catch (Throwable failure) {
                log(module, 5, "HIDDENBAR_NO_MATCH setAlpha " + failure);
            }
        }

        // Probe helper for the build script / verification stage. Not on any
        // hot path; both flags are set by register() before any hook call
        // lands, and callers tolerate the rare race.
        static boolean isOnDrawHooked() { return onDrawHooked; }
        static boolean isAlphaHooked() { return alphaHooked; }
    }

    // ---------------------------------------------------------------------
    // SystemUI true-hide conversion: when the OS user toggle is on, SystemUI
    // calls OplusNavigationBarView.updateSwipeUpGestureBarVisible(1) which
    // makes the entire navigation window alpha=0 AND detaches the guide bar;
    // the touch route to handleValidTouchEvent dies, so mBack goes silent.
    // Hooking that funnel method lets us clamp the argument back to 0 (visible)
    // whenever mBack/barOnly needs the bar, then the existing HiddenBar pixel
    // suppressor takes over so the user sees no bar but every gesture still works.
    // Falls back gracefully (logged NO_MATCH) if the class/method signature changes.
    // ---------------------------------------------------------------------

    static final class SystemHide {
        /** True when the latest funnel call was clamped from a hide arg to 0 by us. */
        static volatile boolean sConvertedHideActive;
        private static volatile boolean hookInstalled;
        /** Logs the proxy override once per active period to avoid log spam. */
        private static volatile boolean sProxyOverrideLogged;
        /** Logs the utils-hide override once per active period to avoid log spam. */
        private static volatile boolean sUtilsHideLogged;
        /** Navbar view captured from Hook 1 so we can re-apply "shown" once
         *  the config (possibly delayed by provider retries) finally loads. */
        private static volatile WeakReference<Object> sNavbarViewRef;
        /** Logs the config-driven reapply once per active period. */
        private static volatile boolean sReappliedLogged;

        /**
         * Pure decision function kept independent of the hook so it can be
         * unit-tested without an Android runtime.
         *
         * SystemUI can ask the bar to hide via several entry points; the funnel
         * here covers both:
         *   - updateSideGestureBarVisible(int): arg=1 -> hide
         *   - updateWindowAlpha(int): arg=1 or arg=8 (View.GONE) -> hide
         * (View.GONE == 8 is the value updateViewVisible$1 uses when the gesture
         *  guide bar is also hidden -- so any non-zero request is a hide.)
         *
         * We clamp non-zero to 0 only when a feature actually needs the bar:
         *   - mBack: relies on the handle's onTouch for tap/long-press -> Home/Back
         *   - barOnly: relies on the handle starting gestures from its hit region
         *
         * When both are off, we respect the user's request and let SystemUI truly
         * hide the bar (window alpha 0 + guide bar GONE) -- that's exactly what
         * users without mBack expect (apps get the extra bottom inset and content
         * shifts up).
         */
        static boolean shouldConvert(int requested, boolean mbackEnabled, boolean barOnlyEnabled) {
            return requested != 0 && (mbackEnabled || barOnlyEnabled);
        }

        /**
         * Upstream chokepoint decision: when SystemUI reads the swipe-side-bar
         * type (0 = shown, non-zero = hidden) and a feature needs the bar, we
         * report "shown" so the whole pipeline (observer field,
         * updateViewVisible$1 runnable, inflater visibility, window alpha,
         * insets) behaves as if the user kept the bar on. Pixel drawing stays
         * under the module's own barHidden control.
         */
        static boolean shouldOverrideProxyHide(int realType, boolean mbackEnabled, boolean barOnlyEnabled) {
            return realType != 0 && (mbackEnabled || barOnlyEnabled);
        }

        static void register(XposedModule module, ClassLoader loader) {
            if (hookInstalled) return;
            hookInstalled = true;
            Class<?> nav = null;
            // Hook 1: updateSideGestureBarVisible(int) -- the prevention toggle entry.
            // SystemUI calls this from SwipeSideGestureBarTypeObserver.onChange -> ?
            // OplusNavigationBarView, which sets mNavigationInflaterView visibility
            // and schedules updateWindowAlphaDelay(1).
            try {
                nav = Class.forName(
                        "com.oplusos.systemui.navigationbar.OplusNavigationBarView",
                        false, loader);
                Method method = nav.getDeclaredMethod("updateSideGestureBarVisible", int.class);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object arg;
                            try {
                                arg = chain.getArg(0);
                            } catch (Throwable ignored) {
                                return chain.proceed();
                            }
                            if (!(arg instanceof Integer)) return chain.proceed();
                            int requested = (Integer) arg;
                            Object navView = chain.getThisObject();
                            if (navView != null) {
                                sNavbarViewRef = new WeakReference<>(navView);
                            }
                            boolean mback = GestureConfigClient.isMbackEnabled();
                            boolean barOnly = GestureConfigClient.isBarOnlyEnabled();
                            if (!shouldConvert(requested, mback, barOnly)) {
                                return chain.proceed();
                            }
                            log(module, 5, "SYSHIDE_FUNNEL updateSideGestureBarVisible arg="
                                    + requested + "->0 mback=" + mback
                                    + " barOnly=" + barOnly);
                            sConvertedHideActive = true;
                            return chain.proceed(new Object[] { 0 });
                        });
                log(module, 4, "SYSHIDE_HOOK_OK updateSideGestureBarVisible");
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_NO_MATCH updateSideGestureBarVisible " + failure);
            }
            // Hook 2: updateWindowAlpha(int) -- the actual wlp.alpha setter.
            // This catches the postRunnable updateViewVisible$1 path which calls
            // updateWindowAlpha(8) directly when guide bar is also hidden. We must
            // intercept here even if Hook 1 already converted, because
            // updateWindowAlphaDelay schedules the call 350ms later from the
            // handler, and updateViewVisible$1 can also call it directly.
            try {
                if (nav == null) {
                    nav = Class.forName(
                            "com.oplusos.systemui.navigationbar.OplusNavigationBarView",
                            false, loader);
                }
                Method method = nav.getDeclaredMethod("updateWindowAlpha", int.class);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object arg;
                            try {
                                arg = chain.getArg(0);
                            } catch (Throwable ignored) {
                                return chain.proceed();
                            }
                            if (!(arg instanceof Integer)) return chain.proceed();
                            int requested = (Integer) arg;
                            Object alphaView = chain.getThisObject();
                            if (alphaView != null) {
                                sNavbarViewRef = new WeakReference<>(alphaView);
                            }
                            boolean mback = GestureConfigClient.isMbackEnabled();
                            boolean barOnly = GestureConfigClient.isBarOnlyEnabled();
                            if (!shouldConvert(requested, mback, barOnly)) {
                                sConvertedHideActive = false;
                                return chain.proceed();
                            }
                            log(module, 5, "SYSHIDE_CONVERTED updateWindowAlpha arg="
                                    + requested + "->0 mback=" + mback
                                    + " barOnly=" + barOnly);
                            sConvertedHideActive = true;
                            return chain.proceed(new Object[] { 0 });
                        });
                log(module, 4, "SYSHIDE_HOOK_OK updateWindowAlpha");
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_NO_MATCH updateWindowAlpha " + failure);
            }
            // Hook 3: NavBarSettingsValueProxy.getSwipeSideGestureBarType(Context) --
            // the upstream chokepoint every reader of the hide-bar setting goes
            // through (SwipeSideGestureBarTypeObserver.onChange/onRegister,
            // updateViewVisible$1 via the cached field). Returning 0 here keeps
            // the whole "shown" pipeline alive: inflater view VISIBLE so the
            // home handle keeps receiving touches (mBack), alpha untouched,
            // insets unchanged. Hooks 1/2 above stay as defense-in-depth for
            // callers that bypass the proxy.
            hookProxyGetter(module, loader,
                    "com.oplus.systemui.navigationbar.gesture.proxy.NavBarSettingsValueProxy$Companion");
            hookProxyGetter(module, loader,
                    "com.oplus.systemui.navigationbar.gesture.proxy.NavBarSettingsValueProxy");
            // Hook 4: UtilsStaticToolsExImpl.isHideNavBarGestureMode() -- the
            // visibility gate updateViewVisible$1.run() consults at cold boot.
            // It reads NavBarUtils statics directly (NOT the settings proxy),
            // so on a SystemUI start where the hide toggle is already on, the
            // proxy hook never fires and this check still reports "hide" ->
            // inflater GONE -> mBack dead until the user toggles the setting.
            // Flipping it to false when a feature needs the bar makes the boot
            // path take the "shown" branch (inflater VISIBLE, guide bar GONE,
            // alpha 1.0) regardless of toggle order.
            hookUtilsHideMode(module, loader);
            // Hook 5 (config-driven reapply): the boot-time hide decision may
            // have executed while the config provider was still unreachable
            // (fail-closed defaults), leaving the inflater GONE even though
            // every live read is now overridden. When the config finally
            // loads and a feature needs the bar, re-run the exact same entry
            // point the settings observer uses, with 0 ("shown").
            GestureConfigClient.addConfigListener(() -> reapplyShown(module));
        }

        private static void reapplyShown(XposedModule module) {
            boolean mback = GestureConfigClient.isMbackEnabled();
            boolean barOnly = GestureConfigClient.isBarOnlyEnabled();
            if (!mback && !barOnly) return;
            WeakReference<Object> ref = sNavbarViewRef;
            Object navView = ref == null ? null : ref.get();
            if (navView == null) {
                log(module, 5, "SYSHIDE_REAPPLY_SKIPPED no-navbar-view-captured");
                return;
            }
            log(module, 5, "SYSHIDE_REAPPLY_STATE before " + viewState(navView));

            // Primary path: replay the settings-observer onChange. It re-reads
            // the (hooked) proxy getter into the observer's static field and
            // notifies every registered listener -- the exact pipeline the
            // user's manual settings toggle goes through, which is the only
            // path empirically proven to fully restore the touchable window.
            boolean replayed = replayObserverOnChange(module);
            if (!replayed) {
                // Fallback: drive the visibility pipeline directly.
                try {
                    Method updateVisible = findMethod(navView.getClass(), "updateViewVisible");
                    if (updateVisible != null) {
                        updateVisible.setAccessible(true);
                        updateVisible.invoke(navView);
                        log(module, 5, "SYSHIDE_REAPPLY fallback updateViewVisible");
                    }
                } catch (Throwable failure) {
                    log(module, 5, "SYSHIDE_REAPPLY_FAILED fallback " + failure);
                }
            }

            // Belt-and-braces: the funnel entry the observer path also calls.
            try {
                Method update = findMethod(navView.getClass(),
                        "updateSideGestureBarVisible", int.class);
                if (update != null) {
                    update.setAccessible(true);
                    update.invoke(navView, 0);
                }
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_REAPPLY_FAILED updateSideGestureBarVisible " + failure);
            }

            // Restore the inflater layout: the cold-boot hide branch ran
            // resizeLayout() with hide=true (collapsed frame, rebuilt children).
            try {
                Object inflater = readField(navView, "mNavigationInflaterView");
                if (inflater != null) {
                    Method resize = findMethod(inflater.getClass(), "resizeLayout");
                    if (resize != null) {
                        resize.setAccessible(true);
                        resize.invoke(inflater);
                        log(module, 5, "SYSHIDE_REAPPLY resizeLayout");
                    }
                }
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_REAPPLY_FAILED resizeLayout " + failure);
            }

            if (!sReappliedLogged) {
                log(module, 5, "SYSHIDE_REAPPLY done mback=" + mback
                        + " barOnly=" + barOnly + " onChangeReplay=" + replayed);
                sReappliedLogged = true;
            }
            log(module, 5, "SYSHIDE_REAPPLY_STATE after " + viewState(navView));
        }

        private static String viewState(Object navView) {
            if (!(navView instanceof android.view.View)) return "not-a-view";
            android.view.View view = (android.view.View) navView;
            return "visibility=" + view.getVisibility()
                    + " alpha=" + view.getAlpha()
                    + " attached=" + view.isAttachedToWindow();
        }

        /**
         * Reflectively invokes SwipeSideGestureBarTypeObserver.onChange(false):
         * re-reads the hooked proxy getter into mSwipeSideGestureBarType and
         * notifies the observer's listeners via AbstractObserver.onChange.
         */
        private static boolean replayObserverOnChange(XposedModule module) {
            try {
                WeakReference<Object> ref = sNavbarViewRef;
                Object navView = ref == null ? null : ref.get();
                if (navView == null) return false;
                Class<?> observer = Class.forName(
                        "com.oplus.systemui.navigationbar.observer.SwipeSideGestureBarTypeObserver",
                        false, navView.getClass().getClassLoader());
                java.lang.reflect.Field instance = observer.getDeclaredField("INSTANCE");
                instance.setAccessible(true);
                Object obs = instance.get(null);
                Method onChange = findMethod(observer, "onChange", boolean.class);
                if (obs == null || onChange == null) return false;
                onChange.setAccessible(true);
                onChange.invoke(obs, false);
                log(module, 5, "SYSHIDE_REAPPLY onChange-replay");
                return true;
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_REAPPLY_FAILED onChange-replay " + failure);
                return false;
            }
        }

        /** Reads an instance field by name along the class hierarchy. */
        private static Object readField(Object target, String name) throws Exception {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = c.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                }
            }
            return null;
        }

        private static Method findMethod(Class<?> start, String name, Class<?>... params) {
            for (Class<?> c = start; c != null; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredMethod(name, params);
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }

        private static void hookUtilsHideMode(XposedModule module, ClassLoader loader) {
            try {
                Class<?> utils = Class.forName(
                        "com.oplus.systemui.navigationbar.eximpl.UtilsStaticToolsExImpl",
                        false, loader);
                Method method = utils.getDeclaredMethod("isHideNavBarGestureMode");
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (!Boolean.TRUE.equals(result)) {
                                sUtilsHideLogged = false;
                                return result;
                            }
                            boolean mback = GestureConfigClient.isMbackEnabled();
                            boolean barOnly = GestureConfigClient.isBarOnlyEnabled();
                            if (!shouldOverrideProxyHide(1, mback, barOnly)) {
                                sUtilsHideLogged = false;
                                return result;
                            }
                            if (!sUtilsHideLogged) {
                                log(module, 5, "SYSHIDE_UTILSHIDE_OVERRIDE true->false mback="
                                        + mback + " barOnly=" + barOnly);
                                sUtilsHideLogged = true;
                            }
                            return Boolean.FALSE;
                        });
                log(module, 4, "SYSHIDE_HOOK_OK isHideNavBarGestureMode UtilsStaticToolsExImpl");
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_NO_MATCH isHideNavBarGestureMode "
                        + "UtilsStaticToolsExImpl " + failure);
            }
        }

        private static void hookProxyGetter(XposedModule module, ClassLoader loader, String className) {
            try {
                Class<?> proxy = Class.forName(className, false, loader);
                Method method = proxy.getDeclaredMethod("getSwipeSideGestureBarType", Context.class);
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (!(result instanceof Integer)) return result;
                            int real = (Integer) result;
                            boolean mback = GestureConfigClient.isMbackEnabled();
                            boolean barOnly = GestureConfigClient.isBarOnlyEnabled();
                            if (!shouldOverrideProxyHide(real, mback, barOnly)) {
                                sProxyOverrideLogged = false;
                                return result;
                            }
                            if (!sProxyOverrideLogged) {
                                log(module, 5, "SYSHIDE_PROXY_OVERRIDE type=" + real
                                        + "->0 mback=" + mback + " barOnly=" + barOnly
                                        + " via=" + className);
                                sProxyOverrideLogged = true;
                            }
                            return 0;
                        });
                log(module, 4, "SYSHIDE_HOOK_OK getSwipeSideGestureBarType " + className);
            } catch (Throwable failure) {
                log(module, 5, "SYSHIDE_NO_MATCH getSwipeSideGestureBarType "
                        + className + " " + failure);
            }
        }
    }

    static final class Appearance {
        static void applyAll(View v) {
            applyWidth(v);
            applyVisibility(v);
        }

        static void applyWidth(View v) {
            Integer dp = GestureConfigClient.getBarWidthDp();
            if (dp == null) return; // untouched: leave the system width alone
            int clamped = Math.max(BAR_WIDTH_MIN_DP, Math.min(BAR_WIDTH_MAX_DP, dp));
            int width = Math.round(clamped * density(v));
            ViewGroup.LayoutParams raw = v.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) return;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            if (lp.width == width && lp.gravity == Gravity.CENTER) return;
            lp.width = width;
            lp.gravity = Gravity.CENTER;
            v.setLayoutParams(lp);
            log(4, "HANDLE_WIDTH dp=" + clamped + " px=" + width
                    + " from=" + (raw == null ? "?" : raw.width));
        }

        static void applyVisibility(View v) {
            try {
                int hide = Settings.Secure.getInt(
                        v.getContext().getContentResolver(), SETTING_HIDE_GESTURE_BAR, 0);
                // mBack and the bar-only gate need a visible bar, so they override
                // the "hidden" setting.
                boolean show = GestureConfigClient.isMbackEnabled()
                        || GestureConfigClient.isBarOnlyEnabled() || hide == 0;
                int target = show ? View.VISIBLE : View.INVISIBLE;
                if (v.getVisibility() != target) {
                    v.setVisibility(target);
                    log(4, "HANDLE_VISIBILITY target=" + target
                            + " hide=" + hide + " mback=" + GestureConfigClient.isMbackEnabled()
                            + " barOnly=" + GestureConfigClient.isBarOnlyEnabled());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------------------------------------------------------------
    // mBack: tap = BACK, long-press = HOME, swipe = abandon. Visual ripple on
    // a dedicated overlay surface that never participates in touch dispatch.
    // ---------------------------------------------------------------------

    static final class MBack {
        static final class Gesture {
            boolean down;
            boolean cancelled;
            boolean longPress;
            View handle;
            float downX;
            float downY;
            Runnable longPressRunnable;
        }

        private static final ConcurrentHashMap<Long, Gesture> sGestures =
                new ConcurrentHashMap<Long, Gesture>();
        private static final ConcurrentHashMap<Long, Boolean> sInRange =
                new ConcurrentHashMap<Long, Boolean>();
        private static final WeakHashMap<View, MBackSurface> sSurfaces =
                new WeakHashMap<View, MBackSurface>();

        /** Returns true when the event falls inside the mBack band and was consumed. */
        static boolean onTouch(View handle, MotionEvent ev) {
            int action = ev.getActionMasked();
            long downTime = ev.getDownTime();
            if (action == MotionEvent.ACTION_DOWN) {
                sInRange.put(downTime, isInBarRange(handle, ev));
            }
            if (!Boolean.TRUE.equals(sInRange.get(downTime))) return false;
            handleTouch(handle, ev, downTime);
            return true;
        }

        static void handleTouch(View handle, MotionEvent event, long downTime) {
            int action = event.getActionMasked();
            Gesture g = sGestures.get(downTime);
            if (action == MotionEvent.ACTION_DOWN) {
                Gesture ng = new Gesture();
                ng.down = true;
                ng.handle = handle;
                ng.downX = event.getX();
                ng.downY = event.getY();
                sGestures.put(downTime, ng);
                log(5, "MBACK DOWN x=" + Math.round(event.getX())
                        + " y=" + Math.round(event.getY()));
                // The ripple is cosmetic: never let it block the touch handling.
                try {
                    MBackSurface surface = ensureSurface(handle);
                    if (surface != null) {
                        surface.update();
                        surface.showAnimated();
                    }
                } catch (Throwable ignored) {
                }
                Runnable longPress = new Runnable() {
                    @Override
                    public void run() {
                        Gesture gg = sGestures.get(downTime);
                        if (gg == null || !gg.down || gg.cancelled) return;
                        gg.longPress = true;
                        try {
                            handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        } catch (Throwable ignored) {
                        }
                        log(5, "MBACK LONGPRESS home");
                        triggerNavigation(handle, true);
                    }
                };
                ng.longPressRunnable = longPress;
                handle.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return;
            }
            if (g == null || !g.down) return;
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - g.downX;
                float dy = event.getY() - g.downY;
                float swipe = Math.round(MBACK_SWIPE_DP * density(handle));
                if (Math.abs(dx) > swipe || dy < -swipe) {
                    g.cancelled = true;
                    cancelLongPress(g);
                    log(5, "MBACK SWIPE-CANCEL dx=" + Math.round(dx)
                            + " dy=" + Math.round(dy));
                    try {
                        hideSurface(handle);
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }
            if (action == MotionEvent.ACTION_UP) {
                boolean cancelled = g.cancelled;
                boolean longPressed = g.longPress;
                cancelLongPress(g);
                if (!cancelled && !longPressed) {
                    try {
                        handle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    } catch (Throwable ignored) {
                    }
                    log(5, "MBACK TAP back");
                    triggerNavigation(handle, false);
                } else if (!cancelled && longPressed) {
                    try {
                        handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } catch (Throwable ignored) {
                    }
                }
                sGestures.remove(downTime);
                sInRange.remove(downTime);
                try {
                    hideSurface(handle);
                } catch (Throwable ignored) {
                }
                return;
            }
            if (action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                g.cancelled = true;
                cancelLongPress(g);
                sGestures.remove(downTime);
                sInRange.remove(downTime);
                try {
                    hideSurface(handle);
                } catch (Throwable ignored) {
                }
            }
        }

        /** DOWN lands inside the bar horizontal range (with a small padding band). */
        static boolean isInBarRange(View handle, MotionEvent ev) {
            try {
                int left = getIntField(handle, "viewScreenLeft", Integer.MIN_VALUE);
                if (left == Integer.MIN_VALUE) return true;
                float density = density(handle);
                int pad = Math.round(MBACK_BAND_PADDING_DP * density);
                int right = left + handle.getWidth();
                float x = ev.getX();
                return x >= left - pad && x <= right + pad;
            } catch (Throwable t) {
                return true; // fail-open to match coloros-mod: never block the bar
            }
        }

        static void triggerNavigation(View handle, boolean home) {
            boolean ok = injectKey(home
                    ? KeyEvent.KEYCODE_HOME : KeyEvent.KEYCODE_BACK);
            log(4, "MBACK INJECT " + (home ? "HOME" : "BACK") + " ok=" + ok);
        }

        static void cancelLongPress(Gesture g) {
            if (g != null && g.longPressRunnable != null && g.handle != null) {
                g.handle.removeCallbacks(g.longPressRunnable);
            }
            if (g != null) g.longPressRunnable = null;
        }

        // ---- MBackSurface lifecycle -------------------------------------

        static void syncSurface(View handle) {
            if (GestureConfigClient.isMbackEnabled()) {
                MBackSurface surface = ensureSurface(handle);
                log(4, "MBACK_SURFACE on"
                        + (surface != null ? " host=" + (findHost(handle) != null) : " no-host"));
            } else {
                ViewGroup host = findHost(handle);
                if (host != null && sSurfaces.containsKey(host)) {
                    removeSurface(handle);
                    log(4, "MBACK_SURFACE off");
                }
            }
        }

        static void positionSurface(View handle) {
            MBackSurface surface = surfaceFor(handle);
            if (surface != null) surface.update();
        }

        static void hideSurface(View handle) {
            MBackSurface surface = surfaceFor(handle);
            if (surface != null) surface.hideAnimated();
        }

        static void detach(View handle) {
            for (Iterator<Gesture> it = sGestures.values().iterator(); it.hasNext(); ) {
                Gesture gg = it.next();
                if (gg.handle == handle) {
                    cancelLongPress(gg);
                    it.remove();
                }
            }
            removeSurface(handle);
        }

        private static void removeSurface(View handle) {
            ViewGroup host = findHost(handle);
            if (host == null) return;
            MBackSurface surface = sSurfaces.remove(host);
            if (surface == null) return;
            ViewParent parent = surface.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(surface);
            }
        }

        private static MBackSurface ensureSurface(View handle) {
            MBackSurface existing = surfaceFor(handle);
            if (existing != null) {
                existing.update();
                return existing;
            }
            FrameLayout host = findHost(handle);
            if (host == null) {
                log(4, "MBACK_SURFACE no-host");
                return null;
            }
            MBackSurface surface = new MBackSurface(handle, host);
            surface.setVisibility(View.INVISIBLE);
            // Pure visual layer: not clickable / focusable, no touch listener.
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
            host.addView(surface, 0, lp);
            sSurfaces.put(host, surface);
            surface.update();
            return surface;
        }

        private static MBackSurface surfaceFor(View handle) {
            ViewGroup host = findHost(handle);
            return host == null ? null : sSurfaces.get(host);
        }

        private static FrameLayout findHost(View view) {
            ViewParent parent = view.getParent();
            FrameLayout fallback = null;
            while (parent instanceof View) {
                if (parent instanceof FrameLayout) {
                    if (fallback == null) fallback = (FrameLayout) parent;
                    if (parent.getClass().getName().contains("NavigationBarFrame")) {
                        return (FrameLayout) parent;
                    }
                }
                parent = parent.getParent();
            }
            return fallback;
        }
    }

    // ---------------------------------------------------------------------
    // Cosmetic ripple overlay following the bar's real on-screen geometry.
    // ---------------------------------------------------------------------

    static final class MBackSurface extends View {
        private final View source;
        private final ViewGroup host;
        private final Runnable hideRunnable = new Runnable() {
            @Override
            public void run() {
                setVisibility(View.INVISIBLE);
            }
        };

        MBackSurface(View source, ViewGroup host) {
            super(source.getContext());
            this.source = source;
            this.host = host;
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(1000.0f);
            android.content.res.ColorStateList rippleColor =
                    android.content.res.ColorStateList.valueOf(Color.argb(64, 255, 255, 255));
            setBackground(new RippleDrawable(rippleColor, null, mask));
            setAlpha(1.0f);
            setWillNotDraw(true);
        }

        void showAnimated() {
            removeCallbacks(hideRunnable);
            setAlpha(1.0f);
            setVisibility(View.VISIBLE);
            setPressed(true);
            if (getBackground() != null) {
                getBackground().setHotspot(getWidth() / 2.0f, getHeight() / 2.0f);
            }
        }

        void hideAnimated() {
            setPressed(false);
            removeCallbacks(hideRunnable);
            postDelayed(hideRunnable, MBACK_RIPPLE_HIDE_DELAY_MS);
        }

        void update() {
            if (getParent() != host) return;
            int barHeight = getSourceInt("mHeight", source.getHeight());
            int handleBottom = getSourceInt("mHandleBottom", 0);
            float density = density(source);
            int pad = Math.round(MBACK_BAND_PADDING_DP * density);
            int width = source.getWidth() + pad * 2;
            int height = barHeight + pad * 2;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp.width != width || lp.height != height) {
                lp.width = width;
                lp.height = height;
                setLayoutParams(lp);
            }
            int[] sl = new int[2];
            int[] hl = new int[2];
            source.getLocationInWindow(sl);
            host.getLocationInWindow(hl);
            int cx = (sl[0] - hl[0]) + source.getWidth() / 2;
            // Bar center in host coords: view top + bar center offset from the view top.
            int cy = (sl[1] - hl[1]) + source.getHeight() - handleBottom - barHeight / 2;
            setX(cx - width / 2f);
            setY(cy - height / 2f);
            invalidate();
        }

        private int getSourceInt(String field, int fallback) {
            return getIntField(source, field, fallback);
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers.
    // ---------------------------------------------------------------------

    /** Injects a key down/up pair via InputManager; returns success. */
    private static boolean injectKey(int keyCode) {
        try {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Object inputManager = inputManagerClass.getMethod("getInstance").invoke(null);
            Method inject = inputManagerClass.getMethod(
                    "injectInputEvent", InputEvent.class, int.class);
            long now = SystemClock.uptimeMillis();
            boolean ok = true;
            ok &= (Boolean) inject.invoke(inputManager,
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                            android.os.Process.myUid(), 0, KeyEvent.FLAG_FROM_SYSTEM
                                    | KeyEvent.FLAG_VIRTUAL_HARD_KEY),
                    0);
            ok &= (Boolean) inject.invoke(inputManager,
                    new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                            android.os.Process.myUid(), 0, KeyEvent.FLAG_FROM_SYSTEM
                                    | KeyEvent.FLAG_VIRTUAL_HARD_KEY),
                    0);
            return ok;
        } catch (Throwable failure) {
            log(5, "MBACK INJECT-FAILED " + failure);
            return false;
        }
    }

    private static float density(View v) {
        return v.getResources().getDisplayMetrics().density;
    }

    /** Reads an int field from the class hierarchy; returns fallback when missing. */
    private static int getIntField(View v, String name, int fallback) {
        try {
            Field f = findField(v.getClass(), name);
            if (f == null) return fallback;
            f.setAccessible(true);
            return f.getInt(v);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static Object getObjectField(Object value, String name) {
        if (value == null) return null;
        try {
            Field field = findField(value.getClass(), name);
            if (field == null) return null;
            field.setAccessible(true);
            return field.get(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> k = clazz; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static void report(XposedModule module, String outcome, String detail) {
        try {
            module.log(4, TAG, outcome + " " + detail);
            GestureConfigClient.reportStatus(module, outcome, detail);
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
    }

    private static void log(XposedModule module, int priority, String message) {
        try {
            module.log(priority, TAG, message);
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
    }

    private static void log(int priority, String message) {
        XposedModule m = sModule;
        if (m != null) {
            log(m, priority, message);
        } else {
            try {
                android.util.Log.w(TAG, message);
            } catch (Throwable ignored) {
            }
        }
    }
}