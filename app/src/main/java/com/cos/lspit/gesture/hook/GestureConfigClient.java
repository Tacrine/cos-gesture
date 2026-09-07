package com.cos.lspit.gesture.hook;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

/**
 * SystemUI-side mirror of the module config. Reads one snapshot from the
 * module's ConfigProvider and then refreshes it on a background observer
 * thread; the ACTION_DOWN hot path only reads the three volatile booleans
 * (zero Binder traffic). Every failure keeps the current snapshot, so the
 * proven all-enabled default survives first launch and provider crashes.
 */
public final class GestureConfigClient {
    private static final String TAG = "COS16-Gesture";
    // String literals on purpose: the Java hook side must not depend on the Kotlin config classes.
    private static final Uri CONFIG_URI =
            Uri.parse("content://com.cos.lspit.gesture.config/config");
    private static final Uri STATUS_URI =
            Uri.parse("content://com.cos.lspit.gesture.config/status");

    private static volatile boolean masterEnabled = true;
    private static volatile boolean leftEnabled = true;
    private static volatile boolean rightEnabled = true;

    private GestureConfigClient() {}

    public static boolean isMasterEnabled() { return masterEnabled; }

    public static boolean isLeftEnabled() { return leftEnabled; }

    public static boolean isRightEnabled() { return rightEnabled; }

    /** Loads the first snapshot and starts the live observer. Idempotent-guarded. */
    public static void init() {
        Context context = currentContext();
        if (context == null) return;
        refresh(context);
        HandlerThread thread = new HandlerThread("cos16-gesture-cfg");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        context.getContentResolver().registerContentObserver(
                CONFIG_URI, true, new ContentObserver(handler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        Context current = currentContext();
                        if (current != null) refresh(current);
                    }
                });
    }

    /** Re-queries the provider; any failure keeps the current snapshot. */
    private static void refresh(Context context) {
        try (Cursor cursor = context.getContentResolver()
                .query(CONFIG_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int master = readColumn(cursor, "master");
            int left = readColumn(cursor, "left");
            int right = readColumn(cursor, "right");
            if (master >= 0) masterEnabled = master != 0;
            if (left >= 0) leftEnabled = left != 0;
            if (right >= 0) rightEnabled = right != 0;
            Log.i(TAG, "CONFIG_UPDATE master=" + masterEnabled
                    + " left=" + leftEnabled + " right=" + rightEnabled);
        } catch (Throwable ignored) {
            // Keep current snapshot; never break SystemUI.
        }
    }

    /** Returns the column value, or -1 when missing (caller then keeps the snapshot). */
    private static int readColumn(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0) return -1;
        return cursor.getInt(index);
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
        } catch (Throwable ignored) {
            // Status reporting must never affect SystemUI.
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
}
