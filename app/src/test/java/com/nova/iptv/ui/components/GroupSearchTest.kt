package com.nova.iptv.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h860dp-port-xhdpi")
class GroupSearchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun groupFilterIgnoresAccentsAndCase() {
        val entries = listOf(GroupChoice("a", "Cinéma | FR", 3), GroupChoice("b", "Sport", 1), GroupChoice("c", "CINEMA UK", 2))
        assertEquals(listOf("a", "c"), filterGroups(entries, "cinema").map { it.key })
        assertEquals(3, filterGroups(entries, "  ").size)
        assertEquals(emptyList<GroupChoice>(), filterGroups(entries, "docu"))
    }

    @Test
    fun searchingAGroupSelectsIt() {
        val groups = (1..40).map { GroupCount("Groupe $it", it) } + GroupCount("Documentaires", 7)
        var selected: String? = null
        compose.setContent {
            NovaTheme {
                var current by remember { mutableStateOf<String?>(null) }
                GroupChips(groups, current, favoritesKey = "fav", onSelect = { current = it; selected = it })
            }
        }
        compose.onNodeWithContentDescription("Rechercher un groupe").performClick()
        compose.onNodeWithText("Rechercher un groupe").performTextInput("documentaire")
        compose.onAllNodesWithText("Groupe 1").assertCountEquals(0)
        compose.onNode(hasText("Documentaires") and hasClickAction()).performClick()
        assertEquals("Documentaires", selected)
        compose.onAllNodesWithText("Rechercher un groupe").assertCountEquals(0)
        compose.onNodeWithText("Documentaires  7").assertIsDisplayed()
    }
}
