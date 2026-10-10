package dev.qqmusic.aahelper

import android.content.pm.PackageManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(UnstableApi::class)
class QQMusicMediaService : MediaLibraryService() {
    companion object {
        private var active: QQMusicMediaService? = null
        fun closeForSettings() { active?.closeBridge(); active?.stopSelf() }
    }
    private lateinit var qq: QQMusicController
    private lateinit var player: QQSessionPlayer
    private lateinit var library: MediaLibrarySession
    private var closed = false
    private var queueSignature: String? = null
    private val update: () -> Unit = {
        player.sourceChanged()
        val signature = "${qq.connected}:${qq.queue?.map { "${it.queueId}:${it.description.title}:${it.description.subtitle}" }}"
        if (signature != queueSignature) {
            queueSignature = signature
            library.notifyChildrenChanged("CURRENT_QUEUE", qq.queue?.size ?: 0, null)
        }
    }
    private fun node(id: String, title: String, subtitle: String? = null, browsable: Boolean = true): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder().setTitle(title)
            .setSubtitle(subtitle).setIsBrowsable(browsable).setIsPlayable(!browsable).build()).build()
    override fun onCreate() {
        super.onCreate()
        if (!MediaPageSettings.isEnabled(this)) { stopSelf(); return }
        active = this
        qq = QQMusicController.get(this); player = QQSessionPlayer(this, qq)
        library = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val pkg = controller.packageName
                val valid = packageManager.getPackagesForUid(controller.uid)?.contains(pkg) == true
                val allowed = controller.uid == applicationInfo.uid || pkg == "com.google.android.projection.gearhead" ||
                    packageManager.checkSignatures(controller.uid, android.os.Process.SYSTEM_UID) == PackageManager.SIGNATURE_MATCH
                DebugLogger.log("AA_MEDIA3 connect controller=$pkg accepted=${valid && allowed} hints=${MediaDiagnostics.bundle(controller.connectionHints)}")
                return if (valid && allowed) MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                    else MediaSession.ConnectionResult.reject()
            }
            override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
                log("getRoot", browser, "ROOT", params)
                return Futures.immediateFuture(LibraryResult.ofItem(node(if (params?.isSuggested == true) "CURRENT_QUEUE" else "ROOT", "当前播放列表"), params))
            }
            override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                log("getChildren page=$page pageSize=$pageSize", browser, parentId, params)
                val items = when (parentId) {
                    "ROOT" -> listOf(node("CURRENT_QUEUE", "当前播放列表", if (qq.queue.isNullOrEmpty()) "QQ音乐未提供标准播放队列" else "${qq.queue!!.size} 首歌曲"), node("CONTROLS", "播放控制"))
                    "CONTROLS" -> listOf(node("resume", "继续播放", "继续 QQ音乐当前队列", false))
                    "CURRENT_QUEUE" -> qq.queue.orEmpty().map { item ->
                        MediaItem.Builder().setMediaId("QUEUE_ITEM:${item.queueId}")
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.description.title)
                                .setArtist(item.description.subtitle).setArtworkUri(item.description.iconUri)
                                .setIsBrowsable(false).setIsPlayable(false).build()).build()
                    }
                    else -> emptyList()
                }
                val start = page.toLong() * pageSize
                val selected = if (page < 0 || pageSize <= 0 || start >= items.size) emptyList() else items.subList(start.toInt(), minOf(items.size.toLong(), start + pageSize).toInt())
                return Futures.immediateFuture(LibraryResult.ofItemList(selected, params))
            }
            override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
                log("getItem", browser, mediaId, null)
                val item = when (mediaId) {
                    "ROOT" -> node(mediaId, "当前播放列表")
                    "CURRENT_QUEUE" -> node(mediaId, "当前播放列表")
                    "CONTROLS" -> node(mediaId, "播放控制")
                    "resume" -> node(mediaId, "继续播放", browsable = false)
                    else -> null
                }
                return Futures.immediateFuture(if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(item, null))
            }
            override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (mediaItems.size != 1 || mediaItems[0].mediaId != "resume") return Futures.immediateFailedFuture(IllegalArgumentException("Only resume is supported"))
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, 0, 0))
            }
        }).build()
        qq.observe(update); qq.start()
    }
    private fun log(method: String, browser: MediaSession.ControllerInfo, id: String, params: LibraryParams?) {
        DebugLogger.log("AA_MEDIA3 $method controller=${browser.packageName} parentId=$id suggested=${params?.isSuggested} recent=${params?.isRecent} extras=${MediaDiagnostics.bundle(params?.extras)}")
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (!closed && ::library.isInitialized && MediaPageSettings.isEnabled(this)) library else null
    private fun closeBridge() {
        if (closed || !::library.isInitialized) return
        closed = true
        qq.unobserve(update); library.release(); player.release()
    }
    override fun onDestroy() {
        closeBridge()
        if (active === this) active = null
        super.onDestroy()
    }
}
