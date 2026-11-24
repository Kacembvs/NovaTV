package com.nova.iptv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.iptv.core.formatClock
import com.nova.iptv.data.NowNext
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.player.PreviewPlayer
import com.nova.iptv.ui.components.FavoriteButton
import com.nova.iptv.ui.components.NovaButton
import com.nova.iptv.ui.components.NovaIconButton
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.NovaColors

@Composable
internal fun LivePreviewPane(
    channel: ContentEntity?,
    url: String?,
    programmes: NowNext?,
    tv: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewPlayer(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(if (tv) LocalTvInterface.current.cardRadius.dp else 16.dp)))
        if (channel == null) {
            Text(
                if (tv) "Parcourez les chaînes : l'aperçu suit la sélection. OK pour le plein écran."
                else "Touchez une chaîne pour l'aperçu, touchez-la à nouveau pour le plein écran.",
                color = NovaColors.TextSecondary, fontSize = 13.sp,
            )
        } else {
            Text(channel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val now = programmes?.now
            if (now != null) {
                Text("${formatClock(now.startMs)} – ${formatClock(now.endMs)}", color = NovaColors.Cyan, fontSize = 13.sp)
                Text(now.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val duration = (now.endMs - now.startMs).coerceAtLeast(1)
                val progress = ((System.currentTimeMillis() - now.startMs).toFloat() / duration).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = NovaColors.Border)
                now.description?.let {
                    Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = if (tv) 6 else 4, overflow = TextOverflow.Ellipsis)
                }
            } else Text("Pas d'information de programme.", color = NovaColors.TextSecondary, fontSize = 13.sp)
            programmes?.next?.let {
                Text("Ensuite ${formatClock(it.startMs)}  ${it.title}", color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
            val play: @Composable () -> Unit = {
                NovaButton(onClick = onPlay) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Plein écran")
                }
            }
            val library: @Composable () -> Unit = {
                FavoriteButton(favorite, onFavorite)
                NovaIconButton(onClick = onGroup) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à un groupe", tint = NovaColors.TextSecondary)
                }
            }
            if (tv) {
                play()
                Row(verticalAlignment = Alignment.CenterVertically) { library() }
            } else Row(verticalAlignment = Alignment.CenterVertically) { play(); library() }
        }
    }
}
