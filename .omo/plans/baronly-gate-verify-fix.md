# baronly-launcher-region-narrow - Work Plan (方案 A)

## TL;DR (For humans)
**What you'll get**: "仅小白条区域触发底部手势(home/recents/任务切换)、其余底部区域拦截"在真机生效。触发带与用户自定义小白条长度(40-160dp)实时联动，保留现有开关，前端无需修改。

**Decisive finding (为什么 SystemUI veto 是死路)**: 底部手势 region 由 launcher（`com.android.launcher`）注册、归 launcher 进程所有（dumpsys ownerUid=10161）；SystemUI scope 下任何 veto 都是旁观者，拦截不到。原生 `oplus_gesture_region_reduce_*` 设置实测无效（写 system+secure 后 region 仍全宽）。证据见 `.omo/evidence/baronly/task-5-fallback.txt`。

**方案 A（本计划执行）**: 把模块 scope 扩展到 launcher，在 launcher 进程 hook 框架类 `android.view.OplusWindowManager#updateInvalidRegion:(Ljava/lang/String;Ljava/util/List;ZZLandroid/os/Bundle;)Z`——launcher 客户端把每方向 home 手势 region 交 OplusWMS 前必经此方法——barOnly 开启时把 list 中每个非零宽 RectF（OrientationRectF extends RectF）水平对称收窄到以自身 center 为心的白条带，然后照常 proceed 注册；关闭则放行。配置变更（barOnly/宽度）经 GestureConfigClient(provider 监听)驱动 debounce 后反射调用 launcher Kotlin object `NavigationController.INSTANCE.updateTouchRegion()`（dexdump 证实无参 `()V`）强制 launcher 重注册 → 开关即时生效，无需重启 launcher。

**What it will NOT do**: 不改前端(MIUIX UI)、不加新开关、不改宽度范围(保持 40-160dp)、不动 mBack/侧滑逻辑。

**Effort**: 实现已完成，剩设备验证/回归/收尾，约 0.5-1 小时。

## Scope
IN: launcher scope 扩展、LauncherRegionHooker(updateInvalidRegion 收窄)、config 变更即时重注册、区域收窄矩阵验证(home/recents/任务切换/宽度联动/开关回归)、日志收敛、提交。
OUT: 前端改动、新开关、宽度范围调整、mBack 行为改动、SystemUI veto 删除(保留但已知无效,见下)。

## 实现状态
- ✅ [SideGesturePolicy.java](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\java\com\cos\lspit\gesture\config\SideGesturePolicy.java): 抽出纯函数 `barHalfWidthPx(int barWidthDp,float density)` 与 `narrowBandToBar(float[] ltrb,int barWidthDp,float density)`（按自身 center 收窄、退化<2px 返回 false 不动）。`shouldVetoBarOnly` 复用 `barHalfWidthPx`（SystemUI 旁观 veto 保留，已知无效但无害）。
- ✅ [LauncherRegionHooker.java](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\java\com\cos\lspit\gesture\hook\LauncherRegionHooker.java)（新）: hook `android.view.OplusWindowManager.updateInvalidRegion`；仅对 `homegesture_` 前缀 + barOnly armed 收窄 list 内非空 RectF；`GestureConfigClient.init()` + `addConfigListener`(进程无关，ActivityThread 取 context)；变更 debounce 400ms 后反射 `NavigationController.INSTANCE` 调用 `updateTouchRegion()`。descriptor 守卫 fail-closed。
- ✅ [ModuleEntry.java](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\java\com\cos\lspit\gesture\ModuleEntry.java): launcher 包分发到 `LauncherRegionHooker.onPackageLoaded`。
- ✅ `scope.list`: `com.android.systemui` + `com.android.launcher`。
- ✅ 单测（SideGesturePolicyTest 新增收窄矩阵 6 例）green；assembleDebug 成功；APK 已 `adb install -r`。
- ✅ LSPosed scope DB（`/data/adb/lspd/config/modules_config.db`）离线改：scope 表加 `('com.cos.lspit.gesture','com.android.launcher',0)`（sqlite 无 CLI，PC 拉库 VACUUM INTO 后写回），模块 enabled=1。
- ⏳ 设备 reboot 后验证。

