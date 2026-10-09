# 三星真机测试（2026-10-08，America/Los_Angeles）

核心第一版已在三星真机 + 官方DHU上验证。DHU证书问题已通过官方Android Auto更新解决；下方早期失败记录保留作诊断历史。未覆盖系统回收QQ前台播放器进程的场景，真实汽车还需要单独验证。

## 最终结果（19:22 PDT之后）

Device: Samsung SM-M546B / <device-serial>；Android 16 / API 36 / M546BXXUCFZE5。

QQ Music version: 20.9.0.8 / 7458。

AA Helper version: 0.1.0 / 1；最终APK SHA256 `FF6F517E894A6E7D3634E3A2DFD4A6F43F254CA9E060C0125E9E09C83151A2A0`。

Android Auto version: 官方Google Play更新至17.7.663654-release / 177663654。

DHU version: 官方SDK Beta渠道2.1-windows / Build 2022-12-15-495540972。

**PASS（真实手机 + DHU，未使用instrumentation发起这些车机操作）：**

- AA启动器发现QQ音乐AA助手；标准媒体页显示真实歌曲、歌手、封面和播放进度。
- 车机Play/Pause、Next、Previous改变真实com.tencent.qqmusic的PlaybackState/metadata；不只检查助手callback。
- 连续三组车机Play/Pause，对每步真实QQ state=2/3自动断言：`artifacts/samsung-dhu-rapid-*-pause/play.txt`。
- 切歌封面自动更新，播放页保持显示。截图 `samsung-dhu-stable-next.png`。
- QQ外部切歌后车机自动更新标题、歌手、封面，并留在播放页。发起方为公开系统 `cmd media_session monitor QQMusicMediaSession`，直接控制QQ而非助手；前后截图 `samsung-dhu-external-before/after.png`，真实源状态 `samsung-dhu-external-*-state.txt`。
- **普通关闭后的无Session恢复**：QQ官方UI正常关闭；源session消失且package.stopped=false；车机点击“恢复QQ音乐播放”后QQ重建会话并PLAYING，恢复同一首歌和进度。证据 `samsung-dhu-normal-before/after.txt`、`normal-package.txt`、`normal-log.txt`、`normal-restored.png`。普通关闭仍保留过一个主进程，因此本项不冒充系统回收进程。
- **T90 USER_FORCE_STOP**：杀掉QQ进程/session后，仅通过车机入口恢复真实播放；旧元数据清空、新session绑定。证据 `samsung-dhu-T90-*`。
- **真正锁屏**：KEYCODE_SLEEP后记录showing=true、secure=true。仅在DHU点击播放，QQ state=PLAYING；再次检查keyguard仍showing=true。证据 `samsung-dhu-lock-before/after.txt`、`lock-playing.txt`、`locked-playing.png`。
- 锁屏且QQ被Force Stop时，车机恢复播放成功，认证锁定保持；证据 `samsung-dhu-locked-T90-*`。
- 实际DHU退出、重新启动后，TLS验证ok、车机恢复助手metadata和控制；锁屏时也成功。证据 `samsung-dhu-reconnected*.png`、`before-disconnect.txt`。
- 连接前没有QQ session（该前置用Force Stop构造），重新连接DHU：助手onGetRoot尝试准备QQ，随后响应AA播放请求，实际QQ PLAYING，keyguard仍showing=true。证据 `samsung-dhu-cold-connect-before/after/log/keyguard.txt`、`cold-connected.png`。

**最新版真机集成回归：** `artifacts/20261008-191924-controls/result.txt`，`OK (4 tests)`，12.899秒，覆盖真实状态、metadata、封面像素匹配、外部切歌及服务重连。构建、androidTest APK和lint检查通过。

**FAIL（历史已修复）：**

- Android16 QQ metadata空键导致诊断排序NPE：过滤空键后修复。
- DHU证书过期：DHU2.0/2.1连接AA15.6都失败；通过Google Play更新AA17.7后验证ok并建立视频会话。没有绕过证书验证或改变时钟。
- QQ切歌先更新metadata，再短暂STOPPED/NONE，AA回到浏览入口：移除静态浏览项刷新，并为明确跳转/外部新歌metadata建立最长10秒的加载窗口；只有等待期间的STOPPED/NONE映射BUFFERING，不伪造PLAYING。会话销毁清空、超时恢复真实状态。DHU实际切歌和外部切歌复测通过。
- 早期拟锁屏instrumentation测试实际showing=false：不计为锁屏PASS，已由上述真实DHU/keyguard=true测试替代。

**Known limitations / 尚未覆盖：**

- T04未模拟系统内存回收直接结束QQ前台播放器进程。am kill不会杀仍有前台服务的播放器；不将Force Stop或正常关闭当成该场景。
- 当前测试只有这台三星、QQ20.9和AA17.7组合；小米阶段有独立系统限制记录。
- 完整QQ队列未通过公开Queue逐项比对；QQ自己负责队列/DRM/音频，本项目只请求恢复和基础控制。
- AA系统的“自动开始播放音乐”设置可在重新连接时主动向助手发送Play；助手的准备逻辑本身不自动播放。
- Google地图定位未授权，DHU地图面板会显示权限提醒；不影响已验证的媒体功能。
- 未证明Google Play上架符合汽车应用审核；当前是开发安装与AA未知来源测试。

