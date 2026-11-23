package com.nova.iptv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.delay

/** Cinematic banner; rotates slowly (12 s) and stops rotating while the user interacts with it. */
@Composable
fun FeaturedBanner(
    items: List<ContentEntity>,
    tv: Boolean,
    onWatch: (ContentEntity) -> Unit,
    onDetails: (ContentEntity) -> Unit,
) {
    var index by remember(items) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(items, focused) {
        while (!focused && items.size > 1) {
            delay(12_000)
            index = (index + 1) % items.size
        }
    }
    val item = items[index.coerceIn(items.indices)]

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(if (tv) 340.dp else 230.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(NovaColors.Background2)
            .onFocusChanged { focused = it.hasFocus },
    ) {
        Crossfade(item, animationSpec = tween(600), label = "banner") { content ->
            AsyncImage(
                model = content.logo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.45f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(NovaColors.Background, NovaColors.Background.copy(alpha = 0.6f), Color.Transparent))
            )
        )
        Row(
            Modifier.fillMaxSize().padding(if (tv) 32.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (item.kind == ContentKind.MOVIE) "FILM" else "SÉRIE",
                    color = NovaColors.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    item.name,
                    fontSize = if (tv) 34.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(item.year, item.groupTitle, item.rating?.takeIf { it != "0" }?.let { "★ $it" })
                Text(meta.joinToString(" · "), color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                item.description?.let {
                    Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = if (tv) 3 else 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = { onWatch(item) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.kind == ContentKind.MOVIE) "Regarder" else "Voir la série")
                    }
                    OutlinedButton(onClick = { onDetails(item) }) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Détails")
                    }
                }
            }
            ContentImage(
                item.logo,
                item.name,
                Modifier.width(if (tv) 190.dp else 110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)),
            )
        }
        if (items.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                items.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == index) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == index) NovaColors.Cyan else NovaColors.TextSecondary.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}
