# QQ音乐AA助手

<img src="app/src/main/res/drawable/ic_launcher.png" width="128" alt="App 图标">

将官方 QQ音乐的媒体会话桥接到 Android Auto，在车机标准媒体界面显示歌曲、歌手、封面和进度，并支持播放、暂停、上一首和下一首。音频始终由 QQ音乐播放。

独立实验项目，与腾讯音乐或 Google 无隶属关系；真实汽车兼容性仍需验证。

## 项目动机

这个项目源于我使用糯米音乐时的一个不便：连接汽车后，仍需要掏出手机手动点播放，QQ音乐才能开始播放。

QQ音乐AA助手主要为了解决这个问题，让连接汽车后的 QQ音乐能够自动恢复播放，减少每次上车后操作手机的步骤。默认使用“跟随 Android Auto 设置”；如果希望助手主动恢复播放，可以选择“继续上次播放”。

## 下载与安装

前往 [Releases 下载 APK](https://github.com/kanuvic/QQMusicAAHelper/releases/latest)，选择以 `-release.apk` 结尾的安装包，不要下载自动生成的源码压缩包。

当前提供使用独立发行密钥签名、关闭调试的 Release 安装包，支持 Android 9 及以上版本。下载后按手机提示允许浏览器或文件管理器安装应用，再打开安装包。该安装权限与下面 Android Auto 的“未知来源”是两个不同设置，均需分别配置。

**从 0.1.0 Debug 版迁移：** 两种版本签名不同，请先卸载 Debug 版，再安装 Release 版；卸载会清除助手设置与通知使用权授权，安装后需重新授权。后续官方 Release 沿用同一发行密钥，可覆盖升级。

## 首次使用：让 Android Auto 显示助手

**首次使用必须先点击助手首页的“开启通知使用权”并在系统中授权，否则播放控制无法工作。未授权时按钮文字显示红色，授权后恢复黑色。Release 版同样需要手动授权，不会自动弹出普通权限申请。** 下方 Android Auto 设置和通知使用权两项均需完成。

### 1. 开启 Android Auto 开发者模式与未知来源

1. 在手机系统设置中搜索 **Android Auto**，进入其设置页面；也可从 Android Auto 应用信息中的“应用内其他设置”进入。
2. 滑到底部，点击“版本”展开 **版本和权限信息**，连续点击该区域 **10 次**，在弹出的“允许开发设置”提示中确认。
3. 点击右上角 **⋮ → 开发者设置**，勾选 **未知来源 / Unknown sources**。
4. 返回 Android Auto 设置，进入 **自定义启动器 / Customize launcher**；如果列表中出现 **QQ音乐AA助手**，勾选它。
5. 断开并重新连接车机，在 Android Auto 应用列表中打开 **QQ音乐AA助手**。

这是 Android Auto 自己的开发者模式，无需为此打开手机的 USB 调试。GitHub 下载的媒体应用需要允许未知来源才能被 Android Auto 使用；手机仅允许安装 APK 并不代表已完成此设置。

开发者模式与媒体应用未知来源支持依据 [Android 官方测试文档](https://developer.android.com/training/cars/testing?hl=zh-cn#unknown-sources)。菜单名称与位置可能随系统版本不同。

### 2. 开启通知使用权（必需）并准备 QQ音乐

1. 手机安装官方 QQ音乐，登录并确认可以正常播放歌曲。
2. 打开 **QQ音乐AA助手**，点击首页的 **开启通知使用权**，在系统列表中找到 **QQ音乐AA助手：媒体会话访问**，打开开关并确认授权。该权限用于访问 QQ音乐的媒体会话；仅开启应用的“允许发送通知”不能替代它。
3. 如果三星或其他手机显示“受限制的设置”，或授权开关被禁用，进入 **手机设置 → 应用 → QQ音乐AA助手 → 右上角 ⋮ → 允许受限制的设置**，按系统提示确认后，返回上一步开启通知使用权。菜单位置可能随系统版本不同；参考 [Android 官方说明](https://support.google.com/android/answer/12623953?hl=zh-Hans)。
4. 先在 QQ音乐中播放一首歌，返回助手，点击 **查找 Session**，确认 **Notification Access：Granted** 和 **QQ Music Session：Connected**，并显示歌曲信息。需要打开播放器时可点击 **启动 QQ音乐**。
5. 在助手的 **设置** 中选择“跟随 Android Auto 设置”（默认、第一项）、“继续上次播放”、“播放刷歌”或“播放最近歌曲”。
6. 连接车机并选择助手，使用车机上的播放、暂停、上一首、下一首。

### 常见问题

| 问题 | 检查步骤 |
| --- | --- |
| 车机找不到助手 | 确认 Android Auto 开发者设置已勾选“未知来源”，自定义启动器已勾选助手，再断开重连；确认使用的是 Android Auto 环境 |
| 找不到 Session 或没有歌曲信息 | 检查助手的通知使用权，先在官方 QQ音乐播放歌曲，再点击“查找 Session” |
| 点击 play 没反应 / Notification Access：Missing | 点击“开启通知使用权”并手动授权；如提示受限，先在应用信息中“允许受限制的设置”。不需要换装 Debug 版 |
| 连接后没有自动播放 | 检查助手的连接设置；“跟随 Android Auto 设置”由 Android Auto 决定是否发起播放，可手动按播放按钮 |
| 后台播放或恢复失败 | 确认 QQ音乐能正常播放，检查手机对 QQ音乐和助手的后台运行、电池限制；必要时手动打开 QQ音乐再查找 Session |
| APK 无法覆盖安装 | 同包名但签名不同的安装包不能直接覆盖；只有确认签名不一致时才卸载旧版再安装，卸载会清除助手设置和授权 |


## 功能与设置

- 实时镜像 QQ音乐的真实媒体信息和播放状态。
- 无媒体会话时尝试通过启动入口和标准媒体按键恢复，最多等待 16 秒。
- 手机主页提供通知使用权授权、启动 QQ音乐、启动 QQ音乐并应用设置、查找 Session 和日志。
- 点击“查找 Session”先短暂清空状态区，再显示最新结果。

| 连接车机时的选项 | 状态 |
| --- | --- |
| 跟随 Android Auto 设置（默认） | 只准备会话，由 Android Auto 的播放请求决定是否开始播放 |
| 继续上次播放 | 主动恢复上次歌曲、进度和队列；未确认播放时每 3 秒重试，最多重试 10 次 |
| 播放刷歌 | 使用 QQ音乐个性化推荐入口开始播放（原猜你喜欢／雷达）；0.1.2 恢复可选 |
| 播放最近歌曲 | 打开最近歌曲列表并请求自动播放；0.1.3 恢复可选 |

“继续上次播放”首次发送后，每隔 3 秒检查 QQ音乐的真实播放状态；尚未播放时再次发送，最多重试 10 次（加上首次请求最多 11 轮）。最后一次重试后再等 3 秒确认，整个等待最多约 33 秒。确认 PLAYING 后停止；通过助手或 AA 主动暂停、切换启动选项或撤销通知使用权会取消重试。不能保证覆盖已经确认播放之后才发生的再次暂停。

设置保存在本机，升级保留已保存的选择；首次安装或配置无效时默认为“跟随 Android Auto 设置”。下次 Android Auto 连接助手媒体服务时生效。也可以点击首页“启动 QQ音乐并应用设置”，在手机上打开 QQ音乐并立即执行当前选择：继续上次播放、播放刷歌或播放最近歌曲。选择“跟随 Android Auto 设置”时，手机测试只准备会话，不主动播放；真实 AA 是否发送自动播放请求仍需连接 AA 验证。原“启动 QQ音乐”仅打开界面，PLAY 仅继续当前队列。RECENT 对应“播放最近歌曲”，RADIO 对应“播放刷歌”。车机重新选择媒体来源也可能触发连接处理。

刷歌使用 `qqmusic://qq.com/media/playPersonalRadio?p=%7B%7D`，由 QQ音乐自己获取并播放个性化电台队列。已在三星手机 QQ音乐 20.9.0.8 验证从暂停启动、锁屏和强行停止后恢复；首次冷恢复测试曾超时，后续重测通过。仍受账户、网络及系统启动限制影响，16 秒未开始播放会显示错误。新入口的 Android Auto 连接场景和真实车机尚未验证，不保证打开刷歌的沉浸式界面。详见 [刷歌入口研究与测试](docs/PERSONAL-RADIO-SCHEME-RESEARCH.md)。

最近歌曲使用 `qqmusic://qq.com/ui/myTab?p=%7B%22tab%22%3A%22history%22%2C%22direct_play%22%3Atrue%7D`。已验证打开最近歌曲页并触发播放请求；现场出现付费限制提示；一次助手集成测试检测到真实播放，随后从暂停重试超时，重复启动稳定性和 Android Auto 连接场景尚未验证。助手不检测会员权限，也不绕过 QQ音乐的限制。详见 [最近歌曲入口研究与测试](docs/RECENT-PLAY-SCHEME-RESEARCH.md)。

## 使用条件

- Android 9（API 28）或更新版本。
- 已安装且可正常播放的官方 QQ音乐（`com.tencent.qqmusic`）。
- Android Auto，以及真实车机或官方 Desktop Head Unit（DHU）。
- 在系统设置中授予助手“通知使用权”，用于访问 QQ音乐媒体会话。

安装后按上面的首次使用指南完成授权与 Android Auto 设置。账号、会员、版权、网络及后台启动限制仍由 QQ音乐和 Android 系统决定。

## 编译

使用 JDK 17 和 Android SDK 35。创建本机 `local.properties`，不要上传此文件：

```properties
sdk.dir=C:/path/to/Android/Sdk
```

Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

macOS / Linux：

```bash
export JAVA_HOME=/path/to/jdk-17
chmod +x gradlew
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。Windows 也可使用 `scripts/build.ps1`；该脚本优先使用可选的 `.tools/jdk17`，否则使用 `JAVA_HOME`。

### Release 构建与签名

在项目根目录创建 `release-signing.properties`（已被 Git 忽略）：

```properties
storeFile=C:/path/to/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

运行 `./gradlew :app:assembleRelease :app:lintRelease`（Windows 使用 `gradlew.bat`）。已配置签名时输出 `app/build/outputs/apk/release/app-release.apk`；未提供配置时仅生成未签名 APK，不能直接安装。请保管并备份签名密钥和密码，不要上传到仓库。自行生成的密钥与本项目发布密钥不同，不能覆盖官方安装包。

技术栈：Kotlin 2.0.21、AndroidX Media 1.7.0、AGP 8.9.2、Gradle 8.11.1；compile/target SDK 35；版本 0.1.5。

## 清理中间文件

Windows 下双击项目根目录的 `cleanup.bat`。脚本清理项目构建目录、Gradle / Kotlin 缓存和反编译研究目录，保留源码、Git、签名配置、共享工具及 `artifacts` 中的发行文件。构建目录内的 APK 会先复制到 `artifacts/preserved-apks` 并校验，再删除中间文件；下次编译会重新生成缓存。

运行 `cleanup.bat --dry-run` 可预览清理范围，不修改文件。若文件被占用，请关闭 Android Studio 并停止 Gradle 后重试。

## 测试

测试基于真实 QQ音乐媒体会话，检查实际播放状态与 metadata，不使用模拟播放器替代。

```powershell
adb devices
.\scripts\test-device.ps1 -Serial '<你的设备序列号>' -Scenario controls
```

PowerShell 脚本按 Windows 常见 Android SDK 路径定位 adb；其他安装位置需调整。测试结果写入忽略目录 `artifacts/`。

DHU：通过 Android SDK Manager 安装官方 Desktop Head Unit，在手机 Android Auto 开发者菜单启动 **Head Unit Server**，然后执行：

```powershell
adb -s '<你的设备序列号>' forward tcp:5277 tcp:5277
desktop-head-unit.exe --adb=127.0.0.1:5277
```

三星 Android 16 + QQ音乐 20.9.0.8 + Android Auto 17.7 + DHU 2.1 已验证基础控制、封面同步、锁屏操作、无 Session 恢复、Force Stop 后恢复及断开重连。系统正常回收前台播放器进程和真实车辆尚未覆盖。

- [三星真机 / DHU 测试记录](docs/SAMSUNG-TEST-REPORT.md)
- [启动设置及无效入口记录](docs/STARTUP-SETTINGS-TEST-REPORT.md)
- [早期小米测试记录](docs/TEST-REPORT.md)
- [源码研究与引用](docs/RESEARCH.md)

报告中的 `artifacts/` 为本地证据路径；原始截图和日志不随源码发布。报告中的 APK hash 对应当时版本，不代表后续修改后的 APK。

## 权限与隐私

通知使用权仅供官方 `MediaSessionManager` 查找 QQ音乐会话，不读取通知内容。不采集账号、Cookie 或登录凭据，不申请网络、存储、录音、悬浮窗、无障碍或 root 权限。

不自行解码或下载音频，不抓取私有接口，不绕过会员、DRM 或版权限制。日志记录生命周期、状态和 metadata 字段名，歌曲信息在本机显示。

## 项目结构

- `app/src/main/`：界面、设置、媒体桥接、通知监听服务和资源。
- `app/src/androidTest/`：真机集成测试。
- `scripts/`：构建与验证脚本。
- `docs/`：研究、测试结果与限制。

实现独立编写，参考来源记录见 [RESEARCH.md](docs/RESEARCH.md)。


## Android Auto 媒体界面

从 0.1.6 起，助手使用 AndroidX Media3 的 `MediaLibraryService` 和 `MediaLibrarySession`，通过自定义 `Player` 转发 QQ音乐的标准媒体控制，不解码或播放音频。

- 浏览节点包含“当前播放列表”和“播放控制”；播放控制提供“继续播放”。
- 当前测试的 QQ音乐未通过标准媒体会话提供播放队列，因此“当前播放列表”为空。助手不会把当前一首歌、刷歌或最近歌曲冒充完整队列，也不提供任意歌曲跳转。
- 在本次测试的 Android Auto 上，即使助手被禁用，正在运行的 QQ音乐媒体会话也能显示歌曲卡片。这不代表 QQ音乐提供完整的 Android Auto 歌库浏览功能。
- Android Auto 仪表盘可能同时显示 QQ音乐和助手的两张播放卡片；“为您推荐”卡片由 Android Auto 管理，本次切换 Media3 后仍存在。

详细验证记录见 [Media3 迁移与 DHU 对比报告](docs/MEDIA3-MIGRATION-REPORT.md)。
