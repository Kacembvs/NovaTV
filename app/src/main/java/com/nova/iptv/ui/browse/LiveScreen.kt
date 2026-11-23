package com.nova.iptv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nova.iptv.core.formatClock
import com.nova.iptv.data.NowNext
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors

@Composable
fun LiveScreen(onAddProfile: () -> Unit, onOpenGuide: () -> Unit) {
    val vm = novaViewModel(key = "live") { BrowseViewModel(it, ContentKind.LIVE) }
    val context = LocalContext.current
    val epg by vm.epg.collectAsStateWithLifecycle()
    BrowseScaffold(
        vm,
        title = "TV en direct",
        searchHint = "Rechercher une chaîne",
        onAddProfile = onAddProfile,
        headerAction = {
            FilledTonalButton(onClick = onOpenGuide) {
                Icon(Icons.Rounded.CalendarViewWeek, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guide TV")
            }
        },
    ) { items, favorites ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val channel = items[index] ?: return@items
                ChannelRow(
                    channel = channel,
                    programmes = channel.tvgId?.let { epg[it] },
                    isFavorite = channel.streamRef in favorites,
                    onClick = { vm.play(context, channel) },
                    onToggleFavorite = { vm.toggleFavorite(channel) },
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ContentEntity,
    programmes: NowNext?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    NovaCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusScale = 1.02f) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ContentImage(
                url = channel.logo,
                name = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)),
                placeholderSize = 18,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val now = programmes?.now
                if (now != null) {
                    Text(
                        "${formatClock(now.startMs)}–${formatClock(now.endMs)}  ${now.title}",
                        color = NovaColors.TextPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val progress = ((System.currentTimeMillis() - now.startMs).toFloat() / (now.endMs - now.startMs)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        trackColor = NovaColors.Border,
                    )
                    programmes.next?.let { next ->
                        Text(
                            "Ensuite ${formatClock(next.startMs)}  ${next.title}",
                            color = NovaColors.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(channel.groupTitle, color = NovaColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FavoriteButton(isFavorite, onToggleFavorite)
        }
    }
}

/** Shared layout for the Live / Movies / Series screens: header, search, category chips and states. */
@Composable
fun BrowseScaffold(
    vm: BrowseViewModel,
    title: String,
    searchHint: String,
    onAddProfile: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable (LazyPagingItems<ContentEntity>, Set<String>) -> Unit,
) {
    val profileLoad by vm.profile.collectAsStateWithLifecycle()
    if (profileLoad.loading) return
    val profile = profileLoad.profile
    if (profile == null) {
        EmptyState(
            title = "Aucun profil IPTV",
            message = "Ajoutez une playlist M3U ou un compte Xtream Codes pour afficher vos contenus.",
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
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 20.dp)) {
            ScreenHeader(title, subtitle = profile.name, modifier = Modifier.weight(1f))
            headerAction?.invoke()
        }
        SearchField(query, { vm.query.value = it }, searchHint)
        Spacer(Modifier.height(10.dp))
        GroupChips(groups, group, BrowseViewModel.FAVORITES, onSelect = { vm.group.value = it })
        Spacer(Modifier.height(6.dp))
        when (val s = sync) {
            is SyncManager.State.Running -> SyncBanner("Chargement de la playlist… ${s.loaded} contenus", isError = false)
            is SyncManager.State.Failed -> SyncBanner("Échec de l'actualisation : ${s.message}", isError = true)
            null -> Unit
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val refreshing = items.loadState.refresh is LoadState.Loading
            when {
                items.itemCount > 0 -> content(items, favorites)
                refreshing || sync is SyncManager.State.Running ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> EmptyState(
                    title = "Aucun contenu",
                    message = when {
                        query.isNotBlank() -> "Aucun résultat pour « $query »."
                        group == BrowseViewModel.FAVORITES -> "Touchez l'étoile d'un contenu pour l'ajouter à vos favoris."
                        else -> "Ce profil ne contient pas encore ce type de contenu. Actualisez-le depuis l'onglet Profils."
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