**Need real-car verification：** OEM发现/UI/按键、方向盘物理媒体键、无线AA重连、实际驾驶限制及长期驻留。没有把DHU结果当作这些实车测试通过。

充电保持唤醒已恢复原值0；测试结束暂停QQ并退出DHU，保留开发设置与ADB映射供后续使用。

## 早期阶段记录

Device: Samsung SM-M546B / serial <device-serial>

Android: 16 / API 36 / M546BXXUCFZE5

QQ Music: 20.9.0.8 / 7458（新手机安装，访客播放队列；无需登录也能测试这些曲目）

AA Helper: 0.1.0 / 1，Android 16修正版

Android Auto: 初始15.6.654494-release / 156654494；Google Play更新已安装17.7.663654-release / 177663654（targetSdk37），需继续重试DHU

DHU: 已从官方SDK的2.0更新至2.1-windows（Beta渠道） / 2022-12-15-495540972

## 修复

初次三星测试在QQMusicController.select崩溃，原因是QQ MediaMetadata.keySet含null。
只用于诊断的sorted调用对null键触发ComparableTimSort NPE。
已在排序前filterNotNull，保留现有媒体字段同步逻辑。
重新assembleDebug、assembleDebugAndroidTest、lintDebug成功；安装修正版后复测成功。
失败证据 `artifacts/20261008-185047-controls`；不是QQ播放器崩溃。

测试脚本现在允许明确serial；未指定serial时只在恰好一个授权设备的情况下自动选择，避免继续指向旧小米。

## PASS：真实QQ + 三星，媒体桥集成层

- 四项控制测试，11.052秒，`artifacts/20261008-185213-controls/result.txt`：Play/Pause真实状态、连续控制、媒体服务重连、上下首真实曲目变化、标题歌手同步、封面像素匹配、QQ控制器直接切歌后的metadata/art自动更新。
- Force Stop诱导会话销毁重建，4.747秒，`artifacts/20261008-185453-lifecycle-force-stop/result.txt`：旧metadata清空、新QQ token、真实PLAYING、metadata恢复。
- **正常退出后无Session恢复**，5.341秒，`artifacts/20261008-185930-recovery/result.txt`：通过QQ官方UI“更多 → 关闭 → 关闭QQ音乐”退出，不使用am force-stop。退出后dumpsys记录0 sessions，package记录stopped=false。助手收到媒体浏览测试客户端的Play后，QQ创建session并真实PLAYING，标题同步。前置证据 `artifacts/samsung-normal-exit-before.txt`。
- 拟锁屏测试，7.933秒，`artifacts/samsung-locked-tests.txt`：控制和恢复断言通过，但事后核查samsung-locked-before/after实际是showing=false、secure=true，未满足锁屏前置条件。因此**不计为锁屏PASS**，需要重新执行。没有输入身份凭证或绕过认证。

所有上述PASS是instrumentation集成测试，不是DHU车机按钮测试；尤其后台launcher在instrumentation环境中的成功不可直接证明从真实AA中启动成功。
正常退出不是系统回收进程，故T04 NORMAL_PROCESS_DEATH仍需要单独验证。`am kill`尝试没有消除QQ前台播放器会话，不计作通过。

## Android Auto / DHU

- 三星ADB input可执行，没有小米INJECT_EVENTS拒绝。
- 已通过公开AA设置UI启用开发模式、Unknown sources（checked=true）和Head Unit Server。
- ADB forward tcp:5277 tcp:5277已建立。
- DHU2.0以及官方Beta2.1均完成协议版本1.7交换和TLS1.2协商，但随后输出 `Verify returned: certificate has expired`、`Shutting down connection due to auth failure`、`Unrecoverable error -24`。
- 不把TCP connected/TLS协商当成AA连接PASS；没有车机画面，尚未验证发现助手、封面显示或车机按钮。
- 没有关闭证书检查、修改系统时间、修改AA/QQ APK或改USB驱动。
- Google Play的Android Auto更新入口已完成官方更新，包版本实测为17.7.663654-release，需重试握手。
- 手机随后重新锁屏，显示“使用指纹或画出图案”。需要用户解锁后继续更新和DHU排查。
- 官方DHU安装与Beta渠道说明：https://developer.android.com/training/cars/testing/dhu

## 已改变的测试设置

临时设置充电保持唤醒（原stay_on_while_plugged_in=0；先试USB=2，后改全部充电=7），避免自动休眠中断测试；测试全部结束时恢复0。
AA开发模式/未知来源/Head Unit Server用于开发测试。Notification Access通过公开cmd notification allow_listener授予。
各DHU失败连接进程已退出；ADB映射保留。

## 下一步

用户指纹/图案解锁；完成官方AA更新并重试证书握手；连接DHU后验证UI、四键、正常无Session恢复、锁屏及断开重连。真实汽车/OEM/方向盘/无线AA仍另行验证。

