# syshide-mback-compat - Work Plan

## TL;DR (For humans)

**What you'll get**: 系统设置「隐藏手势提示条」开启时,模块拦截 SystemUI 的"整窗真隐藏",转换为与 OLED `barHidden` 相同的"只不绘制像素":handle view 保留、可触摸、布局/导航 insets 不变,mBack 点击/长按、barOnly 拦截、TapShield、长度自定义全部照常工作。系统开关语义 == 模块 barHidden 效果。

**Why this approach**: 反编译 + 真机诊断已确认完整链路(证据见下)。系统的真隐藏走 `OplusNavigationBarView.updateSwipeUpGestureBarVisible(1)` → 导航栏窗口 `WindowManager.LayoutParams.alpha=0` + guide bar GONE,触摸路由随之失效。在该**唯一漏斗方法**上 before-hook 强制参数为 0,即可阻止真隐藏,再复用现有 HiddenBar 吞绘制机制熄灭像素——最小侵入、单点拦截。

**What it will NOT do**:
- 不改变模块 barHidden(OLED)开关的现有行为(两者并存,语义等价)。
- 不在 mBack、barOnly、TapShield **全部关闭**时做转换——功能全关则尊重系统真隐藏(内容上移),因为没有任何功能需要白条区域(默认决策,可在 UI 后续调整)。
- 不修改 `navbar_gesture_visibility` 派生键(保持系统语义,launcher 等其他消费方不受影响)。
- 不新增模块配置项/version bump(config 链路零改动)。

**Effort**: 2 个实现 todo + 1 个测试 todo + 1 个设备验证 todo,约半天。

**Risk**: 
- `OplusNavigationBarView` 类名/方法签名跨 ColorOS 版本可能变化 → hook 全部 try/catch 守护,失败仅记日志降级(退回现状:系统真隐藏,mBack 失效但不崩溃)。
- 转换激活时系统派生键 `navbar_gesture_visibility` 仍为 0,若有第三方(launcher 任务栏等)消费该键可能表现异常 → 已确认 SystemUI 内无读方,launcher 侧留观察项。
- `updateSwipeUpGestureBarVisible` 仅在 `isGestureUpMode()` 时生效——设备 navigation_mode=2(手势)满足;三键导航模式下本就无小白条。

**Decisions I made for you**(默认决策,可否决):
1. 我把此需求视为明确结果(你已确认"系统隐藏 = 不绘制而非真隐藏"),无需再采访。
2. 功能全关时尊重系统真隐藏(fork A,未获明确答复,采用推荐默认)。
3. 转换激活条件 = `mback || barOnly`(与现有 `Appearance.applyVisibility` 强制显示逻辑一致);TapShield 单独开启不激活转换。
4. 模块 UI 的"显示小白条"开关改为读写**真实系统键** `gesture_side_hide_bar_prevention_enable`(当前写的 `hide_gesture_bar_enable` 是另一个开关——"上滑手势条类型",这是本次 bug 的根因之一,必须修)。

---

## 诊断证据(执行者无需重新定位)

真机 + 反编译(设备 10.168.1.134:39015,ColorOS 16 PKG110)已确认:

| 事实 | 证据 |
|---|---|
| 系统用户开关写 `Settings.Secure gesture_side_hide_bar_prevention_enable`(1=隐藏) | 用户切换时 settings 全量 diff 唯一手势相关变化 0→1;`NavBarSettingsValueProxy.KEY_SWIPE_SIDE_GESTURE_BAR_TYPE = "gesture_side_hide_bar_prevention_enable"`(L31) |
| 模块当前观察的 `hide_gesture_bar_enable` 是**另一个**开关 | `KEY_SWIPE_UP_GESTURE_BAR_TYPE = "hide_gesture_bar_enable"`(L36),语义为"上滑手势条类型" |
| 变更入口:`NavBarReceiver` case 3 → `updateNavGestureVisibility` + `updateSwipeUpGestureBarVisible` | NavBarReceiver.java L72-104 |
| 真隐藏执行:`updateSwipeUpGestureBarVisible(i)` → guide bar `setVisibility(8/GONE)` + `updateWindowAlpha(8)` → 导航栏窗口 `lp.alpha=0` | OplusNavigationBarView.java L368-373,updateWindowAlpha 全文 |
| 窗口 alpha=0 后 insets 不变(bottom=48)、窗口仍存在,但 `handleValidTouchEvent` 不再被调 → mBack 死 | dumpsys + 10:12 tap 无 MBACK 日志;barOnly(SideGestureDetector 路径)不受影响仍工作 |
| `navbar_gesture_visibility` 是 SystemUI 自己写的**派生**键(NavBarUtils L117-139),非用户开关 | NavBarUtils.updateNavGestureVisibility |

