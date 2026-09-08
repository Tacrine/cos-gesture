# hintbar-tapshield - Work Plan

> Status: DECISION-COMPLETE — 用户已确认设计,待执行(执行由用户另行启动 worker session)
> Device: PKG110 / ColorOS 16 / 1080×2376 / density 2.75 / adb 10.168.1.134:46075
> Module: `com.cos.lspit.gesture` (SystemUI + Launcher 双 scope, libxposed API)

## TL;DR (For humans)

**What**: 两个交付物 — ① 新功能「小白条点击保护」:小白条矩形区域内消费触摸,防止点击/长按小白条时误触发下层应用(穿透过导航窗的点击落到 app 底部控件)。② 修复:mBack 开启后小白条长度自定义被 UI 禁用的耦合 bug。

**Why ①**: 手势导航下导航窗 touchableRegion 仅覆盖白条,白条上的点击穿到下层 app 造成误触;coloros-mod 已验证 SystemUI 层可拦截。与 barOnly/mBack 哲学一致 — 保护区域即小白条本体(横向宽度 = 用户自定义 barWidthDp),带外区域不受影响。
**Why ②**: 纯 UI 层错误耦合 — 滑块/按钮 `enabled = !config.mbackEnabled`,但运行时宽度应用逻辑(`Appearance.applyWidth`)与 mBack 完全无关;且 mBack 命中区 `isInBarRange` 本就跟随 `viewScreenLeft + getWidth()`,宽度自定义反而让 mBack 更精确。

**NOT**: 不做整条底部手势带拦截(coloros-mod 原方案 A 已否决);不改 launcher scope;不动 mBack/barOnly/side-back 现有逻辑;不加整屏拦截。

**Effort**: 中。SystemUI 侧 1 个 hook 点 + 1 个 overlay view + config/UI 3 处 + 1 行 UI 修复。
**Risk**: 中低 — 见风险表。

**Decisions I made for you**(已按你的确认锁定):
1. 保护区域 = 小白条本体矩形:宽 = 自定义 barWidthDp(未自定义时取系统实际宽度),高 = mHeight + 上下 padding(4dp),横向居中。**与 barWidthDp 联动**。
2. 拦截方式 = coloros-mod `NavigationBar$$ExternalSyntheticLambda10#onComputeInternalInsets` after-hook 改写 touchableRegion + 同矩形透明 `GestureBlockSurface` 消费触摸,双保险。
3. 新独立开关 `hintTapShield`(MIUIX,默认关,导航分组,与 barOnly 相邻);shade(通知/控制中心)展开时自动豁免。
4. mBack 开启时:小白条上的 tap/长按仍由 mBack 消费(现有逻辑),shield 只兜底"mBack 未消费而穿透"的部分 — 两者叠加无冲突(mBack 在 `handleValidTouchEvent` hook 消费,shield 在窗口 touchableRegion/overlay 层,层级不同)。
5. 长度自定义与 mBack 解耦:删除 L201/L211 的 `enabled = !config.mbackEnabled`。

## Scope

### IN
- Config: 新列 `hintTapShield` (INT 0/1, fail-closed) — ConfigProvider.CONFIG_COLUMNS / ConfigStore / GestureConfig / ConfigProvider.query 行 / version 2→3
- GestureConfigClient: 新字段 `hintTapShieldEnabled` + refresh 读取 + CONFIG_UPDATE 日志
- NavigationHandleHooks: 新增 TapShield 区块
  - hook `com.android.systemui.navigationbar.views.NavigationBar$$ExternalSyntheticLambda10#onComputeInternalInsets(ViewTreeObserver$InternalInsetsInfo)` after → 若 active 且非 shade 展开:touchableRegion.set(白条矩形, 窗口坐标系) + `setTouchableInsets(3)`
  - `GestureBlockSurface`(View): setWillNotDraw(true) + setClickable(true) + OnTouchListener 恒 true;位置 = 白条矩形
  - 几何: 复用 MBack 现成口径 — `viewScreenLeft` + `handle.getWidth()`(已随 applyWidth 联动自定义宽度)+ `mHeight` + `mHandleBottom` + MBACK_BAND_PADDING_DP;横向 = viewScreenLeft-3dp .. +width+3dp,纵向 = 白条中心 ± (mHeight/2 + 4dp)
  - attach/layout/detach 生命周期同步(挂进现有 hookAttach/hookLayout/hookDetach 拦截器,加 TapShield.sync 调用)
  - `isShadeExpanded` 移植(coloros-mod L456-482, NavigationBarView.mPanelExpansionInteractor 反射)
  - `OplusNavigationBarView.updateSlippery` after-hook → shade 展开变化时 re-sync + requestLayout
  - gating: `hintTapShieldEnabled && !isShadeExpanded && master`
