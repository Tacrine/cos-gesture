# syshide-mback-touch-fix - Work Plan

## TL;DR (For humans)

**What you'll get**: 系统设置关闭小白条 + 模块开启显示小白条时，mBack 点击（返回）与长按（Home）恢复正常，同时保持现状的其余正确行为：小白条可见、应用内容不上移、barOnly 底部拦截正常。

**Why this approach**: 上一轮修复（syshide-mback-compat）只拦截了 `updateWindowAlpha`（窗口 alpha），但系统隐藏的另一副作用 —— `updateViewVisible$1.run()` 把 `NavigationBarInflaterView` 设为 GONE —— 未被处理。home handle 位于 inflater 内部，父视图 GONE 后不参与触摸分发，mBack 因此失效。本轮改为 hook 最上游的取值函数 `NavBarSettingsValueProxy.getSwipeSideGestureBarType`，让整个 SystemUI 按"显示"状态运行（inflater VISIBLE、handle 可触摸、alpha=1.0、inset 不变），单一 chokepoint 覆盖所有下游路径。

**What it will NOT do**: 不改变 mback/barOnly 都关闭时的行为（此时尊重系统真隐藏，应用内容上移）；不修改 barOnly/TapShield/长度等既有功能逻辑；不修复与本问题无关的 mBack UP 事件偶发丢失问题（如验证中复现另行立项）。

**Effort**: 1 个 hook + 纯函数 + 单测 + 设备验证，约 1-2 小时。

**Risk**: 低。hook 点为单一 getter，失败时优雅降级（NO_MATCH 日志 + 现有 Hook 1/2 仍兜底 alpha）。若 GestureUpEx guide bar 与模块 handle 同时绘制出现双条，验证步骤会捕获并需在 run() 层面补 hook（备选方案已写入任务）。

**Decisions I made for you**:
- 选择上游 proxy hook 而非 hook `$1` Runnable 或 `isHideNavBarGestureMode()`：单一 chokepoint，语义清晰，影响面即"整个系统按显示处理"，且不动 run() 的其余分支逻辑。
- 保留现有 Hook 1（updateSideGestureBarVisible）/ Hook 2（updateWindowAlpha）作为纵深防御：proxy hook 生效后它们基本不触发，但可拦截绕过 proxy 直接读 Settings.Secure 的路径。
- 覆盖条件沿用 `mbackEnabled || barOnlyEnabled`，与 shouldConvert 一致。

## Scope

**IN**:
- NavigationHandleHooks.SystemHide 增加 proxy getter hook（Companion 实例方法 + 静态包装方法两处）
- 新增纯函数 `shouldOverrideProxyHide` + SystemHideTest 单测
- 编译、ADB 安装、设备端验证矩阵（mBack tap/long、barOnly、内容不上移、模块 barHidden 组合）

**OUT**:
- 不改动 HiddenBar/barOnly/TapShield/宽度等既有逻辑
- 不处理 mback/barBoth 均关闭场景的行为（已正确）
- 不重构 SystemHide 现有 hook 结构

## Verification strategy

设备 10.168.1.134:39015，系统设置小白条=隐藏（setting=1），模块显示小白条=开、mBack=开、barOnly=开：

1. `dumpsys window windows` → NavigationBar `alpha=1.0`，`touchableRegion` 覆盖 handle 区域
2. `adb shell input tap 540 2340` → topResumedActivity 切换（Back 生效）
3. `adb shell input swipe 540 2340 540 2340 1500` → Home（长按生效）
4. LSPosed 日志出现 `SYSHIDE_PROXY_OVERRIDE type=1->0`
5. 模块 barHidden=开（仅隐藏像素）时重复 2/3 → mBack 仍生效（核心回归项，即用户报告的原 bug 场景）
6. 单测 `gradlew test` 全绿

## Execution strategy

单 wave 串行：fix-1 → fix-2 → fix-3，随后 F 终验并行。fix-1/fix-2 可本地完成，fix-3 需设备在线。

## Todos

