package com.nova.iptv.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nova.iptv.AppContainer
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.theme.NovaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h860dp-port-xhdpi")
class PortraitBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var profileId = 0L
    private val played = mutableListOf<Pair<List<PlayableItem>, Int>>()

    @Before
    fun setUp() = runBlocking {
        container = AppContainer(compose.activity)
        profileId = container.db.profileDao().insert(ProfileEntity(name = "Source", type = ProfileType.M3U, url = "https://example.com"))
        val dao = container.db.contentDao()
        dao.insertAll(
            listOf(
                item("TF1", ContentKind.LIVE, "France", 0),
                item("France 2", ContentKind.LIVE, "France", 1),
                item("BBC One", ContentKind.LIVE, "UK", 2),
                item("Sky News", ContentKind.LIVE, "UK", 3),
                item("Dune", ContentKind.MOVIE, "Science-fiction", 4),
                item("Alien", ContentKind.MOVIE, "Science-fiction", 5),
            )
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        container.appScope.cancel()
        container.db.close()
    }

    private fun item(name: String, kind: ContentKind, group: String, index: Int) =
        ContentEntity(profileId = profileId, kind = kind, name = name, groupTitle = group, logo = null,
            streamRef = "http://srv/$index.ts", sortIndex = index)

    private fun show(kind: ContentKind, current: PlayableItem, queue: List<PlayableItem>): PortraitBrowserModel {
        val model = PortraitBrowserModel(container, scope, profileId, kind) { list, index -> played += list to index }
        compose.setContent { NovaTheme { PortraitBrowser(model) } }
        compose.runOnIdle { model.setCurrent(queue, current) }
        return model
    }

    @Test
    fun liveOpensOnTheGroupOfTheChannelAndZapsWithinTheChosenGroup() {
        val bbc = item("BBC One", ContentKind.LIVE, "UK", 2).toPlayable()
        show(ContentKind.LIVE, bbc, listOf(bbc))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sky News").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("TF1").assertCountEquals(0)

        compose.onNodeWithText("France  2").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("France 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("France 2").performClick()
        compose.runOnIdle {
            val (list, index) = played.single()
            assertEquals(listOf("TF1", "France 2"), list.map { it.title })
            assertEquals(1, index)
        }
    }

    @Test
    fun filmsPlayOneAtATimeFromTheirGroup() {
        val dune = item("Dune", ContentKind.MOVIE, "Science-fiction", 4).toPlayable()
        show(ContentKind.MOVIE, dune, listOf(dune))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alien").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Alien").performClick()
        compose.runOnIdle {
            val (list, index) = played.single()
            assertEquals(listOf("Alien"), list.map { it.title })
            assertEquals(0, index)
        }
    }
}
