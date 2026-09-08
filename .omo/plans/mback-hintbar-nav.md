# mback-hintbar-nav - Work Plan

## TL;DR (For humans)

**What you'll get**: 在现有侧滑拦截模块上新增三个功能——①小白条显示开关与系统设置为同一个设置（双向同步）；②小白条长度自定义（80–120dp 滑条）；③mBack 手势（轻触白条返回、长按回桌面、划动让位）。全部移植自 rikumi/coloros-mod（MIT，保留版权声明）。

**Why this approach**: coloros-mod 已在 Android 16 ColorOS 上验证同套 Oplus 类 hook；配置通道、失败守卫、MIUIX UI、作用域重启全部复用本仓库既有模式；白条显示直接读写系统设置键而非自绘（同一设置、零绘制、最稳）。

**What it will NOT do**: 不移植手势区增高、不禁止长按白条动画、不做防点击穿透、不改旋转按钮位置、不加 Robolectric、不做动画美化。不改动已验证的侧滑拦截行为（SideGestureDetector hook）。

**Effort**: 8 个实施任务 + 4 个最终验证任务，4 个 wave。

**Risk**: ①系统设置键名/命名空间/极性未证实（Milestone 0 探测阻塞）；②按键注入路径在纯手势模式下可能只剩 InputManager 反射（Milestone 0 spike 阻塞）；③OplusNavigationHandle 本机不存在（NO_MATCH 守卫，mBack 静默失效）。

**Decisions I made for you（均已记录可否决）**:
1. 白条显示 = 直读写系统设置键（候选 `hide_gesture_bar_enable`，探测确认），不自绘
2. 长度范围 80–120dp，默认 100dp
3. mBack 行为完全照搬 coloros-mod（轻触返回/长按回桌面/|dx|>20dp 或 dy<-20dp 放行/多指作废/ripple+震动）
4. **mBack ON 时强制显示白条**（hook 侧观察者持续置 0，记忆原值，OFF 时恢复）；mBack ON 时 UI 禁用显示开关
5. 划动让位 = 只吞 `handleValidTouchEvent`（白条自身处理），事件流经正常分发继续走系统手势——照搬，E2E 断言上滑/侧滑仍有效
6. 配置 fail-closed：`mback` 列缺失/异常 → false（返回/桌面接管绝不能误开）；`barWidthDp` 缺失 → 完全跳过宽度改写。既有三开关保持 fail-open 不变
7. 测试 = JVM 纯逻辑单测（状态机/钳制/解析）+ 真机 E2E，无人工确认步骤
8. 我把此需求按 CLEAR 路由（你给出了明确交付物），采纳了推荐默认；若你有具体不同想法，说一声我改计划

## Scope

**IN**:
- GestureConfig/ConfigStore/ConfigProvider/GestureConfigClient 四文件链扩展 `mback`（int 0/1）与 `barWidthDp`（int dp）两列，version 1→2
- 系统设置键读写通道（探测确认键名后）：manifest 加 `WRITE_SECURE_SETTINGS`；写链 hook 侧进程内写（首选）→ Shizuku → root su `settings put` → 功能禁用+UI 提示
- `OplusNavigationHandle` 统一 hook 协调器 `NavigationHandleHooks`（width + mBack 子处理器共用 hook 站点）
- 白条宽度改写（LayoutParams.width + Gravity.CENTER，setVertical/onAttachedToWindow/onLayout after）
- mBack：handleValidTouchEvent beforeHook 全接管 + 纯逻辑状态机 MBackGestureSpec（JVM 可测）+ MotionEvent 适配器 + 按键注入（BACK/HOME）+ MBackSurface ripple 反馈层
- MIUIX UI：mBack 开关、长度滑条（仅 commit 时写配置）、显示开关（mBack ON 时禁用）
- 真机 E2E 矩阵 + 证据文件

