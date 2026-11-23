package com.nova.iptv.ui.browse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nova.iptv.AppContainer
import com.nova.iptv.data.NowNext
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.FavoriteEntity
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.Playback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileLoad(val loading: Boolean = true, val profile: ProfileEntity? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowseViewModel(private val c: AppContainer, val kind: ContentKind) : ViewModel() {
    val query = MutableStateFlow("")
    val group = MutableStateFlow<String?>(null)

    val profile: StateFlow<ProfileLoad> = c.profiles.activeProfile
        .map { ProfileLoad(loading = false, profile = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileLoad())

    private val profileId = c.profiles.activeProfile.map { it?.id }.distinctUntilChanged()

    val groups: StateFlow<List<GroupCount>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.db.contentDao().groups(id, kind) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteKeys: StateFlow<Set<String>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else c.db.libraryDao().favoriteKeys(id).map { it.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val syncState: StateFlow<SyncManager.State?> = combine(profileId, c.sync.states) { id, states -> id?.let { states[it] } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Now / next programmes keyed by channel EPG id (live only). */
    val epg: StateFlow<Map<String, NowNext>> = profileId
        .flatMapLatest { id -> if (id == null || kind != ContentKind.LIVE) flowOf(emptyMap()) else c.epg.nowNext(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val items: Flow<PagingData<ContentEntity>> = combine(
        profileId,
        group,
        query.debounce(250).map { it.trim() }.distinctUntilChanged(),
    ) { id, g, q -> Triple(id, g, q) }
        .flatMapLatest { (id, g, q) ->
            if (id == null) {
                flowOf(PagingData.empty())
            } else {
                val dao = c.db.contentDao()
                Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false)) {
                    if (g == FAVORITES) dao.pageFavorites(id, kind, q) else dao.page(id, kind, g, q)
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    fun toggleFavorite(item: ContentEntity) {
        viewModelScope.launch {
            val library = c.db.libraryDao()
            if (item.streamRef in favoriteKeys.value) {
                library.removeFavorite(item.profileId, item.streamRef)
            } else {
                library.addFavorite(FavoriteEntity(item.profileId, item.streamRef, item.kind, item.name, item.logo))
            }
        }
    }

    /** Live channels and M3U episodes are queued with their group, which enables zapping / next episode. */
    fun play(context: Context, item: ContentEntity) {
        viewModelScope.launch {
            val siblings = if (item.kind == ContentKind.LIVE || item.kind == ContentKind.SERIES) {
                c.db.contentDao().listGroup(item.profileId, item.kind, item.groupTitle)
            } else {
                emptyList()
            }
            val queue = siblings.takeIf { list -> list.any { it.id == item.id } } ?: listOf(item)
            val start = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            Playback.start(context, c, item.profileId, queue.map { it.toPlayable() }, start)
        }
    }

    companion object {
        const val FAVORITES = "\u0000favorites"
    }
}
