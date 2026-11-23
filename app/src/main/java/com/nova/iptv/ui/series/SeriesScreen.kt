package com.nova.iptv.ui.series

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nova.iptv.AppContainer
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.FavoriteEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.source.XtreamClient
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.ui.Playback
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FavoriteButton
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.components.RatingButtons
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SeriesUiState {
    data object Loading : SeriesUiState
    data class Error(val message: String) : SeriesUiState
    data class Loaded(val content: ContentEntity, val info: XtreamClient.SeriesInfo) : SeriesUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModel(private val c: AppContainer, private val ref: String) : ViewModel() {
    val state = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val season = MutableStateFlow<Int?>(null)
    private val profileId = MutableStateFlow<Long?>(null)

    val progress: StateFlow<Map<String, HistoryEntity>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.db.libraryDao().episodeHistory(id) }
        .map { list -> list.associateBy { it.itemKey } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val isFavorite: StateFlow<Boolean> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.db.libraryDao().favoriteKeys(id) }
        .map { ref in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val rating: StateFlow<Int?> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.library.rating(id, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rate(content: ContentEntity, value: Int) {
        viewModelScope.launch {
            c.library.rate(content.profileId, ref, ContentKind.SERIES, content.name, content.logo, value, rating.value)
        }
    }

    init { load() }

    fun load() {
        viewModelScope.launch {
            state.value = SeriesUiState.Loading
            try {
                val profile = c.profiles.activeProfile.first() ?: error("Aucun profil actif.")
                profileId.value = profile.id
                val content = c.db.contentDao().getByRef(profile.id, ref) ?: error("Série introuvable dans ce profil.")
                val info = withContext(Dispatchers.IO) {
                    c.xtream.seriesInfo(c.profiles.credentials(profile), ref.substringAfter(':'))
                }
                state.value = SeriesUiState.Loaded(content, info)
                season.value = info.episodes.firstOrNull()?.season
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = SeriesUiState.Error(e.toUserMessage())
            }
        }
    }

    fun toggleFavorite(content: ContentEntity) {
        viewModelScope.launch {
            val library = c.db.libraryDao()
            if (isFavorite.value) library.removeFavorite(content.profileId, ref)
            else library.addFavorite(FavoriteEntity(content.profileId, ref, ContentKind.SERIES, content.name, content.logo))
        }
    }

    fun play(context: Context, loaded: SeriesUiState.Loaded, episodes: List<XtreamClient.Episode>, index: Int) {
        val cover = loaded.info.cover ?: loaded.content.logo
        val items = episodes.map { episode ->
            PlayableItem(
                ref = episodeRef(episode),
                title = loaded.content.name,
                kind = ContentKind.EPISODE,
                logo = cover,
                subtitle = episodeLabel(episode),
                parentKey = ref,
            )
        }
        Playback.start(context, c, loaded.content.profileId, items, index)
    }

    companion object {
        fun episodeRef(e: XtreamClient.Episode) = "series/${e.id}.${e.extension}"
        fun episodeLabel(e: XtreamClient.Episode): String {
            val prefix = "S${e.season} · É${e.number}"
            return if (e.title.isBlank()) prefix else "$prefix — ${e.title}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(ref: String, onBack: () -> Unit) {
    val vm = novaViewModel(key = "series-$ref") { SeriesViewModel(it, ref) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selectedSeason by vm.season.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val rating by vm.rating.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
            Text("Série", fontSize = 16.sp, color = NovaColors.TextSecondary)
        }
        when (val s = state) {
            SeriesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is SeriesUiState.Error -> EmptyState("Impossible de charger la série", s.message, actionLabel = "Réessayer", onAction = vm::load)
            is SeriesUiState.Loaded -> {
                val seasons = s.info.episodes.map { it.season }.distinct()
                val episodes = s.info.episodes.filter { it.season == selectedSeason }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ContentImage(
                                s.info.cover ?: s.content.logo,
                                s.content.name,
                                Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.content.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                val meta = listOfNotNull(s.info.releaseDate?.take(4), s.info.genre, "${seasons.size} saison(s)")
                                Text(meta.joinToString(" · "), color = NovaColors.TextSecondary, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (s.info.episodes.isNotEmpty()) {
                                        Button(onClick = {
                                            val resumeIndex = s.info.episodes.indexOfLast { progress.containsKey(SeriesViewModel.episodeRef(it)) }
                                            vm.play(context, s, s.info.episodes, resumeIndex.coerceAtLeast(0))
                                        }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                            Text(if (progress.isEmpty()) "Lecture" else "Continuer")
                                        }
                                    }
                                    FavoriteButton(favorite, { vm.toggleFavorite(s.content) })
                                    RatingButtons(rating) { vm.rate(s.content, it) }
                                }
                            }
                        }
                    }
                    val plot = s.info.plot ?: s.content.description
                    if (!plot.isNullOrBlank()) {
                        item { Text(plot, color = NovaColors.TextSecondary, fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                    }
                    if (seasons.size > 1) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(seasons) { number ->
                                    FilterChip(
                                        selected = number == selectedSeason,
                                        onClick = { vm.season.value = number },
                                        label = { Text("Saison $number") },
                                    )
                                }
                            }
                        }
                    }
                    if (episodes.isEmpty()) {
                        item { Text("Aucun épisode disponible.", color = NovaColors.TextSecondary) }
                    }
                    items(episodes, key = { e -> e.id }) { episode ->
                        val history = progress[SeriesViewModel.episodeRef(episode)]
                        NovaCard(onClick = { vm.play(context, s, s.info.episodes, s.info.episodes.indexOf(episode)) }, modifier = Modifier.fillMaxWidth(), focusScale = 1.02f) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(SeriesViewModel.episodeLabel(episode), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (history != null && history.durationMs > 0) {
                                    LinearProgressIndicator(
                                        progress = { history.progress },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
