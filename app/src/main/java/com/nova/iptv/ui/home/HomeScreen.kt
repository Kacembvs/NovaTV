package com.nova.iptv.ui.home

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nova.iptv.AppContainer
import com.nova.iptv.core.isTelevision
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.*
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.domain.isXtreamSeries
import com.nova.iptv.ui.Playback
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HomeState(
    val loading: Boolean = true,
    val profile: ProfileEntity? = null,
    val sync: SyncManager.State? = null,
    val counts: Map<ContentKind, Int> = emptyMap(),
    val continueWatching: List<HistoryEntity> = emptyList(),
    val recentChannels: List<HistoryEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val seriesInProgress: List<HistoryEntity> = emptyList(),
    val recommendations: List<ContentEntity> = emptyList(),
    val liked: List<RatingEntity> = emptyList(),
    val newMovies: List<ContentEntity> = emptyList(),
    val newSeries: List<ContentEntity> = emptyList(),
    /** Large banner at the top of Home (recommendations / new titles, or a random pick). */
    val featured: List<ContentEntity> = emptyList(),
)

private data class Discovery(
    val seriesInProgress: List<HistoryEntity>,
    val recommendations: List<ContentEntity>,
    val liked: List<RatingEntity>,
    val newMovies: List<ContentEntity>,
    val newSeries: List<ContentEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = c.profiles.activeProfile
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(HomeState(loading = false))
            } else {
                val library = c.db.libraryDao()
                val base = combine(
                    library.continueWatching(profile.id),
                    library.recentLive(profile.id),
                    library.favorites(profile.id),
                    c.db.contentDao().kindCounts(profile.id),
                    c.sync.states.map { it[profile.id] },
                ) { resume, live, favorites, counts, sync ->
                    HomeState(
                        loading = false,
                        profile = profile,
                        sync = sync,
                        counts = counts.associate { it.kind to it.count },
                        continueWatching = resume,
                        recentChannels = live,
                        favorites = favorites,
                    )
                }
                val discovery = combine(
                    c.library.seriesInProgress(profile.id),
                    c.library.recommendations(profile.id),
                    c.library.liked(profile.id),
                    c.db.contentDao().latest(profile.id, ContentKind.MOVIE, 20),
                    c.db.contentDao().latest(profile.id, ContentKind.SERIES, 20),
                ) { series, recommended, liked, newMovies, newSeries ->
                    Discovery(series, recommended, liked, newMovies, newSeries)
                }
                // Re-picked when the catalogue grows (e.g. during the first sync).
                val randomPick = c.db.contentDao().kindCounts(profile.id)
                    .map { counts -> counts.sumOf { it.count } / 500 }
                    .distinctUntilChanged()
                    .mapLatest { c.db.contentDao().randomFeatured(profile.id) }
                combine(base, discovery, randomPick, c.parental.hiddenGroups(profile.id)) { b, d, random, hidden ->
                    fun List<ContentEntity>.visible() = filter { it.groupTitle !in hidden }
                    val featured = (d.recommendations + d.newMovies + d.newSeries).visible()
                        .filter { it.logo != null && (it.kind == ContentKind.MOVIE || it.streamRef.isXtreamSeries()) }
                        .distinctBy { it.streamRef }
                        .take(6)
                        .ifEmpty { random.visible() }
                    b.copy(
                        seriesInProgress = d.seriesInProgress,
                        recommendations = d.recommendations.visible(),
                        liked = d.liked,
                        newMovies = d.newMovies.visible(),
                        newSeries = d.newSeries.visible(),
                        featured = featured,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun playHistory(context: Context, list: List<HistoryEntity>, index: Int) {
        Playback.start(context, c, list[index].profileId, list.map { it.toPlayable() }, index)
    }

    fun playSingle(context: Context, profileId: Long, item: PlayableItem) {
        Playback.start(context, c, profileId, listOf(item), 0)
    }
}

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onAddProfile: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val vm = novaViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tv = context.isTelevision()

    if (state.loading) return
    val profile = state.profile
    if (profile == null) {
        Welcome(onAddProfile)
        return
    }
    // Films open their detail page, Xtream series their season list, everything else plays directly.
    val open: (PlayableItem) -> Unit = { item ->
        when {
            item.kind == ContentKind.MOVIE -> onOpenMovie(item.ref)
            item.ref.isXtreamSeries() -> onOpenSeries(item.ref)
            else -> vm.playSingle(context, profile.id, item)
        }
    }
    val posterWidth = if (tv) 150.dp else 110.dp

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                NovaLogo(Modifier.height(if (tv) 64.dp else 48.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onNavigate("search") }) { Icon(Icons.Rounded.Search, contentDescription = "Recherche") }
                AssistChip(
                    onClick = { onNavigate("profiles") },
                    label = { Text(profile.name, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        when (val s = state.sync) {
            is SyncManager.State.Running -> item { SyncBanner("Chargement de « ${profile.name} »… ${s.loaded} contenus", isError = false) }
            is SyncManager.State.Failed -> item { SyncBanner("Échec du chargement : ${s.message}", isError = true) }
            null -> if (profile.lastSyncError != null) item { SyncBanner("Dernière actualisation : ${profile.lastSyncError}", isError = true) }
        }
        if (state.featured.isNotEmpty()) {
            item {
                FeaturedBanner(
                    items = state.featured,
                    tv = tv,
                    onWatch = { content ->
                        if (content.streamRef.isXtreamSeries()) onOpenSeries(content.streamRef)
                        else vm.playSingle(context, profile.id, content.toPlayable())
                    },
                    onDetails = { content ->
                        if (content.kind == ContentKind.MOVIE) onOpenMovie(content.streamRef) else onOpenSeries(content.streamRef)
                    },
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                val series = (state.counts[ContentKind.SERIES] ?: 0) + (state.counts[ContentKind.EPISODE] ?: 0)
                QuickTile("TV en direct", "${state.counts[ContentKind.LIVE] ?: 0} chaînes", Icons.Rounded.LiveTv, Modifier.weight(1f)) { onNavigate("live") }
                QuickTile("Films", "${state.counts[ContentKind.MOVIE] ?: 0} titres", Icons.Rounded.Movie, Modifier.weight(1f)) { onNavigate("movies") }
                QuickTile("Séries", "$series titres", Icons.Rounded.VideoLibrary, Modifier.weight(1f)) { onNavigate("series") }
            }
        }
        if (state.continueWatching.isNotEmpty()) {
            item {
                HomeRow("Reprendre la lecture") {
                    itemsIndexed(state.continueWatching, key = { _, h -> h.itemKey }) { index, entry ->
                        ResumeCard(entry, Modifier.width(if (tv) 300.dp else 220.dp)) {
                            vm.playHistory(context, state.continueWatching, index)
                        }
                    }
                }
            }
        }
        if (state.seriesInProgress.isNotEmpty()) {
            item {
                HomeRow("Séries en cours") {
                    itemsIndexed(state.seriesInProgress, key = { _, h -> "s:" + h.parentKey }) { _, entry ->
                        PosterCard(
                            title = entry.title,
                            imageUrl = entry.logo,
                            subtitle = entry.subtitle,
                            modifier = Modifier.width(posterWidth),
                            onClick = { entry.parentKey?.let(onOpenSeries) },
                        )
                    }
                }
            }
        }
        if (state.recentChannels.isNotEmpty()) {
            item {
                HomeRow("Dernières chaînes regardées") {
                    itemsIndexed(state.recentChannels, key = { _, h -> h.itemKey }) { index, entry ->
                        ChannelTile(entry.title, entry.logo, Modifier.width(if (tv) 180.dp else 132.dp)) {
                            vm.playHistory(context, state.recentChannels, index)
                        }
                    }
                }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item {
                HomeRow("Mes favoris") {
                    itemsIndexed(state.favorites, key = { _, f -> f.itemKey }) { _, favorite ->
                        if (favorite.kind == ContentKind.LIVE) {
                            ChannelTile(favorite.title, favorite.logo, Modifier.width(if (tv) 180.dp else 132.dp)) { open(favorite.toPlayable()) }
                        } else {
                            PosterCard(favorite.title, favorite.logo, { open(favorite.toPlayable()) }, Modifier.width(posterWidth))
                        }
                    }
                }
            }
        }
        if (state.liked.isNotEmpty()) {
            item {
                HomeRow("Mes coups de cœur") {
                    itemsIndexed(state.liked, key = { _, r -> "l:" + r.itemKey }) { _, liked ->
                        PosterCard(liked.title, liked.logo, { open(PlayableItem(liked.itemKey, liked.title, liked.kind, liked.logo)) }, Modifier.width(posterWidth))
                    }
                }
            }
        }
        if (state.recommendations.isNotEmpty()) {
            item {
                HomeRow("Recommandé pour vous") {
                    itemsIndexed(state.recommendations, key = { _, c -> "r:" + c.id }) { _, content ->
                        PosterCard(content.name, content.logo, { open(content.toPlayable().copy(kind = content.kind)) }, Modifier.width(posterWidth), subtitle = content.groupTitle)
                    }
                }
            }
        }
        listOf("Nouveaux films" to state.newMovies, "Nouvelles séries" to state.newSeries).forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item {
                    HomeRow(title) {
                        itemsIndexed(list, key = { _, c -> "n:" + c.id }) { _, content ->
                            PosterCard(content.name, content.logo, { open(content.toPlayable().copy(kind = content.kind)) }, Modifier.width(posterWidth), subtitle = content.year)
                        }
                    }
                }
            }
        }
        if (state.continueWatching.isEmpty() && state.recentChannels.isEmpty() && state.favorites.isEmpty() && state.recommendations.isEmpty()) {
            item {
                Text(
                    "Lancez une chaîne ou un film : vos reprises, favoris et recommandations apparaîtront ici.",
                    color = NovaColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}
@Composable
private fun Welcome(onAddProfile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NovaLogo(Modifier.fillMaxWidth(0.7f).heightIn(max = 220.dp))
        Spacer(Modifier.height(20.dp))
        Text("Beyond Streaming", color = NovaColors.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Ajoutez votre playlist M3U ou votre compte Xtream Codes pour retrouver vos chaînes, films et séries.",
            color = NovaColors.TextSecondary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddProfile, modifier = Modifier.height(52.dp)) { Text("Ajouter un profil IPTV", fontSize = 16.sp) }
    }
}

@Composable
private fun HomeRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    NovaCard(onClick = onClick, modifier = modifier.height(96.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = NovaColors.Cyan)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text(subtitle, color = NovaColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ResumeCard(entry: HistoryEntity, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        NovaCard(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box {
                ContentImage(entry.logo, entry.title, Modifier.fillMaxSize())
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        entry.subtitle?.let { Text(it, fontSize = 11.sp, color = NovaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ChannelTile(title: String, logo: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        NovaCard(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            ContentImage(
                logo,
                title,
                Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
