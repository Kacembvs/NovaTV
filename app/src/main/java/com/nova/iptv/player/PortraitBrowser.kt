package com.nova.iptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.iptv.AppContainer
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.domain.isXtreamSeries
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.components.GroupChips
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.series.SeriesViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Portrait player: navigation under the video, following where playback started —
 * groups → channels, groups → films, groups → series → episodes.
 */
class PortraitBrowserModel(
    private val c: AppContainer,
    private val scope: CoroutineScope,
    private val profileId: Long,
    val kind: ContentKind,
    private val onPlay: (List<PlayableItem>, Int) -> Unit,
) {
    data class SeriesEpisodes(val ref: String, val name: String, val items: List<PlayableItem>)

    val groups = MutableStateFlow<List<GroupCount>>(emptyList())
    val group = MutableStateFlow<String?>(null)
    val items = MutableStateFlow<List<ContentEntity>>(emptyList())
    /** Episodes of the series picked in the list (the playing queue until then). */
    val series = MutableStateFlow<SeriesEpisodes?>(null)
    val queue = MutableStateFlow<List<PlayableItem>>(emptyList())
    val current = MutableStateFlow<PlayableItem?>(null)
    val loadingRef = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    private var itemsJob: Job? = null
    private var started = false

    fun setCurrent(queue: List<PlayableItem>, item: PlayableItem) {
        this.queue.value = queue
        current.value = item
        if (!started) {
            started = true
            start(item)
        }
    }

    private fun start(item: PlayableItem) {
        scope.launch {
            val hidden = c.parental.hiddenGroups(profileId).first()
            val all = c.db.contentDao().groups(profileId, kind).first().filter { it.name !in hidden }
            groups.value = all
            val dao = c.db.contentDao()
            val content = dao.getByRef(profileId, item.ref) ?: item.parentKey?.let { dao.getByRef(profileId, it) }
            select(content?.groupTitle?.takeIf { name -> all.any { it.name == name } } ?: all.firstOrNull()?.name)
        }
    }

    fun select(name: String?) {
        if (name == null) return
        group.value = name
        message.value = null
        itemsJob?.cancel()
        itemsJob = scope.launch { items.value = c.db.contentDao().listGroup(profileId, kind, name) }
    }

    fun pick(index: Int) {
        val list = items.value
        val item = list.getOrNull(index) ?: return
        message.value = null
        when {
            kind == ContentKind.MOVIE -> onPlay(listOf(item.toPlayable()), 0)
            kind == ContentKind.SERIES && item.streamRef.isXtreamSeries() -> openSeries(item)
            // Channels and M3U episodes are queued with their group (zapping / next episode).
            else -> onPlay(list.map { it.toPlayable() }, index)
        }
    }

    fun playEpisode(list: List<PlayableItem>, index: Int) = onPlay(list, index)

    private fun openSeries(item: ContentEntity) {
        scope.launch {
            loadingRef.value = item.streamRef
            try {
                val profile = c.db.profileDao().get(profileId) ?: error("Profil introuvable.")
                val info = withContext(Dispatchers.IO) {
                    c.xtream.seriesInfo(c.profiles.credentials(profile), item.streamRef.substringAfter(':'))
                }
                val episodes = info.episodes.map {
                    PlayableItem(
                        ref = SeriesViewModel.episodeRef(it),
                        title = item.name,
                        kind = ContentKind.EPISODE,
                        logo = info.cover ?: item.logo,
                        subtitle = SeriesViewModel.episodeLabel(it),
                        parentKey = item.streamRef,
                    )
                }
                if (episodes.isEmpty()) message.value = "Aucun épisode disponible pour « ${item.name} »."
                else series.value = SeriesEpisodes(item.streamRef, item.name, episodes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.toUserMessage()
            } finally {
                loadingRef.value = null
            }
        }
    }

    companion object {
        fun kindFor(item: PlayableItem) = when (item.kind) {
            ContentKind.LIVE -> ContentKind.LIVE
            ContentKind.MOVIE -> ContentKind.MOVIE
            else -> ContentKind.SERIES
        }
    }
}

@Composable
fun PortraitBrowser(model: PortraitBrowserModel, modifier: Modifier = Modifier) {
    val groups by model.groups.collectAsState()
    val group by model.group.collectAsState()
    val items by model.items.collectAsState()
    val series by model.series.collectAsState()
    val queue by model.queue.collectAsState()
    val current by model.current.collectAsState()
    val loading by model.loadingRef.collectAsState()
    val message by model.message.collectAsState()
    val episodes = series?.items ?: queue.takeIf { current?.kind == ContentKind.EPISODE }.orEmpty()
    val playingRef = if (model.kind == ContentKind.SERIES) series?.ref ?: current?.parentKey ?: current?.ref else current?.ref

    LazyColumn(
        modifier = modifier.fillMaxWidth().background(NovaColors.Background),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        current?.let { item ->
            item(key = "now") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.subtitle?.let { Text(it, color = NovaColors.TextSecondary, fontSize = 13.sp, maxLines = 2) }
                }
            }
        }
        if (model.kind == ContentKind.SERIES && episodes.isNotEmpty()) {
            item(key = "episodes") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        series?.let { "Épisodes · ${it.name}" } ?: "Épisodes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    val rowState = rememberLazyListState()
                    LaunchedEffect(episodes, current?.ref) {
                        val index = episodes.indexOfFirst { it.ref == current?.ref }
                        if (index >= 0) rowState.scrollToItem(index)
                    }
                    LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(episodes, key = { _, it -> it.ref }) { index, episode ->
                            EpisodeCard(episode, playing = episode.ref == current?.ref) { model.playEpisode(episodes, index) }
                        }
                    }
                }
            }
        }
        item(key = "groups") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (model.kind) {
                        ContentKind.LIVE -> "Chaînes"
                        ContentKind.MOVIE -> "Films"
                        else -> "Séries"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                GroupChips(groups, group, favoritesKey = "", onSelect = model::select, showFavorites = false, showAll = false)
            }
        }
        message?.let { text ->
            item(key = "message") { Text(text, color = NovaColors.Error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        if (group != null && items.isEmpty()) {
            item(key = "empty") { Text("Ce groupe est vide.", color = NovaColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            BrowserRow(
                item = item,
                playing = item.streamRef == playingRef,
                loading = loading == item.streamRef,
                opensEpisodes = model.kind == ContentKind.SERIES && item.streamRef.isXtreamSeries(),
            ) { model.pick(index) }
        }
    }
}

@Composable
private fun EpisodeCard(episode: PlayableItem, playing: Boolean, onClick: () -> Unit) {
    NovaCard(
        onClick = onClick,
        modifier = Modifier.width(160.dp).height(72.dp),
        color = if (playing) MaterialTheme.colorScheme.primary else NovaColors.Card,
    ) {
        val text = if (playing) MaterialTheme.colorScheme.onPrimary else NovaColors.TextPrimary
        Column(Modifier.padding(10.dp)) {
            Text(if (playing) "▶ En lecture" else "Épisode", fontSize = 11.sp, color = text.copy(alpha = 0.75f))
            Text(episode.subtitle ?: episode.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BrowserRow(item: ContentEntity, playing: Boolean, loading: Boolean, opensEpisodes: Boolean, onClick: () -> Unit) {
    NovaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = if (playing) NovaColors.Cyan.copy(alpha = 0.18f) else NovaColors.Card,
        focusScale = 1.02f,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val live = item.kind == ContentKind.LIVE
            ContentImage(
                url = item.logo,
                name = item.name,
                modifier = Modifier.size(width = if (live) 64.dp else 44.dp, height = if (live) 40.dp else 66.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.year?.let { Text(it, color = NovaColors.TextSecondary, fontSize = 12.sp) }
            }
            when {
                loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                playing -> Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "En lecture", tint = NovaColors.Cyan)
                opensEpisodes -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = NovaColors.TextSecondary)
            }
        }
    }
}