**OUT（Must-NOT-Have）**: 手势区增高、禁长按动画、防点击穿透（含 `NavigationBar$$ExternalSyntheticLambda10` hook）、旋转按钮位置、Robolectric、自绘白条、对 SideGestureDetector hook 的任何行为改动。

**真值表（bar-visibility × mBack）**:
| 系统设置白条 | mback 配置 | 结果 |
|---|---|---|
| 显示(0) | off | 系统原生行为 |
| 隐藏(1) | off | 系统原生行为（模块不干预） |
| 显示(0) | on | mBack 生效，白条保持显示 |
| 隐藏(1) | on | hook 侧观察者立即置回 0 强制显示，mBack 生效；记忆原值，mBack off 时恢复 |

## Verification strategy

- **JVM**: `./gradlew testDebugUnitTest`（JAVA_HOME=C:\Users\Administrator\.jdk-21 路径同现命令）。新增 `MBackGestureSpecTest`（状态矩阵：tap/long-press/|dx|>20/dy<-20/多指/中途回调配置快照）、`GestureConfigTest` 扩展（mback fail-closed、barWidthDp 缺失跳过、旧列兼容）、宽度钳制函数测试
- **E2E（全部 shell 可执行，证据入 `.omo/evidence/mback-hintbar-nav/`）**:
  - 配置通道：`adb shell content query --uri content://com.cos.lspit.gesture.config/config` 含 `mback=,barWidthDp=`
  - 宽度：`adb exec-out screencap -p` + 像素扫描断言白条像素行程 ≈ dp×density
  - mBack：`adb logcat -d | findstr MBACK` 决策日志 + `dumpsys activity` 返回栈断言（am start 第二 Activity → tap 白条 → topResumedActivity 回退）
  - 让位：mBack ON 时上滑回桌面正常（dumpsys 断言 launcher 前台）、侧滑返回仍被既有 veto
  - 失败路径：无写权限 → 功能禁用 + 日志；类不存在 → NO_MATCH 状态、mBack 静默失效、白条宽度不改
- **最终验证波** F1-F4 见下

## Execution strategy

Milestone 门控：**M0 探测（Task 1-2）阻塞一切功能代码**；注入 spike（Task 3）阻塞 mBack 移植。配置扩展（Task 2）与探测并行。之后 Wave 2 双特性并行 → Wave 3 mBack + UI → Wave 4 E2E。所有 hook 注册沿用既有守卫模式（class-not-found/descriptor → NO_MATCH + reportStatus，绝不崩 SystemUI）。单一 `NavigationHandleHooks` 协调器持有 OplusNavigationHandle 全部 hook 站点，分发给 width/mBack 子处理器，避免同方法多 hook 顺序未定义。观察者线程（cos16-gesture-cfg）一切 UI/LayoutParams 操作 post 主线程。滑条仅在拖动结束（commit）时写配置（防 notifyChange 风暴）。每个手势在 ACTION_DOWN 取配置快照，手势中途配置变更不影响在途手势。

## Todos

