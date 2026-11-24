package com.nova.iptv.ui.profiles

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.view.Display
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nova.iptv.AppContainer
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.theme.NovaTheme
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvInterface
import com.nova.iptv.ui.theme.NovaThemeId
import com.nova.iptv.data.ThemeSettings
import com.nova.iptv.ui.TvAppFrame
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.After
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
class TvSettingsNavigationTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var vm: ProfilesViewModel
    private val showContent = mutableStateOf(true)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UiModeManager::class.java)).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, "w960dp-h540dp-land-mdpi")
        container = AppContainer(context)
        container.parental.removePin()
        val id = container.db.profileDao().insert(ProfileEntity(name = "Test", type = ProfileType.M3U, url = "https://example.com"))
        container.profiles.setActive(id)
        container.parental.setPin("2468")
        vm = ProfilesViewModel(container)
    }

    @After
    fun tearDown() {
        compose.runOnIdle { showContent.value = false }
        compose.waitForIdle()
        vm.viewModelScope.cancel()
        container.appScope.cancel()
        container.db.close()
    }

    @Test
    fun horizontalSettingsReachTheInterfacePickerAndPersistItsChoice() {
        runBlocking {
            container.settings.setTvInterface(TvInterface.STUDIOS)
            container.settings.setTheme(NovaThemeId.PURE_LIGHT)
        }
        compose.setContent {
            if (showContent.value) NovaTheme {
                val theme by container.settings.theme.collectAsState(ThemeSettings(tvInterface = TvInterface.STUDIOS))
                CompositionLocalProvider(LocalTvInterface provides theme.tvInterface) {
                    TvAppFrame("profiles", {}) { modifier -> Box(modifier) { TvSettingsScreen(vm, {}, {}) } }
                }
            }
        }
        compose.onNodeWithText("Ajouter un profil").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(hasText("Profils") and hasClickAction()).assertIsFocused().performKeyInput {
            repeat(4) { pressKey(Key.DirectionRight) }
        }
        compose.onNode(hasText("Apparence") and hasClickAction()).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("NOVA Classique", substring = true).assertIsFocused()
        repeat(6) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithText("NOVA+\n", substring = true).assertIsFocused().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.theme.value.tvInterface == TvInterface.PLUS
        }
        assertEquals(NovaThemeId.MONOCHROME, runBlocking { container.settings.theme.first().theme })
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("NOVA Guide", substring = true).assertIsFocused().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            vm.theme.value.tvInterface == TvInterface.GUIDE
        }
        assertEquals(TvInterface.GUIDE, runBlocking { container.settings.theme.first().tvInterface })
        assertEquals(NovaThemeId.NOVA_BLUE, runBlocking { container.settings.theme.first().theme })
        compose.onNodeWithText("NOVA Guide", substring = true).assertIsSelected().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNode(hasText("Apparence") and hasClickAction()).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("NOVA Classique", substring = true).assertIsFocused()
    }

    @Test
    fun compactMobileSettingsUseTheSameSectionsByTouchWithoutForcingFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UiModeManager::class.java)).setCurrentModeType(Configuration.UI_MODE_TYPE_NORMAL)
        compose.setContent { if (showContent.value) NovaTheme { TvSettingsScreen(vm, {}, {}, compact = true) } }
        compose.onNodeWithText("Ajouter un profil").assertIsDisplayed().assertIsNotFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(0)
        compose.onNode(hasText("Lecture") and hasClickAction()).performClick()
        compose.onNodeWithText("Épisode suivant automatique").assertIsDisplayed()
        compose.onNode(hasText("Contrôle parental") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Protéger les réglages").assertExists()
        compose.onNode(hasText("Apparence") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Interface TV et paysage", substring = true).assertIsDisplayed()
    }

    @Test
    fun fourDirectionsReachAllParentalActionsAndReturnToSections() {
        compose.setContent { if (showContent.value) NovaTheme { TvSettingsScreen(vm, {}, {}) } }
        compose.onNodeWithText("Ajouter un profil").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNode(hasText("Profils") and hasClickAction()).assertIsFocused().performKeyInput {
            repeat(5) { pressKey(Key.DirectionDown) }
        }
        compose.onNode(hasText("Contrôle parental") and hasClickAction()).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Protéger les réglages").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Catégories verrouillées").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Reverrouiller les contenus").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Changer le code PIN").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Supprimer le code PIN").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Changer le code PIN").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNode(hasText("Contrôle parental") and hasClickAction()).assertIsFocused()
    }
}
