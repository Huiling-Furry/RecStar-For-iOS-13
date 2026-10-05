# RecStar iOS 13 适配说明

> 上游项目：[sdercolin/recstar](https://github.com/sdercolin/recstar)
> 目标：在**不改变 UI 与功能**的前提下，把 iOS 端的最低支持版本从 14.1 降到 **iOS 13.0**，并让构建产物可用于签名安装。

---

## 一、当前进度：源码适配已完成

所有在源码层面能识别出的 **iOS 14+ 硬依赖都已移除或替换**。剩下唯一没做的事是**编译验证**——本环境是 Linux，无法编译 iOS，必须在 macOS 上跑一次真实构建（已提供 GitHub Actions 工作流，见第四节）。

判定依据：iOS 13 上"能不能装、能不能跑"取决于三件事，都已处理：

| 维度 | 状态 |
| --- | --- |
| 可执行文件 `minos`（LC_BUILD_VERSION / LC_VERSION_MIN_IPHONEOS） | 由 Xcode 的 `IPHONEOS_DEPLOYMENT_TARGET=13.0` 控制，已全部改完 |
| 链接的系统框架是否 iOS 14+ 才有 | Kotlin/Native 1.9.20 在音频 API 上需要 `platform.AVFAudio` 绑定，但工程仍只链接 `AVFoundation`；`UniformTypeIdentifiers`、`SwiftUI` 已清零 |
| 运行期调用的 API 可用性 | 源码里 iOS 端用到的 API 全部是 iOS 13 及更早的（见第三节） |

---

## 一点五、构建阻断已修复（2026-10 复核）

首轮适配只做了源码改造，但项目当时**根本构建不起来**——Gradle 在配置阶段就失败，因此 `shared` framework 从未被生成过。现已定位并修复。

**根因**：`shared/build.gradle.kts` 里 BuildKonfig 用的是动态版本 `version "+"`：

```kotlin
id("com.codingfeline.buildkonfig") version "+"   // 旧写法
```

`+` 会解析到 BuildKonfig 的最新版（当前为 **0.23.0**），而 0.16+ 起该插件要求 **Gradle 8.14+**，本项目的 wrapper 是 **Gradle 8.7**，于是直接报：

```
BuildKonfig requires Gradle 8.14 or later, but is applied to 8.7.
```

同一个文件的 `buildscript` 里写的又是 `0.15.1`，两处自相矛盾。**不要为了它去升级 Gradle**——Gradle 8.14 会同时牵动 Kotlin 1.9.20、Compose 1.5.10、AGP 8.5.2 这一整条旧技术栈。

**修复方式**：把插件版本收敛到 `gradle.properties` 一处定义，由 `settings.gradle.kts` 的 `pluginManagement` 统一分发。

| 文件 | 改动 |
| --- | --- |
| `gradle.properties` | 新增 `buildkonfig.version=0.15.1`（带注释说明为何不能用 `+`） |
| `settings.gradle.kts` | `pluginManagement.plugins` 里登记 `buildkonfig` 与 `kotlin("plugin.serialization")`，版本全部取自 `gradle.properties` |
| `shared/build.gradle.kts` | `buildkonfig` 去掉 `version "+"`；`plugin.serialization` 由 `1.9.10` 改为跟随 `kotlin.version`（**1.9.20**）；**删除整个 `buildscript` 块** |

删掉 `buildscript` 是因为它同时声明了 `kotlin-gradle-plugin` 和 `buildkonfig-gradle-plugin` 的 classpath，版本与 plugins DSL 各写一份——这正是 `1.9.10` 与 `1.9.20` 分裂的温床。现在 plugins DSL 提供的 classpath 已足够（顶部的 `FieldSpec` import 依然有效）。

**另外修掉一个 iOS 功能失效**：`FileInteractor.kt` 用 `canOpenURL` 打开 `shareddocuments://`，但 `Info.plist` 没有声明该 scheme。iOS 9+ 起 `canOpenURL` 查白名单外的 scheme 会直接返回 `false`，导致"打开共享文档目录"点了没反应。已在 `Info.plist` 补上：

```xml
<key>LSApplicationQueriesSchemes</key>
<array><string>shareddocuments</string></array>
```

同时补了 `<key>UIBackgroundModes</key><array><string>audio</string></array>`，让切后台/锁屏时录音与播放不被挂起。如果你不希望 App 在后台运行，删掉这一项即可。

---

## 二、改动清单

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | `IPHONEOS_DEPLOYMENT_TARGET` 14.1 → **13.0**（4 处配置全覆盖）；移除 `iOSApp.swift` / `ContentView.swift` / `SafeAreaObserverModifier.swift` 的引用与编译项；把 `LaunchScreen.storyboard` 加入 Resources | 统一最低版本；清理 SwiftUI 残留与新增启动图资源 |
| `iosApp/iosApp/AppDelegate.swift` | 用 `@UIApplicationMain` + `UIWindow` 的 **UIKit 生命周期**取代 SwiftUI 的 `App` 生命周期；新增 `ComposeContainerViewController` 承载 Compose，并在 `viewSafeAreaInsetsDidChange()` 里把安全区通过 `NSNotificationCenter` 发给 Kotlin | `@main struct X: App` / `WindowGroup` 是 **iOS 14+**；iOS 13 只能用 AppDelegate + window。安全区回调搬到 UIKit 侧，替代原来依赖 SwiftUI 的 `SafeAreaObserverModifier` |
| `iosApp/iosApp/Info.plist` | 删除 `UIApplicationSceneManifest`；`UIRequiredDeviceCapabilities` 的 `armv7` → **arm64**；新增 `UILaunchStoryboardName = LaunchScreen`（原来的 `UILaunchScreen` 字典是 **iOS 14+** 才支持的键） | 回到 window 生命周期；Compose/Skiko 只有 64 位；iOS 13 只认 storyboard 启动图 |
| `iosApp/iosApp/LaunchScreen.storyboard` | **新增** | iOS 13 需要 storyboard 形式的启动图 |
| `shared/src/iosMain/kotlin/io/Uti.kt` | `UTType`（`UniformTypeIdentifiers`，**iOS 14+**）→ UTI 字符串常量：`public.text` / `public.audio` / `public.data` | 去掉 iOS 14+ 框架依赖 |
| `shared/src/iosMain/kotlin/io/FileInteractor.kt` | `UIDocumentPickerViewController(forOpeningContentTypes:asCopy:)`（**iOS 14+**）→ `UIDocumentPickerViewController(documentTypes:inMode: UIDocumentPickerModeImport)`（iOS 8+） | 同上 |
| `shared/src/iosMain/kotlin/audio/AudioSession.kt`<br>`audio/AudioPlayer.kt`<br>`audio/AudioRecorder.kt`<br>`audio/AVAudioPlayerDelegate.kt` | 按 Kotlin/Native 1.9.20 的真实 SDK bindings 使用 `platform.AVFAudio.*`；工程链接仍保持 `AVFoundation` | **本次最关键的一处**，详见第三节 |
| `shared/src/iosMain/kotlin/ui/model/ProvideSafeAreaInsets.kt` | 改为监听 `SafeAreaDidChange` 通知来更新安全区 | 配合 AppDelegate 的 UIKit 化改造 |
| `.github/workflows/build-ios-ipa.yml` | **新增**：macOS runner 上构建并产出 IPA，支持可选签名 | 本机无法编译时的替代方案 |

---

## 三、重点解释：Kotlin/Native 的 AVFAudio binding 与 iOS 13

GitHub macOS Runner 的真实编译证明：在 Kotlin/Native 1.9.20 + Xcode 16 SDK 下，`AVAudioSession`、`AVAudioPlayer`、`AVAudioRecorder`、`AVAudioEngine` 等符号必须从 `platform.AVFAudio` 导入；写成 `platform.AVFoundation` 会直接出现 `Unresolved reference`，因此不能只按静态源码判断。

这不等于工程必须链接一个 iOS 14+ 的二进制框架：iOS 13 SDK 已在 AVFoundation 的框架目录中提供 AVFAudio 相关 headers，工程 linker 仍保持 `linkerOpts("-framework", "AVFoundation")`。最终是否存在运行期最低系统问题，必须以 archive 后的 `otool -L` 和 iOS 13 真机测试为准。

因此本次修复分为两部分：

1. Kotlin 源码使用 Kotlin/Native SDK 实际提供的 `platform.AVFAudio` binding，保证 macOS 真编译通过；
2. Xcode 工程继续只链接 `AVFoundation`，并在 CI 中检查是否意外出现 `UniformTypeIdentifiers` 或 `SwiftUI`。

此前文档把“binding 模块名”和“最终链接的系统 framework”混为一谈，已更正。

---

## 四、如何产出 IPA

### 方式 A：GitHub Actions（推荐，不需要你有 Mac）

1. 把这份源码推到你自己的 GitHub 仓库。
2. Actions → **Build iOS IPA (iOS 13)** → Run workflow。
3. 参数默认即可（`ios_deployment_target=13.0`、`configuration=Release`）。
4. 跑完在 Artifacts 里下载 `RecStar-iOS13-unsigned-ipa` 或 `RecStar-iOS13-signed-ipa`。

工作流会顺带打印校验信息，用来确认适配是否真的生效：

```
vtool -show-build                  # 看 minos 是否 13.0
PlistBuddy:MinimumOSVersion        # 看打包后的 Info.plist
otool -L                           # 若出现 AVFAudio/UniformTypeIdentifiers/SwiftUI 说明有 14+ 依赖回流
codesign -dv                       # 看签名信息
```

> 注意：工作流特意选择 **Xcode 16.x**（`macos-15` runner）。更新的 Xcode 可能已经不再接受 iOS 13 作为部署目标。

### 方式 B：本地 macOS + Xcode

```bash
# 1. 生成 Kotlin framework
cd shared && ./gradlew :shared:embedAndSignAppleFrameworkForXcode   # 或直接在 Xcode 里 Build

# 2. 归档（未签名）
cd iosApp
xcodebuild archive \
  -project iosApp.xcodeproj -scheme iosApp \
  -configuration Release -sdk iphoneos \
  -archivePath /tmp/RecStar.xcarchive \
  IPHONEOS_DEPLOYMENT_TARGET=13.0 \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO

# 3. 打包 IPA
mkdir -p /tmp/ipa/Payload
cp -R /tmp/RecStar.xcarchive/Products/Applications/RecStar.app /tmp/ipa/Payload/
cd /tmp/ipa && zip -qry RecStar-unsigned.ipa Payload
```

---

## 五、签名

签名必须用**你自己的 Apple 证书**，我无法代签。工作流已内置签名链路，把下面 4 个 Secrets 配好，再勾选 `sign=true` 即可产出**已签名 IPA**：

| Secret | 内容 |
| --- | --- |
| `TEAM_ID` | 10 位 Team ID（例如 `S7CF8UHY7B`） |
| `BUILD_CERTIFICATE_BASE64` | 导出的 `.p12` 证书，做 `base64 -i cert.p12` 后的字符串 |
| `P12_PASSWORD` | 该 p12 的导出密码 |
| `BUILD_PROVISION_PROFILE_BASE64` | `.mobileprovision` 文件做 base64 后的字符串 |

签名时按需选择：

- `signing_identity`：`Apple Development`（个人或付费开发者账号，装自己设备用这个）/ `Apple Distribution`（上架或 Ad Hoc）
- `export_method`：`development`（AltStore / Sideloadly 自签）/ `ad-hoc` / `app-store` / `enterprise`

**Bundle ID 有个坑**：`Config.xcconfig` 里 `TEAM_ID=` 是空的，而 `pbxproj` 里写的是

```
PRODUCT_BUNDLE_IDENTIFIER = "${BUNDLE_ID}${TEAM_ID}"
```

也就是最终 Bundle ID = `com.sdercolin.recstar` **拼接**你的 Team ID（所以上游仓库里出现了 `com.sdercolin.recstarS7CF8UHY7B`）。你的描述文件必须匹配这个拼出来的 ID，否则签名会失败。

若不想用 CI 签名，本地方式是在 `iosApp/Configuration/LocalConfig.xcconfig`（该路径已被 `#include?` 引入且不会入库）里写 `TEAM_ID = 你的TeamID`，然后在 Xcode 里正常 Archive 导出。

---

## 六、未验证项与风险（请务必读）

1. **iOS 真实编译已做，真机运行也已在 iOS 13 设备上验证通过（2026-10-05 用户确认）。** 已在 GitHub macOS runner 上真实完成 `:shared:compileKotlinIosArm64` 与 `xcodebuild archive` 并产出 IPA；用户已装到 iOS 13 真机确认可正常启动、录音（一次点击授权）、播放、文件选择器、旋转、前后台切换均正常。Compose/Skiko 在 iOS 13 上的运行时风险至此关闭。唯一待重新确认的是听筒路由修复（commit `56ea33e`），需装在修复后的新 IPA 上复测。
2. **Compose Multiplatform 1.5.10 的 iOS 运行时仍是最大未知项。** 它的 Kotlin 层经 Kotlin/Native 编译后最低版本是 12.0（`osVersionMin.ios_arm64`），不构成门槛；Skiko 原生层（Compose 1.5.10 对应的 0.7.34）目标文件版本标记是 11.0，也不构成门槛。但**如果 Compose 的 UIKit 桥接内部调用了某个 iOS 14+ 的 API，仍然可能运行期崩溃**。工作流的 `otool -L` 能查框架层依赖，查不了运行期 API；`strings` 扫描能发现明显的新 API 符号名，但 Kotlin/Native 走 cinterop 发 ObjC 消息，**编译器不做 availability 检查**，调了 iOS 14+ API 照样编译通过，只在老设备上崩。这是 CI 堵不死的洞，只能靠人工审查 + 真机。
3. **iOS 13 模拟器现在基本拿不到**（现代 Xcode 不再提供 iOS 13 的 simulator runtime），请用**真机**验证。
4. 未签名 IPA 无法直接安装，需要用 AltStore / Sideloadly / TrollStore 之类的工具自签；已签名 IPA 则受你证书对应的设备白名单约束。
5. 已经在 `Info.plist` 里保留的 `CADisableMinimumFrameDurationOnPhone` 是 iOS 15+ 才认识的键，在 iOS 13 上会被忽略，无害。

---

## 七、如果构建失败，优先看这几处

- 报 `Library not loaded: AVFAudio` → 说明某处 `platform.AVFAudio` 没换干净，全局搜一遍。
- 报 SwiftUI 相关符号找不到 → `pbxproj` 里可能还残留 SwiftUI 文件的编译项。
- `MinimumOSVersion` 不是 13.0 → 检查 `IPHONEOS_DEPLOYMENT_TARGET` 是否 4 处都改了，以及 `Config.xcconfig` 里有没有覆盖。
- 签名报 profile 不匹配 → 见第五节 Bundle ID 拼接的坑。
- 报 `BuildKonfig requires Gradle 8.14 or later` → 说明 `version "+"` 又回来了，或 `buildkonfig.version` 被改高。见第一点五节。

---

## 八、已实测通过的项（复核记录）

以下都在**干净的 Gradle 环境**里实跑过（沙箱自带的 `~/.gradle/init.gradle` 本身有语法错误 `mavelCentral()`，会污染任何构建，需另设 `GRADLE_USER_HOME` 绕开）。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| Gradle 配置阶段 | `./gradlew projects` | **BUILD SUCCESSFUL** |
| Kotlin 实际编译 | `./gradlew :shared:compileKotlinDesktop` | **BUILD SUCCESSFUL**，`generateBuildKonfig` 正常执行 |
| 插件版本落定 | `./gradlew :shared:buildEnvironment` | `buildkonfig-gradle-plugin:0.15.1`、`plugin.serialization:1.9.20` |
| iOS target 与 framework 任务 | `./gradlew :shared:tasks -Dos.name="Mac OS X"` | `iosArm64 / iosX64 / iosSimulatorArm64` 均创建成功，`embedAndSignAppleFrameworkForXcode`、`iosX64MainBinaries` 存在 |
| iOS 源码不含 SwiftUI/UTType API | 全仓搜索 `SwiftUI\|UniformTypeIdentifiers\|UTType\|UIApplicationSceneManifest` | 无真实引用；`platform.AVFAudio` 仅作为 Kotlin/Native 1.9.20 的音频 binding |

最后一项的 iOS 验证用 `-Dos.name="Mac OS X"` 骗过 `shared/build.gradle.kts` 里的 `isMac` 判断，从而在不改代码的前提下让 iOS target 参与配置——这样能验证配置路径，但**编译 iOS 产物仍然需要真 Mac + Xcode**。

### 一个已知的版本细节

`buildEnvironment` 显示 BuildKonfig 0.15.1 会带进 `kotlin-gradle-plugin:1.9.21`，于是 Kotlin 构建插件被 Gradle 冲突解析提升到了 **1.9.21**，而项目声明的是 `1.9.20`。这是**补丁级差异**，实测编译正常，Kotlin/Native 取用的仍是 1.9.20，因此**没有强行 force 回 1.9.20**——强行降级构建插件有可能破坏 BuildKonfig，而收益只是版本号字面一致。知道这件事即可。

### 关于依赖锁定

现在项目里**已经没有任何动态版本**（`+` / `latest.release` / `SNAPSHOT` 全部清零），直接依赖也全是精确版本号。为进一步锁住 Skiko、Compose 内部模块等传递依赖，已新增 GitHub workflow：

```text
.github/workflows/generate-gradle-lock.yml
```

它专门使用 GitHub 的 **macOS runner**，让 iOS 的所有 configuration 真实参与解析，然后生成完整的 `gradle.lockfile` 并上传为 artifact。这样全程不需要本地 Mac。

使用步骤：

1. 打开 GitHub 仓库的 **Actions**；
2. 选择 **Generate Gradle Dependency Lock**；
3. 点击 **Run workflow**；
4. 等待 `recstar-gradle-lockfile-macos` artifact 生成；
5. 下载 artifact 中的 lockfile（本项目是多模块工程，文件可能位于 `shared/gradle.lockfile`，必须放回原目录）；
6. 提交并推送所有下载的 `gradle.lockfile`。

该 workflow **只上传，不自动提交**，避免 Actions 无意修改用户分支。提交后，`build-ios-ipa.yml` 会自动使用仓库中的各模块 lockfile；如果仓库还没有 lockfile，workflow 会给出 warning，但不会因此阻断首次打包。

生成 lockfile 的核心命令由 workflow 执行，不要在 Linux 上生成，因为 Linux 看不到 iOS configuration，生成的锁文件会不完整。

---

## 九、麦克风授权已改为一次完成

权限请求现在只在用户第一次点击录音时触发，不会在进入会话页面或 Recorder Demo 时提前弹窗。

调用链已经改为真正的异步流程：

```text
首次点击录音
  → 系统弹出麦克风权限框
  → 用户点击允许
  → 当前挂起的录音操作继续执行
  → 直接开始录音
```

改动包括：

- `PermissionChecker.checkAndRequestRecordingPermission()` 改为 `suspend`。
- iOS 消费 `requestAccessForMediaType` 回调，不再读取请求前的旧状态。
- Android 使用 Activity Result API 等待系统回调，并防止重复请求。
- `SessionScreenModel` 和 `RecorderDemo` 删除初始化时的权限请求。
- 已授权状态下不再弹窗，一次点击直接开始录音。
- 用户拒绝后，系统仍允许再次请求时可以重试；系统不再允许请求时显示手动授权提示。

## 十、GitHub Actions 生成未签名 IPA（适合爱思助手/i4）

针对免费 Apple ID + Windows 爱思助手/i4，使用新增的：

```text
.github/workflows/build-ios-unsigned.yml
```

打开 GitHub Actions → **Build iOS 13 Unsigned IPA** → Run workflow，然后设置：

```text
configuration = Release
ios_deployment_target = 13.0
```

这个 workflow **不读取证书、不读取 Team ID、不做 CI 内 Apple 签名**，只在 GitHub macOS Runner 上：

1. 编译 Kotlin/Native iOS framework；
2. 用 Xcode 归档未签名 App；
3. 校验最低版本和系统框架；
4. 打包并上传：

```text
RecStar-iOS13-unsigned-ipa
```

下载这个 artifact 后，在 Windows 爱思助手/i4 中使用 Apple ID 进行个人签名。artifact 里还会附带 `i4-signing-guide.txt`，列出签名和安装注意事项。个人签名通常受 Apple 的 7 天有效期和设备限制影响。

注意：未签名 IPA **不能直接安装**，必须经过爱思助手/i4 或其他重签名工具处理。CI 会检查最低系统版本、Bundle ID、版本号、未签名状态以及 `SwiftUI`/`UniformTypeIdentifiers` 链接；但 Compose/Skiko 的触摸、旋转、音频和页面生命周期仍必须在真实 iOS 13 设备上验收。

### 免费 Apple ID + 爱思助手/i4 签名的实际限制

这条路能解决"装得上"，但有明确天花板，请按这个预期使用：

| 限制 | 说明 |
| --- | --- |
| 有效期 | Apple 对个人签名通常给 **7 天**，过期后 App 打不开，需要重新签名安装 |
| 设备数 | 免费账号每年最多注册少量 UDID，名额有限 |
| entitlements | 重签名会改写 entitlements，`get-task-allow` 之外的能力（如后台音频、iCloud）可能被丢弃 |
| Bundle ID | 重签名可能改写 Bundle ID，麦克风授权会重新弹一次 |
| 后台能力 | 个人签名下 `UIBackgroundModes: audio` 的实际行为不能完全保证 |
| 不可证明兼容性 | 签名成功 ≠ iOS 13 能跑，运行时仍要靠真机验收 |

如果你需要一个能长期分发、后台能力稳定的版本，只能走付费 Apple Developer 账号 + 正式 App ID + provisioning profile（即第十一节的签名路线）。

### 真机验收

签名安装后请按 `docs/iOS13-device-acceptance.md` 逐项验收。该文件明确区分了
"CI 已验证的静态项"和"只有真机能验证的运行时项"，并说明崩溃日志怎么抓。

## 十一、GitHub Actions 已签名 IPA 打包

仍然保留 `.github/workflows/build-ios-ipa.yml` 作为需要 Apple Developer 证书和 provisioning profile 的官方签名路线。使用：`.github/workflows/build-ios-ipa.yml` → GitHub Actions → **Build iOS IPA (iOS 13)** → Run workflow，然后设置：

```text
sign = true
configuration = Release
ios_deployment_target = 13.0
```

签名打包需要配置以下 Repository Secrets：

| Secret | 内容 |
| --- | --- |
| `TEAM_ID` | 10 位 Apple Team ID |
| `BUILD_CERTIFICATE_BASE64` | 导出的 `.p12` 证书 Base64 |
| `P12_PASSWORD` | `.p12` 导出密码 |
| `BUILD_PROVISION_PROFILE_BASE64` | 与最终 Bundle ID 和导出类型匹配的 `.mobileprovision` Base64 |

默认 Bundle ID 是工程约定的前缀拼接规则：

```text
最终 Bundle ID = bundle_id 输入值 + TEAM_ID
```

例如 `com.sdercolin.recstar` + `S7CF8UHY7B` 会得到 `com.sdercolin.recstarS7CF8UHY7B`。Provisioning profile 必须精确匹配这个最终 ID，否则 workflow 会在安装 profile 阶段直接失败。

workflow 现在会在上传前校验：

- Team ID、签名身份、导出方式和 Bundle ID 格式；
- Provisioning profile 的 Team ID 与 `application-identifier`；
- 归档产物的 iOS 最低版本 `13.0`、Bundle ID 和 embedded profile；
- 导出后实际 IPA 的 Bundle ID、Team ID 和 `codesign --verify` 结果。

只有全部校验通过才会上传 `RecStar-iOS13-signed-ipa` artifact。`development` / `ad-hoc` 包仍受设备注册限制；`app-store` 包应通过 App Store/TestFlight 等渠道使用，**已签名不等于可以直接安装到任意设备**。

另一条 `.github/workflows/release-ios.yml` 是固定的 App Store/TestFlight 发布流程，不是通用手动打包入口。

---

## 十二、有意未改的 API 警告

以下 API 仍然保留，因为它们在 iOS 13 可用，只会产生弃用警告，不影响最低版本兼容：

- `UIApplication.openURL`
- `presentModalViewController`
- `UIDocumentPickerViewController(documentTypes:inMode:)`

等 GitHub Actions 真正 archive 和真机验证通过后，再单独做现代 API 清理更稳妥。
