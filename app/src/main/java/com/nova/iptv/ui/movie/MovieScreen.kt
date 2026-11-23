package com.nova.iptv.ui.movie

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.nova.iptv.AppContainer
import com.nova.iptv.core.formatDuration
import com.nova.iptv.core.isTelevision
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.data.source.XtreamClient
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.Playback
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MovieUiState {
    data object Loading : MovieUiState
    data class Error(val message: String) : MovieUiState
    data class Loaded(
        val content: ContentEntity,
        val info: XtreamClient.VodInfo?,
        val similar: List<ContentEntity>,
    ) : MovieUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MovieViewModel(private val c: AppContainer, private val ref: String) : ViewModel() {
    val state = MutableStateFlow<MovieUiState>(MovieUiState.Loading)
    private val profileId = MutableStateFlow<Long?>(null)

    val history: StateFlow<HistoryEntity?> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.db.libraryDao().observeHistory(id, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val favorite: StateFlow<Boolean> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(false) else c.library.isFavorite(id, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val rating: StateFlow<Int?> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.library.rating(id, ref) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            state.value = MovieUiState.Loading
            try {
                val profile = c.profiles.activeProfile.first() ?: error("Aucun profil actif.")
                profileId.value = profile.id
                val content = c.db.contentDao().getByRef(profile.id, ref) ?: error("Film introuvable dans ce profil.")
                val similar = c.db.contentDao().similar(profile.id, content.kind, content.groupTitle, ref)
                state.value = MovieUiState.Loaded(content, null, similar)
                // Details come from the provider when available; the screen is already usable without them.
                if (profile.type == ProfileType.XTREAM && ref.startsWith("movie/")) {
                    val id = ref.substringAfter("movie/").substringBefore('.')
                    val info = runCatching {
                        withContext(Dispatchers.IO) { c.xtream.vodInfo(c.profiles.credentials(profile), id) }
                    }.getOrNull()
                    if (info != null) state.value = MovieUiState.Loaded(content, info, similar)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = MovieUiState.Error(e.toUserMessage())
            }
        }
    }

    fun play(context: Context, content: ContentEntity, fromStart: Boolean) {
        Playback.start(context, c, content.profileId, listOf(content.toPlayable()), 0, fromStart)
    }

    fun toggleFavorite(content: ContentEntity) {
        viewModelScope.launch {
            c.library.setFavorite(content.profileId, ref, content.kind, content.name, content.logo, !favorite.value)
        }
    }

    fun rate(content: ContentEntity, value: Int) {
        viewModelScope.launch {
            c.library.rate(content.profileId, ref, content.kind, content.name, content.logo, value, rating.value)
        }
    }
}

@Composable
fun MovieScreen(ref: String, onBack: () -> Unit, onOpenMovie: (String) -> Unit) {
    val vm = novaViewModel(key = "movie-$ref") { MovieViewModel(it, ref) }
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val rating by vm.rating.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tv = context.isTelevision()

    when (val s = state) {
        MovieUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is MovieUiState.Error -> Column {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            EmptyState("Impossible d'ouvrir le film", s.message, actionLabel = "Réessayer", onAction = vm::load)
        }
        is MovieUiState.Loaded -> {
            val content = s.content
            val info = s.info
            val cover = info?.cover ?: content.logo
            val saved = history
            val resumable = saved != null && saved.positionMs > 10_000 &&
                (saved.durationMs <= 0 || saved.positionMs < saved.durationMs * 0.95)
            val primaryFocus = remember { FocusRequester() }
            LaunchedEffect(content.id) { runCatching { primaryFocus.requestFocus() } }
            val headerHeight = if (tv) 380.dp else 260.dp

            Box(Modifier.fillMaxSize()) {
                val backdrop = info?.backdrop ?: cover
                if (backdrop != null) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(headerHeight),
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(headerHeight).background(
                        Brush.verticalGradient(listOf(NovaColors.Background.copy(alpha = 0.4f), NovaColors.Background))
                    )
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White)
                        }
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = if (tv) 120.dp else 70.dp),
                        ) {
                            ContentImage(
                                cover,
                                content.name,
                                Modifier.width(if (tv) 180.dp else 120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(content.name, fontSize = if (tv) 30.sp else 22.sp, fontWeight = FontWeight.Bold)
                                val meta = listOfNotNull(
                                    (info?.releaseDate ?: content.year)?.take(4),
                                    info?.duration?.let(::formatRuntime),
                                    info?.genre,
                                    (info?.rating ?: content.rating)?.takeIf { it != "0" && it.isNotBlank() }?.let { "★ $it" },
                                )
                                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = NovaColors.TextSecondary, fontSize = 13.sp)
                                Text(content.groupTitle, color = NovaColors.Violet, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { vm.play(context, content, fromStart = false) },
                                    modifier = Modifier.focusRequester(primaryFocus),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (resumable) "Reprendre à ${formatDuration(saved!!.positionMs)}" else "Regarder")
                                }
                                if (resumable) {
                                    OutlinedButton(onClick = { vm.play(context, content, fromStart = true) }) {
                                        Icon(Icons.Rounded.Replay, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Recommencer")
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FavoriteButton(favorite, { vm.toggleFavorite(content) })
                                RatingButtons(rating) { vm.rate(content, it) }
                            }
                            if (resumable) {
                                LinearProgressIndicator(progress = { saved!!.progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
                            }
                        }
                    }
                    val plot = info?.plot ?: content.description
                    item {
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                plot ?: "Aucun synopsis fourni par la source.",
                                color = if (plot == null) NovaColors.TextSecondary else NovaColors.TextPrimary,
                                fontSize = 14.sp,
                            )
                            info?.director?.let { Text("Réalisation : $it", color = NovaColors.TextSecondary, fontSize = 13.sp) }
                            info?.cast?.let { Text("Avec : $it", color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 3) }
                        }
                    }
                    if (s.similar.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionTitle("Titres similaires")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(s.similar, key = { it.id }) { item ->
                                        PosterCard(item.name, item.logo, { onOpenMovie(item.streamRef) }, Modifier.width(if (tv) 150.dp else 110.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Xtream durations come as "01:52:00" or a number of minutes. */
private fun formatRuntime(raw: String): String? {
    val parts = raw.split(':').mapNotNull { it.trim().toIntOrNull() }
    val minutes = when {
        parts.size >= 2 -> parts[0] * 60 + parts[1]
        parts.size == 1 -> parts[0]
        else -> return null
    }
    if (minutes <= 0) return null
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}
