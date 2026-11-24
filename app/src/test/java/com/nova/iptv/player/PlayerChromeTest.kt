package com.nova.iptv.player

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaPalettes
import com.nova.iptv.ui.theme.NovaTheme
import com.nova.iptv.ui.theme.NovaThemeId
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvInterface
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
@Config(sdk = [28, 34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerChromeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val originalPalette = NovaColors.palette

    @Before
    fun setUp() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
    }

    @After
    fun tearDown() {
        compose.runOnIdle { NovaColors.palette = originalPalette }
    }

    private fun drawWindow(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun assertReadable(bitmap: Bitmap, node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInWindow
        var dark = 0
        var white = 0
        var pixels = 0
        for (y in bounds.top.toInt() + 4 until bounds.bottom.toInt() - 4) {
            for (x in bounds.left.toInt() + 4 until bounds.right.toInt() - 4) {
                val color = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(color)
                val green = android.graphics.Color.green(color)
                val blue = android.graphics.Color.blue(color)
                if (maxOf(red, green, blue) < 120) dark++
                if (minOf(red, green, blue) > 220) white++
                pixels++
            }
        }
        assertTrue("The background must remain dark, not a bright fill", dark > pixels * 0.6)
        assertTrue("The icon/label must be drawn in a contrasting light colour", white > 10)
    }

    @Test
    fun playerRowsAdoptEachTvThemeWithoutLosingTheirDarkBackgroundOrRemoteFocus() {
        var style by mutableStateOf(TvInterface.PLUS)
        compose.setContent {
            val first = remember { FocusRequester() }
            NovaTheme {
                CompositionLocalProvider(LocalTvInterface provides style) {
                    Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
                        Row {
                            BarButton(Icons.Rounded.ClosedCaption, "Sous-titres", {}, Modifier.focusRequester(first))
                            BarButton(Icons.Rounded.Speed, "Vitesse", {})
                        }
                        Column(Modifier.width(400.dp).padding(top = 24.dp)) {
                            PanelRow("Piste disponible", false, {})
                            PanelRow("Piste active", true, {})
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        for (next in TvInterface.entries) {
            compose.runOnIdle {
                style = next
                NovaColors.palette = NovaPalettes.of(next.defaultPalette)
            }
            compose.onNodeWithContentDescription("Sous-titres").assertIsFocused()
            val row = compose.onNodeWithText("Piste disponible")
            val bounds = row.fetchSemanticsNode().boundsInWindow
            val bitmap = drawWindow()
            assertEquals(
                "The player must use ${next.label}'s actual card colour",
                NovaPalettes.of(next.defaultPalette).card.toArgb(),
                bitmap.getPixel(bounds.right.toInt() - 12, bounds.top.toInt() + 12),
            )
            assertReadable(bitmap, row)
            assertReadable(bitmap, compose.onNodeWithContentDescription("Sous-titres"))
            val directory = File("build/reports/tv-designs")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "${next.name.lowercase()}-player.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("Sous-titres").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Vitesse").assertIsFocused()
    }

    @Test
    fun playerControlsRemainReadableWhenFocusMoves() {
        var speedClicks = 0
        compose.setContent {
            val first = remember { FocusRequester() }
            NovaTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF101829)).padding(24.dp)) {
                    Row {
                        BarButton(Icons.Rounded.ClosedCaption, "Sous-titres", {}, Modifier.focusRequester(first))
                        BarButton(Icons.Rounded.Speed, "Vitesse", { speedClicks++ })
                        BarButton(Icons.Rounded.Settings, "Options", {})
                    }
                    Column(Modifier.width(400.dp).padding(top = 24.dp)) {
                        PanelRow("Désactivés", false, {})
                        PanelRow("Complets · Français", true, {})
                        PanelRow("Complets · Anglais", false, {})
                    }
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.onNodeWithContentDescription("Sous-titres").assertIsFocused()
        val controls = listOf("Sous-titres", "Vitesse", "Options").map { compose.onNodeWithContentDescription(it) }
        controls.forEach { it.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp) }
        var bitmap = drawWindow()
        controls.forEach { assertReadable(bitmap, it) }
        bitmap.recycle()

        controls[0].performKeyInput { pressKey(Key.DirectionRight) }
        controls[1].assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, speedClicks) }
        compose.onNodeWithText("Complets · Français")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Désactivés").assertIsFocused()
        compose.onNodeWithText("Désactivés").assertIsNotSelected()
        compose.onNodeWithText("Complets · Français").assertIsSelected().assertIsNotFocused()
        compose.onNodeWithContentDescription("Sélectionné").assertExists()

        // Playback chrome stays dark even when the application's light palette is selected.
        compose.runOnIdle { NovaColors.palette = NovaPalettes.of(NovaThemeId.PURE_LIGHT) }
        bitmap = drawWindow()
        controls.forEach { assertReadable(bitmap, it) }
        listOf("Désactivés", "Complets · Français", "Complets · Anglais").forEach {
            assertReadable(bitmap, compose.onNodeWithText(it))
        }
        val directory = File("build/reports")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "player-chrome.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
