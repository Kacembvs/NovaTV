package com.nova.iptv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import com.nova.iptv.ui.components.NovaButton as Button
import com.nova.iptv.ui.components.NovaTextButton as TextButton
import com.nova.iptv.ui.components.NovaIconButton as IconButton
import com.nova.iptv.ui.components.NovaFilledTonalButton as FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nova.iptv.core.formatClock
import com.nova.iptv.core.isTvLayout
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.player.PreviewPlayer
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvInterface
import com.nova.iptv.ui.theme.TvLivePresentation

/**
 * TiviMate-style Live TV for TV and large screens: categories | channels | preview + programme.
 * Remote: focusing a channel previews it, OK opens full screen. Touch: first tap previews, second tap opens.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveTvLayout(vm: BrowseViewModel, onOpenGuide: () -> Unit, onAddProfile: () -> Unit) {
    val context = LocalContext.current
    val tv = isTvLayout()
    val style = LocalTvInterface.current
    if (tv && style.live != TvLivePresentation.PREVIEW) {
        TvLiveGallery(vm, onOpenGuide, onAddProfile)
        return
    }
    val profileLoad by vm.profile.collectAsStateWithLifecycle()
    if (profileLoad.loading) return
    val profile = profileLoad.profile
    if (profile == null) {
        EmptyState(
            title = "Aucun profil IPTV",
            message = "Ajoutez une playlist M3U ou un compte Xtream Codes pour afficher vos chaînes.",
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
    val epg by vm.epg.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewUrl by vm.previewUrl.collectAsStateWithLifecycle()
    val customGroups by vm.customGroups.collectAsStateWithLifecycle()
    var managingGroups by remember { mutableStateOf(false) }
    var groupsFor by remember { mutableStateOf<ContentEntity?>(null) }
    groupsFor?.let { CustomGroupsDialog(vm, it) { groupsFor = null } }
    if (managingGroups) CustomGroupsDialog(vm, null) { managingGroups = false }
    val items = vm.items.collectAsLazyPagingItems()
    val channelsFocus = remember { FocusRequester() }
    val categoryModifier = if (tv) {
        Modifier.focusProperties { right = if (items.itemCount > 0) channelsFocus else FocusRequester.Cancel }
    } else Modifier

    Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Zone 1: categories
        Column(Modifier.width(if (tv && style == TvInterface.GUIDE) 180.dp else if (tv) 240.dp else 200.dp).fillMaxHeight()) {
            Text("TV en direct", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            Text(profile.name, color = NovaColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "all") { CategoryItem("Toutes les chaînes", null, group == null, modifier = categoryModifier) { vm.group.value = null } }
                item(key = "fav") { CategoryItem("Favoris", null, group == BrowseViewModel.FAVORITES, star = true, modifier = categoryModifier) { vm.group.value = BrowseViewModel.FAVORITES } }
                items(customGroups, key = { "c:" + it.id }) { g ->
                    val key = BrowseViewModel.customKey(g.id)
                    CategoryItem(g.name, g.count, group == key, folder = true, modifier = categoryModifier) { vm.group.value = key }
                }
                item(key = "manage") {
                    TextButton(onClick = { managingGroups = true }) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("  Mes groupes", fontSize = 13.sp)
                    }
                }
                items(groups, key = { "g:" + it.name }) { g ->
                    CategoryItem(g.name, g.count, group == g.name, modifier = categoryModifier) { vm.group.value = g.name }
                }
                item(key = "hidden") {
                    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
                    HiddenCategoriesButton(hiddenCount, vm::unlock)
                }
            }
        }

        // Zone 2: channels
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { vm.query.value = it }, if (tv) "Rechercher" else "Rechercher une chaîne", Modifier.weight(1f), horizontalPadding = if (tv) 0.dp else 20.dp)
                if (tv) {
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Rounded.CalendarViewWeek, contentDescription = "Guide TV")
                    }
                } else {
                    FilledTonalButton(onClick = onOpenGuide) {
                        Icon(Icons.Rounded.CalendarViewWeek, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guide TV")
                    }
                }
            }
            when (val s = sync) {
                is SyncManager.State.Running -> SyncBanner("Chargement… ${s.loaded} contenus", isError = false, Modifier.padding(top = 8.dp))
                is SyncManager.State.Failed -> SyncBanner(s.message, isError = true, Modifier.padding(top = 8.dp))
                null -> Unit
            }
            if (items.itemCount == 0) {
                EmptyState("Aucune chaîne", if (query.isNotBlank()) "Aucun résultat pour « $query »." else "Cette catégorie est vide.")
            }
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().then(if (tv) Modifier.focusRequester(channelsFocus).focusGroup() else Modifier),
            ) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    val channel = items[index] ?: return@items
                    val programme = channel.tvgId?.let { epg[it]?.now }
                    val selected = preview?.id == channel.id
                    NovaCard(
                        onClick = {
                            if (tv || selected) vm.play(context, channel) else vm.preview.value = channel
                        },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus && tv) vm.preview.value = channel },
                        color = if (selected) NovaColors.Card else NovaColors.Background2,
                        focusScale = 1.02f,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                color = NovaColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(32.dp),
                            )
                            ContentImage(
                                channel.logo,
                                channel.name,
                                Modifier.size(width = 56.dp, height = 38.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit,
                                placeholderSize = 14,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    programme?.title ?: channel.groupTitle,
                                    color = NovaColors.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (channel.streamRef in favorites) {
                                Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = NovaColors.Cyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Zone 3: preview and programme
        LivePreviewPane(
            preview, previewUrl, preview?.tvgId?.let { epg[it] }, tv,
            preview?.streamRef in favorites,
            onPlay = { preview?.let { vm.play(context, it) } },
            onFavorite = { preview?.let(vm::toggleFavorite) },
            onGroup = { groupsFor = preview },
            modifier = Modifier.weight(0.9f).fillMaxHeight().padding(end = 8.dp),
        )
    }
}

@Composable
private fun CategoryItem(name: String, count: Int?, selected: Boolean, star: Boolean = false, folder: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    NovaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (selected) NovaColors.Card else Color.Transparent,
        focusScale = 1.02f,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (star || folder) {
                Icon(if (star) Icons.Rounded.Star else Icons.Rounded.Folder, contentDescription = null, tint = NovaColors.Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) NovaColors.Cyan else NovaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            count?.let { Text("$it", color = NovaColors.TextSecondary, fontSize = 12.sp) }
        }
    }
}
