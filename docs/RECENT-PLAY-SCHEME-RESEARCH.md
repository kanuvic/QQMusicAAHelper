# 最近歌曲入口研究与测试（0.1.3）

用户要求：先完整查找“播放最近歌曲”入口，若仍没有可靠入口则删除不可用选项；随后提示 QQ音乐桌面图标长按菜单存在“最近播放”。

研究对象为用户提供的 QQ音乐 20.9.0.8 APK，仍不修改 APK、不接入私有 HTTP / IPC 接口。

## 已确认的桌面快捷方式

Manifest 的 `android.app.shortcuts` 指向资源 `0x7f140023`，实际 APK 文件 `r/j/a9.xml`。

“最近播放”快捷方式的声明为：

- ID：`playRecent`
- Action：`android.intent.action.VIEW`
- Target：导出的 `com.tencent.qqmusic.third.DispacherActivityForThird`
- Extra：`shortcutScheme=playRecent`

`com.tencent.qqmusic.third.s.a`（ShortcutJumpManager）识别该 extra，打开 `RecentPlayFragment`，携带 `com.tencent.qqmusic.ACTION_AUTO_PLAY_SONG_LIST.QQMusicPhone=true` 和来源 `ShortCut`。因此桌面快捷方式是带 extra 的 Intent，不能直接当作 URL Scheme。

## 新 URL 候选与静态证据

```text
qqmusic://qq.com/ui/myTab?p=%7B%22tab%22%3A%22history%22%2C%22direct_play%22%3Atrue%7D
qqmusic://qq.com/ui/voiceCall?p=%7B%22intent%22%3A%22recentPlay%22%2C%22direct_play%22%3A1%7D
```

- `UIPlugin.q` 方法表将 `myTab` 分派到 `G2`。它读取 `tab` 和布尔 `direct_play`，`tab=history` 调用最近播放页面跳转。
- `voiceCall` 分派到 `m3`，`intent=recentPlay`、整数 `direct_play=1` 转为 `tab=history`、布尔 `direct_play=true`，再走 `G2`。
- `RecentPlayFragment` 有 `direct_play` 与快捷方式自动播放标记；任一为真时定位到歌曲页签，标记继续传递给歌曲列表子页面。
- `MediaApiPlugin` 的最近列表引用位于原唱 / 伴奏 / AI 演奏相关流程，尚不能据此把它视为通用最近歌曲入口；不为此改变用户的播放模式。

## 解锁后的真机验证与集成

- 手机解锁并亮屏后，快捷方式和两个 URL 都能打开“最近播放”的歌曲页，截图可见 16 首历史歌曲；多数为 VIP 歌曲。
- 多次启动时真实源保持 PAUSED / NONE。用户现场报告 QQ音乐提示因未付费无法播放，因此不能把未进入 PLAYING 等同于链接无效。
- 强行停止 QQ音乐后使用 `myTab` 启动，Activity 为 COLD，进入最近歌曲页；当次状态 NONE，未开始音频播放。
- 助手已将 RECENT 换为 `ui/myTab` + `history` + `direct_play=true`，恢复可选，保留名称“播放最近歌曲”。不检测会员权限，不绕过账户或版权限制。
- 实际通过助手发起 RECENT 的 `realQQEntryStartsPlayback` 测试通过，耗时 15.112 秒；日志记录真实状态 PLAYING、`Playback entry confirmed: RECENT`。测试结束暂停。
- 随后增加明确 PAUSED 前提的 `recentSongsStartFromPausedSession`，重试 16.187 秒后超时失败；没有把此失败算成通过。尚不能证明重复启动每次可靠，也不能确定所有超时均由付费限制导致。手动操作可能影响现场测试，以上通过结果不代表完全隔离的自动化验证。
- 设置保存测试通过，包括重新启用的 RECENT。
- Debug / Release 构建与两种 lint 均通过；Android Auto 连接场景、真实车机及长期播放尚未验证。

## 同版通知使用权按钮

首页“开启通知使用权”文字未授权时为红色，授权后为黑色。每次显示状态及返回前台时读取系统真实授权状态。真机临时撤销授权后红字、恢复授权返回前台后黑字，均已截图验证；测试结束已恢复原授权。

反编译证据保存在工作区 `.research/qqmusic-20.9.0.8`：`launcher-shortcuts-tree.txt`、`ThirdShortcut-s.java`、`UIPlugin.java`、`RecentPlayFragment.java`。原始反编译源码和用户 APK 不上传 GitHub。