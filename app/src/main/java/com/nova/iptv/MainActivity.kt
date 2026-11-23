package com.nova.iptv

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.NovaAppUi
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(NovaColors.palette.isLight)
        val container = (application as NovaApp).container
        container.appScope.launch {
            container.profiles.activeProfile.first()?.let { container.epg.refreshIfStale(it.id) }
        }
        setContent {
            NovaTheme {
                val light = NovaColors.palette.isLight
                LaunchedEffect(light) { applySystemBars(light) }
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Surface(Modifier.fillMaxSize(), color = NovaColors.Background) {
                        NovaAppUi()
                    }
                }
            }
        }
    }

    /** Dark status/navigation bar icons on the light theme, light icons otherwise. */
    private fun applySystemBars(light: Boolean) {
        val style = if (light) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
