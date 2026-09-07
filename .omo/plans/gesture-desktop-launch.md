# gesture-desktop-launch - Work Plan

## TL;DR (For humans)

**你会得到什么**：现有 COS16 侧滑返回拦截模块（libxposed API 102，作用域 `com.android.systemui`）之上新增：桌面图标启动的 MIUIX 设置界面、拦截总开关 + 左/右侧独立开关（改动实时生效，无需重启）、Hook 状态显示、以及带二次确认的"重启作用域（SystemUI）"按钮（Root 优先、Shizuku 次之、都不可用时给手动指引）。

**为什么这样做**：宿主是闭源 LSP-IT，libxposed service 的 remote preferences 支持性未证明，因此配置通道用模块自有的 ContentProvider + ContentObserver——任何 LSPosed 分支都可用，且输入事件路径零 Binder 调用（volatile 快照），不拖慢 SystemUI。设备 root 已验证（`/system/bin/su`），重启走 root 最稳。

**不会做什么**：不改 HookPolicy 的目标类/方法/SHA-256 守卫；不动 `scope.list`（仍是 systemui 单行，不做作用域增删 API）；不接 libxposed service 库；不做多语言/自定义图标；不修改主仓库 `F:\Downloads\Git\COS` 的任何未跟踪文件；不恢复本分支已删除的 `.omo` 历史 evidence（删除本身作为 Task 1 提交）。

**工作量**：6 个实施任务（6 波，严格串行）+ 4 项最终验证。均为小型任务，单会话可完成编码，设备联调需一台已 root、已装 LSP-IT 的 Android 16 真机（设备 3B661M01NH500000 已验证可用）。

**风险**：MIUIX 组件 API 名（0.9.3）若与预期不符，按计划中的回退规则处理；Shizuku 以 ADB 模式运行时无法 kill systemui，会优雅降级为手动指引；SystemUI hook 状态只在注册时回报一次（够用，不做持续心跳）。

**我替你做的决定**（你已授权"其余自行确认"）：
1. 构建脚手架直接复用主仓库已验证版本（AGP 8.6.1 / Gradle 8.10.2 / compileSdk 36 / minSdk 26），并新加入 Kotlin 2.0.21 + Compose（BOM 2024.12.01）+ MIUIX 0.9.3 + Shizuku 13.1.5。
2. 配置通道：自有 ContentProvider（`com.cos.lspit.gesture.config`，query-only 读配置 + insert 写状态），ContentObserver 实现实时生效——对应你选的 3C。
3. 开关粒度：master/left/right 三个布尔（你选的 2C）。
4. 重启：root 优先 → Shizuku → 手动指引，全部带二次确认（你选的 4D + 二次确认）。
5. 默认快照 all-true：Provider 不可读时保持现有拦截行为不变（延续现状，最安全）。
6. versionCode 2 / versionName "1.1"。
7. 界面中文，单一 `MainActivity`。

## Scope

**IN**：
- 根 Gradle 脚手架入库（从主仓库复制并扩展 version catalog）。
- `.omo` 临时文件清理的提交（工作树中已有未暂存删除）。
- 配置层：`GestureConfig` + `ConfigStore`（SharedPreferences）+ `ConfigProvider`。
- Hook 集成：`GestureConfigClient`（SystemUI 进程内 volatile 快照 + ContentObserver）+ `SideGesturePolicy` 左右独立 veto + `SideBackHooker` 咨询配置与状态回报。
- 重启引擎：`RestartCommands`（纯函数）+ `ScopeRestarter`（root/Shizuku/手动）。
- UI：`MainActivity` + `GestureSettingsScreen`（MIUIX Compose）+ strings + Manifest 桌面入口与 Provider 声明。
- 设备端到端验证与证据留存。

**OUT（Must NOT Have）**：
- 不引入 `io.github.libxposed:service`，不读写 LSP-IT 内部 AIDL/数据库。
- 不修改 `HookPolicy.TARGET_*` / `EXPECTED_SYSTEMUI_SHA256` / fail-closed 逻辑。
- 不修改 `META-INF/xposed/scope.list`、`java_init.list`、`module.prop` 的既有内容（`minApiVersion=101`、`targetApiVersion=102`、`staticScope=true` 保持不变）。
- 不删除/重写现有 JVM 测试；只按新签名更新并新增用例。
- 不提交 `local.properties`、`*.apk`、`.gradle/`、`build/` 产物。
- 不触碰主仓库 `F:\Downloads\Git\COS` 工作区（只读复制源）。

