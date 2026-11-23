package com.nova.iptv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nova.iptv.core.formatClock
import com.nova.iptv.core.isTelevision
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.player.PreviewPlayer
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.theme.NovaColors

/**
 * TiviMate-style Live TV for TV and large screens: categories | channels | preview + programme.
 * Remote: focusing a channel previews it, OK opens full screen. Touch: first tap previews, second tap opens.
 */
@Composable
fun LiveTvLayout(vm: BrowseViewModel, onOpenGuide: () -> Unit, onAddProfile: () -> Unit) {
    val context = LocalContext.current
    val tv = context.isTelevision()
    val profileLoad by vm.profile.collectAsStateWithLifecycle()
    if (profileLoad.loading) return
    val profile = profileLoad.profile
    if (profile == null) {
        EmptyState(
            title = "Aucun profil IPTV",
            message = "Ajoutez une playlist M3U ou un compte Xtream Codes pour afficher vos chaînes.",
            actionLabel = "Ajouter un profil",
            onAction = onAddProfile,
            modifier = Modifier.fillMaxSize().wrapContentHeight(),
        )
        return
    }
    val query by vm.query.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewUrl by vm.previewUrl.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()

    Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Zone 1: categories
        Column(Modifier.width(if (tv) 240.dp else 200.dp).fillMaxHeight()) {
            Text("TV en direct", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            Text(profile.name, color = NovaColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "all") { CategoryItem("Toutes les chaînes", null, group == null) { vm.group.value = null } }
                item(key = "fav") { CategoryItem("Favoris", null, group == BrowseViewModel.FAVORITES, star = true) { vm.group.value = BrowseViewModel.FAVORITES } }
                items(groups, key = { "g:" + it.name }) { g ->
                    CategoryItem(g.name, g.count, group == g.name) { vm.group.value = g.name }
                }
                item(key = "hidden") {
                    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
                    HiddenCategoriesButton(hiddenCount, vm::unlock)
                }
            }
        }

        // Zone 2: channels
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { vm.query.value = it }, "Rechercher une chaîne", Modifier.weight(1f).padding(end = 0.dp))
                FilledTonalButton(onClick = onOpenGuide) {
                    Icon(Icons.Rounded.CalendarViewWeek, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guide TV")
                }
            }
            when (val s = sync) {
                is SyncManager.State.Running -> SyncBanner("Chargement… ${s.loaded} contenus", isError = false, Modifier.padding(top = 8.dp))
                is SyncManager.State.Failed -> SyncBanner(s.message, isError = true, Modifier.padding(top = 8.dp))
                null -> Unit
            }
            if (items.itemCount == 0) {
                EmptyState("Aucune chaîne", if (query.isNotBlank()) "Aucun résultat pour « $query »." else "Cette catégorie est vide.")
            }
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    val channel = items[index] ?: return@items
                    val programme = channel.tvgId?.let { epg[it]?.now }
                    val selected = preview?.id == channel.id
                    NovaCard(
                        onClick = {
                            if (tv || selected) vm.play(context, channel) else vm.preview.value = channel
                        },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus && tv) vm.preview.value = channel },
                        color = if (selected) NovaColors.Card else NovaColors.Background2,
                        focusScale = 1.02f,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                color = NovaColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(32.dp),
                            )
                            ContentImage(
                                channel.logo,
                                channel.name,
                                Modifier.size(width = 56.dp, height = 38.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit,
                                placeholderSize = 14,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    programme?.title ?: channel.groupTitle,
                                    color = NovaColors.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (channel.streamRef in favorites) {
                                Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = NovaColors.Cyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Zone 3: preview and programme
        Column(
            Modifier.weight(0.9f).fillMaxHeight().padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewPlayer(previewUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
            val channel = preview
            if (channel == null) {
                Text(
                    if (tv) "Parcourez les chaînes avec la télécommande : l'aperçu suit la sélection. OK pour le plein écran."
                    else "Touchez une chaîne pour l'aperçu, touchez-la à nouveau pour le plein écran.",
                    color = NovaColors.TextSecondary,
                    fontSize = 13.sp,
                )
            } else {
                val programmes = channel.tvgId?.let { epg[it] }
                Text(channel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val now = programmes?.now
                if (now != null) {
                    Text("${formatClock(now.startMs)} – ${formatClock(now.endMs)}", color = NovaColors.Cyan, fontSize = 13.sp)
                    Text(now.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val progress = ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs)).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = NovaColors.Border)
                    now.description?.let {
                        Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = if (tv) 6 else 4, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("Pas d'information de programme.", color = NovaColors.TextSecondary, fontSize = 13.sp)
                }
                programmes?.next?.let { next ->
                    Text("Ensuite ${formatClock(next.startMs)}  ${next.title}", color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.play(context, channel) }) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Plein écran")
                    }
                    FavoriteButton(channel.streamRef in favorites, { vm.toggleFavorite(channel) })
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(name: String, count: Int?, selected: Boolean, star: Boolean = false, onClick: () -> Unit) {
    NovaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) NovaColors.Card else Color.Transparent,
        focusScale = 1.02f,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (star) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = NovaColors.Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) NovaColors.Cyan else NovaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            count?.let { Text("$it", color = NovaColors.TextSecondary, fontSize = 12.sp) }
        }
    }
}
