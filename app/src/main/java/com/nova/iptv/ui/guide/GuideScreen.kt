package com.nova.iptv.ui.guide

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nova.iptv.AppContainer
import com.nova.iptv.core.formatClock
import com.nova.iptv.core.isTelevision
import com.nova.iptv.data.EpgManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.EpgProgrammeEntity
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.Playback
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.GroupChips
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

private const val HALF_HOUR_MS = 30 * 60_000L
private const val WINDOW_MS = 12 * 3_600_000L

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModel(private val c: AppContainer) : ViewModel() {
    /** Start of the timeline: the previous half hour minus 30 minutes. */
    val windowStart: Long = System.currentTimeMillis().let { it - it % HALF_HOUR_MS - HALF_HOUR_MS }
    val group = MutableStateFlow<String?>(null)

    val profile: StateFlow<ProfileEntity?> = c.profiles.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val profileId = c.profiles.activeProfile.map { it?.id }.distinctUntilChanged()

    val groups: StateFlow<List<GroupCount>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.db.contentDao().groups(id, ContentKind.LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<ContentEntity>?> = combine(profileId, group) { id, g -> id to g }
        .flatMapLatest { (id, g) -> if (id == null) flowOf(emptyList()) else c.db.contentDao().guideChannels(id, g) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val programmes: StateFlow<Map<String, List<EpgProgrammeEntity>>> = profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else c.db.epgDao().window(id, windowStart, windowStart + WINDOW_MS)
        }
        .map { list -> list.groupBy { it.channelId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val epgState: StateFlow<EpgManager.State?> = combine(profileId, c.epg.states) { id, s -> id?.let { s[it] } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshGuide() {
        profile.value?.let { c.epg.refresh(it.id) }
    }

    fun play(context: Context, list: List<ContentEntity>, index: Int) {
        val profileId = list.getOrNull(index)?.profileId ?: return
        Playback.start(context, c, profileId, list.map { it.toPlayable() }, index)
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    val vm = novaViewModel { GuideViewModel(it) }
    val context = LocalContext.current
    val tv = context.isTelevision()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val programmes by vm.programmes.collectAsStateWithLifecycle()
    val epgState by vm.epgState.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<Pair<Int, EpgProgrammeEntity>?>(null) }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val dpPerMinute = if (tv) 6.dp else 4.dp
    val channelColumn = if (tv) 200.dp else 120.dp
    val rowHeight = if (tv) 72.dp else 60.dp
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hasChannels = !channels.isNullOrEmpty()
    LaunchedEffect(hasChannels) {
        if (!hasChannels) return@LaunchedEffect
        val minutes = ((System.currentTimeMillis() - vm.windowStart) / 60_000L - 15).coerceAtLeast(0)
        scroll.scrollTo(with(density) { (dpPerMinute * minutes.toFloat()).roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
            Column(Modifier.weight(1f)) {
                Text("Guide TV", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val status = when (val s = epgState) {
                    is EpgManager.State.Running -> "Chargement du guide… ${s.programmes} programmes"
                    is EpgManager.State.Failed -> s.message
                    null -> profile?.name.orEmpty()
                }
                Text(
                    status,
                    color = if (epgState is EpgManager.State.Failed) NovaColors.Error else NovaColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            TextButton(onClick = vm::refreshGuide, enabled = epgState !is EpgManager.State.Running) { Text("Actualiser") }
        }
        GroupChips(groups, group, favoritesKey = "", onSelect = { vm.group.value = it }, showFavorites = false)
        Spacer(Modifier.height(8.dp))

        val list = channels ?: return@Column
        if (list.isEmpty()) {
            val running = epgState is EpgManager.State.Running
            EmptyState(
                title = if (running) "Chargement du guide…" else "Aucun programme",
                message = if (running) {
                    "Les programmes apparaîtront au fur et à mesure."
                } else {
                    "Aucune chaîne de cette sélection n'a de guide. Vérifiez la source XMLTV du profil (onglet Réglages)."
                },
                actionLabel = if (running) null else "Actualiser le guide",
                onAction = vm::refreshGuide,
            )
            return@Column
        }

        TimeHeader(vm.windowStart, channelColumn, dpPerMinute, scroll)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(list, key = { _, c -> c.id }) { index, channel ->
                Row(Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 2.dp)) {
                    NovaCard(
                        onClick = { vm.play(context, list, index) },
                        modifier = Modifier.width(channelColumn).fillMaxHeight().padding(start = 8.dp, end = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        focusScale = 1.02f,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                            ContentImage(
                                channel.logo,
                                channel.name,
                                Modifier.size(if (tv) 48.dp else 36.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit,
                                placeholderSize = 14,
                            )
                            Text(
                                channel.name,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    ProgrammeTimeline(
                        programmes = channel.tvgId?.let { programmes[it] }.orEmpty(),
                        windowStart = vm.windowStart,
                        now = now,
                        dpPerMinute = dpPerMinute,
                        scroll = scroll,
                        onClick = { programme ->
                            if (programme.startMs <= now && programme.endMs > now) {
                                vm.play(context, list, index)
                            } else {
                                details = index to programme
                            }
                        },
                    )
                }
            }
        }
    }

    details?.let { (index, programme) ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(programme.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${channels?.getOrNull(index)?.name.orEmpty()} · ${formatClock(programme.startMs)}–${formatClock(programme.endMs)}",
                        color = NovaColors.Cyan,
                    )
                    Text(programme.description ?: "Pas de description.", color = NovaColors.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    details = null
                    channels?.let { vm.play(context, it, index) }
                }) { Text("Regarder la chaîne") }
            },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun TimeHeader(windowStart: Long, channelColumn: Dp, dpPerMinute: Dp, scroll: ScrollState) {
    Row(Modifier.fillMaxWidth().height(28.dp)) {
        Spacer(Modifier.width(channelColumn))
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            for (slot in 0 until (WINDOW_MS / HALF_HOUR_MS).toInt()) {
                Text(
                    formatClock(windowStart + slot * HALF_HOUR_MS),
                    color = NovaColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(dpPerMinute * 30f).padding(start = 4.dp),
                )
            }
        }
    }
}

/** All rows share one [ScrollState], so the whole grid scrolls horizontally together. */
@Composable
private fun RowScope.ProgrammeTimeline(
    programmes: List<EpgProgrammeEntity>,
    windowStart: Long,
    now: Long,
    dpPerMinute: Dp,
    scroll: ScrollState,
    onClick: (EpgProgrammeEntity) -> Unit,
) {
    val windowEnd = windowStart + WINDOW_MS
    val totalWidth = dpPerMinute * (WINDOW_MS / 60_000f)
    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
        Box(Modifier.width(totalWidth).fillMaxHeight()) {
            if (programmes.isEmpty()) {
                Text(
                    "Pas d'information",
                    color = NovaColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                )
            }
            programmes.forEach { programme ->
                val start = programme.startMs.coerceAtLeast(windowStart)
                val end = programme.endMs.coerceAtMost(windowEnd)
                if (end <= start) return@forEach
                val offset = dpPerMinute * ((start - windowStart) / 60_000f)
                val width = dpPerMinute * ((end - start) / 60_000f)
                val airing = programme.startMs <= now && programme.endMs > now
                NovaCard(
                    onClick = { onClick(programme) },
                    modifier = Modifier.offset(x = offset).width(width).fillMaxHeight().padding(horizontal = 1.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (airing) NovaColors.Card else NovaColors.Background2,
                    focusScale = 1f,
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center) {
                        Text(
                            programme.title,
                            fontSize = 12.sp,
                            fontWeight = if (airing) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${formatClock(programme.startMs)}–${formatClock(programme.endMs)}",
                            fontSize = 10.sp,
                            color = NovaColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (now in windowStart until windowEnd) {
                Box(
                    Modifier
                        .offset(x = dpPerMinute * ((now - windowStart) / 60_000f))
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(NovaColors.Cyan),
                )
            }
        }
    }
}
