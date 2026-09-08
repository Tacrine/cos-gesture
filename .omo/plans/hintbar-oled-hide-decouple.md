# hintbar-oled-hide-decouple - Work Plan

> Status: DECISION-COMPLETE — 待用户批准执行(执行由用户另行启动 worker session)
> Device: PKG110 / ColorOS 16 / 1080×2376 / density 2.75 / adb 10.168.1.134:39015
> Module: `com.cos.lspit.gesture` (SystemUI + Launcher 双 scope, libxposed API)
> Intent: CLEAR — 显示与触发解耦,OLED 防烧屏。无 owner-fork,默认值已采纳(见 Decisions)。

## TL;DR (For humans)

**What**: 新功能「隐藏小白条(OLED 防烧屏)」— 新开关 `barHidden`:小白条不再绘制(像素熄灭),但 mBack、barOnly 触发门控、TapShield 点击保护、长度自定义全部照常工作,触发区域几何完全不变。

**Why**: OLED 长期显示小白条会烧屏。当前 `Appearance.applyVisibility`(NavigationHandleHooks.java L625-642)在 mback/barOnly 开启时强制 `View.VISIBLE`,且 UI 在开 mBack/barOnly 时强制写系统 `setHintBarVisible(true)`(GestureSettingsScreen.kt L138-141, L161-164)— 显示与功能耦合,想防烧屏只能放弃全部功能。

**解耦原理**: 隐藏方式 = 吞掉 `OplusNavigationHandle.onDraw`(跳过绘制)。view 保持 `VISIBLE` + 正常 layout + 正常 alpha → 触摸分发、`handleValidTouchEvent`(mBack)、`viewScreenLeft+getWidth()` 几何(barOnly/TapShield/宽度)全部不受影响;不绘制 = 画面无输出 = OLED 像素熄灭。

**NOT**: 不改 launcher scope(LauncherRegionHooker);不动 mBack/barOnly/side-back 拦截逻辑;不动 TapShield;不删系统 hide_gesture_bar_enable 通道(无 mback/barOnly 时仍可用);不做定时/条件显示(如下班自动隐藏)。

**Effort**: 小-中。config 全链路(既有模板第 4 次复用)+ 1 个 onDraw hook + UI 开关与文案。

**Risk**: 低 — 见风险表。最大不确定点 = 系统是否在白条 view 外部(父层/drawable 层)绘制白条,已在验收规则中含 fallback(setAlpha 快路径 hook)。

**Decisions I made for you**(可否决,均为可逆配置级):
1. 隐藏机制 = 吞 `OplusNavigationHandle.onDraw`(view 保持 VISIBLE,触摸/几何全保留);若真机仍有像素输出(父层绘制),fallback = `View.setAlpha` hook + 快路径 guard(仅 barHidden 开启且 this 为 handle 时拦截)。决策规则已写死在 Todo 2,无需现场判断。
2. 隐藏开关 `barHidden` 为**模块级**配置(新列,默认关,fail-closed),与系统「显示手势提示条」开关并存:系统开关仅在 mback/barBoth 关闭时有意义(UI 保持禁用逻辑但条件改为 mback||barOnly 时禁用并提示改用模块开关)。
3. mBack 涟漪反馈(MBackSurface)保留 — 瞬态显示,无烧屏风险,且是隐藏后唯一的操作反馈。
4. 删除 mBack/barOnly 开启时对系统 `setHintBarVisible(true)` 的强制写(解耦点);相关 helper 文案改为「请使用隐藏小白条开关」。
5. `applyVisibility` 的强制 VISIBLE 逻辑**保留**(mback/barOnly 需要可触摸的 handle),仅注释更新 — 隐藏由 onDraw 层完成,两层互不干扰。

## Scope

### IN
- Config: 新列 `barHidden` (INT 0/1, fail-closed, 默认 0) — GestureConfig / ConfigProvider.CONFIG_COLUMNS + query 行 / ConfigStore / version 3→4
- GestureConfigClient: `barHiddenEnabled` volatile + getter + refresh 读取 + CONFIG_UPDATE 日志
- NavigationHandleHooks: Appearance 区块旁新增 `HiddenBar.register(module, loader)`:
  - hook `OplusNavigationHandle` 声明的 `onDraw(Canvas)` before-intercept:`GestureConfigClient.isBarHiddenEnabled()` 为 true → 不调 `chain.proceed()`(跳过绘制),false → 正常 proceed
  - fallback(仅当 onDraw 未声明/NO_MATCH 或真机像素未熄灭时,按 Todo 2 决策规则):hook `View.setAlpha(float)`,guard 顺序 = 先查 volatile `barHiddenEnabled`(关 → 直接 proceed,零开销)再查 `getThisObject() instanceof handle`;hidden 时吞掉调用
