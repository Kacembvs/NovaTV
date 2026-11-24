package com.nova.iptv.ui.browse

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nova.iptv.core.isTvLayout
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.isXtreamSeries
import com.nova.iptv.ui.components.*
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvCatalogPresentation
import kotlin.math.ceil

@Composable
fun CatalogScreen(kind: ContentKind, onOpenSeries: (String) -> Unit, onOpenMovie: (String) -> Unit, onAddProfile: () -> Unit, initialGroup: String? = null) {
    val vm = novaViewModel(key = "catalog-$kind-$initialGroup") { BrowseViewModel(it, kind) }
    if (initialGroup != null) LaunchedEffect(initialGroup) { vm.group.value = initialGroup }
    val context = LocalContext.current
    val title = if (kind == ContentKind.MOVIE) "Films" else "Séries"
    val hint = if (kind == ContentKind.MOVIE) "Rechercher un film" else "Rechercher une série"
    val open: (ContentEntity) -> Unit = { item ->
        when {
            item.kind == ContentKind.MOVIE -> onOpenMovie(item.streamRef)
            item.streamRef.isXtreamSeries() -> onOpenSeries(item.streamRef)
            else -> vm.play(context, item)
        }
    }
    if (isTvLayout()) {
        TvCatalogLayout(vm, title, hint, onAddProfile, open)
        return
    }
    BrowseScaffold(vm, title = title, searchHint = hint, onAddProfile = onAddProfile) { items, favorites ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val item = items[index] ?: return@items
                PosterCard(
                    title = item.name,
                    imageUrl = item.logo,
                    subtitle = item.year,
                    isFavorite = item.streamRef in favorites,
                    onToggleFavorite = { vm.toggleFavorite(item) },
                    onClick = { open(item) },
                )
            }
        }
    }
}

@Composable
private fun TvCatalogLayout(
    vm: BrowseViewModel,
    title: String,
    hint: String,
    onAddProfile: () -> Unit,
    onOpen: (ContentEntity) -> Unit,
) {
    val style = LocalTvInterface.current
    val horizontal = style.horizontalCategories
    val profile by vm.profile.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selection by vm.group.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val favorites by vm.favoriteKeys.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val catalog = vm.items.collectAsLazyPagingItems()
    val categoriesFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    if (profile.loading) return
    if (profile.profile == null) {
        EmptyState("Aucun profil IPTV", "Ajoutez une source pour afficher le catalogue.", actionLabel = "Ajouter un profil", onAction = onAddProfile)
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(profile.profile?.name.orEmpty(), color = NovaColors.TextSecondary, fontSize = 14.sp)
            }
            SearchField(query, { vm.query.value = it }, hint, Modifier.width(260.dp).focusRequester(searchFocus), horizontalPadding = 0.dp)
        }
        (sync as? SyncManager.State.Failed)?.let { Text(it.message, color = NovaColors.Error) }
        if (horizontal) {
            Box(Modifier.focusRequester(categoriesFocus).focusProperties {
                if (catalog.itemCount > 0) down = gridFocus
            }.focusGroup()) {
                GroupChips(groups, selection, BrowseViewModel.FAVORITES, { vm.group.value = it })
            }
            HiddenCategoriesButton(hiddenCount, vm::unlock)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!horizontal) {
                LazyColumn(
                    modifier = Modifier.width(200.dp).fillMaxHeight().focusRequester(categoriesFocus).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    val target = if (catalog.itemCount > 0) gridFocus else searchFocus
                    item {
                        TvActionButton(
                            "Tout le catalogue", { vm.group.value = null }, selected = selection == null,
                            modifier = Modifier.fillMaxWidth().focusProperties { right = target },
                        )
                    }
                    item {
                        TvActionButton(
                            "Mes favoris", { vm.group.value = BrowseViewModel.FAVORITES }, selected = selection == BrowseViewModel.FAVORITES,
                            modifier = Modifier.fillMaxWidth().focusProperties { right = target },
                        )
                    }
                    items(groups, key = { it.name }) { g ->
                        TvActionButton(
                            "${g.name} (${g.count})", { vm.group.value = g.name }, selected = selection == g.name,
                            modifier = Modifier.fillMaxWidth().focusProperties { right = target },
                        )
                    }
                    item { HiddenCategoriesButton(hiddenCount, vm::unlock) }
                }
            }
            if (catalog.itemCount == 0) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (catalog.loadState.refresh is LoadState.Loading || sync is SyncManager.State.Running) {
                        CircularProgressIndicator()
                    } else EmptyState("Aucun résultat", "Choisissez une autre catégorie ou modifiez la recherche.")
                }
            } else if (style.catalog == TvCatalogPresentation.LIST) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight().focusRequester(gridFocus).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(6.dp),
                ) {
                    items(catalog.itemCount, key = catalog.itemKey { it.id }) { index ->
                        val item = catalog[index] ?: return@items
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NovaCard({ onOpen(item) }, Modifier.weight(1f).focusProperties {
                                left = categoriesFocus
                                if (index == 0) up = searchFocus
                            }) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ContentImage(item.logo, item.name, Modifier.width(72.dp).height(104.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                        Text(listOfNotNull(item.year, item.groupTitle).joinToString(" · "), color = NovaColors.Cyan)
                                        Text(item.description ?: "Ouvrir la fiche", maxLines = 3, color = NovaColors.TextSecondary)
                                    }
                                }
                            }
                            FavoriteButton(item.streamRef in favorites, { vm.toggleFavorite(item) })
                        }
                    }
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val availableWidth = maxWidth.value - 12f + 16f
                    val widthColumns = (availableWidth / (style.cardWidth + 16f)).toInt()
                    // Keep the title and favorite control visible below the image.
                    val fittedCardWidth = ((maxHeight.value - 72f) * style.posterAspectRatio).coerceAtLeast(96f)
                    val heightColumns = ceil(availableWidth / (fittedCardWidth + 16f)).toInt()
                    val columns = maxOf(1, widthColumns, heightColumns)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        modifier = Modifier.fillMaxSize().focusRequester(gridFocus).focusGroup(),
                    ) {
                        items(count = catalog.itemCount, key = catalog.itemKey { it.id }) { index ->
                            val item = catalog[index] ?: return@items
                            PosterCard(
                                title = item.name,
                                imageUrl = item.logo,
                                subtitle = item.year,
                                isFavorite = item.streamRef in favorites,
                                onToggleFavorite = { vm.toggleFavorite(item) },
                                onClick = { onOpen(item) },
                                focusModifier = Modifier.focusProperties {
                                    if (!horizontal && index % columns == 0) left = categoriesFocus
                                    if (index < columns) up = if (horizontal) categoriesFocus else searchFocus
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