反编译源码位置(PC,供参考,勿提交):`%TEMP%\sysui-src\sources\`。

---

## Scope

**IN**:
1. [SystemGestureBarSettings.kt](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\java\com\cos\lspit\gesture\config\SystemGestureBarSettings.kt) — 改读写真实系统键。
2. [NavigationHandleHooks.java](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\java\com\cos\lspit\gesture\hook\NavigationHandleHooks.java) — 新增 SystemHide 转换 hook;HiddenBar 条件扩展;SETTING 常量更新。
3. [GestureSettingsScreen.kt](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\java\com\cos\lspit\gesture\ui\GestureSettingsScreen.kt) — "显示小白条"开关文案/helper 更新(联动真实系统键后行为说明)。
4. [strings.xml](f:\Downloads\Git\COS.worktrees\gesture-interception-desktop-launch\app\src\main\res\values\strings.xml) — 文案。
5. 设备验证矩阵。

**OUT**:
- OLED barHidden 功能的行为修改(已完成并验证)。
- 三键导航模式、任务栏模式支持。
- `navbar_gesture_visibility` 派生键的写入/干预。
- config 链路(GestureConfig/ConfigProvider/ConfigStore/GestureConfigClient)任何改动。

## Verification strategy

- 单元测试:转换判定纯函数(`shouldConvertSystemHide`)用例;`.\gradlew.bat :app:testDebugUnitTest`。
- 设备验证(adb,10.168.1.134:39015):像素采样(PIL,注意用 `cmd /c` 重定向避免 UTF-16 损坏 PNG)+ logcat(`logcat -d | Select-String COS16-Gesture`)+ `input tap`/长按模拟。SystemUI 重载必须 `su -c "killall -9 com.android.systemui"`。
- 每场景截图与日志存 `.omo/evidence/syshide-mback-compat/`。

## Execution strategy

Wave 1(todo 1-2 可并行,无共享文件冲突则顺序执行更稳:todo 2 先行,todo 1 依赖其常量命名):
- Todo 2 是核心 hook;Todo 1 是 UI/设置键修正。
- Wave 2: todo 3 测试;Wave 3: todo 4 设备矩阵;Wave 4: F1-F4 终验。

## Todos

- [ ] 1. 实现 SystemHide 转换 hook(NavigationHandleHooks.java)
  What to do / Must NOT do: 在 `register()` 中新增静态类 `SystemHide`:
  - 反射定位 `com.oplusos.systemui.navigationbar.OplusNavigationBarView`(用 handle view 的 classloader,从 `View.getParent()` 链上找 instanceof 该类名的实例亦可,推荐:在 `applyVisibilityHandles`/handle attach 时由 `v.getRootView()` 向上遍历 parent 找到 OplusNavigationBarView 实例并取其 Class,避免硬编码 classloader 查找失败);hook 其 `updateSwipeUpGestureBarVisible(int)` before-intercept:
    - 当 `arg != 0 && (GestureConfigClient.isMbackEnabled() || GestureConfigClient.isBarOnlyEnabled())` → 记 `SYSHIDE_CONVERTED arg=1→0 mback=.. barOnly=..`,调用 `chain.proceed` 前把参数改为 0(libxposed:通过 `chain.args[0]` 或重新 proceed 传参,参考现有 SideBackHooker 的参数改写方式)。
    - 同时维护 `static volatile boolean sConvertedHideActive`,转换发生时置 true,arg==0 或功能全关时置 false。
  - `HiddenBar` 的 onDraw 吞绘制条件从 `isBarHiddenEnabled()` 扩展为 `isBarHiddenEnabled() || SystemHide.sConvertedHideActive`(shade 展开守卫保留);日志标签区分 `HIDDENBAR_SKIP_DRAW`(模块 barHidden)与 `HIDDENBAR_SKIP_DRAW_SYSHIDE`(系统转换),便于验证。
  - 顶层常量 `SETTING_HIDE_GESTURE_BAR` 值改为 `"gesture_side_hide_bar_prevention_enable"`(或新增 `SETTING_SIDE_GESTURE_BAR_HIDE` 并替换 applyVisibility 中的读取;**旧键 `hide_gesture_bar_enable` 的读取删除**)。`Appearance.applyVisibility` 逻辑保持 `show = mback || barOnly || hide==0` 不变(键换后该守卫才真正生效)。
  - Must NOT: 不得 hook `NavBarUtils.updateNavGestureVisibility`(静态方法,影响面大);不得修改 `navbar_gesture_visibility`;不得在 onDraw 高频路径读 Settings.Secure(只用 volatile 标志)。
  Parallelization: Wave 1 | Blocked by: 无 | Blocks: 2, 4
  References (executor has NO interview context): NavigationHandleHooks.java L162(register 挂 hook 处)、L181-210(hookTouch 反射+intercept 模式参考)、L628-695(HiddenBar 现有实现)、L719-736(Appearance.applyVisibility 与 SETTING_HIDE_GESTURE_BAR 使用)、L744+(MBack.handleValidTouchEvent 调用链);SideBackHooker.java L99-125(参数检查与日志模式);libxposed API:`module.hook(method).setExceptionMode(PROTECTIVE).intercept(chain -> ...)`,参数改写参考 libxposed `XposedInterface` beforeHookedMember/chain args 能力(若 chain 不支持改参,则 before-hook 中直接吞掉原调用并反射调用 `updateSwipeUpGestureBarVisible(0)`/或仅吞掉返回 null,再由 applyVisibility 兜底强制 view VISIBLE——两条路都写进代码,先试 chain 改参)。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:assembleDebug` 编译通过;`javap`/反编译产物中存在 `SYSHIDE_CONVERTED` 日志字面量(grep class 输出即可,或直接信任源码编译)。
  QA scenarios (name the exact tool + invocation): happy: 编译成功无警告级错误;failure: 类/方法找不到时 try/catch 记 `SYSHIDE_NO_MATCH` 且不 crash(代码审查断言 catch 分支存在),Evidence `.omo/evidence/syshide-mback-compat/t1-build.log`
  Commit: Y | feat(hook): convert system gesture-bar hide to pixel-level hide when mBack/barOnly active

