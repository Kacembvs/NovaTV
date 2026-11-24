package com.nova.iptv.ui.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.ExperimentalTestApi
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.ui.components.PinDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ParentalNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pinConfirmationCanReachValidationWithoutExtraText() {
        var submitted: String? = null
        compose.setContent {
            MaterialTheme {
                PinDialog("Nouveau code", null, confirm = true, onDismiss = {}) {
                    submitted = it
                    null
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("2468")
        compose.onAllNodes(hasSetTextAction())[0].performImeAction()
        compose.onAllNodes(hasSetTextAction())[1].assertIsFocused().performTextInput("2468")
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        compose.onNodeWithText("Valider").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("2468", submitted) }
    }

    @Test
    fun categoriesCanBeSelectedAndDialogClosedWithRemote() {
        var dismissed = false
        var selected = emptySet<String>()
        compose.setContent {
            var locked by remember { mutableStateOf(emptySet<String>()) }
            MaterialTheme {
                ParentalGroupDialog(
                    groups = listOf(GroupCount("Sport", 10), GroupCount("Cinéma", 20)),
                    locked = locked,
                    tv = true,
                    onSetLocked = { name, value ->
                        locked = if (value) locked + name else locked - name
                        selected = locked
                    },
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNodeWithText("Sport").assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(setOf("Sport"), selected) }
        compose.onNodeWithText("Sport").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Terminé").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
