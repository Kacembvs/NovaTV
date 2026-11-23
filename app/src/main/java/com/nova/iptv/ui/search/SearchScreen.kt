package com.nova.iptv.ui.search

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.isXtreamSeries
import com.nova.iptv.ui.Playback
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

data class SearchResults(
    val query: String = "",
    val channels: List<ContentEntity> = emptyList(),
    val movies: List<ContentEntity> = emptyList(),
    val series: List<ContentEntity> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    /** Searches the local catalogue only (no network), across every content type. */
    val results: StateFlow<SearchResults> = c.profiles.activeProfile.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { id -> (if (id == null) flowOf(emptySet()) else c.parental.hiddenGroups(id)).map { id to it } }
        .combine(query.debounce(250).map { it.trim() }.distinctUntilChanged()) { (id, hidden), q -> Triple(id, hidden.toList(), q) }
        .mapLatest { (id, hidden, q) ->
            if (id == null || q.length < 2) {
                SearchResults(q)
            } else {
                val dao = c.db.contentDao()
                SearchResults(
                    query = q,
                    channels = dao.search(id, ContentKind.LIVE, q, 30, hidden),
                    movies = dao.search(id, ContentKind.MOVIE, q, 30, hidden),
                    series = dao.search(id, ContentKind.SERIES, q, 30, hidden),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun play(context: Context, list: List<ContentEntity>, index: Int) {
        val item = list.getOrNull(index) ?: return
        Playback.start(context, c, item.profileId, list.map { it.toPlayable() }, index)
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenMovie: (String) -> Unit, onOpenSeries: (String) -> Unit) {
    val vm = novaViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tv = context.isTelevision()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!tv) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, start = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
            Text("Recherche", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        SearchField(query, { vm.query.value = it }, "Chaîne, film, série…", Modifier.focusRequester(focus))
        Spacer(Modifier.height(12.dp))

        when {
            results.query.length < 2 -> EmptyState("Recherche globale", "Tapez au moins 2 caractères pour chercher dans tout votre catalogue.")
            results.isEmpty -> EmptyState("Aucun résultat", "Rien ne correspond à « ${results.query} ».")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (results.channels.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Chaînes (${results.channels.size})")
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.channels.size) { index ->
                                    val channel = results.channels[index]
                                    Column(Modifier.width(if (tv) 180.dp else 132.dp)) {
                                        NovaCard(
                                            onClick = { vm.play(context, results.channels, index) },
                                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                                        ) {
                                            ContentImage(
                                                channel.logo,
                                                channel.name,
                                                Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Fit,
                                            )
                                        }
                                        Text(channel.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                listOf("Films" to results.movies, "Séries" to results.series).forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionTitle("$title (${list.size})")
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(list.size) { index ->
                                        val item = list[index]
                                        PosterCard(
                                            title = item.name,
                                            imageUrl = item.logo,
                                            subtitle = item.year ?: item.groupTitle,
                                            modifier = Modifier.width(if (tv) 150.dp else 110.dp),
                                            onClick = {
                                                when {
                                                    item.kind == ContentKind.MOVIE -> onOpenMovie(item.streamRef)
                                                    item.streamRef.isXtreamSeries() -> onOpenSeries(item.streamRef)
                                                    else -> vm.play(context, listOf(item), 0)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Résultats limités à 30 par type. Précisez la recherche pour affiner.",
                        color = NovaColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
