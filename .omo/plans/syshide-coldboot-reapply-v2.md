# syshide-coldboot-reapply-v2 - Work Plan

## TL;DR (For humans)

**What this fixes**: 系统设置开启「隐藏小白条」时冷启动 SystemUI（重启手机 / killall / 重装模块后），mBack 点击与长按失效，必须手动 toggle 系统设置才能激活。当前 Hook 5（reapply 调 `updateSideGestureBarVisible(0)`）已触发但窗口仍 `alpha=0 + NOT_TOUCHABLE`，无效。

**Root cause（本轮反编译已确认）**:
1. 冷启动时模块 config provider 不可达（app stopped 态被 `OplusAppStartupManager` 拦）→ `GestureConfigClient` fail-closed `mback=false` → Hook 3/4 不转换 → `onRegister`/`updateViewVisible$1.run()`/`resizeLayout()` 全部走 hide 分支：inflater GONE、window alpha=0、NOT_TOUCHABLE、touchableRegion 空、窗口 frame 移位。
2. config 慢重试加载后，Hook 3/4 对**未来**读取生效，但**没有任何事件重新触发显示管线**。
3. 现有 reapply 只调 `updateSideGestureBarVisible(0)`，它只恢复 homeHandle/inflater visibility + alpha，**不调 `resizeLayout()`（窗口 frame/insets 恢复）也不通知 observer listener 链**，所以窗口仍不可触摸。

**Fix 方案**: reapply v2 改为**重放用户 toggle 的完整路径**：反射调 `SwipeSideGestureBarTypeObserver.INSTANCE.onChange(false)`。该方法经已被 Hook 3 拦截的 `NavBarSettingsValueProxy$Companion.getSwipeSideGestureBarType` 写回 `mSwipeSideGestureBarType=0`，并 `super.onChange` 通知所有 listener → 触发与手动 toggle 完全相同的恢复管线（用户已实证该路径恢复 mBack）。再保留 `updateSideGestureBarVisible(0)` + 新增 `resizeLayout()` 作为 belt-and-braces。

**Effort**: ~2-4 小时（1 个代码 todo + 构建装机 + 设备矩阵验证）。
**Risk**: `AbstractObserver.onChange` 语义未知细节（可能重注册 observer）；已配 fallback（直接调 `updateViewVisible()` + `resizeLayout()`）。
**Decisions made for you**: reapply 触发点维持 config listener（Hook 5 位置不动）；不改动 fail-closed 语义；不动慢重试。

## Scope

**IN**:
- 修改 [NavigationHandleHooks.java](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/app/src/main/java/com/cos/lspit/gesture/hook/NavigationHandleHooks.java) 中 `SystemHide.reapplyShown`（当前 L892 附近）
- 新增 inflater view 捕获（`mNavigationInflaterView` 字段）
- 新增 reapply 前后状态诊断日志
- 单测（如新增可测纯逻辑）
- 设备冷启动矩阵验证 + 回归

**OUT / Must-NOT-Have**:
- 不改 `shouldConvert`/`shouldOverrideProxyHide` 语义与现有 14 个单测的断言
- 不改 `GestureConfigClient` fail-closed / 慢重试逻辑
- 不 hook `AbstractObserver` 本体（只在 fallback 需要时）
- 不处理与小白条无关的功能

## Verification strategy

三层：
1. **单测**: `:app:testDebugUnitTest --tests "com.cos.lspit.gesture.hook.SystemHideTest"` 全绿（现有 14+ 用例不回归）
2. **编译装机**: `:app:assembleDebug` + `adb install -r`
3. **设备矩阵**（核心，见 Todos T3/T4）: 冷启动 hide=1 场景 mBack tap/longpress 恢复 + 全回归

设备环境（已配好）:
- adb: `C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- 设备: `10.168.1.134:39015`（root，LSPosed 已激活模块）
- JAVA_HOME: `C:\Users\Administrator\.jdks\jdk-21.0.12.1+1`
- 构建: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests "com.cos.lspit.gesture.hook.SystemHideTest" --console=plain -q`

## Execution strategy

单 worker 顺序执行：T1（代码）→ T2（构建+单测+装机）→ T3（冷启动核心场景）→ T4（回归矩阵）→ F1-F4 终验。T3 若 onChange 重放无效，按 T1 内置的 fallback 分支迭代，不需要重新规划。