- [ ] 2. 修正模块"显示小白条"开关联动真实系统键(SystemGestureBarSettings.kt + GestureSettingsScreen.kt + strings.xml)
  What to do / Must NOT do:
  - `SystemGestureBarSettings` 读写键从 `hide_gesture_bar_enable` 改为 `gesture_side_hide_bar_prevention_enable`(默认值 0=显示;写入语义:开关"显示小白条"ON → 写 0,OFF → 写 1)。
  - GestureSettingsScreen 该开关行:开启条件/文案不变,helper 文案更新,说明"关闭后若 mBack/仅手势条拦截开启,系统隐藏将被转换为不绘制像素,功能不受影响"。
  - strings.xml 更新对应 helper 文案(中文)。
  - Must NOT: 不得改动 barHidden(OLED)开关行及其文案;不得新增配置字段;不得改 GestureSettingsScreen 其他开关逻辑。
  Parallelization: Wave 1 | Blocked by: 1(常量/键名一致) | Blocks: 4
  References: SystemGestureBarSettings.kt(全文 27 行,现写 `hide_gesture_bar_enable`);GestureSettingsScreen.kt L120-135(系统显示开关行)、L177+(barHidden 行,勿动);strings.xml(switch_show_gesture_bar / gesture_bar_helper 等现有 key,以实际为准)。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 全绿。
  QA scenarios: happy: 模块 UI 切换开关后 `adb shell settings get secure gesture_side_hide_bar_prevention_enable` 返回值与开关一致;failure: SystemUI 未运行/设置写入失败时不崩溃(catch 分支),Evidence `.omo/evidence/syshide-mback-compat/t2-ui-toggle.log`
  Commit: Y | fix(ui): mirror real system gesture-bar toggle key gesture_side_hide_bar_prevention_enable