- [ ] 1. 设备探测：白条系统设置键名/命名空间/极性 + 直写生效性
  What to do / Must NOT do: 翻转系统「显示手势提示条」开关前后各 dump `adb shell settings list secure`/`global`/`system`，diff 出真实键（候选 `hide_gesture_bar_enable`/`navbar_gesture_visibility`），确认命名空间与极性（隐藏=0 还是 1）。然后 `adb shell settings put <ns> <key> <val>` 直写 → 观察白条是否无重启即时隐藏/显示，记录"是否需 hook 侧写"结论。同时验证 `pm grant com.cos.lspit.gesture android.permission.WRITE_SECURE_SETTINGS` 在声明该权限后可用（本任务先只在命令行 `adb shell settings put` 验证写生效，manifest 改动归 Task 4）。Must NOT: 不写任何产品代码；探测后必须把键名/命名空间/极性/直写结论回填进本文件第 1 行下方（executor 手动更新本计划的 PROBE-RESULT 块）。
  Parallelization: Wave 1 | Blocked by: none | Blocks: 4
  References (executor has NO interview context): 设备 3B661M01NH500000 在线，root su 可用；`adb=C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe`；候选键值快照见 `.omo/drafts/mback-hintbar-nav.md` 风险节；系统设置路径：设置→桌面与锁屏/系统导航→「显示手势提示条」（在系统导航手势设置内）
  Acceptance criteria (agent-executable): `.omo/evidence/mback-hintbar-nav/probe-setting-key.txt` 含 before/after diff、确认的键名+ns+极性、直写即时生效结论（YES/NO）
  QA scenarios (name the exact tool + invocation): happy=翻转系统开关 diff 出唯一键变更并直写白条即时变化；failure=两个候选键都不变（则检索全部 ns diff，仍无 → 该功能降级为只读同步，记录并按只读继续 Task 4），Evidence <.omo/evidence/mback-hintbar-nav/probe-setting-key.txt>
  Commit: N | evidence only

- [ ] 2. 配置通道扩展：mback/barWidthDp 列 + fail-closed 语义
  What to do / Must NOT do: 按既有 master/left/right 模式同步改 5 处：`GestureConfig.kt`（+`mbackEnabled: Boolean = false`、`barWidthDp: Int? = null`，`fromValues` 加参）、`ConfigStore.kt`（读写键）、`ConfigProvider.kt`（CONFIG_COLUMNS 加 "mback","barWidthDp"，addRow 补列）、`GestureConfigClient.java`（volatile 快照 + 读列；**mback fail-closed：列缺失/不可解析 → false；barWidthDp 缺失/越界 → null=不改写**；mback/barWidthDp 变更也走既有 notifyChange→observer→refresh，UI 类操作 post 主线程）、UI 侧暂不动。version 1→2。扩展 `GestureConfigTest`。Must NOT: 不改既有三开关 fail-open 语义；不加 Robolectric。
  Parallelization: Wave 1 | Blocked by: none | Blocks: 3,4,5,6,7
  References: app/src/main/java/com/cos/lspit/gesture/config/GestureConfig.kt（全部）、ConfigStore.kt、ConfigProvider.kt（CONFIG_COLUMNS/query/insert）、app/src/main/java/com/cos/lspit/gesture/hook/GestureConfigClient.java（readColumn/refresh/CONFIG_UPDATE 日志行——扩展日志为 `CONFIG_UPDATE master=.. left=.. right=.. mback=.. barWidthDp=..`）、app/src/test/java/com/cos/lspit/gesture/config/GestureConfigTest.kt
  Acceptance criteria (agent-executable): `$env:JAVA_HOME='C:\Users\Administrator\.jdks\jdk-21.0.12.1+1'; .\gradlew testDebugUnitTest` EXIT=0，新测试覆盖：mback 缺失→false、mback=0→false、mback=1→true、barWidthDp 缺失→null、barWidthDp=250→钳制或 null（按实现断言）、旧 provider 游标（4 列）兼容
  QA scenarios (name the exact tool + invocation): happy=单测全绿 + provider 查询含新列（本任务后 install 不必须）；failure=mback 列异常值解析不抛异常返回 false，Evidence <.omo/evidence/mback-hintbar-nav/task-2-unittest.txt>
  Commit: Y | feat(config): add mback and bar-width columns with fail-closed defaults

