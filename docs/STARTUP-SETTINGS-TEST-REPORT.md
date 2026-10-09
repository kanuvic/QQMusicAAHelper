# 连接车机时的播放设置（2026-10-08）

后续按用户要求暂停这两个入口的研究：“播放最近播放”更名为“播放最近歌曲”；“播放猜你喜欢电台”和“播放最近歌曲”均标注“暂时无效”，置灰不可选。读取旧的 RADIO/RECENT 配置时回退到默认“继续上次播放”，避免继续触发无效入口。以下链接测试结果保留为历史记录。

手机主页新增“设置”入口，选择立即保存，下次 Android Auto 浏览器连接时读取。默认继续上次播放。

| 选项 | 行为 | 验证结果 |
| --- | --- | --- |
| 继续上次播放 | 主动请求 QQ音乐恢复歌曲、进度和队列 | 三星 + DHU 从真实 PAUSED 恢复到 PLAYING |
| 播放猜你喜欢电台 | 打开用户提供的 playRadio / radioId=99 链接，等待 QQ音乐实际播放 | 入口能解析，但当前 QQ音乐 20.9.0.8 未开始播放；16 秒超时，不能算通过 |
| 播放最近播放 | 打开用户提供的 today / mid=31 链接，等待 QQ音乐实际播放 | 入口能解析，但当前 QQ音乐 20.9.0.8 未开始播放；16 秒超时，不能算通过 |
| 跟随 Android Auto 设置 | 只准备 QQ音乐；收到 AA 的播放请求才转发，助手不主动播放或暂停 | DHU 连接时保持源 PAUSED，随后 AA 发出 onPlay 才变成 PLAYING；AA 关闭自动播放的分支未做本轮实测 |

原“保持暂停”选项已按用户要求替换。自动恢复与标准播放按钮都调用 onPlay，不能依据该回调可靠区分来源；本轮曾观测 AA 首次请求被拒后约 5 秒重试。最终实现已删除暂停拦截和专用继续播放按钮。旧 PAUSED 设置迁移为 INHERIT。

前三项由用户的设置明确授权主动开始播放，第四项遵循 AA 的媒体播放控制流程： https://developer.android.com/training/cars/media/enable-playback 。原版助手连接时仅 prepare，依赖 AA 发出播放请求；现在第一项主动恢复，第四项保留原先依赖 AA 的行为。

QQ URI 严格限定 com.tencent.qqmusic，用 ACTION_VIEW 交给 QQ，未实现自己的音频播放、接口抓取或点击自动化。URL 打开返回成功不视为播放成功，等待真实 MediaSession PLAYING，失败在车机显示错误。失败后 AA 自动重试不会偷偷恢复旧队列，可点击浏览项重试入口。尚未证明两个链接在当前版本能选择目标队列，也未证明失败原因是登录、版本还是链接语义。

本轮验证：

- assembleDebug、assembleDebugAndroidTest、lintDebug 成功。
- 四个设置值持久化验证通过，真实磁盘偏好检查确认写入；结束恢复 RESUME。
- 最终版本基础控制回归四项通过，10.934 秒，artifacts/20261008-202259-controls/result.txt。
- 主动恢复源状态证据：artifacts/startup-resume-final-source.txt。
- 跟随 AA 源状态证据：artifacts/startup-inherit-final-source.txt。
- QQ 链接集成测试 realQQEntryStartsPlayback：RADIO 和 RECENT 分别失败（真实源未播放），不混入通过的回归结果。
- 已安装最终 APK，SHA256：8689F1C3EBAFBBA54A5EB7F49854C18237381BEE3645A9EF5F6FFDAB428F83E2。

连接触发使用经过 UID/package 校验的 Android Auto onGetRoot；5 秒内重复 root 请求合并，不以手机主页自己的浏览连接触发启动设置。车机重新选择助手媒体来源也可能产生新的 root 请求，因此不声称这是所有真实汽车的物理连接事件；真实车辆仍需验证。
