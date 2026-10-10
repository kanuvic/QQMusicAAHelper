# Media3 迁移与 DHU 对比报告

日期：2026-10-09。主项目 0.1.6（versionCode 7），从 v0.1.5 / 823719d 迁移。

## 迁移决定

用户要求尝试替换 legacy MediaBrowserServiceCompat，比较真机 / DHU 效果后决定保留 Media3。主服务现在继承 MediaLibraryService，创建 MediaLibrarySession；自定义 SimpleBasePlayer 读取官方 QQ音乐的 MediaController，转发播放、暂停、上一首和下一首。没有使用 ExoPlayer、音频解码器、自有音源、QQ 私有接口或绕过付费限制。

使用 media3-session 1.9.3。保留 androidx.media 1.7.0 供现有手机页面和测试的兼容客户端使用；服务端不再继承 MediaBrowserServiceCompat。Media3 的兼容层仍接受 AA 的旧浏览协议，这不等于旧服务实现仍在运行。

## 标准队列的实际结果

`QQ_MUSIC_QUEUE_EXPOSED=false`，限本次 QQ音乐 20.9.0.8 / 三星 Android 16 测试环境。

- QQmusic MediaController.queue = null；queueTitle = null。
- dumpsys media_session 的 queue size = 0，active queue item id = -1。
- QQmusic 没有宣告 ACTION_SKIP_TO_QUEUE_ITEM；customActions 为空。
- session extras = null，playback extras 只有试播 / 时长字段，未发现完整歌曲队列。
- 可以取得当前歌曲、歌手、封面、时长、进度和播放状态。

浏览根 ROOT 包含 CURRENT_QUEUE（当前播放列表）和 CONTROLS（播放控制）。CURRENT_QUEUE 只读取真实标准 queue，当前返回空列表，根节点说明 QQ音乐未提供标准播放队列；CONTROLS 提供 resume（继续播放）。建议请求根返回 CURRENT_QUEUE。没有把单首歌、刷歌或最近歌曲当作完整队列。

Player 内部需要当前条目才能表达当前歌曲，但不宣告 GET_TIMELINE；该条目不是 QQ 的完整队列。未来若 QQ 提供标准队列，现有节点包含只读映射；有队列情况下的渲染、任意歌曲跳转和更新订阅尚未真机验证，不宣称支持任意点歌。

## AA 实际请求与卡片

AA 包名 com.google.android.projection.gearhead。实际 Media3 getRoot 的 LibraryParams 为 suggested=false、recent=false，extras 包含：

- android.media.browse.CONTENT_STYLE_SUPPORTED=true
- android.media.browse.SEARCH_SUPPORTED=true
- androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_LIMIT=4
- androidx.media3.session.LibraryParams.Extras.KEY_ROOT_CHILDREN_BROWSABLE_ONLY=true
- com.google.android.gms.car.media.BrowserIconSize=158（另一次为 56）

legacy 原始 flags=1 的 root hint 被 Media3 转换成 BROWSABLE_ONLY=true。

仪表盘仍能分别显示官方 QQ音乐来源卡片和助手来源卡片，二者镜像同一首歌。Media3 未消除重复视觉效果。

`HOST_CONTROLLED_TITLE=true`：针对本次观察到的仪表盘“为您推荐”卡片。更改助手浏览根标题、换成 Media3 后，它仍显示“为您推荐”以及灰色占位内容。滑动到这张卡片时没有观察到对应的助手 getChildren 请求；不能把它认定为助手浏览根页面。没有找到能由媒体服务删除这张宿主卡片的公开设置。

应用列表在临时禁用 / 恢复助手后未显示助手入口；没有通过 DHU 完成 CURRENT_QUEUE / CONTROLS 浏览页面展示验证。节点接口由原生 Media3 MediaBrowser 真机测试验证，不将它冒充 AA UI 验证。

## 不运行助手时 QQ音乐是否可显示

临时用 pm disable-user 禁用整个助手包（未卸载），重新连接 DHU：只存在 QQ音乐媒体会话，仪表盘仍显示 QQ音乐歌名、歌手、封面与播放控件。