- UI: GestureSettingsScreen 新增 SwitchRow(导航卡片, barOnly 之后);strings.xml 新文案(中)
- 修复: GestureSettingsScreen L201 Slider `enabled = !config.mbackEnabled` → `true`;L211 Button 同 → `true`
- 测试: GestureConfigTest 新列默认/解析用例;现有用例全绿

### OUT
- 整条底部手势带拦截(方案 A)
- launcher scope 任何改动
- mBack / barOnly / side-back / 提示条显隐逻辑改动(mBack 开启时隐藏条开关仍禁用 — mBack 需要可见条,保留)
- 通知面板/键盘等非 shade 场景特判(coloros-mod 亦无)

## Verification strategy

- 构建: `$env:JAVA_HOME='C:\Users\Administrator\.jdks\jdk-21.0.12.1+1'` + `.\gradlew.bat :app:assembleDebug` → exit 0
- 单测: `.\gradlew.bat :app:testDebugUnitTest` → 全绿(含新用例)
- 设备(adb 10.168.1.134:46075, 无线):
  1. 安装 + LSPosed 作用域内 `adb shell su -c 'killall com.android.systemui'`
  2. 开关默认关 → 行为与现状完全一致(回归基线)
  3. 开 mback + 拖长度滑块 → **长度立即生效**(bug 修复验证);tap 白条 = 返回,mBack 正常
  4. 开 hintTapShield → 打开底部有可点控件(如 Chrome 地址栏/设置列表底项)的 app,点/长按白条位置 → **下层控件不触发**;白条外同高度区域 → 正常穿透
  5. 长按白条 → 不触发下层(若有 app 长按行为)且 mBack 长按 = home 照常
  6. 上滑 home / 上滑停留 / 左右切任务 → 照常(launcher `[Gesture Monitor] swipe-up` 为 SPY 窗,不受影响)
  7. 下拉通知中心/控制中心展开 → 面板底部可正常点击(豁免)
  8. 关 hintTapShield → 立即恢复穿透(运行期切换,无需重启 SystemUI)
  9. logcat 过滤 `COS16-Gesture`:`TAPSHIELD` 状态日志可见
- 证据: `.omo/evidence/hintbar-tapshield/`(build.txt, test.txt, install.txt, logcat.txt, 各场景截图)

## Execution strategy

3 waves, 顺序执行:
- Wave 1: config 全链路(列/版本/客户端/UI 开关) + UI 解耦修复 + 单测
- Wave 2: SystemUI TapShield hook(Insets hook + Surface + 几何 + 生命周期 + shade 豁免)
- Wave 3: 构建装机设备验证(9 场景)+ 证据落盘

## Todos