## Todos

- [ ] 1. 实现 reapply v2（onChange 重放 + resizeLayout + 诊断日志）
  What to do / Must NOT do:
  - 重写 `SystemHide.reapplyShown(XposedModule)`（[NavigationHandleHooks.java](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/app/src/main/java/com/cos/lspit/gesture/hook/NavigationHandleHooks.java) L892 附近，现逻辑为仅反射调 `updateSideGestureBarVisible(0)`）。新逻辑按顺序：
    1. 前置条件不变：`mback || barOnly` 且 `sNavbarViewRef` 有实例，否则 log `SYSHIDE_REAPPLY_SKIPPED`（保持现有日志 tag）。
    2. **主路径 — onChange 重放**: 反射获取 `com.oplus.systemui.navigationbar.observer.SwipeSideGestureBarTypeObserver` 的静态字段 `INSTANCE`，对其调 `onChange(boolean)` 传 `false`（用现有 `findMethod` 沿类层级找，`setAccessible(true)`）。成功 log `SYSHIDE_REAPPLY onChange-replay`。该调用内部会经 Hook 3 拦截的 proxy getter 写 `mSwipeSideGestureBarType=0` 并通知 listener。
    3. **belt-and-braces**: 保留现有 `updateSideGestureBarVisible(0)` 反射调用。
    4. **新增 inflater 恢复**: 从 navbar view（`sNavbarViewRef.get()`）沿类层级读字段 `mNavigationInflaterView`（类型 `Lcom/android/systemui/navigationbar/views/NavigationBarInflaterView;`，声明在 `NavigationBarView`），对该对象调 `resizeLayout()`（PUBLIC FINAL，反编译确认）。成功 log `SYSHIDE_REAPPLY resizeLayout`。
    5. **诊断日志**: 在 onChange 重放前、全部调用后各 log 一行 `SYSHIDE_REAPPLY_STATE`：包含 navbar view 的 `getVisibility()`、`getAlpha()`、`isAttachedToWindow()`（直接强转 `android.view.View` 调用——`OplusNavigationBarView` 继承 View，模块编译期有 android framework）。这样 T3 失败时 dump 直接定位哪一步没生效。
    6. **Fallback 注释**: 在代码注释里写明「若 onChange 重放无效（listener 未注册或语义不同），fallback 为直接调 navbar view 的 `updateViewVisible()`（private，findMethod 反射）+ inflater 的 `resizeLayout()`」——实现主路径时把 fallback 也实现成 catch 分支：若 `onChange` 抛异常或找不到，走 fallback 并 log `SYSHIDE_REAPPLY fallback updateViewVisible`。
  - Must NOT do: 不改 `hookProxyGetter`/`hookUtilsHideMode`/Hook 1/2 的现有逻辑；不动 `sConvertedHideActive`；所有新反射调用包 try/catch，单点失败不阻断后续步骤；日志防刷屏用现有 `sReappliedLogged` 模式（每种日志一个 flag，状态从 active→inactive 时重置）。
  - 注意: `onChange(false)` 与 config listener 同在主线程回调（`GestureConfigClient.addConfigListener` 已保证主线程），直接同步调用即可，无需 post。
  - 参考（executor 无上下文，务必读）:
    - [NavigationHandleHooks.java](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/app/src/main/java/com/cos/lspit/gesture/hook/NavigationHandleHooks.java) L720-950：SystemHide 全部现有代码、字段区、findMethod 辅助、log() 辅助
    - [GestureConfigClient.java](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/app/src/main/java/com/cos/lspit/gesture/hook/GestureConfigClient.java) L84-86/L197-206：addConfigListener 主线程回调机制
    - 反编译 `SwipeSideGestureBarTypeObserver.onChange(Z)` / `onRegister`: [c3_dis_full.txt](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/apk_extract/c3_dis_full.txt) L1704511-L1704700（onChange 经 Companion proxy getter 写静态字段 + super.onChange 通知 listener——这就是用户手动 toggle 的恢复路径）
    - 反编译 `resizeLayout`: [c4_full2.txt](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/apk_extract/c4_full2.txt) L1114262-L1114300（isHideNavBarGestureMode ? GONE : VISIBLE + inflateChildren + updateCurrentView + getDefaultLayout）
    - 反编译 `updateSideGestureBarVisible` + `updateWindowAlpha`: c4_full2.txt L1117398-L1117470（arg=0 → homeHandle VISIBLE + inflater VISIBLE + alpha 1.0，`updateWindowAlpha` 确认 arg 是 View visibility 码：0→#3f80=1.0f，非0→0.0f）
  - 若新增纯逻辑（如 reapply 步骤选择函数），加对应单测到 [SystemHideTest.java](F:/Downloads/Git/COS.worktrees/gesture-interception-desktop-launch/app/src/test/java/com/cos/lspit/gesture/hook/SystemHideTest.java)；纯反射编排无法单测，不强求。
  - Acceptance criteria (agent-executable): `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests "com.cos.lspit.gesture.hook.SystemHideTest" --console=plain -q` 退出码 0，无编译错误，现有用例零失败。
  - QA scenarios: happy = 编译+单测绿（输出存 `.omo/evidence/task-1-build.txt`）；failure = 故意在 onChange 反射处传错方法名编译仍过（反射运行时才发现），确认 catch 分支日志路径存在（代码 review 确认每个反射调用都有独立 try/catch）。
  - Commit: Y | feat(hook): replay settings-observer onChange to restore navbar after cold-boot hide

