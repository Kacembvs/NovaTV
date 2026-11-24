package com.nova.iptv.data.download

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.test.core.app.ApplicationProvider
import com.nova.iptv.AppContainer
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.downloads.formatBytes
import com.nova.iptv.ui.downloads.groupDownloads
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
// Downloads write their index and the cache index from several threads: use real SQLite locking.
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class OfflineDownloadsTest {
    private val server = MockWebServer()
    private lateinit var container: AppContainer
    private lateinit var downloads: OfflineDownloads
    private val movie = Random(42).nextBytes(300_000)

    @Before
    fun setUp() {
        server.start(java.net.InetAddress.getLoopbackAddress(), 0)
        val context = ApplicationProvider.getApplicationContext<Context>()
        container = AppContainer(context)
        downloads = container.downloads
        downloads.viaService = false
        downloads.manager.requirements = Requirements(0)
        downloads.manager.resumeDownloads()
    }

    @After
    fun tearDown() {
        downloads.manager.release()
        OfflineDownloads.releaseShared()
        container.appScope.cancel()
        container.db.close()
        server.shutdown()
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; items = ${downloads.items.value}" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            downloads.refresh()
        }
    }

    @Test
    fun downloadedMovieIsReadBackFromDiskWithoutTheNetworkAndCanBeDeleted() {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(movie)))
        val url = "http://127.0.0.1:${server.port}/vod/film.mp4"
        val profileId = runBlocking {
            container.db.profileDao().insert(ProfileEntity(name = "Test", type = ProfileType.M3U, url = "https://example.com/list.m3u"))
        }
        val item = PlayableItem(url, "La traversée", ContentKind.MOVIE, logo = "https://example.com/poster.jpg")

        runBlocking { downloads.download(profileId, item) }
        waitFor { downloads.item(profileId, url)?.status == OfflineStatus.COMPLETED }

        val saved = downloads.item(profileId, url)!!
        assertEquals("La traversée", saved.meta.title)
        assertEquals(movie.size.toLong(), saved.bytes)
        assertEquals(1, server.requestCount)
        val request = downloads.completedRequests(profileId, listOf(url, "absent"))[url]
        assertNotNull("Only completed items are offered to the player", request)
        assertEquals(setOf(url), downloads.completedRequests(profileId, listOf(url, "absent")).keys)

        // The player's data source must read the saved file, not the server.
        val source = downloads.playbackDataSourceFactory(ApplicationProvider.getApplicationContext()).createDataSource()
        val mediaItem = request!!.toMediaItem()
        source.open(DataSpec.Builder().setUri(mediaItem.localConfiguration!!.uri).setKey(mediaItem.localConfiguration!!.customCacheKey).build())
        val read = ByteArrayOutputStream()
        val buffer = ByteArray(16_384)
        while (true) {
            val count = source.read(buffer, 0, buffer.size)
            if (count < 0) break
            read.write(buffer, 0, count)
        }
        source.close()
        assertArrayEquals(movie, read.toByteArray())
        assertEquals("Offline playback must not contact the server", 1, server.requestCount)

        downloads.remove(saved.id)
        waitFor { downloads.item(profileId, url) == null }
        assertEquals(0L, downloads.storage().usedBytes)
    }

    @Test
    fun pausedDownloadReportsItsStateAndResumes() {
        server.enqueue(MockResponse().setBody(Buffer().write(movie)).throttleBody(16_384, 50, java.util.concurrent.TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody(Buffer().write(movie)))
        val url = "http://127.0.0.1:${server.port}/series/12.mkv"
        val profileId = runBlocking {
            container.db.profileDao().insert(ProfileEntity(name = "Test", type = ProfileType.M3U, url = "https://example.com/list.m3u"))
        }
        val episode = PlayableItem(url, "Ma série", ContentKind.EPISODE, subtitle = "S2 · É5 — Le retour", parentKey = "xseries:9")
        runBlocking { downloads.download(profileId, episode) }
        waitFor { downloads.item(profileId, url)?.status == OfflineStatus.DOWNLOADING }
        val id = downloads.item(profileId, url)!!.id
        downloads.pause(id)
        waitFor { downloads.item(profileId, url)?.status == OfflineStatus.PAUSED }
        downloads.resume(id)
        waitFor { downloads.item(profileId, url)?.status == OfflineStatus.COMPLETED }
        val meta = downloads.item(profileId, url)!!.meta
        assertEquals(2, meta.season)
        assertEquals(5, meta.episode)
        assertEquals("xseries:9", meta.parentKey)
    }

    @Test
    fun episodesAreGroupedUnderTheirSeriesInEpisodeOrder() {
        fun item(ref: String, kind: ContentKind, parent: String?, season: Int?, episode: Int?, at: Long) = OfflineItem(
            DownloadMeta(1, ref, if (parent == null) "Film $ref" else "Ma série", kind, parentKey = parent, season = season, episode = episode, subtitle = "S$season · É$episode"),
            OfflineStatus.COMPLETED, 100f, 1_000_000, at,
        )
        val groups = groupDownloads(listOf(
            item("e3", ContentKind.EPISODE, "xseries:9", 1, 3, 10),
            item("m1", ContentKind.MOVIE, null, null, null, 5),
            item("e1", ContentKind.EPISODE, "xseries:9", 1, 1, 30),
            item("e2b", ContentKind.EPISODE, "xseries:9", 2, 1, 20),
        ))
        assertEquals(listOf("xseries:9", "1|m1"), groups.map { it.key })
        assertEquals(listOf("e1", "e3", "e2b"), groups.first().items.map { it.meta.ref })
        assertEquals("3 Mo", formatBytes(3_000_000))
        assertEquals("1,4 Go", formatBytes(1_500_000_000))
    }

    @Test
    fun metadataSurvivesThePersistentIndex() {
        val meta = DownloadMeta(3, "series/1.mkv", "Ma série", ContentKind.EPISODE, "logo", "S1 · É2", "xseries:1", 1, 2)
        assertEquals(meta, DownloadMeta.fromBytes(meta.toBytes()))
        assertNull(DownloadMeta.fromBytes("pas du json".toByteArray()))
    }
}