- [ ] 3. 注入 spike：handleValidTouchEvent 存在性 + BACK 键注入验证
  What to do / Must NOT do: 最小 spike（临时代码，独立分支提交，验证后本任务内回退或保留为骨架）：hook `com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle#handleValidTouchEvent(android.view.MotionEvent)` beforeHook，log 参数确认类/方法存在；ACTION_DOWN 时注入 KEYCODE_BACK（`Class.forName("android.hardware.input.InputManager").getMethod("getInstance")` + `injectInputEvent(new KeyEvent(ACTION_DOWN/UP, KEYCODE_BACK), 0x04000000|0x40000000)` 即 FLAG_FROM_SYSTEM|FLAG_VIRTUAL_HARD_KEY 的 int 值 671088640... 以实际编译为准用常量名），log MBACK_SPIKE。真机验证 `am start` 两个 Activity → `input tap <白条中心坐标>`（坐标取 uiautomator dump 或固定屏 1264×2780 下约 (632,2700)，以实测为准）→ dumpsys 断言返回。Must NOT: 不实现状态机/反馈层；spike 通过后此 hook 站点即 Task 5/6 的协调器入口，不重复注册。
  Parallelization: Wave 1 | Blocked by: 2（mback 配置关闭时 spike 仍执行，仅为验证通道，可用 BuildConfig 或临时常量旁路——选临时常量） | Blocks: 5,6
  References: coloros-mod GestureHooks.java L221-L244（hook 形态）、L783-L806（injectHomeKey 反射模板，BACK 同构）；本仓库 SideBackHooker.java L47-L77（hook 注册 + NO_MATCH 守卫模板）、L91-L117（beforeHook 拦截形态）；设备 root：`& $adb shell "su -c '...'"` 注意 PowerShell 内 $() 需引号包裹
  Acceptance criteria (agent-executable): `.\gradlew assembleDebug` EXIT=0；install 后 logcat 出现 `HOOK_REGISTERED ...OplusNavigationHandle#handleValidTouchEvent`；tap 白条 → `adb shell dumpsys activity activities | findstr topResumedActivity` 显示回退一层的 Activity
  QA scenarios (name the exact tool + invocation): happy=BACK 注入成功返回；failure=类不存在 → NO_MATCH 日志 + SystemUI 不崩（watchdog 检查 `dumpsys activity | findstr systemui` 进程存活），Evidence <.omo/evidence/mback-hintbar-nav/task-3-spike.txt>
  Commit: Y | spike(hook): verify handleValidTouchEvent hook site and BACK key injection

- [ ] 4. 特性①白条显示与系统设置联动（读 + 写链 + 强制显示）
  What to do / Must NOT do: 前置 = Task 1 PROBE-RESULT。manifest 加 `<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"/>`。模块 UI 侧：读写确认后的系统键（`Settings.<ns>.getInt/putInt`）；直接写 catch SecurityException → Shizuku（`rikka.shizuku` 已有依赖）→ root `su -c settings put` → 全失败则显示禁用态+提示文案。若 Task 1 结论为「直写不即时生效需 SystemUI 侧重读」→ 写入后由 hook 侧观察该键变化触发白条 View 重新布局（onLayout hook 已有，post requestLayout），以探测结论为准实现。hook 侧（mBack 强制显示逻辑）：GestureConfigClient 观察 mback=true 时，hook 进程内注册该系统键 ContentObserver，发现非显示值即 putInt 回显示值并 log `MBACK_FORCE_SHOW`；记忆原值逻辑放模块 app prefs（mback 关闭时恢复一次）。UI 显示开关在 mback ON 时禁用（文案注明）。Must NOT: 不自绘白条；不动系统键以外的任何设置；不在观察者线程做 UI 操作。
  Parallelization: Wave 2 | Blocked by: 1,2 | Blocks: 7
  References: app/src/main/AndroidManifest.xml（provider 声明旁加 uses-permission）、app/src/main/java/com/cos/lspit/gesture/ui/GestureSettingsScreen.kt（开关模式）、config/ConfigStore.kt（记忆原值模式参照）、hook/GestureConfigClient.java（observer 模式）；Shizuku: app 内已有 rikka.shizuku 依赖与 manifest provider
  Acceptance criteria (agent-executable): UI 开关翻转 → `adb shell settings get <ns> <key>` 值同步翻转；系统设置页手动翻转 → 模块 UI 重新进入后显示一致；mback=1 且系统键置隐藏 → ≤2s 内 logcat 出现 MBACK_FORCE_SHOW 且 `settings get` 回显示值；无权限链（撤权测试：`pm revoke`）→ UI 禁用态 + 日志，不崩
  QA scenarios (name the exact tool + invocation): happy=双向往返同步；failure=权限全失 → 禁用态+提示，Evidence <.omo/evidence/mback-hintbar-nav/task-4-sync.txt>
  Commit: Y | feat(setting): sync hint bar visibility with the system setting

