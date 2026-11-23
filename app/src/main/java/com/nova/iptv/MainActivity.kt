package com.nova.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.NovaAppUi
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NovaApp).container
        setContent {
            NovaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Surface(Modifier.fillMaxSize(), color = NovaColors.Background) {
                        NovaAppUi()
                    }
                }
            }
        }
    }
}