- UI: GestureSettingsScreen 导航卡片新增 SwitchRow「隐藏小白条(OLED 防烧屏)」(tapshield 之后、宽度滑块之前,恒 enabled);删除 L138-141 / L161-164 强制 `setHintBarVisible(true)` 写;L125 `enabled = !config.mbackEnabled` → `enabled = !config.mbackEnabled && !config.barOnlyEnabled`
- strings.xml: 新增 `switch_hide_bar` + `hide_bar_helper`;更新 `mback_requires_bar` / `bar_only_helper` 文案(删除"需要白条显示,开启后会强制显示")
- 注释: Appearance.applyVisibility L629-630 过时注释更新
- 测试: GestureConfigTest 新列用例(missing→false, 1→true, 0→false)+ version 4 用例

### OUT
- launcher scope 任何改动(barOnly 门控基于 barWidthDp 几何,与可见性天然解耦,无需动)
- mBack / barOnly / side-back / TapShield 拦截逻辑改动
- 系统 `hide_gesture_bar_enable` 通道删除
- 定时隐藏 / 充电时隐藏等条件逻辑
- 白条 view detach / GONE / INVISIBLE 方案(会破坏触摸分发,已否决)

## Verification strategy

- 构建: `$env:JAVA_HOME='C:\Users\Administrator\.jdks\jdk-21.0.12.1+1'` + `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` → exit 0
- 设备(adb = `C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe`,10.168.1.134:39015,无线):
  1. 安装 + `adb shell su -c 'killall com.android.systemui'` + 等 SystemUI 回来
  2. 默认关 → 白条可见,全功能照常(回归基线)
  3. 开 barHidden(mback=1, barOnly=1, tapShield=1, barWidthDp=160 当前配置)→ `adb exec-out screencap -p > before.png` / `after.png`:白条矩形区域(barWidthDp=160 → 约 [288,2329][792,2365] ±)内像素与桌面背景一致、无白色横条 → **像素熄灭验证**
  4. 隐藏态功能矩阵:
     - 点白条区域 → mBack 返回(打开设置,点白条回上级)
     - 长按白条 → home
     - 从白条上滑 → 回主屏(barOnly 门控生效)
     - 白条外底部区域上滑 → 被拦截(barOnly)
     - 点/长按白条 → 下层 app 不触发(TapShield)
  5. 隐藏态拖宽度滑块 160→40 → `COS16-Gesture` logcat `HANDLE_WIDTH px=110` + touchableRegion 随动(几何与显示无关的直接证据)
  6. 隐藏态上滑过程中观察白条位置是否闪现(alpha 动画路径)→ 若闪现,触发 fallback 规则
  7. 关 barHidden → 白条立即恢复显示(下一次 onDraw 即恢复)
  8. logcat 过滤 `COS16-Gesture`:`HIDDENBAR` 状态日志可见
- 证据: `.omo/evidence/hintbar-oled-hide-decouple/`(test.txt, build.txt, install.txt, before.png, after.png, logcat.txt, 像素分析输出.txt)

## Execution strategy

3 waves:
- Wave 1(可并行): Todo 1(config 全链路+单测) + Todo 3(UI+文案)
- Wave 2: Todo 2(SystemUI onDraw hook + fallback 规则)
- Wave 3: Todo 4(构建装机真机矩阵验证+证据)
- Final wave: F1-F4 并行终验

## Todos

