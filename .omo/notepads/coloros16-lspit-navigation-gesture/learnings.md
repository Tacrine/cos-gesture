# learnings


## [2026-09-06] Execution learnings
- First hook subagent (grok-4.6) FABRICATED full success: claimed 4 files + passing tests, changed nothing. Lesson: always verify via git status + own build run; grok model unreliable for autonomous multi-file edits here.
- Gradle 8.10.2 dist download needs networkTimeout>=120000. JAVA_HOME must be graalvm-21 for Java 21 target.
- AOSP-16 baseline gate method confirmed from source: EdgeBackGestureHandler.isWithinTouchRegion(MotionEvent)Z single call site at ACTION_DOWN allow decision.


## [2026-09-06] Verified build completion (hook-impl-2 + Atlas)
- local.properties must use FORWARD slashes: sdk.dir=C:/... AGP reads via java.util.Properties which strips unescaped backslashes.
- GraalVM jlink.exe fails AGP JdkImageTransform for android-36 core-for-system-modules.jar. Fix: standard HotSpot JDK 21 (Temurin 21.0.12.1) at C:\Users\Administrator\AppData\Local\Jdk\jdk-21.0.12.1+1. Gradle JAVA_HOME must point there.
- AndroidManifest android:description literal invalid at aapt2; must use @string resource.
- VERIFIED: :app:test (debug+release, 5/5 HookPolicyTest pass) + :app:assembleDebug + :app:assembleRelease all BUILD SUCCESSFUL.
- Release APK: META-INF/xposed/{java_init.list,module.prop,scope.list} present; classes.dex has 0 libxposed api definitions (compileOnly clean).
- Suppress warning: android.suppressUnsupportedCompileSdk=36 added to gradle.properties.
- hook-impl-2 agent honest and reliable (no fabrication); reported real errors, accepted fixes, produced verifiable files.

## [2026-09-07] Task-1 re-open: "LSP-IT" host investigation (status NO_GO but identity RESOLVED)
- RESOLVED: "LSP-IT" = "LSPosed IT (Internal Test)" — real host, NOT a separate closed-source product. It is LSPosed's own master-CI line (proof: build.gradle.kts verCode=commitCount+4200) distributed via internal-test leaks/Telegram/mirrors; official GitHub Releases stop at v1.9.2 (Android <=14).
- Strongest domain proof: DHD2280/Oxygen-Customizer README (a real OOS/ColorOS16 module) requires "LSPosed IT (Internal Test)" for OOS16 (or LSPosed-Irena 7280+ w/ Zygisk Next 534+ / ReLSPosed 7200+ w/ ReZygisk); needs Magisk/KSU/APatch + Zygisk.
- libxposed API 102 module floor = LSPosed v2.1.0 (7769)+ (official Xposed Module Repository: modern-api102.apk only for >= v2.1.0-7769). v2.1.0-7769 "implements libxposed API 102" (lsposed.cn, low trust). v2-line supports Android 8.1-17 Beta (official Telegram); LSPosed IT proven on Android 16 (mrdong916 HyperOS install of v1.9.2-it-7573).
- Manager package: org.lsposed.manager (verified from source). Disable = deactivate module in manager + reboot (official README). Restart = reboot.
- Ruled out: VeryBaaad/LSPosed-ITed (fork of LSPosed-Irena, look-alike name); Dreamland (Android <=14); ReLSPosed (archived 2026-02); official stable LSPosed (Android <=14).
- Verdict NO_GO (strict gate): no trustworthy downloaded v2.1.0+ binary + no on-device ColorOS V16.1.0/SDK36 + API-102 proof. Flip evidence = 1 verified artifact (zip/APK+sha256, APK shows libxposed>=102) + install on device + manager reports API 102. Security note: IT binaries are leak-distributed w/ malware warnings — hash-verify before use.
- File: .omo/evidence/coloros16-lspit/task-1-reopen.json

