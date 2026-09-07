package com.cos.lspit.gesture.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import org.junit.Test;

public class HookPolicyTest {
    /** Plain JVM fixtures stand in for the android-typed target in descriptor tests. */
    private static final class Fixtures {
        @SuppressWarnings("unused")
        static void gateLike(String event) {}

        @SuppressWarnings("unused")
        static boolean aospBooleanReturn(String event) {
            return false;
        }
    }

    @Test
    public void disabledYieldsHookDisabled() {
        assertEquals("HOOK_DISABLED", HookPolicy.outcome(false, true, true, true, true));
    }

    @Test
    public void hashMismatchYieldsHashMismatch() {
        assertEquals("HASH_MISMATCH", HookPolicy.outcome(true, false, true, true, true));
    }

    @Test
    public void classMissingYieldsNoMatch() {
        assertEquals("NO_MATCH", HookPolicy.outcome(true, true, false, true, true));
    }

    @Test
    public void methodMissingYieldsNoMatch() {
        assertEquals("NO_MATCH", HookPolicy.outcome(true, true, true, false, true));
    }

    @Test
    public void descriptorMismatchYieldsNoMatch() {
        // DESCRIPTOR_MATCH gate: a wrong descriptor/return type must fail closed.
        assertEquals("NO_MATCH", HookPolicy.outcome(true, true, true, true, false));
    }

    @Test
    public void allGuardsPassYieldHookRegistered() {
        assertEquals("HOOK_REGISTERED", HookPolicy.outcome(true, true, true, true, true));
    }

    @Test
    public void policyTargetsProvenOplusGate() {
        assertTrue(HookPolicy.ENABLED);
        assertEquals("com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector",
                HookPolicy.TARGET_CLASS);
        assertEquals("onMotionEventImpl", HookPolicy.TARGET_METHOD);
        assertEquals("(Landroid/view/MotionEvent;)V", HookPolicy.TARGET_DESCRIPTOR);
        assertEquals("7144D7E0E7DA46BC5F408C71C7B8D761DF97EE1C6B52F99FA2F40577183B71B8",
                HookPolicy.EXPECTED_SYSTEMUI_SHA256);
    }

    @Test
    public void wrongReturnTypeChangesDescriptorAndNeverMatches() throws Exception {
        Method voidMethod = Fixtures.class.getDeclaredMethod("gateLike", String.class);
        Method boolMethod = Fixtures.class.getDeclaredMethod("aospBooleanReturn", String.class);
        assertEquals("(Ljava/lang/String;)V", HookPolicy.buildDescriptor(voidMethod));
        // The old AOSP-style method returned Z, not V: a different descriptor entirely.
        assertEquals("(Ljava/lang/String;)Z", HookPolicy.buildDescriptor(boolMethod));
        assertFalse(HookPolicy.descriptorMatches(boolMethod));
        assertFalse(HookPolicy.descriptorMatches(voidMethod));
    }
}