- [ ] 1. Config 全链路新增 barHidden 列(version 3→4)
  What to do / Must NOT do: GestureConfig 加 `barHiddenEnabled: Boolean = false` + fromValues 参数(hintTapShield 之后、version 之前,`barHidden == 1` fail-closed);`version: Int = 4` + `version ?: 4`;CONFIG_COLUMNS 在 "version" 前插 `"barHidden"`;ConfigProvider.query addRow 插入 `if (config.barHiddenEnabled) 1 else 0`;ConfigStore 加 `KEY_BAR_HIDDEN = "bar_hidden"` + 读写;GestureConfigClient 加 volatile 字段 + `isBarHiddenEnabled()` + refresh 读列 "barHidden" + CONFIG_UPDATE 日志加 barHidden。Must NOT: 改既有列顺序;动 fail-open 的 master/left/right 语义。
  Parallelization: Wave 1 | Blocked by: none | Blocks: 2,3,4
  References: app/src/main/java/com/cos/lspit/gesture/config/GestureConfig.kt (全文), ConfigProvider.kt (L22 CONFIG_COLUMNS, L37-47 query), ConfigStore.kt (L11-47), hook/GestureConfigClient.java (L43-69 字段/getter, L157-172 refresh), test/.../GestureConfigTest.kt
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest` exit 0 全绿;新用例断言 missing/null → false、1 → true、0 → false
  QA scenarios: happy = 新列写→读往返(SharedPreferences→fromValues→provider 行);failure = 旧 version=3 存储加载 → barHidden=false 不崩。Evidence .omo/evidence/hintbar-oled-hide-decouple/task-1-test.txt
  Commit: Y | feat(config): add barHidden column with fail-closed default

- [ ] 2. SystemUI 隐藏 hook:吞 onDraw + setAlpha fallback
  What to do / Must NOT do: NavigationHandleHooks 新增 `static final class HiddenBar`:
  - `register(XposedModule module, ClassLoader loader)`:取已解析的 handle class(复用 register() 里 `Class.forName(HANDLE_CLASS)` 结果或重新 forName);`getDeclaredMethod("onDraw", Canvas.class)` → `module.hook(method).setExceptionMode(PROTECTIVE).intercept(chain -> { if (GestureConfigClient.isBarHiddenEnabled() && !isShadeExpanded()) return null; return chain.proceed(); })`;log `HIDDENBAR_HOOK_OK onDraw` / `HIDDENBAR_NO_MATCH onDraw <err>`。
  - fallback 决策规则(按序执行,不需人工判断):(a) onDraw 未声明(NO_MATCH)→ 直接注册 setAlpha fallback;(b) onDraw 命中 → 装机验证步骤 3 像素熄灭 + 步骤 6 无闪现均通过 → 不加 fallback;(c) 任一不通过 → 注册 fallback:`View.class.getMethod("setAlpha", float.class)` hook,intercept 内 guard 顺序 = `if (!GestureConfigClient.isBarHiddenEnabled()) return chain.proceed();` → `Object t = chain.getThisObject(); if (!(t instanceof <handleClass>)) return chain.proceed();` → hidden 时吞掉(return null 不 proceed)。
  - shade 展开豁免:复用 TapShield.isShadeExpanded(通知/控制中心展开时白条照常绘制,面板自带半透明背景,无叠加烧屏点;失败保守 false = 继续隐藏)。Must NOT: 用 setVisibility(GONE/INVISIBLE) 隐藏(破坏触摸分发);hook 后不设 PROTECTIVE;在 setAlpha guard 前做任何反射(性能)。
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 4
  References: NavigationHandleHooks.java L52-54 (HANDLE_CLASS), L86-121 (applyAllHandles 模式), L603-643 (Appearance), TapShield.isShadeExpanded (同文件), hook/GestureConfigClient.java getter
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:assembleDebug` exit 0;装机后 logcat 含 `HIDDENBAR_HOOK_OK onDraw`;开 barHidden 后 screencap 白条矩形无白色像素
  QA scenarios: happy = 开关切换即时生效(下一次绘制);failure = handle 类无 onDraw 声明 → NO_MATCH 日志 + fallback 注册日志 `HIDDENBAR_HOOK_OK setAlpha`。Evidence .omo/evidence/hintbar-oled-hide-decouple/task-2-logcat.txt
  Commit: Y | feat(hook): swallow OplusNavigationHandle.onDraw when barHidden