- [ ] 1. 实现 proxy getter hook（chokepoint 转换）
  What to do / Must NOT do: 在 `NavigationHandleHooks.SystemHide.register()` 中新增 Hook 3：
  - 类 `com.oplus.systemui.navigationbar.gesture.proxy.NavBarSettingsValueProxy$Companion`，方法 `getSwipeSideGestureBarType(Context)I`（c3.dex a44d48）
  - 类 `com.oplus.systemui.navigationbar.gesture.proxy.NavBarSettingsValueProxy`（静态包装，c3.dex a45ad0，内部委托 Companion，两处都 hook 防止静态直调绕过）
  - 逻辑：`int real = (Integer) chain.getArg(0 参数为 Context，返回值为 int)`——注意此方法是**返回值拦截**而非参数拦截：`Object result = chain.proceed(); int real = (Integer) result;` 若 `shouldOverrideProxyHide(real, mback, barOnly)` 为真则 `return 0`，日志 `SYSHIDE_PROXY_OVERRIDE type=1->0 mback=... barOnly=...`（level 5，避免高频刷屏可只在值实际改变时记录；首次记录后同值静默）
  - 新增纯函数 `static boolean shouldOverrideProxyHide(int realType, boolean mbackEnabled, boolean barOnlyEnabled) { return realType != 0 && (mbackEnabled || barOnlyEnabled); }`
  - 读取 mback/barOnly 用 `GestureConfigClient.isMbackEnabled()/isBarOnlyEnabled()`（SystemUI 进程内可用，与现有 Hook 1/2 一致）
  - Must NOT: 不修改现有 Hook 1/Hook 2 的注册与逻辑；不 hook setter `setSwipeSideGestureBarType`；不在 hook 内抛异常（PROTECTIVE 模式 + try/catch 包裹）
  Parallelization: Wave 1 | Blocked by: none | Blocks: 2, 3
  References (executor has NO interview context):
  - app/src/main/java/com/cos/lspit/gesture/hook/NavigationHandleHooks.java:723-832（SystemHide 类、register() 现有 hook 写法、log() 用法）
  - app/src/main/java/com/cos/lspit/gesture/hook/NavigationHandleHooks.java:748-750（shouldConvert 纯函数样板）
  - apk_extract/c3_dis_full.txt a44d48（Companion.getSwipeSideGestureBarType 定义）、a45ad0/a45ae4（静态包装委托 Companion）、a54c08（SwipeSideGestureBarTypeObserver.onChange 经 proxy 读取——证明此 chokepoint 覆盖 observer）
  - apk_extract/c4_full2.txt 21f1b4（updateViewVisible$1.run 依赖 mSwipeSideGestureBarType 走隐藏分支）
  Acceptance criteria (agent-executable): `.\gradlew.bat compileDebugJavaWithJavac` 编译通过；代码中存在 `shouldOverrideProxyHide` 且被 proxy hook 调用；两个类名 hook 均有 try/catch + NO_MATCH 日志
  QA scenarios (name the exact tool + invocation): happy: 编译通过且 grep 到 SYSHIDE_PROXY_OVERRIDE 字符串; failure: 类名不存在时（模拟）注册走 NO_MATCH 分支不崩溃——由既有 try/catch 结构保证，review 确认; Evidence: .omo/evidence/task-1-syshide-mback-touch-fix.txt
  Commit: Y | feat(syshide): hook NavBarSettingsValueProxy getter as upstream chokepoint

- [ ] 2. 扩展 SystemHideTest 单测
  What to do / Must NOT do: 在 SystemHideTest.java 新增 `shouldOverrideProxyHide` 测试组，覆盖：
  - `proxyHide_typeOne_mbackOn` → true（核心场景：系统隐藏 + mBack）
  - `proxyHide_typeOne_barOnlyOn_mbackOff` → true
  - `proxyHide_typeOne_bothOff` → false（尊重系统真隐藏）
  - `proxyHide_typeZero_mbackOn` → false（系统显示时绝不干预）
  - `proxyHide_unexpectedValue_mbackOn`（如 type=2）→ true（任何非 0 视为隐藏请求，与 shouldConvert 语义一致）
  Must NOT: 不改动现有 10 个 shouldConvert 用例
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 3
  References: app/src/test/java/com/cos/lspit/gesture/hook/SystemHideTest.java（现有测试写法）
  Acceptance criteria (agent-executable): `.\gradlew.bat test --tests "com.cos.lspit.gesture.hook.SystemHideTest"` 全绿
  QA scenarios: happy: 15 个用例全部 PASS; failure: 若 shouldOverrideProxyHide 误写为 `== 1`，unexpectedValue 用例失败暴露; Evidence: .omo/evidence/task-2-syshide-mback-touch-fix.txt
  Commit: Y | test(syshide): cover shouldOverrideProxyHide decision matrix

