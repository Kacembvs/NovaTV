package com.nova.iptv.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import com.nova.iptv.ui.components.TvActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.os.Build
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class TvFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tvControlsVisiblyChangeWhenFocused() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        compose.setContent {
            CompositionLocalProvider(LocalRippleConfiguration provides tvRippleConfiguration(isLight = false)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column {
                        TvActionButton("Enregistrer", {}, Modifier.testTag("button"))
                        TvActionButton("Annuler", {}, Modifier.testTag("text"))
                        TvActionButton("Favori", {}, Modifier.testTag("icon"), icon = Icons.Rounded.Star)
                        TvActionButton("Favoris", {}, Modifier.testTag("chip"), selected = true)
                    }
                }
            }
        }
        for (tag in listOf("button", "text", "icon", "chip")) {
            val node = compose.onNodeWithTag(tag)
            val before = node.captureToImage().toPixelMap()
            node.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            compose.waitForIdle()
            val after = node.captureToImage().toPixelMap()
            var changed = 0
            for (y in 0 until before.height) for (x in 0 until before.width) {
                val a = before[x, y]
                val b = after[x, y]
                if (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.15f) changed++
            }
            assertTrue("$tag: focus is not sufficiently visible", changed > before.width * before.height / 20)
        }
    }
}