- [ ] 3. 单元测试(转换判定)
  What to do / Must NOT do: 在现有测试模块(参考 GestureConfigTest.kt 所在 sourceSet)为转换判定纯函数补测试。若 todo 1 将判定抽为 `NavigationHandleHooks.shouldConvertSystemHide(boolean arg, boolean mback, boolean barOnly)`(static 纯函数),则用例覆盖:(1, true, false)→true、(1, false, true)→true、(1, false, false)→false、(0, *, *)→false 四类。若 Android 依赖导致 NavigationHandleHooks 不可单测,则把函数放到无依赖工具类再测。
  Must NOT: 不为 hook 副作用写假单测;不改既有测试。
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 4
  References: GestureConfigTest.kt(现有测试风格);app/build.gradle.kts(测试配置,以实际为准)。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest` 全绿,新用例出现在报告。
  QA scenarios: happy: 新用例通过;failure: 反例断言(全关不转换)通过,Evidence `.omo/evidence/syshide-mback-compat/t3-test.log`
  Commit: Y | test(hook): cover system-hide conversion decision

- [ ] 4. 设备验证矩阵(adb 安装 + 8 场景)
  What to do / Must NOT do: `adb install -r` 新 APK → `su -c "killall -9 com.android.systemui"` → 逐场景验证,每场景留截图+logcat 证据:
  1. 系统 UI 关白条(prevention=1)+ mback=1:白条区域像素暗(<60 RGB,采样 bar 中心,bar_width_dp 当前 108 → 中心 x≈540,y≈2350)+ `input tap 540 2340` 后 logcat 出现 `SYSHIDE_CONVERTED` 与 `MBACK TAP back`/`INJECT BACK ok=true`。
  2. 同状态长按(`input swipe 540 2340 540 2340 900`)→ `MBACK LONGPRESS home` + `INJECT HOME ok=true`。
  3. 同状态 barOnly:底部区域侧滑仍被拦截(PASS down/既有拦截日志)。
  4. 同状态宽度:白条隐藏但 `HANDLE_WIDTH` 日志出现(布局保留,不验证像素宽度因已隐藏——验证布局不变用 dumpsys window insets bottom=48 不变)。
  5. 系统 UI 开白条(prevention=0)+ mback=1:白条像素亮(>140 RGB)、点击 mBack 正常(回归)。
  6. 模块 barHidden=1 + prevention=0:像素暗、mBack 正常(既有 OLED 回归)。
  7. mback=0, barOnly=0, tapShield 任意 + prevention=1:系统真隐藏生效,模块不干预(无 SYSHIDE_CONVERTED 日志)。
  8. 重启 SystemUI 后场景 1 复验(持久性,防止只在 onChange 生效而 boot 路径漏)。
  Must NOT: 不得用 PowerShell `>` 直接重定向 PNG(UTF-16 损坏),必须 `cmd /c "adb exec-out screencap -p > file.png"`;验证完把 prevention 恢复为用户期望值(当前用户偏好:系统隐藏开启,保持 1)。
  Parallelization: Wave 3 | Blocked by: 1, 2, 3 | Blocks: F1-F4
  References: 诊断章节机制表;adb 路径 `C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe`,设备 10.168.1.134:39015;prefs 直改法 `/data/data/com.cos.lspit.gesture/shared_prefs/gesture_config.xml`(改后需 killall SystemUI)。
  Acceptance criteria (agent-executable): 8 场景证据文件齐全,场景 1-6、8 全部符合预期,场景 7 符合"不干预"预期。
  QA scenarios: happy+failure 即上述矩阵本身,Evidence `.omo/evidence/syshide-mback-compat/`(s1.png..s8.png + matrix.log)
  Commit: N |(仅证据文件,不入库)

## Final verification wave

- [ ] F1. Plan compliance audit
  对照本计划逐 todo 核对:改动的文件清单、Must NOT 清单(尤其:config 链路零改动、navbar_gesture_visibility 未被写入、onDraw 高频路径无 Settings.Secure 读取)、所有 `- [ ] N.` 行完成。Evidence `.omo/evidence/syshide-mback-compat/F1-audit.md`
- [ ] F2. Code quality review
  审查 NavigationHandleHooks.java 新增 SystemHide 类:volatile 语义、try/catch 守护、日志节流(SYSHIDE_CONVERTED 可能在每次切换触发,确认无高频刷屏)、与 HiddenBar/TapShield 交互无回归。Evidence `.omo/evidence/syshide-mback-compat/F2-review.md`
- [ ] F3. Real manual QA
  重跑场景 1/5/7(核心三态)+ 场景 8(重启持久),用户在场时请用户手动操作系统设置开关各一次确认 UI 联动。Evidence `.omo/evidence/syshide-mback-compat/F3-qa.md`
- [ ] F4. Scope fidelity
  确认无 scope 外文件被修改(`git status` 对照 IN/OUT 清单;注意工作区已有未提交的 OLED barHidden 改动,属上一计划,不得混入本次提交)。Evidence `.omo/evidence/syshide-mback-compat/F4-scope.md`

## Commit strategy

- 每 todo 单独 commit(见各 todo Commit 行),消息含 `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`。
- 工作区已有的 OLED barHidden 未提交改动:保持原状,由用户决定是否单独提交,本次提交不得包含(除非 F4 阶段用户明确要求)。

## Success criteria

1. 系统设置关闭小白条后:mBack 点击=BACK、长按=HOME 在白条原位置正常触发,像素不显示,应用内容不上移。
2. barOnly 拦截、TapShield、宽度自定义在系统隐藏态全部照常。
3. 系统开白条、模块 barHidden、功能全关三态行为与改造前一致(无回归)。
4. `:app:testDebugUnitTest` 与 `:app:assembleDebug` 全绿。
