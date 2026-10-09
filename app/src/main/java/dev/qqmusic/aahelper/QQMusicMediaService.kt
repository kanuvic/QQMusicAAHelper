package dev.qqmusic.aahelper

import android.content.pm.PackageManager
import android.os.Bundle
import android.media.MediaMetadata
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

class QQMusicMediaService : MediaBrowserServiceCompat() {
    private lateinit var session: MediaSessionCompat
    private lateinit var qq: QQMusicController
    private var lastLoggedState = -1
    private var startupMode = StartupMode.RESUME
    private var connectedAt = 0L
    private val update: () -> Unit = { mirror() }
    override fun onCreate() {
        super.onCreate(); DebugLogger.log("Service created")
        qq = QQMusicController.get(this)
        session = MediaSessionCompat(this, "QQMusicAA")
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // Failed URL selection must not silently resume the previous queue
                // when AA retries its automatic command. A browser click can retry.
                if (startupMode.uri != null && qq.playbackEntryFailed) return
                qq.command("play")
            }
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                if (mediaId == "resume") {
                    if (startupMode.uri != null && qq.playbackEntryFailed) qq.openPlaybackEntry(startupMode)
                    else qq.command("play")
                }
            }
            override fun onPrepare() { qq.prepare() }
            override fun onPause() { qq.command("pause") }
            override fun onSkipToNext() { qq.command("next") }
            override fun onSkipToPrevious() { qq.command("previous") }
        })
        session.isActive = true; sessionToken = session.sessionToken
        qq.observe(update); qq.start()
    }
    private fun mirror() {
        val meta = qq.metadata
        val awaitingTrackMetadata = qq.transitioning && meta?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        if (awaitingTrackMetadata) { /* Previous source metadata stays visible while an explicit skip loads. */ }
        else if (meta == null) session.setMetadata(null)
        else {
            val builder = MediaMetadataCompat.Builder()
            for (key in listOf(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
                meta.getString(key)?.let { builder.putString(key, it) }
            }
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, meta.getLong(MediaMetadata.METADATA_KEY_DURATION))
            val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            art?.let {
                // Cap binder size while keeping artwork supplied by the official player.
                val scaled = if (it.width > 512 || it.height > 512) {
                    val ratio = 512f / maxOf(it.width, it.height)
                    android.graphics.Bitmap.createScaledBitmap(it, maxOf(1, (it.width * ratio).toInt()), maxOf(1, (it.height * ratio).toInt()), true)
                } else it
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, scaled)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, scaled)
            }
            session.setMetadata(builder.build())
        }
        val state = qq.playback
        // QQ reports STOPPED/NONE during an explicit skip. The bridge is waiting
        // for that requested track, so BUFFERING is truthful; never invent PLAYING.
        // Destroyed sessions and transitions beyond 10 seconds expose real state.
        // Platform and compat playback state numeric values are defined identically.
        val displayState = if (qq.transitioning && (state?.state == PlaybackStateCompat.STATE_NONE ||
            state?.state == PlaybackStateCompat.STATE_STOPPED)) PlaybackStateCompat.STATE_BUFFERING
        else state?.state ?: PlaybackStateCompat.STATE_NONE
        if (displayState != lastLoggedState) {
            lastLoggedState = displayState
            DebugLogger.log("Bridge state: source=${state?.state} display=$displayState transition=${qq.transitioning} titlePresent=${!meta?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()}")
        }
        var actions = PlaybackStateCompat.ACTION_PLAY
        if (state != null) {
            actions = 0L
            for (supported in listOf(PlaybackStateCompat.ACTION_PLAY, PlaybackStateCompat.ACTION_PAUSE,
                PlaybackStateCompat.ACTION_PLAY_PAUSE, PlaybackStateCompat.ACTION_SKIP_TO_NEXT, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
                if (state.actions and supported != 0L) actions = actions or supported
            }
        }
        @android.annotation.SuppressLint("WrongConstant") // Platform/compat state values match; see above.
        val builder = PlaybackStateCompat.Builder().setActions(actions or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
            .setState(displayState, state?.position ?: 0,
                state?.playbackSpeed ?: 0f, state?.lastPositionUpdateTime ?: android.os.SystemClock.elapsedRealtime())
        if (qq.status.startsWith("Timeout") || !qq.access) {
            builder.setState(PlaybackStateCompat.STATE_ERROR, 0, 0f)
                .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, if (!qq.access) "请在手机设置中开启QQ音乐AA助手的通知使用权" else qq.status)
        }
        session.setPlaybackState(builder.build())
        // The single resume entry is static. Metadata/state callbacks update the
        // MediaSession; refreshing browser children can disturb AA navigation.
    }
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (packageManager.getPackagesForUid(clientUid)?.contains(clientPackageName) != true) return null
        val ownUid = applicationInfo.uid
        if (clientUid != ownUid && clientPackageName != "com.google.android.projection.gearhead" &&
            packageManager.checkSignatures(clientUid, android.os.Process.SYSTEM_UID) != PackageManager.SIGNATURE_MATCH) return null
        if (clientPackageName == "com.google.android.projection.gearhead") {
            val now = android.os.SystemClock.elapsedRealtime()
            // AA can request the root multiple times during one startup.
            if (connectedAt == 0L || now - connectedAt > 5000) {
                connectedAt = now
                startupMode = StartupSettings.get(this)
                DebugLogger.log("Android Auto connected; startup=${startupMode.name}")
                when (startupMode) {
                    StartupMode.RESUME -> qq.prepare(true)
                    StartupMode.INHERIT -> qq.prepare()
                    else -> qq.openPlaybackEntry(startupMode)
                }
                mirror()
            }
        }
        return BrowserRoot("root", null)
    }
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mode = startupMode
        val description = MediaDescriptionCompat.Builder().setMediaId("resume")
            .setTitle(if (mode == StartupMode.INHERIT) "继续上次播放" else mode.title)
            .setSubtitle(if (mode == StartupMode.INHERIT) "继续 QQ音乐上次的播放队列" else mode.description).build()
        result.sendResult(if (parentId == "root") mutableListOf(MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)) else mutableListOf())
    }
    override fun onDestroy() { qq.unobserve(update); session.release(); DebugLogger.log("Service destroyed"); super.onDestroy() }
}