- [ ] 5. 特性②白条长度自定义（NavigationHandleHooks 协调器 + width 子处理器）
  What to do / Must NOT do: 新建 `hook/NavigationHandleHooks.java` 协调器持有 OplusNavigationHandle 全部 hook（本任务：`setVertical(boolean)` after、`onAttachedToWindow` after、`onLayout` after，均 findDeclared 限定本类）；width 子处理器：barWidthDp 非空时计算 `width = clamp(dp,80,120) × density`，改 `LinearLayout.LayoutParams.width` + `Gravity.CENTER`（非 LinearLayout.LayoutParams 则跳过 log）；配置 refresh（观察者线程）→ post 主线程重应用 + `view.requestLayout()`；onLayout 后重应用（覆盖 attach 时机外的变更）。注册入口挂 ModuleEntry/SideBackHooker 既有 onPackageLoaded 流程（NO_MATCH 守卫独立于既有 hook，互不影响）。纯函数（clamp、dp→px、LayoutParams 构造参数计算）抽 `MBackGeometry`（JVM 可测）。Must NOT: 不 hook `onDraw` 画布位移（那是手势区增高配套，未移植）；不引入 HandlerThread 以外线程。
  Parallelization: Wave 2 | Blocked by: 2,3 | Blocks: 6
  References: coloros-mod GestureHooks.java L997-L1050（applyGestureBarWidth + 触发时机）、L856-L858（mHeight/mHandleBottom 反射，本任务不需要——仅宽度）；本仓库 SideBackHooker.java L33-L84（注册守卫模板）、GestureConfigClient.java L52-L95（观察者→主线程 post 模板）
  Acceptance criteria (agent-executable): `.\gradlew testDebugUnitTest assembleDebug` EXIT=0；设备装后默认 barWidthDp=null 无任何宽度改动（白条原生）；UI 设 100dp → screencap 像素扫描白条行程 ≈100×density±5%；设 80/120 同理
  QA scenarios (name the exact tool + invocation): happy=三档长度像素断言；failure=barWidthDp 缺失/越界 → 白条完全原生不动（截图 diff 与默认一致），Evidence <.omo/evidence/mback-hintbar-nav/task-5-width.txt>
  Commit: Y | feat(hook): customizable hint bar width via NavigationHandleHooks coordinator