- [ ] 2. 装机并跑冷启动核心场景（原 bug 复现场景）
  What to do / Must NOT do:
  - 设备初始态: 确认 `adb -s 10.168.1.134:39015 shell settings get secure gesture_side_hide_bar_prevention_enable` 为 `0`（当前实测值）→ 先改为 `1`：`adb shell settings put secure gesture_side_hide_bar_prevention_enable 1`
  - 模块侧保持: mback=true, barOnly=true, barHidden=true（config provider 已就绪）
  - 复现冷启动: `adb install -r app\build\outputs\apk\debug\app-debug.apk`（装新 APK 使模块 app 进 stopped 态，模拟冷启动 config 不可达）→ `adb shell su -c "killall com.android.systemui"` → `Start-Sleep 25`
  - 清 stopped 态触发慢重试: `adb shell "monkey -p com.cos.lspit.gesture -c android.intent.category.LAUNCHER 1"` → **立即** `adb shell input keyevent HOME`（陷阱: 不 HOME 则 MainActivity 吃掉后续 input tap）→ `Start-Sleep 35`（等 30s 慢重试 → CONFIG_UPDATE → reapply）
  - 验证日志链: `adb shell "logcat -d | grep -a 'COS16-Gesture' | grep -aE 'CONFIG_UPDATE|SYSHIDE_REAPPLY|SYSHIDE_UTILSHIDE'"`，必须看到: `CONFIG_UPDATE`（或 CONFIG_REFRESH）→ `SYSHIDE_REAPPLY onChange-replay`（或 fallback 日志）→ `SYSHIDE_REAPPLY_STATE` 前后两行，后行 visibility=0(VISIBLE)
  - 验证窗口状态: `adb shell su -c "dumpsys window windows | grep -a 'NavigationBar_displayId_0' -A8 | grep -aE 'alpha=|isVisibleRequested'"` → 期望 `alpha=1.0`、`isVisibleRequested=true`；`adb shell su -c "dumpsys input | grep -a -B2 -A2 NavigationBar | head -20"` → 期望无 `NOT_TOUCHABLE`、`touchableRegion` 非空
  - 验证 mBack: 记 `adb shell date +%H:%M:%S` → `adb shell input tap 540 2345` → sleep 2 → `adb shell input swipe 539 2345 541 2345 1600` → sleep 2 → `adb shell "logcat -d | grep -a 'COS16-Gesture' | grep -aE 'MBACK (DOWN|TAP|LONGPRESS)|INJECT' | tail -8"` → 期望 tap 产生 `MBACK TAP ... INJECT ok=true`、长按产生 `MBACK LONGPRESS ... INJECT ok=true`
  - Must NOT do: 不得在验证中途 toggle 系统设置（那会走 onChange 正常路径掩盖 bug）；日志 grep 用 `logcat -d | grep -a`（直接 `-s TAG` 常为空，buffer 被刷）。
  - Acceptance criteria: 上述日志链 + 窗口状态 + MBACK TAP/LONGPRESS INJECT 全部出现；若 `SYSHIDE_REAPPLY_FAILED` 或窗口仍 alpha=0，用 `SYSHIDE_REAPPLY_STATE` 诊断行定位是 onChange 无效还是 resizeLayout 无效，按 T1 注释中的 fallback 迭代（换 fallback 主路径重编重测），不算计划失败。
  - QA scenarios: happy = 冷启动后无需任何 toggle，mBack 直接可用（存 `.omo/evidence/task-2-coldboot.log`）；failure = 模块 mback/barOnly 全关时冷启动 → 系统真隐藏生效（窗口 alpha=0 正常，**不应**出现 SYSHIDE_REAPPLY——reapply 前置条件不满足），存 `.omo/evidence/task-2-disabled.log`
  - Commit: N（验证性 todo）

