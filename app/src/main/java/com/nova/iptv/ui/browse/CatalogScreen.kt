package com.nova.iptv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.compose.itemKey
import com.nova.iptv.core.isTelevision
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.isXtreamSeries
import com.nova.iptv.ui.components.PosterCard
import com.nova.iptv.ui.novaViewModel

@Composable
fun CatalogScreen(kind: ContentKind, onOpenSeries: (String) -> Unit, onOpenMovie: (String) -> Unit, onAddProfile: () -> Unit) {
    val vm = novaViewModel(key = "catalog-$kind") { BrowseViewModel(it, kind) }
    val context = LocalContext.current
    val tv = context.isTelevision()
    val title = if (kind == ContentKind.MOVIE) "Films" else "Séries"
    val hint = if (kind == ContentKind.MOVIE) "Rechercher un film" else "Rechercher une série"

    BrowseScaffold(vm, title = title, searchHint = hint, onAddProfile = onAddProfile) { items, favorites ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (tv) 160.dp else 108.dp),
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
                    onClick = {
                        when {
                            item.kind == ContentKind.MOVIE -> onOpenMovie(item.streamRef)
                            item.streamRef.isXtreamSeries() -> onOpenSeries(item.streamRef)
                            else -> vm.play(context, item)
                        }
                    },
                )
            }
        }
    }
}