因此本机当前 Android Auto 可以独立展示正在运行的 QQ音乐标准媒体会话；这不能证明 QQ音乐提供完整 Android Auto 歌库浏览功能。本场景未完成禁用助手期间点击 AA 控件的操作验证。

测试后用 pm default-state 恢复助手默认启用状态。禁用包清除了通知使用权，随后已重新授权通知监听并确认能连接 QQmusic session。

## 测试

Debug / 签名 Release 构建、lintDebug、6 项 StartupResumeRetry 单元测试通过。

真机通过的场景：

1. Media3BrowserTest.nativeLibraryReflectsActualQQQueue：ROOT 节点、分页、建议根、真实空队列和 resume 控制节点。
2. RealQQMusicTest.pausedSessionAndControls：兼容客户端连接 Media3 服务，QQ实际播放 / 暂停，歌名 / 歌手镜像。
3. RealQQMusicTest.nextPreviousAndArtwork：实际上一首 / 下一首，封面像素与 QQ 标准元数据一致。
4. RealQQMusicTest.sessionDestroyedAndRecreatedForceStop：强停 QQ 后旧歌名清空，重新拉起产生新 session token 并实际播放，元数据恢复。

测试修正：Media3 通过兼容层可能使用 ALBUM_ART / DISPLAY_ICON 封面字段，PNG 解码后的 bitmap 配置也可能不同。断言检查缩放至 256 的实际像素，不依赖 bitmap 配置。Media3 无当前歌曲时可能提供空元数据对象，销毁测试检查旧歌名清空而不是要求 metadata 对象为 null。初次这两项旧断言失败，修正后复测通过；不是删除功能断言。

未覆盖：新架构的完整真实车辆回归、不同歌单的完整标准队列、非空队列跳转、无权限 / URL 失败 / AA 启动重试的整套迁移真机回归。原有设置和 3 秒重试逻辑保留，启动 URL 失败时继续阻止自动恢复旧队列。

## 官方资料

- https://developer.android.com/media/media3/exoplayer/migration-guide
- https://developer.android.com/media/media3/session/player
- https://developer.android.com/media/media3/session/serve-content
- https://developers.google.com/cars/design/create-apps/media-apps/recommendations
- https://developer.android.com/jetpack/androidx/releases/media

androidx.media 1.8.0（2026-05-06）已将旧库标为 deprecated，推荐 Media3；上述文档没有给出 Android Auto 停用旧协议的日期。
## “为您推荐”内容从哪里来（补充检索）

Google 官方推荐指南说明：AA 默认可从媒体应用浏览树顶部选取推荐；应用可针对 EXTRA_SUGGESTED 请求提供专门推荐根。在 Media3 中通过 LibraryParams.isSuggested 判断请求，在 onGetLibraryRoot 返回该根，再由 onGetChildren 提供条目。Media3 不会自行生成推荐，也不会替助手获取 Spotify 账号的推荐歌单。

支持 AA 不保证某应用在每次仪表盘展示时都有可用推荐；公开文档没有给出该版本仪表盘选择 / 汇总应用的完整策略。推荐根、正在播放队列与平台卡片标题是不同概念。

本次只读检查 Spotify 标准媒体会话，得到 state=ERROR、error=请登录以使用 Spotify。[110]、metadata=null、queue size=0。说明本机 Spotify 当前需要登录，不能仅凭已安装就认为它已经向 AA 提供可用内容。没有操作 Spotify 登录或修改其账号。

助手标准 QQ 会话不提供完整队列，也没有推荐歌库接口。目前 suggested 根返回空 CURRENT_QUEUE，未提供推荐歌单；不会从其他应用复制歌单或用 URL 启动入口假装有完整推荐列表。

参考：
- https://developers.google.com/cars/design/create-apps/media-apps/recommendations
- https://developer.android.com/reference/androidx/media3/session/MediaLibraryService.LibraryParams
- https://developer.android.com/media/media3/session/serve-content
- https://support.spotify.com/us/article/spotify-in-the-car/
