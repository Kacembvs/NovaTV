package com.nova.iptv.ui.downloads

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.scheduler.Requirements
import com.nova.iptv.AppContainer
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.download.OfflineDownloads
import com.nova.iptv.data.download.OfflineStatus
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaTheme
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
// Downloads write their index and the cache index from several threads: use real SQLite locking.
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h860dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = MockWebServer()
    private lateinit var container: AppContainer

    @After
    fun tearDown() {
        container.downloads.manager.release()
        OfflineDownloads.releaseShared()
        container.appScope.cancel()
        container.db.close()
        server.shutdown()
    }

    @Test
    fun downloadedSeriesAreGroupedAndEveryItemOffersPlayAndDelete() {
        server.start(java.net.InetAddress.getLoopbackAddress(), 0)
        repeat(3) { server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(150_000) { i -> i.toByte() }))) }
        container = AppContainer(compose.activity.applicationContext)
        val downloads = container.downloads
        downloads.viaService = false
        downloads.manager.requirements = Requirements(0)
        downloads.manager.resumeDownloads()
        val profileId = runBlocking {
            container.db.profileDao().insert(ProfileEntity(name = "Salon", type = ProfileType.M3U, url = "https://example.com/list.m3u"))
                .also { container.profiles.setActive(it) }
        }
        val items = listOf(
            PlayableItem("http://127.0.0.1:${server.port}/film.mp4", "La traversée", ContentKind.MOVIE),
            PlayableItem("http://127.0.0.1:${server.port}/e1.mkv", "Ma série", ContentKind.EPISODE, subtitle = "S1 · É1 — Le départ", parentKey = "xseries:9"),
            PlayableItem("http://127.0.0.1:${server.port}/e2.mkv", "Ma série", ContentKind.EPISODE, subtitle = "S1 · É2 — L'arrivée", parentKey = "xseries:9"),
        )
        runBlocking { items.forEach { downloads.download(profileId, it) } }
        val deadline = System.currentTimeMillis() + 20_000
        while (downloads.items.value.count { it.status == OfflineStatus.COMPLETED } < 3) {
            check(System.currentTimeMillis() < deadline) { "Timed out: ${downloads.items.value}" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            downloads.refresh()
        }

        compose.setContent {
            NovaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Surface(color = NovaColors.Background) { DownloadsScreen(onBrowse = {}) }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("La traversée").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ma série").assertIsDisplayed()
        compose.onNodeWithText("2 épisode(s)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("S1 · É1 — Le départ").assertDoesNotExist()
        compose.onNodeWithText("Ma série").performClick()
        compose.onNodeWithText("S1 · É1 — Le départ").assertIsDisplayed()
        compose.onNodeWithText("S1 · É2 — L'arrivée").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lire S1 · É2 — L'arrivée").assertHasClickAction()
        compose.onNodeWithText("Téléchargements intelligents").assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/tv-designs")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "downloads-mobile.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onAllNodesWithContentDescription("Options de téléchargement")[0].performClick()
        compose.onNodeWithText("Supprimer le téléchargement").assertIsDisplayed().performClick()
        val removeDeadline = System.currentTimeMillis() + 10_000
        while (downloads.items.value.size > 2) {
            check(System.currentTimeMillis() < removeDeadline) { "Not removed: ${downloads.items.value}" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            downloads.refresh()
        }
    }
}
