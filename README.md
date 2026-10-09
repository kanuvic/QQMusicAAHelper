# QQ音乐AA助手

<img src="app/src/main/res/drawable/ic_launcher.png" width="128" alt="App 图标">

将官方 QQ音乐的媒体会话桥接到 Android Auto，在车机标准媒体界面显示歌曲、歌手、封面和进度，并支持播放、暂停、上一首和下一首。音频始终由 QQ音乐播放。

独立实验项目，与腾讯音乐或 Google 无隶属关系；真实汽车兼容性仍需验证。

## 功能与设置

- 实时镜像 QQ音乐的真实媒体信息和播放状态。
- 无媒体会话时尝试通过启动入口和标准媒体按键恢复，最多等待 16 秒。
- 手机主页提供通知使用权授权、启动 QQ音乐、查找 Session 和日志。
- 点击“查找 Session”先短暂清空状态区，再显示最新结果。

| 连接车机时的选项 | 状态 |
| --- | --- |
| 继续上次播放（默认） | 主动恢复 QQ音乐上次的歌曲、进度和队列 |
| 播放猜你喜欢电台 | 暂时无效，置灰不可选 |
| 播放最近歌曲 | 暂时无效，置灰不可选 |
| 跟随 Android Auto 设置 | 只准备会话，由 Android Auto 的播放请求决定是否开始播放 |

设置保存在本机，下次 Android Auto 连接助手媒体服务时生效。旧配置中的无效选项回退为默认项。车机重新选择媒体来源也可能触发连接处理。

## 使用条件

- Android 9（API 28）或更新版本。
- 已安装且可正常播放的官方 QQ音乐（`com.tencent.qqmusic`）。
- Android Auto，以及真实车机或官方 Desktop Head Unit（DHU）。
- 在系统设置中授予助手“通知使用权”，用于访问 QQ音乐媒体会话。

安装后打开助手完成授权，并在 QQ音乐中准备可播放的歌曲。开发安装的 App 可能需要在 Android Auto 开发者设置中启用 **Unknown sources**。账号、会员、版权、网络及后台启动限制仍由 QQ音乐和 Android 系统决定。

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

技术栈：Kotlin 2.0.21、AndroidX Media 1.7.0、AGP 8.9.2、Gradle 8.11.1；compile/target SDK 35；版本 0.1.0。

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
