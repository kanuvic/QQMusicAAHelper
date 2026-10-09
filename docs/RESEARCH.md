# Phase 0 — 公开源码研究

研究版本：charlottejas/NuomiPlayer `4377964a789836481b7095ccf6912b8afb1ed4cb`。
来源：https://github.com/charlottejas/NuomiPlayer 。许可证 MIT，版权 2025 charlottejas。
研究下载位于忽略目录 `.research/NuomiPlayer`；没有安装仓库 APK，也没有复制其实现。

最相关源码：

- `mobile/src/main/java/com/example/myapplication/QqSessionSniffer.java`：NotificationListenerService 使用自己的 ComponentName 调用 MediaSessionManager.getActiveSessions，按 com.tencent.qqmusic 匹配，取得平台 MediaController 和 Token。
- `shared/src/main/java/com/example/myapplication/shared/MyMusicService.java`：MediaBrowserServiceCompat 暴露本地 MediaSessionCompat，接收本地广播携带的 QQ Token，转发 transport controls，镜像 metadata/state。
- `shared/build.gradle`：AndroidX Media compat 依赖。
- 仓库中提交的 `shared/build/intermediates/.../AndroidManifest.xml` 和 automotive_app_desc.xml 是构建产物，不视作当前版本的源码 Manifest。

QQ 特殊代码：精确 package 常量、QQ metadata 中 `ucar.media.metadata.PLAY_MODE` 诊断。本项目不实现播放模式。发现通过通知回调触发；公开源码没有足以证明无 Session 恢复成功的实现。README 的最新 APK 描述不等于这些公开源码已实现同样行为。

参考的是平台 controller → 自有媒体桥接 session 的设计，采用 AndroidX Media compat 可直接镜像平台会话，不需要构造 Media3 Player 来模拟播放引擎。此选择依据实现复杂度，并未声称兼容性已比 Media3 更好。

本项目舍弃队列、搜索、歌词、进度拖动、收藏、repeat/shuffle、跨播放器选择、LocalBroadcastManager 和额外汽车模块。实现只有 QQ 后端。改为监听 active sessions 变化，销毁后释放 callback、清空 metadata/state，避免保留已失效会话或假装播放。

## 官方资料

- 标准媒体服务及 AA 渲染：https://developer.android.com/training/cars/media
- DHU 安装、ADB tunnel、headless：https://developer.android.com/training/cars/testing/dhu
- 后台 Activity 启动限制：https://developer.android.com/guide/components/activities/secure-bal

## 首次真机观测

MI 9 / Android 11 / API 30 / V12.5.2.0.RFAEUXM。
QQ音乐 20.9.0.8（7458，targetSdk 30）。AA 16.5.661214-release。
启动时 QQMusicMediaSession active=true，state=2 (PAUSED)，metadata size=20。
当前包注册了 androidqqmusic / AndroidQQMusic / qqmusic / qqmusicactive；没有验证具体参数前不使用。
当前包没有注册 MediaBrowserService；有官方 ACTION_MEDIA_BUTTON receiver `androidx.media.session.MediaButtonReceiver`。
Launcher resolve 为 `.activity.AppStarterActivity`，程序通过 PackageManager 动态解析，不写死。
冷恢复用 launcher + 有用户 play 意图时向当前导出的 QQ MEDIA_BUTTON receiver 发送标准 PLAY key event；等待会话确认，超时显示错误。ADB 能启动不代表普通 app 在后台或锁屏也能启动，必须分别实测。
