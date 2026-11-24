package com.nova.iptv.ui.browse

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import com.nova.iptv.AppContainer
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.TvAppFrame
import com.nova.iptv.ui.theme.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvBrowseDesignTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private var profileId = 0L
    private var showContent by mutableStateOf(true)
    private var liveVm: BrowseViewModel? = null

    @Before
    fun setUp() = runBlocking {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
        container = AppContainer(compose.activity)
        profileId = container.db.profileDao().insert(ProfileEntity(name = "Ma source", type = ProfileType.M3U, url = "https://example.com"))
        container.profiles.setActive(profileId)
    }

    @After
    fun tearDown() {
        compose.runOnIdle { showContent = false }
        compose.waitForIdle()
        compose.runOnIdle {
            compose.activity.viewModelStore.clear()
            liveVm?.viewModelScope?.cancel()
        }
        container.appScope.cancel()
        container.db.close()
    }

    private fun item(name: String, kind: ContentKind, group: String, index: Int) =
        ContentEntity(profileId = profileId, kind = kind, name = name, groupTitle = group,
            logo = null, streamRef = "file:///missing/$index.mp4", sortIndex = index)

    private fun awaitContent(text: String) {
        try {
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), e)
        }
    }

    private fun drawScreen(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/tv-designs")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun filteredCollectionsKeepTheirContentsAcrossPosterLandscapeAndListLayouts() {
        runBlocking {
            container.db.contentDao().insertAll(listOf(
                item("Atlas", ContentKind.MOVIE, "Aventure", 0),
                item("Balise", ContentKind.MOVIE, "Aventure", 1),
                item("Crépuscule", ContentKind.MOVIE, "Drame", 2),
            ))
        }
        var style by mutableStateOf(TvInterface.STUDIOS)
        var opened: String? = null
        compose.setContent {
            if (showContent) NovaTheme {
                CompositionLocalProvider(LocalAppContainer provides container, LocalTvInterface provides style) {
                    Surface(color = NovaColors.Background) {
                        TvAppFrame("movies", {}) { modifier ->
                            Box(modifier) { CatalogScreen(ContentKind.MOVIE, {}, { opened = it }, {}, "Aventure") }
                        }
                    }
                }
            }
        }
        awaitContent("Atlas")
        for (next in listOf(TvInterface.STUDIOS, TvInterface.DISCOVERY, TvInterface.GUIDE)) {
            compose.runOnIdle { style = next }
            compose.onNodeWithText("Crépuscule").assertDoesNotExist()
            compose.onNodeWithText("Atlas").assertIsDisplayed()
            assertTrue("The title must fit below its image", compose.onNodeWithText("Atlas").fetchSemanticsNode().boundsInWindow.bottom <= 524f)
            val card = compose.onNode(hasClickAction() and hasText(if (next == TvInterface.GUIDE) "Atlas" else "A"))
            val bounds = card.fetchSemanticsNode().boundsInWindow
            when (next) {
                TvInterface.STUDIOS -> assertTrue(bounds.height > bounds.width)
                TvInterface.DISCOVERY -> assertTrue(bounds.width > bounds.height * 1.5f)
                else -> assertTrue(bounds.width > bounds.height * 2f)
            }
            card.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            if (next == TvInterface.GUIDE) {
                card.performKeyInput { pressKey(Key.DirectionRight) }
                val favorite = compose.onAllNodesWithContentDescription("Ajouter aux favoris")[0]
                val target = favorite.fetchSemanticsNode().boundsInWindow
                assertTrue("TV favorite controls need a usable target", target.width >= 48f && target.height >= 48f)
                favorite.assertIsFocused()
                    .performKeyInput { pressKey(Key.DirectionLeft) }
                card.assertIsFocused()
            } else {
                card.performKeyInput { pressKey(Key.DirectionUp) }
                compose.onNodeWithText("Tout").assertIsFocused()
                    .performKeyInput { pressKey(Key.DirectionDown) }
                card.assertIsFocused()
            }
            card.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle { assertEquals("file:///missing/0.mp4", opened); opened = null }
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            drawScreen("${next.name.lowercase()}-catalogue")
        }
    }

    @Test
    fun liveMosaicAndProgrammeListAllowUpDownNavigationWithoutOpeningTheKeyboard() {
        runBlocking {
            container.db.contentDao().insertAll((0..5).map { item("Canal $it", ContentKind.LIVE, "Découverte", it) })
        }
        val vm = BrowseViewModel(container, ContentKind.LIVE).also { liveVm = it }
        var style by mutableStateOf(TvInterface.CINEMA)
        compose.setContent {
            if (showContent) NovaTheme {
                CompositionLocalProvider(LocalAppContainer provides container, LocalTvInterface provides style) {
                    Surface(color = NovaColors.Background) {
                        TvAppFrame("live", {}) { modifier -> Box(modifier) { LiveTvLayout(vm, {}, {}) } }
                    }
                }
            }
        }
        awaitContent("Canal 0")
        for (next in listOf(TvInterface.CINEMA, TvInterface.ANTENNA)) {
            compose.runOnIdle { style = next }
            val first = compose.onNode(hasClickAction() and hasText("1 · Canal 0"))
            first.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
            first.assertIsNotFocused()
            compose.onNode(isFocused()).assert(hasText("Canal", substring = true))
                .performKeyInput { pressKey(Key.DirectionUp) }
            first.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithText("Tout").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            first.assertIsFocused()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            drawScreen("${next.name.lowercase()}-direct")
        }
    }
}
