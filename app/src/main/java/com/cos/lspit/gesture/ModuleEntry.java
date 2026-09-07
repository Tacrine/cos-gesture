package com.cos.lspit.gesture;

import com.cos.lspit.gesture.hook.NavigationHandleHooks;
import com.cos.lspit.gesture.hook.SideBackHooker;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * libxposed module entry point (referenced by
 * {@code META-INF/xposed/java_init.list}).
 *
 * <p>Scoped to {@code com.android.systemui} only (see {@code scope.list}); the
 * actual hook work and the {@code MODULE_READY}/{@code HOST_API}/{@code
 * HOOK_REGISTERED} markers live in {@link SideBackHooker}.
 */
public final class ModuleEntry extends XposedModule {
    private static final String TAG = "COS16-Gesture";
    private static final String SYSTEM_UI = "com.android.systemui";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(4, TAG, "MODULE_LOADED api=" + getApiVersion()
                + " framework=" + getFrameworkName() + "/" + getFrameworkVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (SYSTEM_UI.equals(param.getPackageName())) {
            SideBackHooker.onPackageLoaded(this, param);
                NavigationHandleHooks.register(this, param);
            }
        }
}