## Verification strategy

- **单元测试（JVM，既有模式）**：`.\gradlew.bat :app:testDebugUnitTest --console=plain`。覆盖：策略矩阵（新增左右独立开关分支）、配置解析、重启命令构建。TDD：先写失败测试再实现。
- **构建**：`.\gradlew.bat :app:assembleDebug :app:assembleRelease --console=plain` 均 exit 0；release APK 必须仍含 `META-INF/xposed/{java_init.list,module.prop,scope.list}` 且不含 `io/github/libxposed/api` 类（沿用 task-5 验证法 `jar tf`）。
- **设备 QA（真机 3B661M01NH500000，已 root + LSP-IT + 模块已启用）**：`adb install -r` 后用 `adb logcat -s COS16-Gesture` 验证标记矩阵：`MODULE_READY`、`HOST_API`、`HOOK_REGISTERED`、`STATUS_REPORTED`、`CONFIG_UPDATE`、`VETO`/`PASS`；开关组合手测（master off→边缘侧滑返回恢复系统行为；left off/right on→仅左侧放行）；重启按钮→双确认→SystemUI 自动重启→`HOOK_REGISTERED` 再次出现。
- **最终验证波**：F1 计划符合性审计、F2 代码质量审查、F3 真机手工 QA、F4 范围保真审查。全部 APPROVE 才算完成。

## Execution strategy

- 由 worker 会话（Atlas / Hephaestus / Sisyphus-Junior，用户自行启动）逐任务执行；每个任务独立提交。
- 波次严格串行：T1→T2→T3→T4→T5→T6（Manifest 与 build.gradle.kts 被多个任务触碰，串行避免冲突）。
- 每任务的 QA 证据写入 `.omo/evidence/gesture-desktop-launch/task-N-*.txt`。
- 编码期间**禁止**运行会改设备系统状态的高危命令；Task 6 设备验证中唯一系统级动作是重启 SystemUI（可自动恢复的 persistent 进程）。

## Todos

- [ ] 1. 提交 .omo 清理并入库已验证构建脚手架
  What to do / Must NOT do: 先 `git add -A .omo && git commit` 把已存在的 .omo 删除提交掉；再从 `F:\Downloads\Git\COS`（只读复制源）复制 `settings.gradle.kts`、`build.gradle.kts`（根）、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle\wrapper\gradle-wrapper.jar`、`gradle\wrapper\gradle-wrapper.properties`、`local.properties`（后者不入库）、`.gitignore` 到本 worktree 根；把 `gradle\libs.versions.toml` 扩展为下方完整版（原版只有 agp/compileSdk/minSdk 三项）；新增根 `.gitignore` 追加 `.omo/recovery/`、`.omo/drafts/`、`.omo/evidence/omo-port-vscode-copilot/`、`.kotlin/`。Must NOT：不修改主仓库任何文件；不提交 local.properties；不改 app/ 下代码。
  libs.versions.toml 完整内容（逐字使用）：
  ```toml
  [versions]
  agp = "8.6.1"
  kotlin = "2.0.21"
  compileSdk = "36"
  minSdk = "26"
  coreKtx = "1.13.1"
  activityCompose = "1.9.3"
  lifecycleRuntimeKtx = "2.8.7"
  composeBom = "2024.12.01"
  miuix = "0.9.3"
  junit = "4.13.2"
  libxposedApi = "102.0.0"
  shizuku = "13.1.5"

  [libraries]
  androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
  androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
  androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
  androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
  androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
  miuix-ui = { group = "top.yukonga.miuix.kmp", name = "miuix-ui", version.ref = "miuix" }
  junit = { group = "junit", name = "junit", version.ref = "junit" }
  libxposed-api = { group = "io.github.libxposed", name = "api", version.ref = "libxposedApi" }
  shizuku-api = { group = "dev.rikka.shizuku", name = "api", version.ref = "shizuku" }
  shizuku-provider = { group = "dev.rikka.shizuku", name = "provider", version.ref = "shizuku" }

  [plugins]
  android-application = { id = "com.android.application", version.ref = "agp" }
  kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
  kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
  ```
  Parallelization: Wave 1 | Blocked by: none | Blocks: 2,3,4,5,6
  References (executor has NO interview context - be exhaustive): 工作树现状 `git status --short` 显示 22 个 `.omo` 未暂存删除；主仓库脚手架源 `F:\Downloads\Git\COS\{settings.gradle.kts,build.gradle.kts,gradle.properties,gradlew,gradlew.bat,gradle\wrapper\*,local.properties,.gitignore}`；主仓库 `gradle.properties` 内容 `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` + `android.useAndroidX=true` + `android.suppressUnsupportedCompileSdk=36`；现有 [app/build.gradle.kts](app/build.gradle.kts) 引用 `libs.plugins.android.application`、`libs.versions.compileSdk`、`libs.versions.minSdk`；上次构建证据（git 内 `HEAD:.omo/evidence/coloros16-lspit/phase2/task-5-build.txt`）证明 `gradlew.bat` 在该脚手架上 exit 0。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest --console=plain` exit 0（既有 19 用例全绿：SideGesturePolicyTest 10 + HookPolicyTest 9）；`git status --short` 中不再出现 `.omo` 删除项；`git ls-files` 含 `settings.gradle.kts`、`build.gradle.kts`、`gradle/wrapper/gradle-wrapper.jar`、`gradle/libs.versions.toml` 且不含 `local.properties`。
  QA scenarios (name the exact tool + invocation): happy — `.\gradlew.bat :app:testDebugUnitTest --console=plain | Tee-Object .omo\evidence\gesture-desktop-launch\task-1-unit-test.txt` 输出 BUILD SUCCESSFUL；failure — 临时把 libs.versions.toml 中 kotlin 改为不存在版本号 `9.9.9` 运行 `.\gradlew.bat help`，确认报 unresolved（验证 catalog 生效后改回）。Evidence `.omo/evidence/gesture-desktop-launch/task-1-unit-test.txt`
  Commit: Y | 两条：`chore(cleanup): remove stale OMO evidence temp files` + `build(gradle): commit proven Android build scaffolding with Kotlin/Compose catalog`

