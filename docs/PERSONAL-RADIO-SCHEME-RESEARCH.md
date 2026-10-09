# 刷歌 URL Scheme 研究与验证

研究对象为用户提供的 `QQMusic2005000982.apk`，实际版本是 QQ音乐 **20.9.0.8 / versionCode 7458**，包名 `com.tencent.qqmusic`。

APK SHA-256：`e2574040f42cae2540bd8d2997996a1a6ea7169776e314392d698e9d6caca279`。

## 找到的入口

```text
qqmusic://qq.com/media/playPersonalRadio?p=%7B%7D
```

`%7B%7D` 是 URL 编码后的空 JSON 对象 `{}`。这个方法不要求调用方提供歌曲 ID 或账户凭据；推荐歌曲由 QQ音乐自己获取和播放。

与此前的 `media/playRadio?p={"radioId":"99"}` 不同，新入口调用另一条个性化电台处理流程，不能仅凭旧入口失败就认定个性化电台没有链接入口。

## 静态证据

- Manifest 将 `qqmusic`、`androidqqmusic`、`AndroidQQMusic`、`qqmusicactive` 的 VIEW / BROWSABLE 请求交给导出的 `com.tencent.qqmusic.third.DispacherActivityForThird`。
- 分发器构建 `WebViewPluginEngine` 处理链接。
- `com.tencent.mobileqq.webviewplugin.plugins.l2`（MediaApiPlugin）的方法分发表包含 `playPersonalRadio` 和旧的 `playRadio`。
- 新方法调用 `HeadSetChangeRecoverPlayDataSource.I`；该方法构造 `RadioRequest(99)`，获取个性化电台的推荐歌曲。
- 回调 `com.tencent.mobileqq.webviewplugin.plugins.l2$v` 构建类型 `5`、编号 `99` 的 `MusicPlayList`，再调用 QQ音乐自身的播放队列操作开始播放。
- `RadarRenameExpABTester` 的默认中文名称为“刷歌”，证明本 APK 仍保留雷达对应的个性化业务及重命名逻辑。

这条入口启动个性化推荐队列，不保证同时打开新版刷歌的沉浸式界面。不同 QQ音乐版本、账户、网络和系统后台启动限制仍可能影响结果。

## 当前实测

1. 手机解锁且起初没有活跃 QQ音乐 Session 时，通过系统 ACTION_VIEW 打开新链接：QQ音乐 Activity 启动成功，真实 QQ音乐 MediaSession 达到 `PLAYING(3)`，有歌曲 / 歌手信息。`am start` 的 COLD 表示 Activity 启动状态，本轮没有先强行停止进程，因此不能单凭它认定整个 QQ音乐进程冷启动。
2. 通过助手调用新链接，先确认 QQ音乐真实状态为 `PAUSED`，再等待真实 `PLAYING` 和非空歌曲 metadata：`StartupEntryTest.personalRadioStartsFromPausedSession` 通过（2.937 秒）。
3. 初次强行停止 QQ音乐后，经助手调用出现 16 秒超时，源状态为 `NONE`；随后检查手机已锁屏。本轮失败保留，不把锁屏认定为已证明的唯一原因。
4. 最终 0.1.2 候选 Debug 的六项选择性回归通过（14.169 秒）：播放 / 暂停、上下首 / 封面、QQ 外部切歌、重连 / 快速控制、三个可用设置的持久化，以及锁屏下从 PAUSED 启动刷歌。
5. 锁屏下再次强行停止 QQ音乐后，助手入口测试成功（5.861 秒）；不强行停止的下一次调用成功（1.857 秒）；再次独立强行停止后成功（5.452 秒）。这些测试均确认助手观测到真实 QQ音乐 `PLAYING`，结束后暂停播放。初次超时没有复现，但不能保证所有账户 / 网络 / 版本都能在 16 秒内完成。
6. 曾误用整类回归，包含需要提前清除 Session 的独立恢复场景；七项中六项通过、一项因“已有 Session”的前置条件失败。随后明确选择适合当前状态的六项，全部通过，未将前置条件失败隐藏为成功。

## 助手改动与当前状态

- 版本 `0.1.2 / versionCode 3` 将 RADIO 的链接替换为上述新入口，显示名称改为“播放刷歌”，描述注明“原猜你喜欢／雷达”。
- 本地设置允许选择 RADIO；“播放最近歌曲”仍不可选，未扩大该入口的研究范围。
- Debug / AndroidTest / Release 构建和 Debug / Release Lint 通过。
- Release 沿用原发行密钥；签名验证通过。真机测试使用 Debug 构建，没有卸载 Debug 来测试 Release 签名版。
- 本轮未启动 DHU：手机锁屏，无法完成需要手机界面操作的车机服务配置；新入口的 Android Auto 连接场景及真实车辆仍待验证，不从手机测试推断车机已通过。
- 未修改 QQ音乐 APK，未接入它的私有 HTTP / IPC API，仍通过公开 ACTION_VIEW 请求由 QQ音乐自己播放。

原始反编译文件与 DEX 位于工作区 `.research/qqmusic-20.9.0.8`，不上传仓库。JADX 1.5.6 安装于共用工具目录 `D:\Projects\.tools\jadx-1.5.6`。整包直接加载曾内存不足，改为定位目标类所在 DEX 后单类反编译；大分发方法使用 fallback 指令输出确认，未把反编译异常当作功能缺失。