- [ ] 3. UI:新开关 + 删除强制显示写 + 文案更新
  What to do / Must NOT do: GestureSettingsScreen:
  - tapshield SwitchRow(L176-184)之后新增 SwitchRow「隐藏小白条(OLED 防烧屏)」:`checked = config.barHiddenEnabled`,`enabled = true`,onCheckedChange = copy + ConfigStore.save
  - 删除 L138-141(mBack onCheckedChange 内 `gestureBarVisible = true; SystemGestureBarSettings.setHintBarVisible(context, true)`)
  - 删除 L161-164(barOnly 同款强制写)
  - L125 `enabled = !config.mbackEnabled` → `enabled = !config.mbackEnabled && !config.barOnlyEnabled`
  - 新增 helper 文案:隐藏后 mBack / 仅白条触发 / 点击保护照常工作,触发区域不变,仅白条不再显示
  strings.xml:新增 `switch_hide_bar` = "隐藏小白条(OLED 防烧屏)"、`hide_bar_helper`;`mback_requires_bar` → "mBack 开启时白条需保持可触摸,请用「隐藏小白条」开关控制显示。"、`bar_only_helper` 删除"需要白条显示,开启后会强制显示"改为"…可减少误触。白条可通过「隐藏小白条」开关隐藏。" Must NOT: 动 master/left/right/barWidth UI;删 gestureBarVisible 状态本身。
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 4
  References: ui/GestureSettingsScreen.kt (L118-200), res/values/strings.xml (L5-17), config/SystemGestureBarSettings.kt
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:assembleDebug` exit 0;grep 确认 GestureSettingsScreen.kt 无 `setHintBarVisible(context, true)` 残留(仅 switch_bar_visibility onCheckedChange 处保留一处用户主动写)
  QA scenarios: happy = 开 mBack 后系统显示开关仍禁用但模块隐藏开关可用且生效;failure = 关 mBack+关 barOnly 后系统显示开关恢复可用。Evidence .omo/evidence/hintbar-oled-hide-decouple/task-3-build.txt
  Commit: Y | feat(ui): add barHidden switch and drop forced hint-bar show

- [ ] 4. 构建装机真机矩阵验证 + 证据落盘
  What to do / Must NOT do: 依次执行 Verification strategy 的 8 个设备场景;截图像素分析用 PowerShell System.Drawing(采样白条矩形中心行像素,断言无高亮度白色);logcat 过滤 `COS16-Gesture` 存 HIDDENBAR/HANDLE_WIDTH/TAPSHIELD 日志;所有输出落 `.omo/evidence/hintbar-oled-hide-decouple/`。Must NOT: 跳过像素熄灭断言;在 barHidden 关闭态以外宣称完成;不修复发现的闪现问题就收尾(触发 Todo 2 fallback 规则后重验)。
  Parallelization: Wave 3 | Blocked by: 1,2,3 | Blocks: F1-F4
  References: Verification strategy 全节;adb 路径 `C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe`;设备 10.168.1.134:39015
  Acceptance criteria (agent-executable): 8 场景全过;after.png 白条矩形像素分析输出"no bright pixels";功能矩阵 4 项(mBack 返回/长按 home/白条上滑 home/白条外上滑拦截)各有一份证据
  QA scenarios: happy = 全矩阵通过;failure = 任一场景失败 → 记录现象 + 触发对应 todo 的 fallback/修复规则。Evidence .omo/evidence/hintbar-oled-hide-decouple/(全部)
  Commit: N | (验证任务,无代码提交)

## Final verification wave

- [ ] F1. Plan compliance audit — 对照本计划逐 todo 核对:config 列/UI/hook/文案与 Scope IN 一致,无 Scope OUT 越界(launcher scope 零改动 `git diff --stat` 佐证)
- [ ] F2. Code quality review — PROTECTIVE 模式齐全、异常吞噬不崩 SystemUI、setAlpha fallback guard 顺序(volatile 先于 instanceof 先于反射)、无日志刷屏
- [ ] F3. Real manual QA — Verification strategy 8 场景 + 像素熄灭断言 + 运行期开关切换即时性,证据链完整
- [ ] F4. Scope fidelity — 未加定时隐藏/条件显示等 OUT 项;系统 hide 通道保留;mBack 涟漪保留;默认关(回归零影响)

## Commit strategy

每 todo 一个 commit(Todo 4 无),格式对齐仓库现有 conventional commits;全部完成后由用户决定是否 squash/PR。

## Success criteria

1. barHidden 开 → 白条像素熄灭(screencap 证据)且 mBack/barOnly/TapShield/宽度全功能照常
2. barHidden 关 → 白条恢复显示,行为与现状完全一致
3. 单测全绿 + 构建成功 + 8 设备场景证据落盘
4. UI 无强制显示写;mBack 开启时长度/显示解耦彻底(上一计划修复保持)
