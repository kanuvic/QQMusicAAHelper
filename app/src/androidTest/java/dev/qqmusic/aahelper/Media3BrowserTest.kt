package dev.qqmusic.aahelper

import android.content.ComponentName
import android.media.session.MediaSessionManager
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Media3BrowserTest {
    @Test fun nativeLibraryReflectsActualQQQueue() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val originalPage = MediaPageSettings.isEnabled(context)
        i.runOnMainSync { MediaPageSettings.setEnabled(context, true) }
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaBrowser>
        i.runOnMainSync {
            future = MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, QQMusicMediaService::class.java))).buildAsync()
        }
        val browser = future.get(10, TimeUnit.SECONDS)
        fun <T> onMain(block: () -> T): T {
            var value: T? = null
            i.runOnMainSync { value = block() }
            @Suppress("UNCHECKED_CAST") return value as T
        }
        try {
            val root = onMain { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, root.resultCode)
            assertEquals("ROOT", root.value!!.mediaId)
            val nodes = onMain { browser.getChildren("ROOT", 0, 10, null) }.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("CURRENT_QUEUE", "CONTROLS"), nodes.value!!.map { it.mediaId })
            val page = onMain { browser.getChildren("ROOT", 1, 1, null) }.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("CONTROLS"), page.value!!.map { it.mediaId })
            val suggestion = onMain { browser.getLibraryRoot(LibraryParams.Builder().setSuggested(true).build()) }.get(10, TimeUnit.SECONDS)
            assertEquals("CURRENT_QUEUE", suggestion.value!!.mediaId)
            val list = onMain { browser.getChildren("CURRENT_QUEUE", 0, 100, null) }.get(10, TimeUnit.SECONDS)
            val qq = context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(context, QQMusicSessionListener::class.java))
                .firstOrNull { it.packageName == "com.tencent.qqmusic" }
            assertNotNull("Real QQ session must exist", qq)
            assertEquals(qq!!.queue.orEmpty().map { "QUEUE_ITEM:${it.queueId}" }, list.value!!.map { it.mediaId })
            val controls = onMain { browser.getChildren("CONTROLS", 0, 10, null) }.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("resume"), controls.value!!.map { it.mediaId })
        } finally { i.runOnMainSync { browser.release(); MediaPageSettings.setEnabled(context, originalPage) } }
    }
}
