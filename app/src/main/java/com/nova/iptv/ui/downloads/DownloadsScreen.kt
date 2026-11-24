package com.nova.iptv.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.iptv.core.isTvLayout
import com.nova.iptv.data.DownloadSettings
import com.nova.iptv.data.download.OfflineItem
import com.nova.iptv.data.download.OfflineStatus
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.Playback
import com.nova.iptv.ui.components.ContentImage
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.components.NovaIconButton
import com.nova.iptv.ui.components.NovaTextButton
import com.nova.iptv.ui.components.ScreenHeader
import com.nova.iptv.ui.components.SectionTitle
import com.nova.iptv.ui.components.TvSettingToggle
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.launch

/** A series folder, like the streaming apps: one entry per series, its episodes in order inside. */
internal data class DownloadGroup(val key: String, val title: String, val logo: String?, val items: List<OfflineItem>) {
    val isSeries get() = items.first().meta.kind == ContentKind.EPISODE
    val bytes get() = items.sumOf { it.bytes }
}

internal fun groupDownloads(items: List<OfflineItem>): List<DownloadGroup> {
    val movies = items.filter { it.meta.parentKey == null }
        .map { DownloadGroup(it.id, it.meta.title, it.meta.logo, listOf(it)) }
    val series = items.filter { it.meta.parentKey != null }
        .groupBy { it.meta.parentKey!! }
        .map { (key, episodes) ->
            val sorted = episodes.sortedWith(compareBy({ it.meta.season ?: 0 }, { it.meta.episode ?: 0 }, { it.meta.subtitle }))
            DownloadGroup(key, sorted.first().meta.title, sorted.first().meta.logo, sorted)
        }
    return (series + movies).sortedByDescending { group -> group.items.maxOf { it.updatedAt } }
}

@Composable
fun DownloadsScreen(onBrowse: () -> Unit) {
    val container = LocalAppContainer.current
    val downloads = container.downloads
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by downloads.items.collectAsStateWithLifecycle()
    val profile by container.profiles.activeProfile.collectAsStateWithLifecycle(null)
    val preferences by container.settings.downloads.collectAsStateWithLifecycle(DownloadSettings())
    val profileId = profile?.id
    val items = all.filter { it.meta.profileId == profileId }
    val groups = groupDownloads(items)
    val storage = remember(all) { downloads.storage() }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<OfflineItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val tv = isTvLayout()

    fun play(group: DownloadGroup, start: OfflineItem) {
        val ready = group.items.filter { it.status == OfflineStatus.COMPLETED }
        val index = ready.indexOfFirst { it.id == start.id }
        if (profileId == null || index < 0) return
        Playback.start(context, container, profileId, ready.map { it.meta.toPlayable() }, index)
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("downloads"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Téléchargements", "Vos films et épisodes, disponibles sans connexion") }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (storage.freeBytes > 0) {
                    LinearProgressIndicator(
                        progress = { storage.usedBytes.toFloat() / (storage.usedBytes + storage.freeBytes) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        trackColor = NovaColors.Card,
                    )
                }
                Text(
                    "NOVA : ${formatBytes(storage.usedBytes)}" +
                        if (storage.freeBytes > 0) " · Espace libre : ${formatBytes(storage.freeBytes)}" else "",
                    color = NovaColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingToggle(
                    "Téléchargements intelligents",
                    "Un épisode regardé jusqu'au bout est supprimé et le suivant est téléchargé.",
                    preferences.smartDownloads,
                    { scope.launch { container.settings.setSmartDownloads(it) } },
                )
                TvSettingToggle(
                    "Wi-Fi uniquement",
                    "Préserve vos données mobiles : les téléchargements attendent le Wi-Fi.",
                    preferences.wifiOnly,
                    { scope.launch { container.settings.setDownloadWifiOnly(it) } },
                )
            }
        }
        if (groups.isEmpty()) {
            item {
                EmptyState(
                    "Aucun téléchargement",
                    "Utilisez le bouton Télécharger sur un film ou un épisode pour le regarder ensuite sans connexion, " +
                        "en avion ou en déplacement.",
                    actionLabel = "Trouver un film",
                    onAction = onBrowse,
                )
            }
        }
        items(groups, key = { it.key }) { group ->
            val open = group.key in expanded
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.isSeries) {
                    NovaCard(
                        onClick = { expanded = if (open) expanded - group.key else expanded + group.key },
                        modifier = Modifier.fillMaxWidth(),
                        focusScale = 1.02f,
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ContentImage(group.logo, group.title, Modifier.width(if (tv) 72.dp else 56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(LocalTvInterface.current.cardRadius.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(group.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${group.items.size} épisode(s) · ${formatBytes(group.bytes)}",
                                    color = NovaColors.TextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Replier" else "Voir les épisodes")
                        }
                    }
                    if (open) group.items.forEach { episode ->
                        DownloadRow(episode, showPoster = false, onOpen = { selected = episode }, onPlay = { play(group, episode) })
                    }
                } else {
                    DownloadRow(group.items.first(), showPoster = true, onOpen = { selected = group.items.first() }, onPlay = { play(group, group.items.first()) })
                }
            }
        }
        if (groups.isNotEmpty()) {
            item {
                NovaTextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Rounded.Delete, null, tint = NovaColors.Error)
                    Spacer(Modifier.width(6.dp))
                    Text("Supprimer tous les téléchargements", color = NovaColors.Error)
                }
            }
        }
    }

    selected?.let { item ->
        val current = items.firstOrNull { it.id == item.id }
        if (current == null) selected = null
        else DownloadChoices(current, onDismiss = { selected = null }, onPlay = {
            groups.firstOrNull { group -> group.items.any { it.id == current.id } }?.let { play(it, current) }
        })
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Supprimer tous les téléchargements ?") },
            text = { Text("Les vidéos enregistrées sur cet appareil seront effacées. Vous pourrez les télécharger à nouveau.") },
            confirmButton = {
                NovaTextButton(onClick = {
                    items.forEach { downloads.remove(it.id) }
                    confirmClear = false
                }) { Text("Supprimer", color = NovaColors.Error) }
            },
            dismissButton = { NovaTextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun DownloadRow(item: OfflineItem, showPoster: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val ready = item.status == OfflineStatus.COMPLETED
    NovaCard(onClick = if (ready) onPlay else onOpen, modifier = Modifier.fillMaxWidth(), focusScale = 1.02f) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showPoster) {
                ContentImage(item.meta.logo, item.meta.title, Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(LocalTvInterface.current.cardRadius.dp)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (item.meta.kind == ContentKind.EPISODE) item.meta.subtitle ?: item.meta.title else item.meta.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = if (item.bytes > 0) " · ${formatBytes(item.bytes)}" else ""
                Text(
                    item.statusLabel() + size,
                    color = when (item.status) {
                        OfflineStatus.FAILED -> NovaColors.Error
                        OfflineStatus.COMPLETED -> NovaColors.Cyan
                        else -> NovaColors.TextSecondary
                    },
                    fontSize = 13.sp,
                )
                if (item.active || item.status == OfflineStatus.PAUSED) {
                    val percent = item.percent
                    if (percent != null) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
                }
            }
            if (ready) {
                NovaIconButton(onClick = onPlay, modifier = Modifier.semantics { contentDescription = "Lire ${item.meta.subtitle ?: item.meta.title}" }) {
                    Icon(Icons.Rounded.PlayArrow, null)
                }
            }
            NovaIconButton(onClick = onOpen, modifier = Modifier.semantics { contentDescription = "Options de téléchargement" }) {
                Icon(Icons.Rounded.MoreVert, null)
            }
        }
    }
}