- [ ] 6. 特性③mBack 移植（状态机 + 注入 + MBackSurface）
  What to do / Must NOT do: 前置 = Task 3 spike 通过。`MBackGestureSpec`（纯 JVM 类）：输入原始动作序列（action, x, y, downTime, pointerCount, 热区矩形, 阈值常量）输出决策枚举（DOWN_RECORD/LONG_PRESS_FIRE/BACK_FIRE/ABANDON/CANCEL/NOP）+ 伴随动作（取消长按定时器/震动类型），颜色/View 一概不碰；`MBackTouchController`（薄适配器）：beforeHook 全接管 handleValidTouchEvent（setResult(null)），ACTION_DOWN 取配置快照（mback/barWidthDp 各读一次）+ 建手势状态（key=getDownTime()，ConcurrentHashMap）+ postDelayed(长按, ViewConfiguration.getLongPressTimeout())；MOVE 超阈值 → ABANDON（清状态、取消定时器，**不 setResult 已吞则保持吞**——系统侧滑/上滑经自身分发继续，E2E 断言）；UP 未放弃未长按 → BACK；多指/CANCEL 作废。`triggerNavigation(handle, home)`：向上遍历找 `com.android.systemui.navigationbar.views.NavigationBarView` → back: `getBackButton().getCurrentView().sendEvent(0/1,0,now)`，home: 同构 performClick；皆失败 → Task 3 验证过的 InputManager 注入（BACK/HOME）。`MBackSurface`：白色圆角 GradientDrawable mask 的 RippleDrawable 纯视觉 View，加入 NavigationBarFrame（幂等：已 attach 不重复 add；try/catch；attach/detach log 供 E2E grep），不 clickable 无 OnTouchListener；生命周期挂协调器 declared-only 的 onAttachedToWindow/onLayout/onDetachedFromWindow（颜色代码注释明确警告：findDeclared 必须限定本类否则上溯 android.view.View 全进程生效栈溢出）。震动：performHapticFeedback(LONG_PRESS/VIRTUAL_KEY)。决策日志 `MBACK decision=<..> result=<..>`。Must NOT: 不重写 MotionEvent 坐标；不 hook touchableRegion；每事件不做 Binder/Provider 查询（只用 DOWN 时快照）。
  Parallelization: Wave 3 | Blocked by: 2,3,5 | Blocks: 8
  References: coloros-mod GestureHooks.java L219-L294（hookMBack 注册+生命周期）、L415-L420（热区 band=白条高度/2+4dp 上限 8dp）、L568-L650（handleMBackTouch 状态机原版）、L655-L666（isInMBackBarRange：ev.getX() 与 viewScreenLeft 比较）、L747-L806（triggerNavigation/injectHomeKey）、L812-L897（MBackSurface）；本仓库 Task 3 spike hook 入口、NavigationHandleHooks（Task 5 产物，本任务在其中加 mBack 子处理器注册）
  Acceptance criteria (agent-executable): `.\gradlew testDebugUnitTest` EXIT=0（MBackGestureSpecTest 覆盖：tap→BACK、长按→HOME、|dx|>20dp→ABANDON、dy<-20dp→ABANDON、多指→CANCEL、DOWN 后配置变更不影响在途手势）；真机：logcat MBACK 决策日志序列正确；tap 白条→dumpsys 返回栈回退；长按→dumpsys launcher 前台；ABANDON 后上滑→回桌面正常
  QA scenarios (name the exact tool + invocation): happy=三手势全部命中注入；failure=类缺失（模拟：配置开关 off → handleValidTouchEvent 直接 chain.proceed 原生行为）、注入失败回退链逐级 log，Evidence <.omo/evidence/mback-hintbar-nav/task-6-mback.txt>
  Commit: Y | feat(mback): port Meizu-style back gesture with pure-JVM state machine

- [ ] 7. MIUIX UI：mBack 开关 + 长度滑条 + 显示开关整合
  What to do / Must NOT do: GestureSettingsScreen 增：mBack 开关（写 mback 列）、长度滑条（80–120，显示 dp 值，**仅 commit/onValueChangeFinished 写配置**，拖动中只更新本地状态；含「跟随系统(默认)」未设置态=null 的入口——用开关或「自定义长度」开关包裹滑条，实现 barWidthDp=null 语义）、显示开关（Task 4 产物；mBack ON 时禁用+副文案「mBack 需要白条显示」）、mBack ON 时弹一次确认（说明将接管白条触摸）。中文字符串入 strings.xml。Must NOT: 不做每帧 onValueChange 写入；不加新依赖；不改动既有三开关/重启按钮布局结构。
  Parallelization: Wave 3 | Blocked by: 2,4,5 | Blocks: 8
  References: app/src/main/java/com/cos/lspit/gesture/ui/GestureSettingsScreen.kt（既有 Switch/Slider 用法，miuix 0.9.3 API：top.yukonga.miuix.kmp.basic.Switch/Slider）、config/ConfigStore.kt（save 模式：写后 notifyChange）、strings.xml
  Acceptance criteria (agent-executable): `.\gradlew assembleDebug` EXIT=0；uiautomator dump 含新控件且中文正确；拖动滑条全程 `content query` 值仅在松手后变化一次；mBack 开 → 显示开关 disabled
  QA scenarios (name the exact tool + invocation): happy=uiautomator dump 三控件渲染 + 松手写一次；failure=mBack ON 状态下显示开关点不动（dump 属性断言），Evidence <.omo/evidence/mback-hintbar-nav/task-7-ui.png>
  Commit: Y | feat(ui): mBack switch, width slider and visibility toggle in MIUIX settings

