# 助手播放页面开关与独立启动测试

日期：2026-10-09。版本 0.1.7 / versionCode8。真机 Samsung S24，官方 DHU 2.1，经 ADB 隧道实际连接。

## 发现与修改

用户卸载助手后，在 DHU 首次连接只看到“为您推荐”；启动手机 QQ音乐并播放后，DHU 出现 QQ音乐自己的播放卡片。已有 QQ 标准媒体会话的显示不依赖助手。助手可以在连接车机后主动准备 / 恢复 QQ音乐，但不是唯一启动手段。

原助手会创建镜像 Media3 会话，AA 因此可能展示官方 QQ 和助手两张同歌曲卡片。设置新增“显示 AA助手播放页面”，首次安装 / 升级没有此偏好默认 false。

- 关闭：禁用助手媒体服务组件，不创建 MediaLibrarySession / Player。关闭开关立即释放已有助手会话，不暂停或销毁 QQ 官方会话。
- 开启：启用原 Media3 浏览和控制，手机主页与 AA 都可以访问助手媒体服务。
- 连接检测改为通知监听服务观察公开 AndroidX CarConnection API。真实投屏状态从断开变为连接时调用原共享 applyStartupMode；不依赖自有媒体会话。
- 删除旧 onGetLibraryRoot 的启动触发，避免两条路径重复应用设置。
- 默认 INHERIT 仍只准备 QQ音乐，不主动播放。页面关闭后 AA 不向助手发送播放命令，不能保证 AA 能恢复尚未产生会话的 QQ音乐；希望主动播放应选择 RESUME 或其他主动播放选项。
- 宿主可能缓存旧卡片，设置修改后请断开重连；开关不删除“为您推荐”。

## 真机测试

默认关闭时授予通知监听权限，日志确认 CarConnection 检测到已有 AA 连接并应用 INHERIT。系统仅存在官方 QQ 会话，没有助手会话。

以下五项一次运行全通过：

1. MediaPageToggleTest：开启后真实浏览服务连接且助手会话出现；关闭后助手会话释放、组件禁用，QQ 原会话 token 不变。
2. Media3BrowserTest：临时开启页面，根、子节点、分页、真实 QQ 空队列正确，最后恢复开关。
3. RealQQMusicTest.pausedSessionAndControls：实际播放、暂停、歌曲镜像。
4. RealQQMusicTest.nextPreviousAndArtwork：实际上一首、下一首及封面像素。
5. RealQQMusicTest.sessionDestroyedAndRecreatedForceStop：QQ 销毁后标题清空，新 token 和实际恢复播放。

HiddenCarStartupTest 另行通过：页面关闭、临时选择 RESUME，通过真实 DHU 断开重连验证启动，没有发送伪造广播。测试进程启动时通知监听服务曾先对已有连接应用 RESUME，所以在 DHU 真正断开之后再次强停 QQ，确认无 QQ 或助手会话，再重新启动 DHU。日志记录 projection=false -> true，连接后约 3 秒恢复真实 QQ PLAYING；没有助手会话。原 INHERIT / 页面 false 已恢复，结束时暂停歌曲。此测试需要操作员实际重启 DHU 配合，不能当作完全独立的自动测试运行。

最终 DHU 显示官方 QQ 卡片“痴心绝对 / 李圣杰”及宿主推荐位置，没有助手歌曲卡片。

第一次冷启动测试在 instrumentation 线程构造 CarConnection，因线程无 Looper 失败；改为主线程构造后通过。生产监听服务本就在主线程构造，没有此错误。

## 构建与限制

Debug、签名 Release、lintDebug 和原 6 项启动重试单元测试通过。签名密钥不变。新依赖 androidx.car.app:app:1.7.0，仅使用连接 API；库提供 provider 可见性声明。合并时去掉不需要的 ACCESS_NETWORK_STATE，不增加 INTERNET 权限。

手机安装 Debug 0.1.7，通知使用权已授予，原设置 INHERIT / 页面 false 已恢复。没有卸载换装 Release。

尚未覆盖真实车辆、所有品牌与 AA 版本、长期后台进程回收、通知使用权撤销重授的完整回归。QQ 登录、付费及系统后台启动限制仍适用。页面关闭后没有助手车机入口是预期行为；若某版本 AA 不支持 QQ 原生会话显示，可手动开启助手页面。

参考：

- https://developer.android.com/training/cars/apps/library/connection-api
- https://developer.android.com/reference/androidx/car/app/connection/CarConnection
- https://developer.android.com/media/media3/session/serve-content
