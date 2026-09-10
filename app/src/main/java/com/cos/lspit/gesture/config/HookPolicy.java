package com.cos.lspit.gesture.config;

import java.lang.reflect.Method;

/**
 * Fail-closed policy for the ColorOS 16 (Oplus) side-back hook.
 *
 * <p>The only proven gate for suppressing gesture Back on this firmware is an
 * around/before hook on {@code SideGestureDetector#onMotionEventImpl(MotionEvent)V}
 * inside {@code com.android.systemui} (see {@code discovery.json} result MATCH).
 * The hook is activated ONLY when every guard below passes:
 * {@link #ENABLED}, a live SystemUI APK whose SHA-256 equals
 * {@link #EXPECTED_SYSTEMUI_SHA256}, and an exact target class/method/descriptor
 * resolution. Any mismatch yields {@code NO_MATCH}/{@code HASH_MISMATCH} instead of
 * registering a hook against an unproven target.
 */
public final class HookPolicy {
    /** Master switch. Stays false until the Task-4 host verification returned SUPPORTED. */
    public static final boolean ENABLED = true;

    /** Proven Oplus target (Task 3 MATCH, Task 4 re-verified). NOT the AOSP class. */
    public static final String TARGET_CLASS =
            "com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector";
    public static final String TARGET_METHOD = "onMotionEventImpl";
    /** Exact method descriptor: single MotionEvent parameter, void return. */
    public static final String TARGET_DESCRIPTOR = "(Landroid/view/MotionEvent;)V";

    /** Firmware-drift guard: SystemUI.apk SHA-256 measured on-device (discovery.json). */
    public static final String EXPECTED_SYSTEMUI_SHA256 =
            "7144D7E0E7DA46BC5F408C71C7B8D761DF97EE1C6B52F99FA2F40577183B71B8";

    private HookPolicy() {}

    /**
     * DESCRIPTOR_MATCH check: builds the JVM descriptor for a resolved reflective method
     * and compares it against {@link #TARGET_DESCRIPTOR}. A mismatch (wrong parameter
     * types or wrong return type) means the resolved method is not the proven hook point.
     */
    public static boolean descriptorMatches(Method method) {
        return TARGET_DESCRIPTOR.equals(buildDescriptor(method));
    }

    /** Builds a JVM type descriptor (e.g. {@code (Landroid/view/MotionEvent;)V}) for a method. */
    public static String buildDescriptor(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> param : method.getParameterTypes()) {
            out.append(typeDescriptor(param));
        }
        out.append(')').append(typeDescriptor(method.getReturnType()));
        return out.toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (!type.isArray()) {
            if (type.isPrimitive()) {
                if (type == void.class) return "V";
                if (type == int.class) return "I";
                if (type == boolean.class) return "Z";
                if (type == byte.class) return "B";
                if (type == char.class) return "C";
                if (type == short.class) return "S";
                if (type == long.class) return "J";
                if (type == float.class) return "F";
                if (type == double.class) return "D";
                throw new IllegalArgumentException("unknown primitive " + type.getName());
            }
            return "L" + type.getName().replace('.', '/') + ";";
        }
        // Class.getName() for arrays is already a JVM descriptor ("[I", "[Ljava.lang.String;").
        return type.getName().replace('.', '/');
    }
}
