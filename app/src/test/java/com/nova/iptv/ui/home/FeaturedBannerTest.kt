package com.nova.iptv.ui.home

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.components.TvActionButton
import com.nova.iptv.ui.TvAppFrame
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeaturedBannerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val films = (1..3).map {
        ContentEntity(
            id = it.toLong(), profileId = 1, kind = ContentKind.MOVIE,
            name = "Film $it", groupTitle = "Cinéma", logo = null,
            streamRef = "film:$it", sortIndex = it,
        )
    }

    @Before
    fun setUp() {
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
    }

    @Test
    fun rightFromDetailsCannotHandFocusToAnOffscreenPageOrStartPagerScrolling() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        var details: ContentEntity? = null
        compose.setContent {
            MaterialTheme {
                TvAppFrame("home", {}) { modifier ->
                    Column(modifier) {
                        FeaturedBanner(films, tv = true, onWatch = {}, onDetails = { details = it })
                    }
                }
            }
        }
        val detailsButton = compose.onNodeWithText("Détails")
        compose.onAllNodesWithText("Détails").assertCountEquals(1)
        compose.onAllNodesWithText("Regarder").assertCountEquals(1)
        detailsButton.performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(12) {
            detailsButton.performKeyInput { pressKey(Key.DirectionRight) }
            detailsButton.assertIsFocused()
            compose.onNodeWithText("1 / 3").assertIsDisplayed()
        }
        detailsButton.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(films[0], details) }
        detailsButton.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Regarder").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithContentDescription("Suggestion précédente").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNode(isFocused()).assert(hasContentDescription("Accueil"))
        compose.onNodeWithContentDescription("Suggestion suivante")
            .performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Suggestion suivante").performKeyInput { pressKey(Key.DirectionUp) }
        detailsButton.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        detailsButton.assertIsFocused()
    }

    @Test
    fun touchSwipesAndButtonsBrowseBothWaysAndOpenTheDisplayedTitle() {
        var watched: ContentEntity? = null
        var details: ContentEntity? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(720.dp)) {
                    FeaturedBanner(films, tv = false, onWatch = { watched = it }, onDetails = { details = it })
                }
            }
        }
        compose.onNodeWithTag("featured-banner").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithText("Film 2").assertIsDisplayed()
        compose.onNodeWithText("Regarder").performClick()
        compose.runOnIdle { assertEquals(films[1], watched) }
        compose.onNodeWithText("Détails").performClick()
        compose.runOnIdle { assertEquals(films[1], details) }
        compose.onNodeWithTag("featured-banner").performTouchInput { swipeRight() }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Suggestion précédente").performClick()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Suggestion suivante").performClick()
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun remoteCanBrowseWithoutLosingFocusAndStillReachBannerActions() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        compose.setContent {
            MaterialTheme {
                Row {
                    TvActionButton("Menu", {}, Modifier.width(100.dp))
                    Column(Modifier.width(720.dp)) {
                        FeaturedBanner(films, tv = true, onWatch = {}, onDetails = {})
                    }
                }
            }
        }
        val next = compose.onNodeWithContentDescription("Suggestion suivante")
        next.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        next.assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        next.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        next.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Détails").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Regarder").assertIsFocused()
        val previous = compose.onNodeWithContentDescription("Suggestion précédente")
        previous.performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        previous.assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Menu").assertIsFocused()
    }

    @Test
    fun catalogueRefreshCanReduceTheNumberOfSuggestionsWithoutLeavingAStalePage() {
        var items by mutableStateOf(films)
        var watched: ContentEntity? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(720.dp)) {
                    FeaturedBanner(items, tv = false, onWatch = { watched = it }, onDetails = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Suggestion précédente").performClick()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.runOnIdle { items = films.take(1) }
        compose.onNodeWithText("Film 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Suggestion précédente").assertDoesNotExist()
        compose.onNodeWithText("Regarder").performClick()
        compose.runOnIdle { assertEquals(films[0], watched) }
        compose.runOnIdle { items = emptyList() }
        compose.onNodeWithTag("featured-banner").assertDoesNotExist()
    }

    @Test
    fun rotationPausesOnRemoteFocusAndRestartsItsDelayAfterManualNavigation() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(720.dp)) {
                    FeaturedBanner(films, tv = true, onWatch = {}, onDetails = {})
                    TvActionButton("Hors du bandeau", {})
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(11_000)
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        val next = compose.onNodeWithContentDescription("Suggestion suivante")
        next.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(40_000)
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        next.performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithText("Hors du bandeau").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(11_000)
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
    }
}