---

## 2026-09-07 08:41 CST — Task 3 DONE: exact side-back hook point identified & proven (outcome MATCH)

Ran as DISCOVERY ONLY. No module installed/enabled, no settings/nav-mode change, no repo product source touched.

### Hook point (single chokepoint in SystemUI process, pid 1091)
- Class: `com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector`
  (oplus impl; superclass stub `SideGestureDetectorEx` also lives in SystemUI.apk classes.dex => SystemUI app classloader, NOT bootclasspath).
- Method: `onMotionEventImpl(Landroid/view/MotionEvent;)V` — PUBLIC FINAL, acc 0x0011, classes3.dex class#4219.
- Entry chain: InputMonitor "edge-swipe" -> EdgeBackGestureHandler.onInputEvent$1 -> onMotionEventImpl (unless isGestureUpMode()).
- Allow gate is INLINE in onMotionEventImpl (AOSP isWithinTouchRegion absent: 0 hits in APK). Rejections logged via StringJoiner "|":
  "disabled for quickstep" | "back Gesture not allowed" | "back gesture disabled by sysui flags" | "touch region not valid".
- Downstream final Back: mAllowGesture && shouldRespondToGesture() -> dispatchToBackAnimation -> com.android.wm.shell.back.BackAnimation
  (classes2.dex bundled; pid 1091) -> SideGestureDetector$3.triggerBack -> setTriggerBack(true) or KEYCODE_BACK injection.

### Key evidence
- Artifacts: SystemUI.apk SHA-256 7144D7E0E7DA46BC5F408C71C7B8D761DF97EE1C6B52F99FA2F40577183B71B8 (94,761,448 B);
  OplusLauncher.apk SHA-256 479DCADFE80B0AC06C478A5668004B9A6AF74B355AAD78DA755F0C57DE8BD370 (81,194,045 B).
- t3a (UNLOCKED, left-edge x=2): full chain to "BackAnimation Triggered back" in SystemUI pid 1091 — side-down allowed -> final Back.
- t3b2 (secure lockscreen, right-edge x=1078): "AllowGesture false cuz: |||back gesture disabled by sysui flags|",
  isInvalidEvent:true, NO Triggered back — gate runs BEFORE pilfer/final Back dispatch.
- t3c (bottom-up y=2372, same capture): homegesture_displayid_0 monitor -> launcher pid 1457 Quickstep
  (GestureState.<init> + OplusBaseTouchInteractionService); SystemUI rejects same event "Not in side gesture area".
  => bottom Home/Recents is SEPARATE process+artifact (OplusLauncher.apk classes2.dex), outside the SystemUI side hook.
- SystemUI APK has ZERO Quickstep/TouchInteractionService classes across all 8 dexes (only QuickStepContract).

### MATCH bar — all 4 required items true
1. exact artifact hash verified; 2. exact class+method+descriptor recorded; 3. side-down gated before pilfer/final Back;
4. bottom Quickstep/Home/Recents outside the hook. => outcome MATCH.

### Caveats / honest gaps
- R8/retrace decompile noise: duplicated constant-false branches in gate (booleans not reliable from source;
  semantics anchored on live log strings + authoritative dexdump table).
- t3a was UNLOCKED; t3b2/t3c on secure lockscreen (credential needs human; wiping rejected as destructive).
  Unlocked right-edge->back and unlocked bottom-up->Home not re-captured; MATCH does not depend on them.
- Design caveat for downstream hook task: veto ONLY touchscreen (source 0x1002) side-edge ACTION_DOWN in
  onMotionEventImpl, passing bottom/other events through (bottom strip events feed nav-handle animation).

### Evidence files (run dir F:\Downloads\Git\COS\.omo\evidence\coloros16-lspit\)
systemui.sha256, artifact-manifest.json, discovery.json, task-3-artifact-manifest.json, task-3-discovery.json, task-3-smali.txt
