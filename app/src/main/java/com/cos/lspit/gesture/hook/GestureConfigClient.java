package com.cos.lspit.gesture.hook;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SystemUI-side mirror of the module config. Reads one snapshot from the
 * module's ConfigProvider and then refreshes it on a background observer
 * thread; the ACTION_DOWN hot path only reads the three volatile booleans
 * (zero Binder traffic). Every failure keeps the current snapshot, so the
 * proven all-enabled default survives first launch and provider crashes.
 *
 * <p>Listeners registered through {@link #addConfigListener} run on the main
 * looper after every successful refresh, letting view-affecting hooks
 * (hint-bar width / mBack surface) re-apply to the live handle.
 */
public final class GestureConfigClient {
    private static final String TAG = "COS16-Gesture";

    private static final List<Runnable> CONFIG_LISTENERS = new CopyOnWriteArrayList<Runnable>();
    // String literals on purpose: the Java hook side must not depend on the Kotlin config classes.
    private static final Uri CONFIG_URI =
            Uri.parse("content://com.cos.lspit.gesture.config/config");
    private static final Uri STATUS_URI =
            Uri.parse("content://com.cos.lspit.gesture.config/status");
    private static final int INIT_MAX_RETRIES = 40;
    private static final long INIT_RETRY_DELAY_MS = 500L;
    /** After the fast retries are exhausted, keep retrying at this cadence forever:
     *  a force-stopped module app's provider never auto-restarts, so giving up
     *  permanently would leave mback/barOnly dead until the next manual toggle. */
    private static final long SLOW_RETRY_DELAY_MS = 30_000L;
    private static int initRetriesLeft = INIT_MAX_RETRIES;
    private static volatile boolean slowRetryAnnounced;
    /** Guards the single lazy registration of the background observer thread. */
    private static volatile boolean observerStarted;

    private static volatile boolean masterEnabled = true;
    private static volatile boolean leftEnabled = true;
    private static volatile boolean rightEnabled = true;
        private static volatile boolean mbackEnabled = false;
        private static volatile Integer barWidthDp = null;
        private static volatile boolean barOnlyEnabled = false;
        private static volatile boolean hintTapShieldEnabled = false;
    private static volatile boolean barHiddenEnabled = false;

        private static volatile boolean initialized = false;

        private GestureConfigClient() {}

        public static boolean isMasterEnabled() { return masterEnabled; }

        public static boolean isLeftEnabled() { return leftEnabled; }

        public static boolean isRightEnabled() { return rightEnabled; }

        public static boolean isMbackEnabled() { return mbackEnabled; }

        /** Null means "leave the system hint-bar width untouched". */
        public static Integer getBarWidthDp() { return barWidthDp; }

        /** Bar-only gating: bottom gestures only fire when the finger starts on the hint bar. */
        public static boolean isBarOnlyEnabled() { return barOnlyEnabled; }

        public static boolean isHintTapShieldEnabled() { return hintTapShieldEnabled; }

        public static boolean isBarHiddenEnabled() { return barHiddenEnabled; }

    /** Runs a listener on the SystemUI main looper; used by refresh's notifier. */
    public static void postOnMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    /** Registers a callback fired (on the main looper) after each successful refresh. */
    public static void addConfigListener(Runnable action) {
        if (action != null) CONFIG_LISTENERS.add(action);
    }

    /**
     * Loads the first snapshot and starts the live observer. The host
     * Application may not exist yet when the module registers its hooks
     * (onPackageLoaded fires before Application.onCreate), so the context
     * lookup is retried on the main looper until it succeeds.
     */
    public static void init() {
        if (initialized) return;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (initialized) return;
                Context context = currentContext();
                if (context == null) {
                    retryOrFail("no-application-context");
                    return;
                }
                if (!observerStarted) {
                    synchronized (GestureConfigClient.class) {
                        if (!observerStarted) {
                            observerStarted = true;
                            HandlerThread thread = new HandlerThread("cos16-gesture-cfg");
                            thread.start();
                            Handler handler = new Handler(thread.getLooper());
                            try {
                                context.getContentResolver().registerContentObserver(
                                        CONFIG_URI, true, new ContentObserver(handler) {
                                            @Override
                                            public void onChange(boolean selfChange) {
                                                Context current = currentContext();
                                                if (current != null) refresh(current);
                                            }
                                        });
                            } catch (Throwable failure) {
                                Log.w(TAG, "CONFIG_OBSERVER_FAILED " + failure);
                            }
                        }
                    }
                }
                if (!refresh(context)) {
                    // Provider snapshot not readable yet (module app / provider not
                    // warm on cold SystemUI boot). Keep retrying so mback / width /
                    // barOnly actually arm; a dead silent return here used to leave
                    // them at their defaults until a later config change fired the
                    // observer.
                    retryOrFail("provider-not-ready");
                    return;
                }
                initialized = true;
            }
        }, 0L);
    }

    private static void retryOrFail(String reason) {
        if (initRetriesLeft-- <= 0) {
            if (!slowRetryAnnounced) {
                slowRetryAnnounced = true;
                Log.w(TAG, "CONFIG_INIT_FAILED " + reason
                        + " (fast retries exhausted, retrying every "
                        + (SLOW_RETRY_DELAY_MS / 1000) + "s)");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    init();
                }
            }, SLOW_RETRY_DELAY_MS);
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                init();
            }
        }, INIT_RETRY_DELAY_MS);
    }

    /** Re-queries the provider; returns false when no snapshot could be read
     *  (caller may retry). Any other failure keeps the current snapshot. */
    private static boolean refresh(Context context) {
        try (Cursor cursor = context.getContentResolver()
                .query(CONFIG_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return false;
            int master = readColumn(cursor, "master");
            int left = readColumn(cursor, "left");
            int right = readColumn(cursor, "right");
            int mback = readColumn(cursor, "mback");
            Integer barWidth = readNullableColumn(cursor, "barWidthDp");
            int barOnly = readColumn(cursor, "barOnly");
            int hintTapShield = readColumn(cursor, "hintTapShield");
            int barHidden = readColumn(cursor, "barHidden");
            if (master >= 0) masterEnabled = master != 0;
            if (left >= 0) leftEnabled = left != 0;
            if (right >= 0) rightEnabled = right != 0;
            // mback is fail-closed: only an explicit 1 arms it; anything else
            // (missing column, unparseable) leaves it disabled.
            if (mback >= 0) mbackEnabled = mback == 1;
            if (barWidth != null) barWidthDp = barWidth;
            // barOnly is fail-closed, same as mback.
            if (barOnly >= 0) barOnlyEnabled = barOnly == 1;
            if (hintTapShield >= 0) hintTapShieldEnabled = hintTapShield == 1;
                        if (barHidden >= 0) barHiddenEnabled = barHidden == 1;
                        Log.i(TAG, "CONFIG_UPDATE master=" + masterEnabled
                                + " left=" + leftEnabled + " right=" + rightEnabled
                                + " mback=" + mbackEnabled + " barWidthDp=" + barWidthDp
                                + " barOnly=" + barOnlyEnabled
                                + " hintTapShield=" + hintTapShieldEnabled
                                + " barHidden=" + barHiddenEnabled);
                notifyListeners();
            return true;
        } catch (Throwable failure) {
            // Keep current snapshot; never break SystemUI, but make the failure visible.
            Log.w(TAG, "CONFIG_REFRESH_FAILED " + failure);
            return false;
        }
    }

    private static void notifyListeners() {
        for (Runnable action : CONFIG_LISTENERS) {
            try {
                postOnMain(action);
            } catch (Throwable ignored) {
                // A misbehaving listener must never break the observer thread.
            }
        }
    }

    /** Returns the column value, or -1 when missing (caller then keeps the snapshot). */
    private static int readColumn(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0) return -1;
        return cursor.getInt(index);
    }

        /** Returns the column value, or null when missing/unparseable. */
        private static Integer readNullableColumn(Cursor cursor, String column) {
            int index = cursor.getColumnIndex(column);
            if (index < 0) return null;
            return cursor.isNull(index) ? null : cursor.getInt(index);
        }

    /** Best-effort status report into the module's gesture_status prefs; never throws. */
    public static void reportStatus(XposedModule module, String outcome, String detail) {
        try {
            module.log(4, TAG, "STATUS " + outcome + " " + (detail == null ? "" : detail));
        } catch (Throwable ignored) {
            // Logging is best effort.
        }
        try {
            Context context = currentContext();
            if (context == null) return;
            ContentValues values = new ContentValues();
            values.put("outcome", outcome);
            values.put("detail", detail == null ? "" : detail);
            values.put("at_millis", System.currentTimeMillis());
            context.getContentResolver().insert(STATUS_URI, values);
        } catch (Throwable failure) {
            // Status reporting must never affect SystemUI, but make the failure visible.
            Log.w(TAG, "STATUS_REPORT_FAILED " + failure);
        }
    }

    /** Reflects the host application context without holding any module-side reference. */
    private static Context currentContext() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            return (Context) thread.getMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Public bridge for hook code that needs the host context (e.g. ContentObserver). */
    public static Context systemContext() {
        return currentContext();
    }
}
