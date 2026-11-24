package com.nova.iptv.ui.home

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.TvAppFrame
import com.nova.iptv.ui.theme.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvHomeDesignTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetPalette() {
        NovaColors.palette = NovaPalettes.of(NovaThemeId.NOVA_BLUE)
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-xhdpi")
    fun landscapePhoneShowsTheScaledTvHomeAndAcceptsTouch() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_NORMAL)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w780dp-h360dp-land-xhdpi")
        val film = ContentEntity(1, 1, ContentKind.MOVIE, "La traversée", "Aventure", null, "movie/1.mp4", sortIndex = 0)
        val state = HomeState(
            loading = false,
            profile = ProfileEntity(id = 1, name = "Mon cinéma", type = ProfileType.M3U, url = "https://example.com"),
            counts = mapOf(ContentKind.LIVE to 120),
            featured = listOf(film), newMovies = listOf(film),
        )
        var watched: ContentEntity? = null
        NovaTvInterface.current = TvInterface.PLUS
        NovaColors.palette = NovaPalettes.of(TvInterface.PLUS.defaultPalette)
        try {
            compose.setContent {
                NovaTheme {
                    Surface(color = NovaColors.Background) {
                        com.nova.iptv.ui.LandscapeTvScale {
                            TvAppFrame("home", {}) { modifier ->
                                androidx.compose.foundation.layout.Box(modifier) {
                                    TvHomeScreen(state, {}, {}, { _, _ -> }, { watched = it }, {}, { _, _ -> })
                                }
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("La traversée").assertIsDisplayed()
            compose.onNodeWithContentDescription("Accueil").assertIsDisplayed()
            compose.onNodeWithText("Regarder").performClick()
            compose.runOnIdle {
                assertEquals(film, watched)
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val directory = File("build/reports/tv-designs")
                check(directory.isDirectory || directory.mkdirs())
                File(directory, "phone-landscape-plus.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        } finally {
            NovaTvInterface.current = TvInterface.CLASSIC
        }
    }

    @Test
    fun interfacesRenderTheirPalettesAndHomePrioritiesAndCollectionsOpenTheCorrectCatalogue() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
        val film = ContentEntity(1, 1, ContentKind.MOVIE, "La traversée", "Aventure", null, "movie/1.mp4", sortIndex = 0)
        val state = HomeState(
            loading = false,
            profile = ProfileEntity(id = 1, name = "Mon cinéma", type = ProfileType.M3U, url = "https://example.com"),
            counts = mapOf(ContentKind.LIVE to 120),
            featured = listOf(film), newMovies = listOf(film),
            continueWatching = listOf(HistoryEntity(1, film.streamRef, ContentKind.MOVIE, "Mon film en cours", null, null, 30_000, 90_000, 1)),
        )
        var style by mutableStateOf(TvInterface.CINEMA)
        var collection: Pair<ContentKind, String>? = null
        var navigation: String? = null
        compose.setContent {
            NovaTheme {
                CompositionLocalProvider(LocalTvInterface provides style) {
                    Surface(color = NovaColors.Background) {
                        TvAppFrame("home", {}) { modifier ->
                            androidx.compose.foundation.layout.Box(modifier) {
                                key(style) {
                                    TvHomeScreen(state, { navigation = it }, {}, { _, _ -> }, {}, {}, { kind, group -> collection = kind to group })
                                }
                            }
                        }
                    }
                }
            }
        }
        for (next in TvInterface.entries.filter { it != TvInterface.CLASSIC }) {
            compose.runOnIdle {
                style = next
                NovaColors.palette = NovaPalettes.of(next.defaultPalette)
            }
            when (next) {
                TvInterface.ANTENNA, TvInterface.GUIDE -> compose.onNodeWithText("Votre télévision").assertIsDisplayed()
                TvInterface.DISCOVERY -> compose.onNodeWithText("Mon film en cours").assertIsDisplayed()
                else -> compose.onNodeWithText("La traversée").assertIsDisplayed()
            }
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val directory = File("build/reports/tv-designs")
                check(directory.isDirectory || directory.mkdirs())
                File(directory, "${next.name.lowercase()}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        val collectionButton = hasText("Aventure") and hasClickAction()
        compose.onNodeWithTag("tv-home").performScrollToNode(hasText("Ouvrir le direct"))
        compose.onNodeWithText("Ouvrir le direct").performClick()
        compose.runOnIdle { assertEquals("live", navigation) }
        for (next in listOf(TvInterface.STUDIOS, TvInterface.PLUS)) {
            compose.runOnIdle {
                style = next
                NovaColors.palette = NovaPalettes.of(next.defaultPalette)
                collection = null
            }
            compose.onNodeWithTag("tv-home").performScrollToNode(collectionButton)
            compose.onNode(collectionButton).performClick()
            compose.runOnIdle { assertEquals(ContentKind.MOVIE to "Aventure", collection) }
        }
    }
}
