# iOS 13 真机验收清单（必须做，CI 无法替代）

> 结论先说清楚：**GitHub CI 只能证明"能编译、能打包、二进制层面没有 iOS 14+ 依赖"，
> 不能证明 App 在 iOS 13 真机上能跑。** Compose Multiplatform / Skiko 的运行时行为
> 只有在真机上启动之后才能确认。下面这份清单就是用来确认它的。

## 一、CI 已经验证过的（静态）

| 项目 | 结果 |
| --- | --- |
| Gradle 配置与插件版本 | 通过（BuildKonfig 锁 0.15.1，不再要求 Gradle 8.14） |
| Kotlin/Native iOS 编译（iosArm64） | 通过（AVFAudio / UIDocumentPickerMode 绑定已修正） |
| Xcode archive（无签名） | 通过 |
| `MinimumOSVersion` | 13.0 |
| `CFBundleIdentifier` | `com.sdercolin.recstar` |
| 版本号 / versionCode | 与 `gradle.properties` 一致 |
| `embedded.mobileprovision` | 不存在（确实是裸包） |
| 代码签名 | 不存在有效签名 |
| 链接的 framework | 未见 SwiftUI / UniformTypeIdentifiers 等 iOS 14+ 框架 |
| Xcode 项目部署目标 | 所有 configuration 均为 `IPHONEOS_DEPLOYMENT_TARGET = 13.0` |
| Swift 源码 | 纯 UIKit，无 SwiftUI，`@UIApplicationMain` + `UIWindow` |

## 二、CI 无法验证的（必须真机确认）

### 1. 启动与 Compose/Skiko 渲染（最高风险）

- [ ] App 能冷启动，不闪退（iOS 13 上 Skiko 的 Metal 后端是唯一未知项）
- [ ] 首屏 Compose UI 正常绘制，不是全黑 / 全白 / 卡死
- [ ] 列表滚动、点击、输入框有响应
- [ ] 旋转到横屏 / 竖屏不崩，UI 重排正常
- [ ] 退到后台再回前台，画面能恢复（Skiko 在 iOS 13 上的 lifecycle 处理）

如果启动即崩：抓设备日志（见第四节），崩溃栈里出现 `Skiko`、`Metal`、`org.jetbrains.skia`
基本可以确认是 Compose/Skiko 在 iOS 13 上的兼容问题，此时只能升级/降级 Compose 版本或放弃 iOS 13。

### 2. 录音

- [ ] 首次点录音 → 弹出麦克风授权框 → 点"允许" → **同一次点击继续开始录音**（这是本次改的重点）
- [ ] 录音中波形/计时有更新
- [ ] 停止后文件存在且可播放
- [ ] 已授权状态下再点录音不再弹框，直接开始

### 3. 播放

- [ ] 播放已录音文件有声音
- [ ] 进度条/时间更新
- [ ] 播放中切后台再回来不崩

### 4. 文件

- [ ] 导入文件能打开系统文件选择器（`UIDocumentPickerViewController` 用的是 iOS 8+ 的
      `documentTypes:inMode:` 重载，iOS 13 上正确；iOS 14 才需要换成 UTType）
- [ ] 导出能调起分享面板
- [ ] "在文件中显示"（`shareddocuments://`）能跳转到"文件"App —— 已在 Info.plist 加
      `LSApplicationQueriesSchemes`，但这个功能只在真机上才能真正验证

### 5. 音频中断

- [ ] 录音中来电话 → 不丢数据 / 不卡死
- [ ] 播放中其他 App 抢音频 → 行为可接受

> 这一项代码里**尚未完整实现** AVAudioSession 中断通知处理，属于已知未完成的遗留项。

### 6. 后台

- [ ] `UIBackgroundModes: audio` 已加，确认后台录音是否真的持续（iOS 13 + 个人签名下
      后台能力可能受限，这是 Apple 的策略限制，不是代码问题）

## 三、建议的测试顺序

1. 先只测"启动 + 首屏渲染"，这一步不过就没必要往下测。
2. 再测录音授权一次点击流程。
3. 再测播放、文件导入导出。
4. 最后测中断和后台。

## 四、崩了怎么抓日志

1. iPhone 上：设置 → 隐私 → 分析与改进 → 分析数据，找 `RecStar-*` 开头的 ips 文件。
2. 或 Mac 上打开"控制台"(Console)，连上手机，按进程名 `RecStar` 过滤，复现崩溃。
3. 重点看崩溃栈里是否有 `Skiko` / `skia` / `Metal` / `Compose` 字样。
4. 把日志发出来，就能定位是 Compose 运行时问题还是业务逻辑问题。

## 五、签名后的额外注意

- 免费 Apple ID 个人签名通常 **7 天** 过期，过期后需重新签名安装。
- 重签名可能改写 Bundle ID 和 entitlements，麦克风授权会重新弹。
- 个人签名**不能**证明 iOS 13 运行时兼容性，它只解决"装得上"。