- [ ] 3. 编译 + ADB 安装 + 设备验证矩阵
  What to do / Must NOT do:
  - 前置确认：`settings get secure gesture_side_hide_bar_prevention_enable` = 1（系统隐藏开）；模块设置：显示小白条=开、mBack=开、barOnly=开。若不满足则先按此调整（系统设置页关闭小白条开关，模块 UI 开启三项）
  - `.\gradlew.bat assembleDebug`（JAVA_HOME=C:\Users\Administrator\.jdks\jdk-21.0.12.1+1）→ `adb install -r` → 等待 LSPosed 重新注入 SystemUI（观察日志出现 `SYSHIDE_HOOK_OK updateSideGestureBarVisible` + `SYSHIDE_HOOK_OK updateWindowAlpha` + 新增 proxy hook OK 日志）
  - SystemUI 进程存在周期性 proxy 读取（onChange/onRegister），若 hook 装好后无触发，执行 `adb shell settings put secure gesture_side_hide_bar_prevention_enable 0` 再 `... 1` 强制 observer onChange
  - 验证矩阵（每项记录原始输出）：
    a. `adb shell dumpsys window windows` → NavigationBar_displayId_0 `alpha=1.0` 且在 visible windows 列表
    b. `adb shell input tap 540 2340` 后 `adb shell dumpsys activity activities | grep topResumedActivity` → 非 launcher 的应用（先打开设置页）被切走 = Back 生效
    c. `adb shell input swipe 540 2340 540 2340 1500` → topResumedActivity 变为 com.android.launcher = 长按 Home 生效
    d. LSPosed 日志 grep SYSHIDE_PROXY_OVERRIDE 至少一条
    e. 【核心回归】模块 UI 切换 barHidden=开（隐藏像素）→ 重复 b/c → mBack 仍生效；再切回
    f. 内容不上移：验证期间 launcher/设置页面底部布局无跳动（对比 NavigationBar frame=[0,2244-1080,2376] 不变）
    g. 【备选触发】若 c 步骤长按偶发失效（已知 mBack UP 事件偶发丢失的既有问题），记录发生频率到 evidence，不阻塞本任务验收（区分于本次修复的完全失效）
  - 若出现双小白条（GestureUpEx guide bar 与模块 handle 同时绘制）：说明 run() 在 proxy hook 装载前已执行过一次，执行 `adb shell pkill com.android.systemui`（LSPosed 环境下 SystemUI 自动重启并重新注入）后复测；若重启后仍双条，将备选方案（追加 hook `OplusNavigationBarView$updateViewVisible$1.run()` 强制走显示分支）作为新任务回报，不自行扩大改动
  Must NOT: 不修改设备上与本模块无关的设置；不在验证中重启整机
  Parallelization: Wave 1 | Blocked by: 1, 2 | Blocks: F1-F4
  References:
  - adb: C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe，设备 10.168.1.134:39015
  - LSPosed 日志: /data/adb/lspd/log/modules_*.log（取最新分卷，grep 'SYSHIDE'）
  - apk_extract/c3_dis_full.txt a45ad0（静态包装）、a54c40（onRegister 启动读取——重启 SystemUI 后即触发 proxy hook）
  Acceptance criteria (agent-executable): 矩阵 a-f 全部通过，evidence 文件含每条命令原始输出
  QA scenarios: happy: 系统隐藏+模块显示时 tap→Back、long→Home、barOnly 拦截、内容不上移; failure: mBack 仍失效 → grep 日志确认 SYSHIDE_PROXY_OVERRIDE 是否出现；未出现则检查 hook 注册日志（NO_MATCH?），出现但仍失效则 dump view hierarchy（adb shell uiautomator dump）确认 handle 可见性，将结果写入 evidence 并升级为备选方案评估; Evidence: .omo/evidence/task-3-syshide-mback-touch-fix.txt
  Commit: N | 验证任务，无代码变更（如触发备选方案则另立任务）

## Final verification wave

- [ ] F1. Plan compliance audit
  对照本计划逐条核对：Hook 3 两处类名、纯函数语义、5 个新单测、验证矩阵 a-f 全部执行并留档。Evidence: .omo/evidence/F1-syshide-mback-touch-fix.txt
- [ ] F2. Code quality review
  审查 NavigationHandleHooks.java 变更：hook 异常安全（PROTECTIVE + try/catch）、日志降噪（override 只记首次/变更）、无对既有 Hook 1/2 的行为回归。Evidence: .omo/evidence/F2-syshide-mback-touch-fix.txt
- [ ] F3. Real manual QA
  设备实测完整用户旅程：系统设置隐藏小白条 → 模块显示小白条 → tap 返回 / 长按 Home / 底部滑动拦截 / barHidden 切换后 mBack 仍活 / 关闭 mback+barOnly 后系统真隐藏（内容上移）。Evidence: .omo/evidence/F3-syshide-mback-touch-fix.txt
- [ ] F4. Scope fidelity
  确认无越界改动：HiddenBar/barOnly/TapShield/宽度逻辑零 diff；mback/barOnly 均关场景行为不变。Evidence: .omo/evidence/F4-syshide-mback-touch-fix.txt

## Commit strategy

- task 1+2 可合并为一次提交或按 todo 分别提交（feat + test），提交信息遵循仓库现有 conventional 风格，附 Co-authored-by: Copilot trailer
- task 3 验证任务不产生提交

## Success criteria

1. 系统设置关闭小白条 + 模块显示小白条：mBack 点击=返回、长按=Home，稳定生效（用户报告的 bug 消失）
2. 同场景下小白条可见、应用内容不上移、barOnly 拦截正常（不回归 round-2 已修复项）
3. barHidden=开时仅隐藏像素，mBack 不受影响
4. mback/barOnly 均关时系统真隐藏照常（内容上移）
5. 全部单测通过，验证矩阵留档
