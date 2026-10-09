# 真机阶段报告（2026-10-08，America/Los_Angeles）

后续已切换至三星 SM-M546B / Android 16。最新状态见 [SAMSUNG-TEST-REPORT.md](SAMSUNG-TEST-REPORT.md)；本文件下方保留小米阶段历史，不代表当前设备。

项目尚未达到最终完成标准：手机媒体桥已验证，Android Auto DHU、普通无会话退出和锁屏尚未完成。

Device: Xiaomi MI 9，serial <device-serial>

Android: 11 / API 30 / V12.5.2.0.RFAEUXM

QQ Music version: 20.9.0.8 / versionCode 7458

AA Helper version: 0.1.0 / versionCode 1 / dev.qqmusic.aahelper

Android Auto version: 16.5.661214-release

DHU version: 2.0-windows / Build 2022-03-30-438482292

Build: JDK 17，Gradle 8.11.1，AGP 8.9.2，Kotlin 2.0.21，SDK 35。
`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 均通过。
lint 有非阻塞警告；v1 明确不支持语音搜索，两个相关 AA lint 检查有注释说明后禁用，未禁用整个 lint。

## PASS（真实 QQ音乐 + 真机，媒体桥集成层）

- QQ_SESSION_FOUND：按 com.tencent.qqmusic 找到真实会话，绑定控制器。
- Play：真实 QQ PlaybackState 到达 STATE_PLAYING。
- Pause：真实 QQ PlaybackState 到达 STATE_PAUSED。
- 连续 Play/Pause：三组连续往返并对真实状态断言。
- Next / Previous：真实 QQ曲名变化；Next 完成加载后再测试 Previous。
- Title / Artist：桥接 session 与 QQ metadata 匹配。
- Artwork：QQ实际 ALBUM_ART 提供 bitmap；桥接 ART 经尺寸归一化后像素匹配。
- QQ外部切歌：直接对 QQ controller 发起操作，不经助手发起；助手通过回调自动更新标题及封面像素。
- 媒体浏览客户端断开、服务销毁后重连：新服务能继续控制真实 QQ。
- T90 USER_FORCE_STOP：停止后 active sessions 为零；助手恢复 QQ session 并真实播放。
- 会话销毁/重建：观察旧 metadata 清空，恢复后 QQ session token 改变且 STATE_PLAYING，标题重新同步。

控制测试证据：`artifacts/20261008-182638-controls/result.txt`，最终 `OK (4 tests)`，11.034 秒。
T90 无会话恢复证据：`artifacts/T90-before.txt`（0 sessions），`T90-user-force-stop.txt`，`T90-logcat.txt`。
销毁重建证据：`artifacts/verified-tests.txt` 和 `verified-logcat.txt` 中对应生命周期测试通过；后续单独生命周期复测见 artifacts 中 lifecycle-force-stop 目录。
额外生命周期复测目录 `artifacts/20261008-182753-lifecycle-force-stop` 尚无测试结果：手机自动锁屏（showing=true / SCREEN_STATE_OFF / INTERACTIVE_STATE_SLEEP），测试APK再次安装等待响应。这轮未执行，不能计为PASS或功能FAIL。先前会话销毁重建通过证据仍保留。

## FAIL / 修复记录

- 首轮 Previous 断言失败：在 QQ发布过渡 metadata 后立即发送相反的切歌请求。测试改为确认 Next 已完成实际播放，再测试 Previous；复测通过。
- 首轮外部切歌与组合测试的最后一次 Play 失败：测试在 QQ仍处于曲目加载状态时结束，上一项的异步 Pause 影响下一项。增加真实 PAUSED 清理确认及等待外部曲目完成加载后，完整控制测试四项通过。保留失败日志，没有把失败轮次计为 PASS。
- 手机 ADB tap：MIUI 抛出 `SecurityException: Injecting to another application requires INJECT_EVENTS permission`。这是当前系统授权阻塞，未绕过。
- 过滤助手与 AndroidRuntime 的最终控制日志没有 FATAL EXCEPTION / SecurityException / IllegalStateException / ANR / DeadObjectException / Timeout。
- 系统 MediaSessionRecord 在早期 instrumentation 进程退出后报告过 Removing dead callback / DeadObjectException；对应的是测试进程结束后的 Binder 清理，未发现助手崩溃。新服务绑定与新 token 测试通过。

## 测试矩阵

| ID | 场景 | 状态与证据范围 |
|---|---|---|
| T01 | QQ正在播放 → AA连接 | 待 DHU；已有真机桥接控制验证 |
| T02 | 暂停且有session → AA连接 | 媒体桥 PASS；AA连接待验证 |
| T03 | 后台无session → AA连接 | 待验证；T90成功不能代替本项 |
| T04 | NORMAL_PROCESS_DEATH → AA连接 | 未完成；am kill 只终止可杀后台主进程，QQ前台播放器进程保留session，不能算本项通过 |
| T05 | 锁屏 → AA连接 | 未执行；ADB按键受MIUI权限限制 |
| T06 | AA断开重连 | 媒体浏览服务重连 PASS；实际AA重连待DHU |
| T07 | 连续Play/Pause | 真机媒体桥 PASS；DHU按键待验证 |
| T08 | Next | 真机真实QQ metadata PASS；DHU待验证 |
| T09 | Previous | 真机真实QQ metadata PASS；DHU待验证 |
| T10 | QQ主动切歌 | QQ controller直接切歌 → 桥metadata和封面更新 PASS；QQ手机UI及DHU待验证 |
| T11 | Session销毁重建 | Force Stop诱导的销毁和新token绑定 PASS；非Force Stop重建待验证 |
| T12 | Artwork变化 | 真机切歌后封面像素与QQ源一致 PASS；DHU显示待验证 |
| T90 | USER_FORCE_STOP | 单独记录 PASS，未作为普通后台退出 |

## DHU 现状与阻塞

### 2026-10-08 18:43 PDT：用户解锁及再次授权后的复测

- ADB serial <device-serial> 为 device，普通 USB调试工作正常。
- 重新测试 ADB tap，仍得到 INJECT_EVENTS SecurityException；不能把普通 USB调试授权当作 MIUI“USB调试（安全设置）”已开启。
- 上次中断的会话生命周期测试已补完：`artifacts/20261008-184339-lifecycle-force-stop/result.txt`，`OK (1 test)`，4.327 秒。真实会话销毁、metadata清空、新token绑定、QQ进入PLAYING均通过。
- 尝试官方 DHU `--headless --usb=<device-serial>`，连续报告 No device found ready yet，未建立AA会话；已停止等待，没有更改USB驱动或系统配置。
- 已通过公开 Settings intent 打开手机开发者选项，方便用户开启“USB调试（安全设置）”。仍待该独立权限，或用户手动完成AA Head Unit Server设置。

SDK 官方安装 DHU 2.0，已验证 `--help` 和 `--version`。设置 ADB forward tcp:5277 tcp:5277。
DHU headless 进程可以启动，TCP打印 connected；尚未建立 AA视频会话，screenshot 返回 “Don't have video focus”。
不把 TCP连接视为 AA握手成功，也不宣称车机发现助手或显示歌曲。
通过当前 Manifest 注册的 SETTINGS action 成功打开手机 AA设置。
尝试通过观察到的“更多选项”坐标点击时被 MIUI拒绝；没有继续注入或尝试安全绕过。
等待用户开启“开发者选项 → USB调试（安全设置）”，随后再自动完成可操作的 AA开发设置和 Head Unit Server 流程。
如果首次权限、开发模式确认或小米账号认证必须人工完成，才继续请求最短人工步骤。
未退出的虚假 DHU连接已关闭；ADB端口映射保留供后续连接。

## Known limitations

- 背景 launcher 返回正常不保证系统实际允许打开界面；成功标准仍是QQ session和真实PlaybackState。
- T90设备成功不能推断其他Android版本、MIUI版本、锁屏状态或真实车机成功。
- 当前无需手工安装APK；Notification Access 已通过系统支持的 cmd notification allow_listener 合法授予。
- 没有QQ URL Scheme参数验证；没有使用猜测的scheme、私有QQ API、修改APK、root或账户绕过。
- 不提供自己的音频引擎。网络、VIP或曲目可用性由QQ负责。
- 图像URI被保留；目前真机最可靠来源为ALBUM_ART bitmap，其他版本的URI可访问性尚待验证。

## Need real-car verification

OEM车机发现、显示及媒体按钮行为；方向盘物理按键；真实无线AA断开重连；实际行车/锁屏限制。
先完成真实手机 + DHU，再进入上述验证。

