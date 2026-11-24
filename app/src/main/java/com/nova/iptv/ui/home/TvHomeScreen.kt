package com.nova.iptv.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.TvInterface

private enum class HomeSection { HERO, COLLECTIONS, LIVE, PORTALS, RESUME, CHANNELS, FAVORITES, RECOMMENDATIONS, MOVIES, SERIES, SERIES_PROGRESS, LIKED }

@Composable
internal fun TvHomeScreen(
    state: HomeState,
    onNavigate: (String) -> Unit,
    onOpen: (PlayableItem) -> Unit,
    onHistory: (List<HistoryEntity>, Int) -> Unit,
    onWatch: (ContentEntity) -> Unit,
    onDetails: (ContentEntity) -> Unit,
    onCollection: (ContentKind, String) -> Unit,
) {
    val style = LocalTvInterface.current
    val order = when (style) {
        TvInterface.STUDIOS -> listOf(HomeSection.HERO, HomeSection.COLLECTIONS, HomeSection.RESUME, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.FAVORITES, HomeSection.RECOMMENDATIONS, HomeSection.CHANNELS)
        TvInterface.ANTENNA -> listOf(HomeSection.LIVE, HomeSection.CHANNELS, HomeSection.PORTALS, HomeSection.FAVORITES, HomeSection.RESUME, HomeSection.HERO, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.RECOMMENDATIONS)
        TvInterface.DISCOVERY -> listOf(HomeSection.RESUME, HomeSection.HERO, HomeSection.PORTALS, HomeSection.RECOMMENDATIONS, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.FAVORITES, HomeSection.CHANNELS)
        TvInterface.GUIDE -> listOf(HomeSection.LIVE, HomeSection.PORTALS, HomeSection.CHANNELS, HomeSection.RESUME, HomeSection.FAVORITES, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.RECOMMENDATIONS, HomeSection.HERO)
        TvInterface.PLUS -> listOf(HomeSection.HERO, HomeSection.LIVE, HomeSection.COLLECTIONS, HomeSection.RESUME, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.CHANNELS, HomeSection.RECOMMENDATIONS, HomeSection.FAVORITES)
        else -> listOf(HomeSection.HERO, HomeSection.RESUME, HomeSection.RECOMMENDATIONS, HomeSection.MOVIES, HomeSection.SERIES, HomeSection.FAVORITES, HomeSection.PORTALS, HomeSection.CHANNELS)
    } + listOf(HomeSection.SERIES_PROGRESS, HomeSection.LIKED)
    val collections = (state.featured + state.newMovies + state.newSeries)
        .distinctBy { it.streamRef }.groupBy { it.kind to it.groupTitle }
        .filterKeys { (kind, group) -> kind in listOf(ContentKind.MOVIE, ContentKind.SERIES) && group.isNotBlank() }
        .entries.take(12)
    val width = style.cardWidth.dp
    val online = rememberIsOnline()

    LazyColumn(
        Modifier.fillMaxSize().testTag("tv-home"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (style == TvInterface.GUIDE) 16.dp else 26.dp),
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(style.label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(state.profile?.name.orEmpty(), color = NovaColors.TextSecondary, fontSize = 13.sp)
                }
                TvActionButton("Rechercher", { onNavigate("search") })
                TvActionButton("Téléchargements", { onNavigate("downloads") }, icon = Icons.Rounded.DownloadForOffline)
                TvActionButton("Mon profil", { onNavigate("profiles") })
            }
        }
        if (!online) item(key = "offline") { OfflineBanner { onNavigate("downloads") } }
        when (val sync = state.sync) {
            is SyncManager.State.Running -> item { SyncBanner("Chargement… ${sync.loaded} contenus", false) }
            is SyncManager.State.Failed -> item { SyncBanner(sync.message, true) }
            null -> state.profile?.lastSyncError?.let { error -> item { SyncBanner(error, true) } }
        }
        order.forEach { section ->
            when (section) {
                HomeSection.HERO -> if (state.featured.isNotEmpty()) item(key = section) {
                    FeaturedBanner(state.featured, true, onWatch, onDetails)
                }
                HomeSection.LIVE -> item(key = section) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Votre télévision", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.counts[ContentKind.LIVE] ?: 0} chaînes · vos programmes, votre rythme",
                            color = NovaColors.TextSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvActionButton("Ouvrir le direct", { onNavigate("live") })
                            TvActionButton("Explorer le guide TV", { onNavigate("guide") })
                        }
                    }
                }
                HomeSection.PORTALS -> item(key = section) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("TV en direct" to "live", "Films" to "movies", "Séries" to "series").forEach { (title, route) ->
                            TvActionButton(title, { onNavigate(route) }, Modifier.weight(1f))
                        }
                    }
                }
                HomeSection.COLLECTIONS -> if (collections.isNotEmpty()) item(key = section) {
                    HomeRow(if (style == TvInterface.PLUS) "À explorer" else "Vos univers") {
                        items(collections, key = { "${it.key.first}:${it.key.second}" }) { entry ->
                            val (kind, group) = entry.key
                            NovaCard({ onCollection(kind, group) }, Modifier.width(220.dp).height(110.dp)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.Center) {
                                    Text(if (kind == ContentKind.MOVIE) "FILMS" else "SÉRIES", fontSize = 11.sp, color = NovaColors.Cyan)
                                    Text(group, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                HomeSection.RESUME -> if (state.continueWatching.isNotEmpty()) item(key = section) {
                    HomeRow("Reprendre la lecture") {
                        itemsIndexed(state.continueWatching, key = { _, it -> it.itemKey }) { index, entry ->
                            ResumeCard(entry, Modifier.width(if (style == TvInterface.DISCOVERY) 330.dp else 280.dp)) {
                                onHistory(state.continueWatching, index)
                            }
                        }
                    }
                }
                HomeSection.CHANNELS -> if (state.recentChannels.isNotEmpty()) item(key = section) {
                    HomeRow("Dernières chaînes regardées") {
                        itemsIndexed(state.recentChannels, key = { _, it -> it.itemKey }) { index, entry ->
                            ChannelTile(entry.title, entry.logo, Modifier.width(if (style == TvInterface.GUIDE) 140.dp else 200.dp)) {
                                onHistory(state.recentChannels, index)
                            }
                        }
                    }
                }
                HomeSection.FAVORITES -> if (state.favorites.isNotEmpty()) item(key = section) {
                    HomeRow("Mes favoris") {
                        items(state.favorites, key = { it.itemKey }) { entry ->
                            if (entry.kind == ContentKind.LIVE) {
                                ChannelTile(entry.title, entry.logo, Modifier.width(180.dp)) { onOpen(entry.toPlayable()) }
                            } else PosterCard(entry.title, entry.logo, { onOpen(entry.toPlayable()) }, Modifier.width(width))
                        }
                    }
                }
                HomeSection.RECOMMENDATIONS, HomeSection.MOVIES, HomeSection.SERIES -> {
                    val (title, list) = when (section) {
                        HomeSection.MOVIES -> "Nouveaux films" to state.newMovies
                        HomeSection.SERIES -> "Nouvelles séries" to state.newSeries
                        else -> "Recommandé pour vous" to state.recommendations
                    }
                    if (list.isNotEmpty()) item(key = section) {
                        HomeRow(title) {
                            items(list, key = { it.id }) { content ->
                                PosterCard(content.name, content.logo,
                                    { onOpen(content.toPlayable().copy(kind = content.kind)) },
                                    Modifier.width(width), subtitle = content.year)
                            }
                        }
                    }
                }
                HomeSection.SERIES_PROGRESS -> if (state.seriesInProgress.isNotEmpty()) item(key = section) {
                    HomeRow("Séries en cours") {
                        items(state.seriesInProgress, key = { it.itemKey }) { entry ->
                            PosterCard(entry.title, entry.logo, {
                                entry.parentKey?.let { onOpen(PlayableItem(it, entry.title, ContentKind.SERIES, entry.logo)) }
                            }, Modifier.width(width))
                        }
                    }
                }
                HomeSection.LIKED -> if (state.liked.isNotEmpty()) item(key = section) {
                    HomeRow("Mes coups de cœur") {
                        items(state.liked, key = { it.itemKey }) { entry ->
                            PosterCard(entry.title, entry.logo,
                                { onOpen(PlayableItem(entry.itemKey, entry.title, entry.kind, entry.logo)) }, Modifier.width(width))
                        }
                    }
                }
            }
        }
    }
}
