package com.nova.iptv.ui.browse

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nova.iptv.core.formatClock
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.TvLivePresentation

@Composable
internal fun TvLiveGallery(vm: BrowseViewModel, onOpenGuide: () -> Unit, onAddProfile: () -> Unit) {
    val context = LocalContext.current
    val style = LocalTvInterface.current
    val programmesFirst = style.live == TvLivePresentation.PROGRAMMES
    val profile by vm.profile.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val customGroups by vm.customGroups.collectAsStateWithLifecycle()
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val url by vm.previewUrl.collectAsStateWithLifecycle()
    val hidden by vm.hiddenCount.collectAsStateWithLifecycle()
    val channels = vm.items.collectAsLazyPagingItems()
    val channelsFocus = remember { FocusRequester() }
    val categoriesFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var managing by remember { mutableStateOf(false) }
    var groupsFor by remember { mutableStateOf<ContentEntity?>(null) }
    if (profile.loading) return
    if (profile.profile == null) {
        EmptyState("Aucun profil IPTV", "Ajoutez une source pour retrouver vos chaînes.", actionLabel = "Ajouter un profil", onAction = onAddProfile)
        return
    }

    @Composable
    fun Channel(channel: ContentEntity, index: Int) {
        val nowNext = channel.tvgId?.let { epg[it] }
        NovaCard(
            { vm.play(context, channel) },
            Modifier.fillMaxWidth().focusProperties { if (index == 0) up = categoriesFocus }
                .onFocusChanged { if (it.hasFocus) vm.preview.value = channel },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContentImage(channel.logo, channel.name, Modifier.width(64.dp).height(42.dp))
                    Text("${index + 1} · ${channel.name}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(nowNext?.now?.title ?: channel.groupTitle, fontSize = if (programmesFirst) 21.sp else 15.sp, maxLines = 2)
                nowNext?.now?.let {
                    Text("${formatClock(it.startMs)} – ${formatClock(it.endMs)}", color = NovaColors.Cyan, fontSize = 12.sp)
                }
                if (programmesFirst) nowNext?.next?.let {
                    Text("Ensuite · ${it.title}", color = NovaColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (programmesFirst) "À l'antenne" else "Toutes vos chaînes", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SearchField(query, { vm.query.value = it }, "Rechercher", Modifier.width(220.dp).focusRequester(searchFocus), 0.dp)
            TvActionButton("Guide TV", onOpenGuide)
            TvActionButton("Mes groupes", { managing = true })
        }
        Box(Modifier.focusRequester(categoriesFocus).focusProperties {
            if (channels.itemCount > 0) down = channelsFocus
        }.focusGroup()) {
            GroupChips(groups, group, BrowseViewModel.FAVORITES, { vm.group.value = it },
                extra = customGroups.map { BrowseViewModel.customKey(it.id) to it.name })
        }
        HiddenCategoriesButton(hidden, vm::unlock)
        when (val state = sync) {
            is SyncManager.State.Running -> SyncBanner("Chargement… ${state.loaded} contenus", false)
            is SyncManager.State.Failed -> SyncBanner(state.message, true)
            null -> Unit
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val listModifier = Modifier.weight(1f).fillMaxHeight().focusRequester(channelsFocus).focusGroup()
            if (channels.itemCount == 0) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (channels.loadState.refresh is LoadState.Loading) CircularProgressIndicator()
                    else EmptyState("Aucune chaîne", "Modifiez la catégorie ou la recherche.")
                }
            } else if (programmesFirst) {
                LazyColumn(listModifier, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                    items(channels.itemCount, key = channels.itemKey { it.id }) { index ->
                        channels[index]?.let { Channel(it, index) }
                    }
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(210.dp), listModifier,
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(channels.itemCount, key = channels.itemKey { it.id }) { index ->
                        channels[index]?.let { Channel(it, index) }
                    }
                }
            }
            LivePreviewPane(
                preview, url, preview?.tvgId?.let { epg[it] }, true, preview?.streamRef in favorites,
                { preview?.let { vm.play(context, it) } }, { preview?.let(vm::toggleFavorite) }, { groupsFor = preview },
                Modifier.width(260.dp).fillMaxHeight(),
            )
        }
    }
    if (managing) CustomGroupsDialog(vm, null) { managing = false }
    groupsFor?.let { channel -> CustomGroupsDialog(vm, channel) { groupsFor = null } }
}
