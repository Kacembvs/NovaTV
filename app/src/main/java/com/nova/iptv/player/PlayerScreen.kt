package com.nova.iptv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.nova.iptv.core.formatDuration
import com.nova.iptv.ui.theme.NovaColors

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onViewCreated: (PlayerView) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onExternal: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { context -> PlayerView(context).also(onViewCreated) }, modifier = Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = state.controlsVisible || state.bannerVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.isLive) {
                            Text(
                                "EN DIRECT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.background(NovaColors.Error, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    state.subtitle?.let { Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                IconButton(onClick = onExternal) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Ouvrir dans un lecteur externe", tint = Color.White) }
            }
        }

        state.resumeFromMs?.let { position ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 20.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(start = 14.dp),
            ) {
                Text("Reprise à ${formatDuration(position)}", color = Color.White, fontSize = 14.sp)
                TextButton(onClick = onRestart) { Text("Recommencer") }
            }
        }

        state.error?.let { message ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(NovaColors.Background2.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
            ) {
                Text(state.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(message, color = NovaColors.TextSecondary, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text("Réessayer") }
                    OutlinedButton(onClick = onExternal) { Text("Lecteur externe") }
                    if (state.isLive && state.canZap) OutlinedButton(onClick = onNext) { Text("Chaîne suivante") }
                }
                TextButton(onClick = onBack) { Text("Fermer") }
            }
        }
    }
}