- [ ] 1. Config 全链路新增 hintTapShield 列
  What to do / Must NOT do: GestureConfig 加 `hintTapShieldEnabled: Boolean = false` + fromValues 参数(在 version 前插入);`CONFIG_COLUMNS` 加 `"hintTapShield"`(version 前);ConfigProvider.query addRow 插入对应值;ConfigStore 读写新键;version 2→3(旧存储无该键 → 默认 false,天然兼容)。Must NOT: 改动既有列顺序语义;删既有迁移逻辑。
  Parallelization: Wave 1 | Blocked by: none | Blocks: 2,3
  References: app/src/main/java/com/cos/lspit/gesture/config/GestureConfig.kt (全文 63 行), ConfigProvider.kt (L22 CONFIG_COLUMNS, L36-49 query), ConfigStore.kt, config/GestureConfigTest.kt
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest` 全绿;新用例断言 missing 列 → false, 1 → true
  QA scenarios: happy = 新列读写往返;failure = 旧 version=2 存储加载 → hintTapShield=false 不崩。Evidence .omo/evidence/hintbar-tapshield/task-1-test.txt
  Commit: Y | feat(config): add hintTapShield column with fail-closed default

- [ ] 2. GestureConfigClient + MIUIX 开关 + 长度/mBack 解耦修复
  What to do / Must NOT do: GestureConfigClient 加 `hintTapShieldEnabled` volatile 字段 + getter + refresh 读取(`hintTapShield == 1` fail-closed)+ CONFIG_UPDATE 日志;GestureSettingsScreen 导航卡片 barOnly helper 之后加 SwitchRow(默认 false);**删除 L201 Slider 与 L211 Button 的 `enabled = !config.mbackEnabled`,改 `enabled = true`**;strings.xml 加 switch_tapshield + tapshield_helper(中)。Must NOT: 动 L125 显示开关的 mback 禁用(mBack 需要可见条);动 mback_requires_bar 提示。
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 3
  References: hook/GestureConfigClient.java (L54-66, L146-176), ui/GestureSettingsScreen.kt (L155-214), res/values/strings.xml
  Acceptance criteria: assembleDebug 通过;UI 无未解析资源引用
  QA scenarios: happy = mback=1 时滑块可拖可应用,保存后 provider 返回新宽度;failure = hintTapShield 列缺失 → 开关 UI 仍可切换但 hook 侧 false。Evidence .omo/evidence/hintbar-tapshield/task-2-build.txt
  Commit: Y | feat(ui): hintTapShield switch + decouple bar width from mBack

- [ ] 3. SystemUI TapShield hook 实现
  What to do / Must NOT do: NavigationHandleHooks 新增 `TapShield` 静态内部类,含:
  (a) `hookInsets(module, loader)`: 反射 `android.view.ViewTreeObserver$InternalInsetsInfo`;hook `com.android.systemui.navigationbar.views.NavigationBar$$ExternalSyntheticLambda10#onComputeInternalInsets` after — 取 `f$0` → `mView` → NavigationBar view;active 时 region.set(barRect) + setTouchableInsets(3)。**注意本工程用 libxposed API(module.hook().intercept),非 XC_MethodHook,按 hookTouch 现有范式翻译**。
  (b) `GestureBlockSurface` 内部类(参照 MBackSurface 结构):willNotDraw + clickable + onTouch 恒 true;update() 位置 = 白条矩形。
  (c) 几何 `barRect(host)`: 横 = `viewScreenLeft`-3dp .. +width+3dp;纵 = 白条中心 ± (mHeight/2 + 4dp);白条中心 y = handle 与 host 的 getLocationInWindow 差值 + height - mHandleBottom - mHeight/2(MBackSurface.update L690-698 同款口径)。
  (d) attach/layout/detach 拦截器内追加 `TapShield.sync/position/remove`(try-catch,失败不影响既有 mBack 路径)。
  (e) `isShadeExpanded(view)` 移植。
  (f) `updateSlippery` after-hook(`com.oplusos.systemui.navigationbar.OplusNavigationBarView`,libxposed 范式)+ findHandleInTree + requestLayout。
  (g) `isActive(view)` = `isHintTapShieldEnabled() && isMasterEnabled() && !isShadeExpanded(view)`;applyAllHandles 追加 TapShield.sync。
  (h) 日志: TAPSHIELD_ON/OFF/REGION/NO_MATCH。
  Must NOT: hook android.view.View 基类方法(须限定 handle 声明);改动 MBack/Appearance 既有逻辑;shade 展开时留下残留拦截(必须 remove)。
  coloros-mod 参考: GestureHooks.java L296-409(hook 骨架), L456-482(isShadeExpanded), L500-506(几何), L508-566(surface 生命周期), L899-938(GestureBlockSurface)。raw: https://raw.githubusercontent.com/rikumi/coloros-mod/master/app/src/main/java/com/rikumi/colorosmod/hooks/GestureHooks.java
  Parallelization: Wave 2 | Blocked by: 2 | Blocks: 4
  References: hook/NavigationHandleHooks.java (L118-187 hookTouch 范式, L196-277 生命周期, L390-630 MBack, L636-705 MBackSurface, L741-761 getIntField), ModuleEntry.java (L34-36)
  Acceptance criteria: assembleDebug exit 0;全部 Throwable 捕获,不让 SystemUI 崩
  QA scenarios: happy = 开关开 → logcat 出现 TAPSHIELD_ON + REGION;failure = SyntheticLambda10 不存在 → NO_MATCH 日志,其余功能不受影响。Evidence .omo/evidence/hintbar-tapshield/task-3-build.txt
  Commit: Y | feat(hook): hint-bar tap-through shield with shade carve-out

