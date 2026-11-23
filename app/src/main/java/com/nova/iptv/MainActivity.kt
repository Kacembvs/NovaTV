package com.nova.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NovaNavy = Color(0xFF080D1B)
private val NovaCyan = Color(0xFF36D9FF)
private val NovaViolet = Color(0xFF8564FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = NovaCyan,
                secondary = NovaViolet,
                background = NovaNavy,
                surface = Color(0xFF111A2D)
            )) {
                NovaHome()
            }
        }
    }
}

@Composable
private fun NovaHome() {
    Surface(modifier = Modifier.fillMaxSize(), color = NovaNavy) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = NovaCyan, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(" NOVA", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(" IPTV", color = NovaCyan, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Votre univers multimédia", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Une expérience pensée pour le mobile et le téléviseur.",
                color = Color(0xFFAFBBD0),
                fontSize = 15.sp
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF121E35),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bienvenue dans NOVA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "La fondation de l’application est en place. La gestion des sources IPTV sera ajoutée à la phase 2.",
                        color = Color(0xFFB9C6DA), fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("EXPLORER", color = NovaCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HomeTile("TV en direct", Modifier.weight(1f))
                HomeTile("Films", Modifier.weight(1f))
                HomeTile("Séries", Modifier.weight(1f))
            }
            Text(
                "Phase 1 — Fondation UI • Les rubriques sont des aperçus visuels, pas encore des lecteurs fonctionnels.",
                color = Color(0xFF77859C), fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun HomeTile(title: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.height(88.dp), color = Color(0xFF172641), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
