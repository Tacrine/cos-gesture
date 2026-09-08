package com.cos.lspit.gesture;

import com.cos.lspit.gesture.hook.LauncherRegionHooker;
import com.cos.lspit.gesture.hook.NavigationHandleHooks;
import com.cos.lspit.gesture.hook.SideBackHooker;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * libxposed module entry point (referenced by
 * {@code META-INF/xposed/java_init.list}).
 *
 * <p>Scoped to {@code com.android.systemui} (side-back + nav-handle work, see
 * {@link SideBackHooker}) and {@code com.android.launcher} (barOnly bottom
 * region narrow, see {@link LauncherRegionHooker}); see {@code scope.list}.
 * The {@code MODULE_READY}/{@code HOST_API}/{@code HOOK_REGISTERED} markers
 * live in the per-package hookers.
 */
public final class ModuleEntry extends XposedModule {
    private static final String TAG = "COS16-Gesture";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String LAUNCHER = "com.android.launcher";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(4, TAG, "MODULE_LOADED api=" + getApiVersion()
                + " framework=" + getFrameworkName() + "/" + getFrameworkVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (SYSTEM_UI.equals(pkg)) {
            SideBackHooker.onPackageLoaded(this, param);
            NavigationHandleHooks.register(this, param);
        } else if (LAUNCHER.equals(pkg)) {
            LauncherRegionHooker.onPackageLoaded(this, param);
        }
    }
}