- [ ] 8. 真机 E2E 矩阵 + 证据收口
  What to do / Must NOT do: 按 Verification strategy 全矩阵执行：配置通道查询、三档宽度像素断言、mBack 三手势 dumpsys 断言、ABANDON 让位断言（上滑回桌面 + 侧滑仍 VETO）、设置双向往返、MBACK_FORCE_SHOW、失败路径（pm revoke / 类缺失日志）。每项留证据文件。发现缺陷：小缺陷当场修+复测+记录，结构性缺陷记录进 evidence 并单独回报。Must NOT: 不以「用户说好」替代断言；不跳过失败路径。
  Parallelization: Wave 4 | Blocked by: 4,5,6,7 | Blocks: F1-F4
  References: Verification strategy 节全部命令；`.omo/evidence/gesture-desktop-launch/task-6-restart.txt`（既有设备操作模式：su kill systemui 重启、LSPosedLogDaemon tag 抓日志、`logcat -d | Select-String 'COS16-Gesture'`）
  Acceptance criteria (agent-executable): `.omo/evidence/mback-hintbar-nav/` 下 task-8-matrix.md 逐项 PASS 与对应原始日志/截图齐全
  QA scenarios (name the exact tool + invocation): happy=全矩阵 PASS；failure=任一项 FAIL → 记录+修复+复测循环，Evidence <.omo/evidence/mback-hintbar-nav/task-8-matrix.md>
  Commit: Y | test(device): full E2E matrix for hint bar sync, width and mBack

## Final verification wave

- [ ] F1. Plan compliance audit
  对照本计划 Scope/真值表逐项核对实现与证据；任何 Scope OUT 项出现 = FAIL。
- [ ] F2. Code quality review
  守卫完整性（每个 hook 独立 try/catch、NO_MATCH 不崩 SystemUI）、fail-closed 语义、无观察者线程 UI 操作、declared-only hook 限定。
- [ ] F3. Real manual QA
  真机全功能手感实测（mBack 三手势、滑条即时生效、设置页往返、SystemUI 重启后全部状态恢复）。
- [ ] F4. Scope fidelity
  确认未触碰 SideGestureDetector veto 行为；确认 coloros-mod MIT 版权声明已保留（移植文件头注释 + NOTICE 或 README 附注）。

## Commit strategy

每任务一提交（见各 todo Commit 行），格式沿用仓库惯例 `feat(scope)/fix(scope)/test(scope)/spike(scope): summary`，附 Co-authored-by: Copilot 尾注。Task 1 evidence-only 不提交代码。

## Success criteria

1. `.\gradlew testDebugUnitTest assembleDebug assembleRelease` 全绿
2. 配置查询含 mback/barWidthDp 两列且 fail-closed 测试通过
3. 白条显示开关与系统设置为同一键值，双向同步（含 mBack 强制显示真值表全行）
4. 白条长度 80/100/120dp 像素级可测生效，默认态零改动
5. mBack 三手势注入生效、让位手势不受影响、既有侧滑拦截不回归
6. 全部断言 agent 可执行，证据齐全于 `.omo/evidence/mback-hintbar-nav/`
