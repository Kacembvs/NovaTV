package com.nova.iptv.ui

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nova.iptv.ui.components.TvActionButton
import com.nova.iptv.ui.components.PosterCard
import com.nova.iptv.ui.theme.*
import org.junit.Assert.assertTrue
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
class TvDesignNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
    }

    @Test
    fun landscapePhoneScalesTheTvFrameToTheTvReferenceHeight() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_NORMAL)
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.width(780.dp).height(360.dp)) {
                LandscapeTvScale {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        androidx.compose.material3.Text("hauteur ${maxHeight.value.toInt()} largeur ${maxWidth.value.toInt()}")
                    }
                }
            }
        }
        compose.onNodeWithText("hauteur 480 largeur 1040").assertExists()
    }

    @Test
    fun landscapePhoneUsesTheTvStyleWhilePortraitKeepsMobilePosterGeometry() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_NORMAL)
        NovaTvInterface.current = TvInterface.ANTENNA
        var portrait by mutableStateOf(false)
        try {
            compose.setContent {
                val base = androidx.compose.ui.platform.LocalConfiguration.current
                val configuration = Configuration(base).apply {
                    orientation = if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                }
                CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                    NovaTheme {
                        PosterCard("Mon film", null, {}, Modifier.width(120.dp), focusModifier = Modifier.testTag("poster"))
                    }
                }
            }
            var bounds = compose.onNodeWithTag("poster").fetchSemanticsNode().boundsInWindow
            assertEquals("Landscape phones show the TV interface", 16f / 9f, bounds.width / bounds.height, 0.15f)
            compose.runOnIdle { portrait = true }
            bounds = compose.onNodeWithTag("poster").fetchSemanticsNode().boundsInWindow
            assertEquals(2f / 3f, bounds.width / bounds.height, 0.01f)
        } finally {
            NovaTvInterface.current = TvInterface.CLASSIC
        }
    }

    @Test
    fun changingNavigationKeepsContentStateAndEveryStyleCanEnterItsContentWithTheRemote() {
        var style by mutableStateOf(TvInterface.CLASSIC)
        compose.setContent {
            NovaTheme {
                CompositionLocalProvider(LocalTvInterface provides style) {
                    TvAppFrame("profiles", {}) { modifier ->
                        var count by remember { mutableIntStateOf(0) }
                        Column(modifier) { TvActionButton("Contenu $count", { count++ }) }
                    }
                }
            }
        }
        compose.onNodeWithText("Contenu 0").performClick()
        for (nextStyle in TvInterface.entries) {
            compose.runOnIdle { style = nextStyle }
            compose.onNodeWithText("Contenu 1").assertExists()
            val settings = compose.onNodeWithContentDescription("Réglages")
            settings.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            val bounds = settings.fetchSemanticsNode().boundsInWindow
            if (nextStyle.navigation == TvNavigation.TOP) {
                assertTrue("Top navigation must actually be above the content", bounds.bottom <= 80f)
            } else {
                assertTrue("Side navigation must actually stay in its reserved column",
                    bounds.right <= if (nextStyle.navigation == TvNavigation.RAIL) 104f else 184f)
            }
            settings.performKeyInput {
                pressKey(if (nextStyle.navigation == TvNavigation.TOP) Key.DirectionDown else Key.DirectionRight)
            }
            compose.onNodeWithText("Contenu 1").assertIsFocused()
        }
    }
}
