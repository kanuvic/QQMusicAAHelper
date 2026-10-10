package dev.qqmusic.aahelper

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.view.KeyEvent

/** Only official QQ Music owns the audio. All calls and observers run on the main looper. */
class QQMusicController private constructor(private val context: Context) {
    companion object {
        const val PACKAGE = "com.tencent.qqmusic"
        @Volatile private var instance: QQMusicController? = null
        fun get(context: Context): QQMusicController = instance ?: synchronized(this) {
            instance ?: QQMusicController(context.applicationContext).also { instance = it }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var startupRetryTask: Runnable? = null
    private val startupResumeRetry = StartupResumeRetry(
        schedule = { delay, check ->
            Runnable { check() }.also { startupRetryTask = it; handler.postDelayed(it, delay) }
        },
        cancelScheduled = { startupRetryTask?.let { handler.removeCallbacks(it) }; startupRetryTask = null },
        isPlaying = { refresh(); playback?.state == PlaybackState.STATE_PLAYING },
        sendPlay = { DebugLogger.log("Startup resume play attempt"); prepare(true) },
        finished = { playing ->
            handler.removeCallbacks(retry); deadline = 0; pendingPlay = false
            status = if (playing) "继续上次播放：QQ音乐已开始播放"
                else "Timeout: 继续上次播放已重试 10 次仍未确认播放，请在手机 QQ音乐中检查"
            DebugLogger.log("Startup resume finished: playing=$playing"); changed()
        }
    )
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, QQMusicSessionListener::class.java)
    private var listening = false
    private var remote: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var pendingPlay = false
    private var deadline = 0L
    private var launchAt = 0L
    private var entryMode: StartupMode? = null
    var playbackEntryFailed = false
        private set
    private var entryDeadline = 0L
    private val entryCheck = object : Runnable {
        override fun run() {
            val mode = entryMode ?: return
            refresh()
            if (playback?.state == PlaybackState.STATE_PLAYING) {
                entryMode = null; playbackEntryFailed = false; status = "${mode.title}：QQ音乐已开始播放"
                DebugLogger.log("Playback entry confirmed: ${mode.name}"); changed()
            } else if (SystemClock.elapsedRealtime() < entryDeadline) handler.postDelayed(this, 500)
            else {
                entryMode = null; playbackEntryFailed = true
                status = "Timeout: ${mode.title}未确认播放，请在手机 QQ音乐中检查登录、会员权限或播放提示"
                DebugLogger.log("Playback entry timeout: ${mode.name}"); changed()
            }
        }
    }
    var transitioning = false
        private set
    private var transitionTitle: String? = null
    private var transitionAt = 0L
    private var transitionLoadingSeen = false
    private var lastTitle: String? = null
    private val transitionTimeout = Runnable {
        transitioning = false; DebugLogger.log("Track transition timeout; exposing actual source state"); changed()
    }
    private val observers = linkedSetOf<() -> Unit>()
    var status = "QQMusic session missing"
        private set
    val metadata: MediaMetadata? get() = remote?.metadata
    val playback: PlaybackState? get() = remote?.playbackState
    val connected: Boolean get() = remote != null
    val access: Boolean get() = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)
    val installed: Boolean get() = try { context.packageManager.getPackageInfo(PACKAGE, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { select(it.orEmpty()) }
    private val retry = object : Runnable {
        override fun run() {
            refresh()
            if (remote != null) { deadline = 0; return }
            if (SystemClock.elapsedRealtime() < deadline) {
                DebugLogger.log("Retry scheduled"); handler.postDelayed(this, 2000)
            } else {
                deadline = 0; pendingPlay = false
                status = "Timeout: QQ音乐未创建媒体会话；可能需要解锁或在QQ音乐中准备播放队列"
                DebugLogger.log("Timeout"); changed()
            }
        }
    }
    fun observe(observer: () -> Unit) { observers.add(observer); observer() }
    fun unobserve(observer: () -> Unit) { observers.remove(observer) }
    private fun changed() {
        val state = playback
        if (transitioning && transitionLoadingSeen && state != null && state.lastPositionUpdateTime >= transitionAt &&
            (state.state == PlaybackState.STATE_PLAYING || state.state == PlaybackState.STATE_PAUSED) &&
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.let { it.isNotBlank() && it != transitionTitle } == true) {
            transitioning = false; handler.removeCallbacks(transitionTimeout)
        }
        observers.toList().forEach { it() }
    }
    private fun beginTrackTransition(oldTitle: String?) {
        transitionTitle = oldTitle
        transitionAt = SystemClock.elapsedRealtime(); transitioning = true; transitionLoadingSeen = false
        handler.removeCallbacks(transitionTimeout); handler.postDelayed(transitionTimeout, 10000)
    }
    fun start() {
        if (!access) { status = "Notification Access missing"; changed(); return }
        try {
            if (!listening) { manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler); listening = true }
            refresh()
        } catch (e: SecurityException) { status = "Notification Access unavailable"; DebugLogger.log("Session access SecurityException"); changed() }
    }
    fun accessDisconnected() {
        startupResumeRetry.cancel()
        if (listening) { manager.removeOnActiveSessionsChangedListener(sessionsChanged); listening = false }
        handler.removeCallbacks(retry); handler.removeCallbacks(entryCheck); entryMode = null
        deadline = 0; pendingPlay = false; detach()
        status = "Notification Access disconnected"; changed()
    }
    fun refresh() {
        DebugLogger.log("Searching QQMusic MediaSession")
        if (!access) { accessDisconnected(); return }
        try { select(manager.getActiveSessions(listener)) }
        catch (_: SecurityException) { accessDisconnected() }
    }
    private fun select(sessions: List<MediaController>) {
        val candidates = sessions.filter { it.packageName == PACKAGE }
        val found = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull { it.sessionToken == remote?.sessionToken } ?: candidates.firstOrNull()
        if (found == null) {
            if (remote != null) detach()
            status = "QQMusic session missing"; DebugLogger.log(status); changed(); return
        }
        if (found.sessionToken == remote?.sessionToken) return
        detach(); remote = found
        lastTitle = found.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val token = found.sessionToken
        callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (remote?.sessionToken != token) return
                val newTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                if (!newTitle.isNullOrBlank()) {
                    if (!lastTitle.isNullOrBlank() && lastTitle != newTitle && !transitioning) {
                        beginTrackTransition(lastTitle)
                        DebugLogger.log("External QQMusic track transition")
                    }
                    lastTitle = newTitle
                }
                DebugLogger.log("Metadata changed; Artwork changed"); changed()
            }
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (remote?.sessionToken != token) return
                if (transitioning && state?.state in listOf(PlaybackState.STATE_NONE,
                    PlaybackState.STATE_STOPPED, PlaybackState.STATE_BUFFERING)) transitionLoadingSeen = true
                DebugLogger.log("Playback state changed: ${state?.state}"); changed()
            }
            override fun onSessionDestroyed() {
                if (remote?.sessionToken != token) return
                DebugLogger.log("Session destroyed"); detach(); changed(); handler.post { refresh() }
            }
        }
        found.registerCallback(callback!!, handler)
        status = "Session attached"; DebugLogger.log("QQMusic session found; Session attached")
        // Platform bundles may expose a null key (observed with QQ on Android 16).
        // Diagnostic sorting must never crash session attachment.
        DebugLogger.log("Metadata fields: ${found.metadata?.keySet()?.filterNotNull()?.sorted()?.joinToString()}")
        handler.removeCallbacks(retry); deadline = 0; changed()
        if (pendingPlay) { pendingPlay = false; command("play") }
    }
    private fun detach() {
        transitioning = false; handler.removeCallbacks(transitionTimeout)
        lastTitle = null
        callback?.let { remote?.unregisterCallback(it) }; callback = null; remote = null
    }
    /** Shared by a real AA connection and the foreground phone preview button. */
    fun applyStartupMode(mode: StartupMode) {
        startupResumeRetry.cancel()
        handler.removeCallbacks(entryCheck); entryMode = null; playbackEntryFailed = false
        handler.removeCallbacks(retry); deadline = 0; pendingPlay = false
        if (remote != null && status.startsWith("Timeout")) status = "Session attached"
        when (mode) {
            StartupMode.RESUME -> {
                start()
                if (access && installed) startupResumeRetry.start()
                else if (!installed) { status = "QQ音乐未安装"; changed() }
            }
            StartupMode.INHERIT -> prepare()
            else -> openPlaybackEntry(mode)
        }
        changed()
    }
    /** Prepare does not start audio. A user's play request is remembered for up to 16 seconds. */
    fun prepare(play: Boolean = false) {
        start()
        if (!access) return
        if (remote != null) { if (play) command("play"); return }
        val newlyRequestedPlay = play && !pendingPlay
        pendingPlay = pendingPlay || play
        if (deadline != 0L) {
            if (newlyRequestedPlay) sendResumeMediaButton()
            return
        }
        if (!installed) { pendingPlay = false; status = "QQ音乐未安装"; changed(); return }
        DebugLogger.log("Attempting QQMusic launch")
        // Launcher is a documented API. No unverified URL scheme or private service names.
        // BAL restrictions can silently reject this even when startActivity returns normally.
        val now = SystemClock.elapsedRealtime()
        if (now - launchAt > 5000 || launchAt == 0L) {
            launchAt = now
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
                if (intent == null) { status = "QQ音乐没有可用的启动入口"; pendingPlay = false; changed(); return }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                DebugLogger.log("Launch intent result: submitted; awaiting session confirmation")
            } catch (e: RuntimeException) { DebugLogger.log("Launch intent result: ${e.javaClass.simpleName}") }
        }
        // Resolve the current exported MEDIA_BUTTON receiver rather than hardcoding a class.
        // This is scoped to QQ Music and only sent for an explicit user play request.
        if (play) sendResumeMediaButton()
        status = "Waiting for MediaSession"; DebugLogger.log(status); changed()
        deadline = SystemClock.elapsedRealtime() + 16000; handler.postDelayed(retry, 1000)
    }
    /** QQ URL entry; only QQ decides its queue and starts audio. */
    fun openPlaybackEntry(mode: StartupMode) {
        startupResumeRetry.cancel()
        val uri = mode.uri ?: return
        playbackEntryFailed = false
        start()
        if (!access) return
        command("pause")
        if (!installed) { playbackEntryFailed = true; status = "Timeout: QQ音乐未安装"; changed(); return }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                playbackEntryFailed = true; status = "Timeout: 当前 QQ音乐版本不支持${mode.title}入口"; changed(); return
            }
            context.startActivity(intent)
            entryMode = mode; entryDeadline = SystemClock.elapsedRealtime() + 16000
            status = "正在等待 QQ音乐${mode.title}"; DebugLogger.log("Playback entry submitted: ${mode.name}")
            handler.postDelayed(entryCheck, 750); changed()
        } catch (e: RuntimeException) {
            playbackEntryFailed = true
            status = "Timeout: 无法打开${mode.title}，请在手机上检查 QQ音乐"
            DebugLogger.log("Playback entry error: ${e.javaClass.simpleName}"); changed()
        }
    }
    private fun sendResumeMediaButton() {
        val probe = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(PACKAGE)
        val receivers = context.packageManager.queryBroadcastReceivers(probe, 0)
        val info = receivers.firstOrNull { it.activityInfo.exported && it.activityInfo.permission == null }?.activityInfo ?: return
        try {
            val now = SystemClock.uptimeMillis()
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                context.sendBroadcast(Intent(probe).setComponent(ComponentName(PACKAGE, info.name))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0)))
            }
            DebugLogger.log("QQMusic targeted media resume submitted")
        } catch (e: RuntimeException) { DebugLogger.log("Media resume: ${e.javaClass.simpleName}") }
    }
    fun command(action: String) {
        DebugLogger.log("AA $action requested")
        if (action == "pause") {
            startupResumeRetry.cancel()
            pendingPlay = false; handler.removeCallbacks(retry); deadline = 0
            entryMode = null; handler.removeCallbacks(entryCheck)
        }
        if (action == "play" && entryMode != null) return
        val controller = remote
        if (controller == null) { if (action == "play") prepare(true); return }
        if (status.startsWith("Timeout")) status = "Session attached"
        try {
            if (action == "next" || action == "previous") {
                beginTrackTransition(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
                changed()
            } else if (action == "pause") {
                transitioning = false; handler.removeCallbacks(transitionTimeout)
            }
            when (action) {
                "play" -> controller.transportControls.play()
                "pause" -> controller.transportControls.pause()
                "next" -> controller.transportControls.skipToNext()
                "previous" -> controller.transportControls.skipToPrevious()
            }
        } catch (e: RuntimeException) { DebugLogger.log("Transport error: ${e.javaClass.simpleName}"); detach(); refresh() }
    }
}