- [ ] 2. 实现配置层 GestureConfig + ConfigStore + ConfigProvider
  What to do / Must NOT do: 新建 Kotlin 文件（app/src/main/java/com/cos/lspit/gesture/config/）。`GestureConfig.kt`：`data class GestureConfig(val masterEnabled: Boolean = true, val leftEnabled: Boolean = true, val rightEnabled: Boolean = true, val version: Int = 1)`，伴生对象 `fun fromValues(master: Int?, left: Int?, right: Int?, version: Int?): GestureConfig`（null 或非 0/1 一律回退 true/1）。`ConfigStore.kt`：单例，持有 `SharedPreferences("gesture_config")`，`fun load(context): GestureConfig`、`fun save(context, config: GestureConfig)`（写后 `context.contentResolver.notifyChange(ConfigProvider.CONFIG_URI, null)`）。`ConfigProvider.kt`：`class ConfigProvider : ContentProvider()`，companion 常量 `AUTHORITY = "com.cos.lspit.gesture.config"`、`CONFIG_URI = Uri.parse("content://$AUTHORITY/config")`、`STATUS_URI = Uri.parse("content://$AUTHORITY/status")`、`CONFIG_COLUMNS = arrayOf("master", "left", "right", "version")`；`query()` 忽略 selection，返回 MatrixCursor 单行（开关 1/0，用 `GestureConfig.fromValues` 解析自 SharedPreferences）；`insert(uri, values)` 仅接受 STATUS_URI，把 `outcome`/`detail`/`at_millis` 写入 `SharedPreferences("gesture_status")` 并返回该 URI；`update`/`delete` 返回 0；`getType` 返回 null；`onCreate` 返回 true。JVM 测试 `GestureConfigTest.kt`（app/src/test/java/com/cos/lspit/gesture/config/）：`fromValues` 的 1/0/null/非法值回退矩阵。Must NOT：Provider 不暴露任何配置写入方法；不在本任务接 hook 或 UI；不用 DataStore/Flow（保持 SharedPreferences 极简）。
  Parallelization: Wave 2 | Blocked by: 1 | Blocks: 3,5
  References: 现有 config 包 [HookPolicy.java](app/src/main/java/com/cos/lspit/gesture/config/HookPolicy.java)、[SideGesturePolicy.java](app/src/main/java/com/cos/lspit/gesture/config/SideGesturePolicy.java)；测试样式参照 [HookPolicyTest.java](app/src/test/java/com/cos/lspit/gesture/config/HookPolicyTest.java)（JUnit 4 断言风格）；Manifest Provider 声明在 Task 5 一并做（本任务只写代码，构建不声明不报错）。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest --console=plain` exit 0 且包含 GestureConfigTest 全部用例；`.\gradlew.bat :app:assembleDebug --console=plain` exit 0。
  QA scenarios: happy — 新增用例 `fromValues(1, 0, 1, 1)` 得 `master=true, left=false, right=true`；failure — `fromValues(null, null, null, null)` 得全 true 默认值（Provider 首启/损坏场景）。Evidence `.omo/evidence/gesture-desktop-launch/task-2-unit-test.txt`
  Commit: Y | `feat(config): add gesture config store and provider channel`

- [ ] 3. Hook 集成：运行时开关生效 + 状态回报
  What to do / Must NOT do: 三处修改。(a) [SideGesturePolicy.java](app/src/main/java/com/cos/lspit/gesture/config/SideGesturePolicy.java) `shouldVeto` 增加 `boolean vetoLeft, boolean vetoRight` 两参数，尾部逻辑改为 `boolean left = x < SIDE_W; boolean right = x > displayWidth - SIDE_W; return (left && vetoLeft) || (right && vetoRight);`（其余守卫不变）。(b) 新建 `app/src/main/java/com/cos/lspit/gesture/hook/GestureConfigClient.java`（纯 Java，无 Kotlin 依赖）：静态 volatile 三布尔默认 true；`init(Context)` 反射 `android.app.ActivityThread.currentApplication()` 无需传参（内部自取 Context），先 `contentResolver.query(CONFIG_URI, null, null, null, null)` 读快照，再在守护 `HandlerThread("cos16-gesture-cfg")` 上 `registerContentObserver(CONFIG_URI, true, observer)`，回调里同步 requery 并 `log CONFIG_UPDATE master=..left=..right=..`；`isMasterEnabled()/isLeftEnabled()/isRightEnabled()`；`reportStatus(XposedModule module, String outcome, String detail)` 用 `ContentResolver.insert(STATUS_URI, ContentValues{outcome, detail, at_millis})` best-effort（try/catch 吞异常，绝不影响 SystemUI）；URI 常量以字符串字面量 `content://com.cos.lspit.gesture.config/config|status` 定义（Java 侧不依赖 Kotlin 类）。query 失败/异常一律保持当前快照（首启即默认全 true）。(c) [SideBackHooker.java](app/src/main/java/com/cos/lspit/gesture/hook/SideBackHooker.java)：注册 hook 成功后调 `GestureConfigClient.init(...)` 并 `reportStatus(module, "HOOK_REGISTERED", target)`；四条 fail-closed 路径（HOOK_DISABLED/HASH_MISMATCH/NO_MATCH×2）各补一条 `reportStatus(module, <outcome>, <原因>)`；`handle()` 中 veto 判定改为 `SideGesturePolicy.shouldVeto(action, source, toolType, x, y, w, h, client.isMasterEnabled() && client.isLeftEnabled(), client.isMasterEnabled() && client.isRightEnabled())`（输入路径只读 volatile，零 Binder）。更新 [SideGesturePolicyTest.java](app/src/test/java/com/cos/lspit/gesture/config/SideGesturePolicyTest.java)：全部旧用例改传新参数（默认 true,true 应保持原断言），新增 `leftOnlyVetoesLeftEdge`、`rightOnlyVetoesRightEdge`、`bothDisabledNeverVetoes` 三用例。Must NOT：不改 HookPolicy 守卫与 descriptor 逻辑；不在 ACTION_DOWN 热路径做任何 IPC/IO；observer 回调里不得抛异常到 SystemUI。
  Parallelization: Wave 3 | Blocked by: 2 | Blocks: 5,6
  References: [SideBackHooker.java:33-76](app/src/main/java/com/cos/lspit/gesture/hook/SideBackHooker.java)（onPackageLoaded 守卫链）、[SideBackHooker.java:83-109](app/src/main/java/com/cos/lspit/gesture/hook/SideBackHooker.java)（handle 拦截体）、[SideGesturePolicy.java:37-45](app/src/main/java/com/cos/lspit/gesture/config/SideGesturePolicy.java)（现 shouldVeto）、[ModuleEntry.java:27-31](app/src/main/java/com/cos/lspit/gesture/ModuleEntry.java)（systemui 包名过滤）；log 标记体系沿用 TAG "COS16-Gesture"。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest --console=plain` exit 0（新旧用例全绿，总数 ≥ 28：19 既有 + 6 配置层 + 3 新策略）；`.\gradlew.bat :app:assembleDebug :app:assembleRelease --console=plain` exit 0；`jar tf app\build\outputs\apk\release\app-release-unsigned.apk | Select-String 'META-INF/xposed'` 仍输出三行。
  QA scenarios: happy — 新用例 `leftOnlyVetoesLeftEdge`：`shouldVeto(ACTION_DOWN, SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER, 2f, 1300f, 1080, 2376, true, false)` 为 true；failure — `bothDisabledNeverVetoes`：同参数 `(false, false)` 为 false（系统手势恢复）。Evidence `.omo/evidence/gesture-desktop-launch/task-3-unit-test.txt`
  Commit: Y | `feat(hook): runtime master/left/right veto gating with live config refresh`

- [ ] 4. 实现重启引擎 RestartCommands + ScopeRestarter
  What to do / Must NOT do: (a) 纯函数对象 `app/src/main/java/com/cos/lspit/gesture/restart/RestartCommands.kt`：`const val TARGET_PACKAGE = "com.android.systemui"`；`fun rootKill(): Array<String> = arrayOf("su", "-c", "kill \$(pidof $TARGET_PACKAGE)")`；`fun shizukuKill(): Array<String> = arrayOf("sh", "-c", "kill \$(pidof $TARGET_PACKAGE)")`；`fun rootProbe(): Array<String> = arrayOf("su", "-c", "id")`。(b) `ScopeRestarter.kt`：`sealed class Capability { object Root; object Shizuku; object None }`、`sealed class RestartResult { object Success; data class Failure(reason: String); object Manual }`；`suspend fun detect(context): Capability`——先 root probe（`Runtime.getRuntime().exec(RestartCommands.rootProbe())` 读输出含 `uid=0` 即 Root），否则查 Shizuku（`Shizuku.pingBinder()` 且 `Shizuku.checkSelfPermission(context) == PERMISSION_GRANTED`，API 13 需 `Shizuku.shouldShowRequestPermissionRationale()` 分支处理未授权→None），否则 None；`suspend fun restart(context, cap): RestartResult`——Root 用 `Runtime.exec(rootKill())` 等 exit 0；Shizuku 用 `Shizuku.newProcess(RestartCommands.shizukuKill(), null, null)`（ADB 模式下 kill 失败/非零退出 → Failure("shizuku-no-perm")）；None → Manual。UI 不在本任务。Shizuku 权限请求监听器留到 Task 5 接 UI 时挂。(c) Manifest 的 ShizukuProvider 声明与本任务无依赖，统一放 Task 5。JVM 测试 `RestartCommandsTest.kt`：命令数组逐字断言（避免 shell 注入/typo）。Must NOT：不用 `am force-stop`（对 persistent 进程无效且语义更重）；不做重启 LSP-IT 管理器/其他作用域；detect/restart 全部 try/catch，异常→Failure，永不 crash UI。
  Parallelization: Wave 4 | Blocked by: 1 | Blocks: 5
  References: 设备 root 证据 git `HEAD:.omo/evidence/coloros16-lspit/task-1-host-contract.json`（`adb shell which su => /system/bin/su`，rootful branch proven）；Shizuku API 13.1.5（Maven Central `dev.rikka.shizuku:api`，`Shizuku.pingBinder()`/`checkSelfPermission()`/`newProcess(String[], String?, String?)`）；SystemUI 为 persistent 进程，kill 后由系统自动拉起（Android 标准行为，无需手动 start）。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest --console=plain` exit 0 含 RestartCommandsTest；`.\gradlew.bat :app:assembleDebug --console=plain` exit 0。
  QA scenarios: happy — `RestartCommandsTest.rootKill()` 精确等于 `["su","-c","kill $(pidof com.android.systemui)"]`；failure — JVM 环境无 root/su，`ScopeRestarter.detect` 在纯 JVM 单测中不测（Android 依赖），仅命令构建测（防越权测设备）。Evidence `.omo/evidence/gesture-desktop-launch/task-4-unit-test.txt`
  Commit: Y | `feat(restart): root-first SystemUI scope restart engine with Shizuku fallback`

