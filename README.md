# COS Gesture

> ColorOS 16 侧滑手势精细化拦截与小白条增强模块。

一个面向 ColorOS 16 / Android 16 的 **LSPosed (libxposed API 102) Xposed 模块**，
在保留 ColorOS 全面屏侧滑返回手感的同时，允许用户独立开关、精确控制拦截区域，
并通过 **MIUIX 桌面端 UI** + 桌面图标启动，让所有设置可一键管理、实时生效、
无需重启手机。

**包名**：`com.cos.lspit.gesture`
**作用域**：`com.android.systemui`、`com.android.launcher`
**License**：[GNU GPL v3](LICENSE)

---

## 功能特性

| # | 特性 | 说明 |
|---|------|------|
| F1 | 桌面图标启动 + MIUIX 设置页 | 应用列表里直接点开「COS 手势」即用，单 Activity + Compose |
| F2 | 总开关 + 左/右侧独立开关 | 一个总开关，两个分开关。关闭后**实时生效**，不需重启 SystemUI |
| F3 | mBack 手势 | 小白条区域 tap=返回，long-press=Home |
| F4 | 仅白条区域触发底部手势 | 把全屏底部 gesture 区域缩窄到白条矩形，其余底部区域被拦截 |
| F5 | 小白条长度自定义 | 70–120 dp 可调，UI 拖动 Slider 实时生效 |
| F6 | 隐藏小白条像素（OLED 防烧屏） | 关闭白条像素但不修改 view 几何/可触摸状态——mBack/barOnly/TapShield/长度自定义全部正常工作，应用内容不上移 |
| F7 | 点击穿透保护（TapShield） | 在白条矩形 + 内边距的范围内消费触摸，防止穿透到下层 app 的底部控件；shade（通知/控制中心）展开时自动让位 |
| F8 | 系统设置兼容 | 即便用户去系统设置里手动隐藏小白条，模块拦截「真隐藏」改为「不绘制」——mBack/长度等照常 |
| F9 | 重启作用域按钮 | UI 一键重启 SystemUI，带二次确认，Root 优先，Shizuku 兜底，都没有就引导手动重启 |

## 系统要求

- 设备：ColorOS 16 / Android 16 真机
- 框架：[LSPosed](https://github.com/LSPosed/LSPosed) 已安装并启用本模块
- Root 或 Shizuku（用于一键重启 SystemUI）
- 屏幕分辨率 1080×2376（其他分辨率未实测，可能存在像素偏差）

## 安装

1. 在 [Releases](../../releases) 下载最新 `app-release.apk`
2. 安装到设备，授予必要权限
3. 在 LSPosed 管理器中启用模块，作用域勾选 `com.android.systemui` 与 `com.android.launcher`
4. 重启 SystemUI（模块内提供一键按钮，或 `su -c killall com.android.systemui`）
5. 桌面找到「COS 手势」图标，打开后即可调整配置

## 构建

```bat
set JAVA_HOME=C:\Users\Administrator\AppData\Local\Jdk\jdk-21.0.12.1+1
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

环境要求：

- **JDK**：标准 HotSpot JDK 21（Temurin 21.0.12.1 验证可用）。GraalVM 21 的 `jlink.exe`
  会令 AGP `JdkImageTransform` 失败，不可作为 Gradle JVM。
- **Android SDK**：platform-36、build-tools 36.0.0。
- **`local.properties`** 中 SDK 路径必须用正斜杠：`sdk.dir=C:/Users/...`（反斜杠会被
  `java.util.Properties` 吞掉）。

## 验证状态

| 项目 | 状态 |
|------|------|
| `./gradlew.bat :app:assembleDebug` | exit 0 |
| `./gradlew.bat :app:testDebugUnitTest` | 全绿 |
| `SystemHideTest` 14 用例 | 全绿 |
| 真机冷启动 + mBack tap/long-press | 通过 |
| 回归矩阵 (live toggle / barHidden / 模块全关 / 慢重试 / 常态) | 全过 |

## 工作原理概览

- **拦截对象**：AOSP-16 `com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler`
  私有方法 `isWithinTouchRegion(MotionEvent)Z`，命中后强制返回 `false`，**不调用**
  `chain.proceed()`。
- **底部上滑手势**（Home / 最近任务）由 Quickstep 负责，不走本钩子路径，保留。
- **小白条区域**：mBack / TapShield / OLED-hide 通过 `NavigationHandleHooks`
  中的 `MBack`、`TapShield`、`HiddenBar` 三个子模块实现，分别 hook
  `OplusNavigationHandle.handleValidTouchEvent`、
  `NavigationBar.ExternalSyntheticLambda10.onComputeInternalInsets`、
  `OplusNavigationHandle.onDraw`。
- **Launcher 拦截**：`LauncherRegionHooker` 在 `com.android.launcher` 进程内 hook
  `OplusWindowManager.updateInvalidRegion`，把全屏底部手势矩形缩窄到白条带，
  解决 barOnly 在 SystemUI 拦不到的盲区。
- **配置通道**：模块自有 `ContentProvider` + `ContentObserver`，不依赖 LSP-IT
  内部接口。

## 致谢 / Credits

本项目的实现思路与关键代码片段借鉴自以下开源项目：

- **[Coloros Mod](https://github.com/rikumi/coloros-mod)** — 侧滑拦截、
  SystemUI 导航栏重构方案的核心参考。感谢 rikumi 与所有贡献者。

同时也感谢以下开源项目（无直接代码复用，但为本项目提供 API 与基础设施）：

- [LSPosed](https://github.com/LSPosed/LSPosed) — libxposed 框架
- [MIUIX](https://miuix.kmp.rikka.app/) — Compose UI 组件库
- [Shizuku](https://shizuku.rikka.app/) — 用户态权限桥

## 免责声明 / Disclaimer

- **本项目为非商业个人作品**，仅供学习与技术研究使用。
- 本模块会修改 ColorOS 系统界面（`com.android.systemui`）的运行时行为，使用前请理解：
  - 错误使用可能导致导航栏异常，需要恢复出厂设置或重启解决。
  - 模块作者不对任何因使用本模块造成的设备故障、数据丢失负责。
- **OPPO、ColorOS 为 OPPO 广东移动通信有限公司的注册商标**，本项目为独立第三方
  实现，与 OPPO 没有任何隶属或合作关系。
- Android 是 Google LLC 的商标，遵循 [Android 开源项目](https://source.android.com/) 许可。

## 贡献

欢迎通过 Issue 报告 Bug、通过 Pull Request 提交改进。本项目采用 GPLv3 协议，
所有衍生作品必须同样以 GPLv3 公开。

## 许可证

```
COS Gesture - ColorOS 16 gesture interception module
Copyright (C) 2026 COS Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

完整许可文本见 [LICENSE](LICENSE)。