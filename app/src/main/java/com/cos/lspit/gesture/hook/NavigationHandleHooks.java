package com.cos.lspit.gesture.hook;

import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Spike (Task 3) coordinate hook on the Oplus navigation handle.
 *
 * <p>Goal: prove that {@code OplusNavigationHandle#handleValidTouchEvent(
 * android.view.MotionEvent)} exists on this ROM and that a KEYCODE_BACK
 * injected from in-process SystemUI reaches the app under test. This is the
 * gate before the full mBack state machine (Task 6) and the hint-bar width
 * hook (Task 5), which will share this same hook site through the
 * {@code register} entry point.
 *
 * <p>Failure is self-limiting: every stage is wrapped so a missing class or
 * method logs {@code NO_MATCH} and never crashes SystemUI; a miss on the
 * inject call is logged and the original {@code handleValidTouchEvent}
 * body still runs via {@code chain.proceed()}.
 */
public final class NavigationHandleHooks {
    private static final String TAG = "COS16-Gesture";
    private static final String HANDLE_CLASS =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle";
    private static final String HANDLE_METHOD = "handleValidTouchEvent";

    /** KeyEvent flags: FLAG_FROM_SYSTEM | FLAG_VIRTUAL_HARD_KEY */
    private static final int FLAG_SYSTEM_KEY = 0x08 | 0x40;
    private static final int INJECT_MODE_ASYNC = 0;

    private NavigationHandleHooks() {}

    public static void register(XposedModule module, PackageLoadedParam param) {
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> handle;
            try {
                handle = Class.forName(HANDLE_CLASS, false, loader);
            } catch (Throwable missing) {
                report(module, "NO_MATCH", "class-not-found " + HANDLE_CLASS);
                return;
            }
            Method method = findHandleMethod(handle, loader);
            if (method == null) {
                report(module, "NO_MATCH", "method-not-found " + HANDLE_METHOD);
                return;
            }
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> onHandleTouch(chain, module));
            report(module, "HOOK_REGISTERED", HANDLE_CLASS + "#" + HANDLE_METHOD);
        } catch (Throwable failure) {
            try {
                module.log(6, TAG, "HOOK_DISABLED", failure);
            } catch (Throwable ignored) {
                // Logging must never make SystemUI fail.
            }
        }
    }

    /**
     * Spike interceptor: on ACTION_DOWN, inject a KEYCODE_BACK down/up pair
     * and then let the original method proceed. Later tasks replace this
     * body with the mBack state machine / hint-bar handling.
     */
    private static Object onHandleTouch(XposedInterface.Chain chain, XposedModule module)
            throws Throwable {
        Object argument;
        try {
            argument = chain.getArg(0);
        } catch (Throwable ignored) {
            return chain.proceed();
        }
        if (argument instanceof MotionEvent) {
            MotionEvent event = (MotionEvent) argument;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                boolean ok = injectKey(KeyEvent.KEYCODE_BACK);
                log(module, 5, "MBACK_SPIKE DOWN injectBack=" + ok);
            }
        }
        return chain.proceed();
    }

    private static Method findHandleMethod(Class<?> handle, ClassLoader loader) {
        try {
            Class<?> motionEvent = Class.forName("android.view.MotionEvent", false, loader);
            return handle.getDeclaredMethod(HANDLE_METHOD, motionEvent);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Injects a key down/up pair via InputManager; returns success. */
    private static boolean injectKey(int keyCode) {
        try {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
            Object inputManager = inputManagerClass.getMethod("getInstance").invoke(null);
            Method inject = inputManagerClass.getMethod(
                    "injectInputEvent", InputEvent.class, int.class);
            long now = android.os.SystemClock.uptimeMillis();
            boolean ok = true;
            ok &= (Boolean) inject.invoke(inputManager,
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                            android.os.Process.myUid(), 0, KeyEvent.FLAG_FROM_SYSTEM
                                    | KeyEvent.FLAG_VIRTUAL_HARD_KEY),
                    INJECT_MODE_ASYNC);
            ok &= (Boolean) inject.invoke(inputManager,
                    new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                            android.os.Process.myUid(), 0, KeyEvent.FLAG_FROM_SYSTEM
                                    | KeyEvent.FLAG_VIRTUAL_HARD_KEY),
                    INJECT_MODE_ASYNC);
            return ok;
        } catch (Throwable failure) {
            android.util.Log.w(TAG, "MBACK_SPIKE inject-failed " + failure);
            return false;
        }
    }

    private static void report(XposedModule module, String outcome, String detail) {
        try {
            module.log(4, TAG, "SPIKE " + outcome + " " + detail);
            GestureConfigClient.reportStatus(module, "SPIKE_" + outcome, detail);
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
}