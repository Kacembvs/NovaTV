package com.nova.iptv.ui.profiles

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.view.Display
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nova.iptv.ui.components.TvActionButton
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileTextFieldTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        shadowOf(compose.activity.getSystemService(UiModeManager::class.java))
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
    }

    private fun keyboardVisible() =
        shadowOf(compose.activity.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    @Test
    fun arrowsPassEveryFieldIncludingEmptyGuideWithoutOpeningKeyboard() {
        val labels = listOf("Nom", "Serveur", "Utilisateur", "Mot de passe", "Guide TV")
        compose.setContent {
            val first = remember { FocusRequester() }
            MaterialTheme {
                Column(Modifier.width(450.dp)) {
                    labels.forEachIndexed { index, label ->
                        ProfileTextField(
                            value = if (index == 3) "secret" else "",
                            onValueChange = {},
                            label = label,
                            visualTransformation = if (index == 3) PasswordVisualTransformation()
                                else androidx.compose.ui.text.input.VisualTransformation.None,
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    TvActionButton("Enregistrer", {})
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        labels.forEach { label ->
            compose.onNodeWithText(label, substring = true).assertIsFocused()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.runOnIdle { assertFalse("Arrow navigation must not open the keyboard", keyboardVisible()) }
            compose.onNodeWithText(label, substring = true).performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithText("Enregistrer").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Guide TV", substring = true).assertIsFocused()
        compose.onNodeWithText("secret", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptyOptionalGuideCanFinishEditingAndReachSave() {
        compose.setContent {
            val first = remember { FocusRequester() }
            val save = remember { FocusRequester() }
            MaterialTheme {
                Column(Modifier.width(450.dp)) {
                    ProfileTextField("", {}, "Guide TV", Modifier.focusRequester(first), nextFocus = save)
                    TvActionButton("Enregistrer", {}, Modifier.focusRequester(save))
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.onNodeWithText("Guide TV", substring = true).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Enregistrer").assertIsFocused()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { assertFalse(keyboardVisible()) }
    }

    @Test
    fun onlyOkStartsInputAndDoneRestoresNavigation() {
        compose.setContent {
            val first = remember { FocusRequester() }
            var value by remember { mutableStateOf("") }
            MaterialTheme {
                Column(Modifier.width(450.dp)) {
                    ProfileTextField(value, { value = it }, "Nom", Modifier.focusRequester(first))
                    TvActionButton("Enregistrer", {})
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.runOnIdle { assertFalse(keyboardVisible()) }
        compose.onNodeWithText("Nom", substring = true).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.runOnIdle { assertTrue("OK must open the input method", keyboardVisible()) }
        compose.onNode(hasSetTextAction()).performTextInput("Salon")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Nom\nSalon").assertIsFocused()
        compose.runOnIdle { assertFalse(keyboardVisible()) }
        compose.onNodeWithText("Nom\nSalon").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Enregistrer").assertIsFocused()
        compose.onNodeWithText("Enregistrer").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Nom\nSalon").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Nom\nSalon").assertIsFocused()
        compose.runOnIdle { assertFalse(keyboardVisible()) }
    }
}