- [ ] 3. 回归矩阵
  What to do / Must NOT do:
  - (a) **live toggle**: 场景 2 成功后，`settings put ... 0` → 等 3s → `settings put ... 1` → tap/longpress 仍出 MBACK INJECT（原有 Hook 3/4 路径回归）
  - (b) **barHidden**: 模块关「显示小白条」再开（通过模块 app UI 或直接改 prefs 后 force-stop 模块 app + 等 30s 慢重试）→ mBack 不受影响、小白条显示状态正确切换
  - (c) **模块全关**: mback=false + barOnly=false → 系统 hide=1 → 窗口应真隐藏（alpha=0），无 OVERRIDE 日志刷屏（`sProxyOverrideLogged`/`sUtilsHideLogged` 复位逻辑）
  - (d) **慢重试回归**: force-stop 模块 app → killall SystemUI → 35s 内 CONFIG_UPDATE 自愈（上轮已验证过，确认未被本轮改动破坏）
  - (e) **无系统隐藏**: setting=0 + mback=true → 一切照常（小白条显示、tap/longpress 正常）——防止 reapply 在不需要时误触发（确认 `shouldOverrideProxyHide(0,...)`=false → reapply 不跑 or 跑了也无害）
  - Must NOT do: 每个子场景前记录设备时间戳再注入再按时间 grep（用户可能在用手机）；每个场景之间 killall SystemUI 复位状态。
  - Acceptance criteria: (a)-(e) 全过，证据存 `.omo/evidence/task-3-regression-<x>.log`
  - QA scenarios: happy = 5 场景全绿；failure = 任一场景失败 → 记录日志 + 回到对应 hook 分析（不在本计划内扩大范围）
  - Commit: N

## Final verification wave

- [ ] F1. Plan compliance audit
  对照本计划逐 todo 核对：Hook 1-5 语义未变、`shouldConvert`/`shouldOverrideProxyHide` 未改、`GestureConfigClient` 未改、新增代码全部在 `SystemHide` 内。`git diff` 审查。
- [ ] F2. Code quality review
  反射调用全部独立 try/catch；日志防刷屏 flag 复位正确（inactive→active 转换时重置）；无内存泄漏（WeakReference 使用正确）；单测全绿。
- [ ] F3. Real manual QA
  重跑 T2 完整场景一遍（从 install 开始），确认非偶发。附带用户实操路径验证：重启手机（真冷启动）后直接 tap 小白条区域 → 返回生效。`adb reboot` 后等 90s（开机+SystemUI 起慢）再测。
- [ ] F4. Scope fidelity
  确认无超出 Scope IN 的改动（`git status` 无意外文件）。

## Commit strategy

分批提交，conventional 风格，中文 scope 可选但保持仓库现有风格（查 `git log --oneline -5` 对齐）：
1. `feat(hook): replay settings-observer onChange to restore navbar after cold-boot hide` — T1 代码 + 单测
2. 若 fallback 路径被启用且改动较大，单独 `fix(hook): ...`
每条带 `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`。
证据文件（.omo/evidence/）不提交。

## Success criteria

1. 冷启动（killall / 重装 / 重启手机）+ 系统 hide=1 + 模块 mback=true：**无需任何手动 toggle**，小白条区域 tap → Back、长按 → Home，日志有完整 `CONFIG_UPDATE → SYSHIDE_REAPPLY → MBACK ... INJECT ok=true` 链。
2. 窗口状态: `alpha=1.0`、`isVisibleRequested=true`、无 `NOT_TOUCHABLE`、`touchableRegion` 非空。
3. 回归矩阵 (a)-(e) 全过，14+ 单测全绿。
4. 模块全关时系统 hide 行为与无模块时一致（真隐藏）。
