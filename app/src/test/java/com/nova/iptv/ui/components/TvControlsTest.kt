package com.nova.iptv.ui.components

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvControlsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun drawWindow(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    @Test
    fun focusMovesIndependentlyOfSelectedOption() {
        compose.setContent {
            val first = remember { FocusRequester() }
            MaterialTheme {
                Column {
                    TvActionButton("Actif", {}, selected = true, modifier = Modifier.focusRequester(first))
                    TvActionButton("Changer le code", {})
                    TvActionButton("Supprimer le code", {}, destructive = true)
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        val change = compose.onNodeWithText("Changer le code")
        val before = drawWindow()
        compose.onNodeWithText("Actif").assertIsFocused().assertIsSelected()
            .performKeyInput { pressKey(Key.DirectionDown) }
        change.assertIsFocused().assertIsNotSelected()
        compose.onNodeWithText("Actif").assertIsSelected().assertIsNotFocused()
        val after = drawWindow()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
        }
        assertTrue("Focus must be drawn, not just reported in semantics", changed > 100)
        before.recycle()
        after.recycle()
        change.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Supprimer le code").assertIsFocused()
    }

    @Test
    fun posterAndFavoriteAreReachableWithTheRemote() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        compose.setContent {
            val first = remember { FocusRequester() }
            MaterialTheme {
                Column(Modifier.width(120.dp)) {
                    PosterCard("Film", null, {}, isFavorite = false, onToggleFavorite = {},
                        focusModifier = Modifier.testTag("poster").focusRequester(first))
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.onNodeWithTag("poster").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Ajouter aux favoris").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("poster").assertIsFocused()
    }

    @Test
    fun toggleRowIsASingleRemoteTargetAndOkChangesItsValue() {
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            val first = remember { FocusRequester() }
            MaterialTheme {
                Column {
                    TvSettingToggle("Protéger les réglages", null, checked, { checked = it }, Modifier.focusRequester(first))
                    TvActionButton("Catégories verrouillées", {})
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.onNodeWithText("Protéger les réglages").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Activé").assertExists()
        compose.onNodeWithText("Protéger les réglages").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Catégories verrouillées").assertIsFocused()
    }
}