- [ ] 4. 设备验证(9 场景)+ 证据
  What to do / Must NOT do: adb connect 10.168.1.134:46075;install;`adb shell su -c 'killall com.android.systemui'`;按 Verification strategy 逐场景执行并记录;每场景截图/logcat 落 `.omo/evidence/hintbar-tapshield/`。Must NOT: 跳过回归场景 2/6/9;验证失败改代码后不重跑全场景。
  Parallelization: Wave 3 | Blocked by: 3 | Blocks: none
  References: 本文件 Verification strategy;evidence 惯例见 .omo/evidence/baronly/
  Acceptance criteria (agent-executable): 9 场景全部符合预期;evidence 目录含 build/test/install/logcat/截图
  QA scenarios: happy = 全部通过;failure = 任一场景不符 → 记录现象 + logcat,迭代修复后重跑。Evidence .omo/evidence/hintbar-tapshield/task-4-verify.txt
  Commit: N

## Final verification wave

- [ ] F1. Plan compliance audit
  对照本计划逐条核查:config 链路 / UI 开关 / 解耦修复 / TapShield hook / 9 设备场景证据齐全。
- [ ] F2. Code quality review
  try-catch 完备(不得让 SystemUI 崩);无 hook 到 View 基类;libxposed API 与既有范式一致;字符串资源完整。
- [ ] F3. Real manual QA
  设备实测场景 3/4/5/8(保护生效 + 解耦生效 + 运行期开关即时性)。
- [ ] F4. Scope fidelity
  未越界:launcher 未动,mBack/barOnly/side-back 行为无回归,显示开关 mback 禁用保留。

## Commit strategy

Task 1-3 各一 commit,按仓库既有 message 风格,带 Co-authored-by: Copilot trailer。Task 4 仅证据,`.omo/` 不提交(仓库既有惯例)。

## Success criteria

1. mBack 开启时长度滑块可自定义且立即生效
2. hintTapShield 开 → 白条区点/长按不再穿透触发下层 app
3. 白条外区域、底部手势、shade 面板操作全部无回归
4. 开关运行期切换即时生效,默认关
5. 全部单测绿 + 设备 9 场景证据齐

## 风险表

| 风险 | 缓解 |
|---|---|
| SyntheticLambda10 类名随 ROM 混淆变化 | NO_MATCH 日志 + GestureBlockSurface 独立兜底(overlay 不依赖该 hook) |
| touchableRegion 改写与 gesture monitor 冲突 | 白条矩形 ⊂ 原 touchableRegion(原即白条),风险极低;场景 6 验证 |
| mBack 与 shield 双消费冲突 | 层级不同(handleValidTouchEvent vs 窗口 dispatch);场景 3/5 验证 |
| viewScreenLeft 未初始化(MIN_VALUE) | isInBarRange 已有 fail-open 先例;shield 同样 fail-open(不拦截) |
| shade 字段反射失败 | isShadeExpanded 内部 try-catch → false(未展开),保守 |