- [ ] 5. 实现 MIUIX 桌面入口 UI 与 Manifest 声明
  What to do / Must NOT do: (a) [app/build.gradle.kts](app/build.gradle.kts) 按下文完整替换（plugins 加 kotlin-android/kotlin-compose；buildFeatures 加 `compose = true`；kotlin compilerOptions jvmTarget 21；dependencies 加 core-ktx/lifecycle/activity-compose/compose-bom/ui/miuix-ui/shizuku-api/shizuku-provider；versionCode 2 / versionName "1.1"）。(b) [AndroidManifest.xml](app/src/main/AndroidManifest.xml) 按下文完整替换（MainActivity LAUNCHER 入口 + ConfigProvider exported + ShizukuProvider 声明）。(c) [strings.xml](app/src/main/res/values/strings.xml) 新增：`app_name=COS16 手势`、`settings_title=侧滑返回拦截`、`switch_master=拦截总开关`、`switch_left=拦截左侧边缘`、`switch_right=拦截右侧边缘`、`section_scope=作用域`、`status_label=Hook 状态`、`status_unknown=未回报（SystemUI 未重启或模块未启用）`、`btn_restart=重启作用域（SystemUI）`、`dialog_restart_title=确认重启`、`dialog_restart_message=将结束 SystemUI 进程并由系统自动重启，屏幕状态栏/导航栏会短暂消失后恢复。继续？`、`restart_result_ok=已执行重启指令`、`restart_result_manual_title=需要手动重启`、`restart_result_manual=未检测到 Root/Shizuku 权限。请重启手机，或在 LSP-IT 中重新启用模块。`。(d) 新建 `app/src/main/java/com/cos/lspit/gesture/ui/MainActivity.kt`：`class MainActivity : ComponentActivity()`，onCreate 中 `enableEdgeToEdge(); setContent { MiuixTheme { GestureSettingsScreen() } }`。(e) 新建 `ui/GestureSettingsScreen.kt`：`@Composable fun GestureSettingsScreen()`，状态 = `remember { mutableStateOf(ConfigStore.load(context)) }` + `remember { mutableStateOf(readStatus(context)) }`（读 `SharedPreferences("gesture_status")` 的 outcome/detail/at_millis）；布局 `Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) })` 内 `LazyColumn`：`SmallTitle("拦截设置")` → `Card` 内三个 `SuperSwitch`（总开关；左右两项 `enabled = masterEnabled`，子开关关闭时 master 仍可开）；`SmallTitle(stringResource(R.string.section_scope))` → `Card` 内状态行（outcome + SimpleDateFormat 格式化 at_millis）+ `Button(stringResource(R.string.btn_restart))` → 点击置 `showConfirm = true` → `SuperDialog(showConfirm)` 二次确认（标题/文案用上述 strings，确认后 `scopeViewModel` 协程调 `ScopeRestarter.detect` → `restart`，结果 Snackbar/文案：Success→`restart_result_ok`，Manual→`restart_result_manual`，Failure→显示 reason）；每次 SuperSwitch onCheckedChange → `ConfigStore.save` → 局部 state 刷新 + `status` 不变。MIUIX 组件回退规则：优先 import `top.yukonga.miuix.kmp.basic.{Scaffold, TopAppBar, Card, SmallTitle, Text}` 与 `top.yukonga.miuix.kmp.extra.{SuperSwitch, SuperDialog}`；若 0.9.3 实际包名/签名不同（编译报 unresolved），改用 `top.yukonga.miuix.kmp.basic.Switch` + 手写 Row 布局与 `androidx.compose.material3.AlertDialog` 替代，保持 MIUIX 主题与视觉不变——这是唯一允许的等价替换。(f) Shizuku 权限：`MainActivity.onCreate` 注册 `Shizuku.addRequestPermissionResultListener`，`onDestroy` 移除；`ScopeRestarter.detect` 返回需要 Shizuku 且未授权时先 `Shizuku.requestPermission(1001)` 再降级提示。Must NOT：不做多 Activity/导航图；不引入 miuix-preference；不显示广告/版本更新检查等额外功能；不改 xposed 元数据文件。
  app/build.gradle.kts 完整内容（逐字）：
  ```kotlin
  plugins {
      alias(libs.plugins.android.application)
      alias(libs.plugins.kotlin.android)
      alias(libs.plugins.kotlin.compose)
  }

  android {
      namespace = "com.cos.lspit.gesture"
      compileSdk = libs.versions.compileSdk.get().toInt()
      defaultConfig {
          applicationId = "com.cos.lspit.gesture"
          minSdk = libs.versions.minSdk.get().toInt()
          targetSdk = libs.versions.compileSdk.get().toInt()
          versionCode = 2
          versionName = "1.1"
      }
      compileOptions {
          sourceCompatibility = JavaVersion.VERSION_21
          targetCompatibility = JavaVersion.VERSION_21
      }
      buildFeatures { buildConfig = false; compose = true }
      packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
  }

  kotlin {
      compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
      }
  }

  dependencies {
      compileOnly(libs.libxposed.api)
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.lifecycle.runtime.ktx)
      implementation(libs.androidx.activity.compose)
      implementation(platform(libs.androidx.compose.bom))
      implementation(libs.androidx.compose.ui)
      implementation(libs.miuix.ui)
      implementation(libs.shizuku.api)
      implementation(libs.shizuku.provider)
      testImplementation(libs.junit)
  }
  ```
  AndroidManifest.xml 完整内容（逐字）：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      <application
          android:label="@string/app_name"
          android:description="@string/module_description">
          <activity
              android:name="com.cos.lspit.gesture.ui.MainActivity"
              android:exported="true"
              android:label="@string/app_name">
              <intent-filter>
                  <action android:name="android.intent.action.MAIN" />
                  <category android:name="android.intent.category.LAUNCHER" />
              </intent-filter>
          </activity>
          <provider
              android:name="com.cos.lspit.gesture.config.ConfigProvider"
              android:authorities="com.cos.lspit.gesture.config"
              android:exported="true" />
          <provider
              android:name="rikka.shizuku.ShizukuProvider"
              android:authorities="${applicationId}.shizuku"
              android:multiprocess="false"
              android:enabled="true"
              android:exported="true"
              android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
      </application>
  </manifest>
  ```
  Parallelization: Wave 5 | Blocked by: 2,3,4 | Blocks: 6
  References: MIUIX 官方坐标 `top.yukonga.miuix.kmp:miuix-ui:0.9.3`（Maven Central，2026-07-04 发布；Compose Multiplatform，API 实验性——版本必须固定 0.9.3 不得浮动）；官方示例 `ComponentActivity + setContent + MiuixTheme`（compose-miuix-ui/miuix example/android MainActivity.kt）；ConfigProvider URI 见 Task 2；ScopeRestarter API 见 Task 4；既有 [proguard-rules.pro](app/proguard-rules.pro) 不动（无 minify）。
  Acceptance criteria (agent-executable): `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain` 全部 exit 0；`jar tf app\build\outputs\apk\debug\app-debug.apk | Select-String 'META-INF/xposed'` 三行俱在；`aapt dump badging` 或 `jar tf` 输出含 `classes.dex` 与 Compose/MIUIX 类（dex 变大即可）。
  QA scenarios: happy — `.\gradlew.bat :app:assembleDebug --console=plain | Tee-Object .omo\evidence\gesture-desktop-launch\task-5-build.txt` BUILD SUCCESSFUL；failure — 若 MIUIX/Compose 解析失败（如 miuix-ui 依赖 org.jetbrains.compose 坐标），把 Gradle 报错中列出的缺失坐标逐条加入 dependencies（确定性规则），重跑至 exit 0，把解决过程追加进 task-5-build.txt。Evidence `.omo/evidence/gesture-desktop-launch/task-5-build.txt`
  Commit: Y | `feat(ui): MIUIX desktop launcher with live switches and confirmed scope restart`

- [ ] 6. 真机端到端验证并留存证据
  What to do / Must NOT do: 设备 3B661M01NH500000（已 root、Android 16 SDK 36、ColorOS V16.1.0、LSP-IT 已启用本模块）。步骤：(1) `adb install -r app\build\outputs\apk\debug\app-debug.apk`；(2) `adb logcat -c` 后手动从桌面打开"COS16 手势"，确认界面三开关 + 状态行出现；(3) 触发 SystemUI 重启（用 UI 重启按钮，root 路径）后抓 `adb logcat -d -s COS16-Gesture`，断言标记序列含 `MODULE_READY`、`HOST_API`、`HOOK_REGISTERED`、`STATUS_REPORTED`；(4) UI 关闭总开关 → logcat 出现 `CONFIG_UPDATE master=false...`，边缘侧滑返回恢复系统行为（无 VETO 日志，系统返回动画正常）；(5) 重开总开关 → VETO 日志恢复，侧滑被拦截；(6) 仅关左侧 → 左缘侧滑放行、右缘仍 VETO；(7) 状态行显示 HOOK_REGISTERED 与时间；(8) 全程截图/日志存证。Must NOT：不测 HASH_MISMATCH 分支（不换 SystemUI 固件）；不修改 `HookPolicy.EXPECTED_SYSTEMUI_SHA256`；不在设备上执行计划外命令；若 root kill 后 SystemUI 未自动恢复（异常情况），等待 10s 后用 `adb shell su -c "start"` 兜底并记录。
  Parallelization: Wave 6 | Blocked by: 5 | Blocks: none
  References: 设备契约 git `HEAD:.omo/evidence/coloros16-lspit/task-1-host-contract.json`（sdk 36、su 路径、SystemUI apk 路径 `/system_ext/priv-app/SystemUI/SystemUI.apk`）；上次安装启用流程 git `HEAD:.omo/evidence/coloros16-lspit/phase2/task-6-install.txt`；hook 日志 TAG "COS16-Gesture"；Task 3 定义的标记集合。
  Acceptance criteria (agent-executable): 上述 (1)-(7) 全部通过；`.omo/evidence/gesture-desktop-launch/` 下存在 `task-6-install.txt`、`task-6-logcat.txt`、`task-6-toggle-matrix.md`（含 6 组开关组合×左右缘×期望结果的实测表）、`task-6-restart.txt`。
  QA scenarios: happy — 开关矩阵实测与 [SideGesturePolicyTest](app/src/test/java/com/cos/lspit/gesture/config/SideGesturePolicyTest.java) 断言一一对应；failure — 若 UI 显示 `status_unknown`（无 STATUS_REPORTED），检查模块是否在 LSP-IT 中启用、logcat 是否有 `MODULE_READY`，把排查记录写入 task-6-restart.txt。Evidence `.omo/evidence/gesture-desktop-launch/task-6-*.txt`
  Commit: Y | `test(device): verify live toggles and confirmed scope restart on Android 16 host`

## Final verification wave

- [ ] F1. Plan compliance audit
  逐条对照本计划 Scope IN/OUT 与 Todos 验收标准，核对 6 个任务证据文件齐全、命令输出真实（非自述）；特别核对 OUT 清单：HookPolicy/scope.list/module.prop 未被改动（`git diff e2fda10..HEAD -- app/src/main/java/com/cos/lspit/gesture/config/HookPolicy.java app/src/main/resources/` 应为空或仅本计划明确允许的行）。
- [ ] F2. Code quality review
  派 code-review 审查新增 Kotlin/Java：Provider 无写入口、hook 热路径零 IPC、全部 try/catch 兜底、命名与既有风格一致、无死代码；问题清单为空或已修复。
- [ ] F3. Real manual QA
  复跑 Task 6 的开关矩阵与重启流程一遍（不依赖 Task 6 证据，独立复验），记录到 `.omo/evidence/gesture-desktop-launch/final-qa.txt`。
- [ ] F4. Scope fidelity
  确认未实现任何 OUT 项（无 libxposed service 依赖、无作用域增删、无多语言/图标、主仓库未被修改 `git -C F:\Downloads\Git\COS status` 与执行前一致）。

## Commit strategy

- 每任务至少一条 conventional commit（见各 todo Commit 行），全部附 `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>` 尾注。
- 顺序：chore(cleanup) → build(gradle) → feat(config) → feat(hook) → feat(restart) → feat(ui) → test(device)。
- 禁止提交 `local.properties`、`.gradle/`、`build/`、`*.apk`、`.omo/recovery/`、`.omo/drafts/`。
- `.omo/evidence/gesture-desktop-launch/` 证据文件随对应 test(device) 或最终波提交。

## Success criteria

1. 桌面出现"COS16 手势"图标，打开即 MIUIX 风格设置页（总开关/左/右/状态/重启按钮）。
2. 三个开关改动在 1 秒内对 SystemUI 生效（logcat CONFIG_UPDATE ↔ 手感变化对应），无需重启作用域。
3. "重启作用域"点击 → 二次确认 → SystemUI 重启 → 模块重新注册（HOOK_REGISTERED 再现），Root 不可用时明确降级提示而非静默失败。
4. 既有 fail-closed 守卫（哈希/类/方法/描述符）行为与 `e2fda10` 完全一致；Provider 不可读时拦截行为保持默认全开（现状不变）。
5. `:app:testDebugUnitTest` ≥ 30 用例全绿（终态约 32 例）；debug/release 构建均 exit 0；release APK 保留三个 `META-INF/xposed` 条目且不含 libxposed api 类。
6. F1-F4 全部 APPROVE，证据链完整可复核。