## Verification strategy
- 单测: `.\gradlew.bat :app:testDebugUnitTest`（JDK21: `$env:JAVA_HOME='C:\Users\Administrator\.jdks\jdk-21.0.12.1+1'`）。
- 设备（无线 adb `10.168.1.125:37379`，SDK adb `C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe`，root/ksu）:
  - 模块加载: launcher 进程 logcat 出现 `MODULE_READY launcher` + `HOOK_REGISTERED android.view.OplusWindowManager#updateInvalidRegion`。模块日志经 `LSPosedLogDaemon` 中继，判活用全量 `adb logcat -d` + `Select-String`。
  - 区域收窄: `adb shell "dumpsys input | grep -A2 -B2 homegesture"` 或对应 region dump——barOnly ON 时 homegesture region x 从全宽 `[0..1080]` 收窄到白条带(40dp→474..606,160dp→309..771)；OFF 恢复全宽。注: 需要 launcher 已注册新 region；若 launcher 后台未注册先按 HOME 或切方向触发。
  - 手势矩阵: 注入 `input swipe`（SOURCE_TOUCHSCREEN 通路，端口对照先做带内 PASS）: 带外 x=200/900 上滑不触发 home（焦点不变、无 `handleNormalGestureEnd`、有 `REGION_NARROW`）；带内 x=540 上滑触发 HOME（有 `handleNormalGestureEnd: endTarget = HOME`）；宽度联动换 160dp 后带外 x=400(原在 40dp 带外)变带内；开关回归 OFF→全宽可触发。
  - 用户物理复验(F3): 带外/带内各滑几次确认手感。

## Todos
- [x] launch-doc: plan 翻新（本文件）
- [x] launch-policy: SideGesturePolicy 纯函数 + 单测
- [x] launch-hook: LauncherRegionHooker + ModuleEntry 分发
- [x] launch-refresh: NavigationController.INSTANCE.updateTouchRegion 反射刷新
- [x] launch-scope: scope.list + LSPosed DB 加 com.android.launcher（已 reboot 待验）
- [ ] launch-verify: 模块 launcher 加载→区域收窄→手势矩阵→宽度联动→开关回归（进行中）
- [ ] launch-logtrim: 日志收敛 + commit

## Risk / Fallback
- (R1) libxposed 无法 hook 框架类/launcher 未加载模块 → logcat `HOOK_DISABLED`/无 marker；改查 lspd modules log 确认 scope 注入。若框架类 hook 受限，退路: hook launcher 自有类 `NavigationController` 内调用 updateInvalidRegion 的上游方法（`updateTouchRegion`/`addInterceptRect`）mutate。
- (R2) updateInvalidRegion 的 regionList 为空/仅当前方向/横屏异形 → 收窄只对非零宽元素、退化跳过，天然安全；横屏另测。
- (R3) 反射刷新失败(类隐藏/签名差异) → 捕获静默；配置仍在下一次自然重注册(旋转/launcher 重启)生效；可加 force-stop launcher 兜底。
- (R4) 收窄后 SystemUI 侧 `shouldVetoBarOnly` 冗余 veto 对带外仍在 SystemUI 可见的事件不构成伤害（region 已收窄，事件根本到不了 detector）。
- 设备无 sqlite3 CLI（仅 libsqlite.so）；DB 改动一律 PC 离线 VACUUM INTO + 写回 + reboot。

## Evidence
`.omo/evidence/baronly/`:
- task-5-fallback.txt: 决定性证据 + 方案 A/B 对比（已建）
- launch-scope.txt: scope DB 改动记录（待建）
- launch-verify.txt: 模块加载/区域收窄/手势矩阵结果（待建）
