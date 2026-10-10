package dev.qqmusic.aahelper

import android.media.MediaMetadata as PM
import android.media.session.PlaybackState
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream

/** Standard QQ session adapter; no audio decoder and no invented QQ play queue. */
@UnstableApi
internal class QQSessionPlayer(private val context: android.content.Context, private val qq: QQMusicController) : SimpleBasePlayer(Looper.getMainLooper()) {
    fun sourceChanged() = invalidateState()
    fun currentItem(): MediaItem? {
        val m = qq.metadata ?: return null
        val title = m.getString(PM.METADATA_KEY_TITLE) ?: return null
        val md = MediaMetadata.Builder().setTitle(title).setArtist(m.getString(PM.METADATA_KEY_ARTIST))
            .setAlbumTitle(m.getString(PM.METADATA_KEY_ALBUM)).setIsBrowsable(false).setIsPlayable(true)
        val bitmap = m.getBitmap(PM.METADATA_KEY_ART) ?: m.getBitmap(PM.METADATA_KEY_ALBUM_ART)
        if (bitmap != null) {
            val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height))
            val art = android.graphics.Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true)
            val bytes = ByteArrayOutputStream().also { art.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            md.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder().setMediaId("now_playing").setMediaMetadata(md.build()).build()
    }
    override fun getState(): State {
        val s = qq.playback
        val item = currentItem()
        val commands = Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA, Player.COMMAND_RELEASE, Player.COMMAND_SET_MEDIA_ITEM)
        if (s != null && s.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) commands.add(Player.COMMAND_SEEK_TO_NEXT)
        if (s != null && s.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        val duration = qq.metadata?.getLong(PM.METADATA_KEY_DURATION)?.takeIf { it > 0 }
        val state = State.Builder().setAvailableCommands(commands.build())
            .setPlayWhenReady(s?.state == PlaybackState.STATE_PLAYING, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaybackState(if (item == null) Player.STATE_IDLE else if (s?.state == PlaybackState.STATE_BUFFERING) Player.STATE_BUFFERING else Player.STATE_READY)
        if (item != null) {
            // Player needs a current item. GET_TIMELINE remains unavailable, so this
            // single-item internal timeline is not advertised as a complete QQ queue.
            state.setPlaylist(listOf(MediaItemData.Builder("current").setMediaItem(item).setMediaMetadata(item.mediaMetadata)
                .setDurationUs(duration?.times(1000) ?: C.TIME_UNSET).setIsSeekable(false).build()))
                .setCurrentMediaItemIndex(0).setContentPositionMs {
                    val p = qq.playback
                    val elapsed = if (p?.state == PlaybackState.STATE_PLAYING)
                        ((SystemClock.elapsedRealtime() - p.lastPositionUpdateTime).coerceAtLeast(0) * p.playbackSpeed).toLong() else 0
                    ((p?.position ?: 0) + elapsed).coerceIn(0, duration ?: Long.MAX_VALUE)
                }
        }
        if (!qq.access || qq.playbackEntryFailed || qq.status.startsWith("Timeout")) state.setPlaybackState(Player.STATE_IDLE).setPlayerError(PlaybackException(
            if (!qq.access) "请先开启通知使用权" else qq.status, null, PlaybackException.ERROR_CODE_UNSPECIFIED))
        return state.build()
    }
    override fun getPlaceholderState(suggestedPlaceholderState: State): State = getState()
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!playWhenReady || !qq.playbackEntryFailed || StartupSettings.get(context).uri == null)
            qq.command(if (playWhenReady) "play" else "pause")
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> { qq.prepare(); return Futures.immediateVoidFuture() }
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleSetMediaItems(items: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val mode = StartupSettings.get(context)
        if (mode.uri != null && qq.playbackEntryFailed) qq.openPlaybackEntry(mode) else qq.prepare()
        return Futures.immediateVoidFuture()
    }
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) { Player.COMMAND_SEEK_TO_NEXT -> qq.command("next"); Player.COMMAND_SEEK_TO_PREVIOUS -> qq.command("previous") }
        return Futures.immediateVoidFuture()
    }
}
