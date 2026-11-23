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
import com.nova.iptv.data.db.CustomGroupCount
import com.nova.iptv.data.db.CustomGroupEntity
import com.nova.iptv.data.db.CustomGroupItemEntity
import com.nova.iptv.data.db.FavoriteEntity
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.db.toPlayable
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileLoad(val loading: Boolean = true, val profile: ProfileEntity? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowseViewModel(private val c: AppContainer, val kind: ContentKind) : ViewModel() {
    val query = MutableStateFlow("")
    val group = MutableStateFlow<String?>(null)

    val profile: StateFlow<ProfileLoad> = c.profiles.activeProfile
        .map { ProfileLoad(loading = false, profile = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileLoad())

    private val profileId = c.profiles.activeProfile.map { it?.id }.distinctUntilChanged()

    /** Categories hidden by the parental control (empty when unlocked). */
    private val hidden: Flow<Set<String>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else c.parental.hiddenGroups(id) }

    /** Number of hidden categories, to offer unlocking. */
    val hiddenCount: StateFlow<Int> = hidden.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val groups: StateFlow<List<GroupCount>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.db.contentDao().groups(id, kind) }
        .combine(hidden) { list, h -> list.filter { it.name !in h } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun unlock(pin: String): Boolean = c.parental.verify(pin)

    val favoriteKeys: StateFlow<Set<String>> = profileId
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else c.db.libraryDao().favoriteKeys(id).map { it.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val syncState: StateFlow<SyncManager.State?> = combine(profileId, c.sync.states) { id, states -> id?.let { states[it] } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Now / next programmes keyed by channel EPG id (live only). */
    val epg: StateFlow<Map<String, NowNext>> = profileId
        .flatMapLatest { id -> if (id == null || kind != ContentKind.LIVE) flowOf(emptyMap()) else c.epg.nowNext(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Live TV (3-zone layout): channel shown in the preview pane. */
    val preview = MutableStateFlow<ContentEntity?>(null)

    /** Resolved after a short delay so quickly scrolling through channels does not open every stream. */
    val previewUrl: StateFlow<String?> = preview
        .debounce(600)
        .mapLatest { channel ->
            if (channel == null) return@mapLatest null
            val profile = c.profiles.get(channel.profileId) ?: return@mapLatest null
            withContext(Dispatchers.Default) { c.profiles.streamUrls(profile, listOf(channel.streamRef)).firstOrNull() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: Flow<PagingData<ContentEntity>> = combine(
        profileId,
        group,
        query.debounce(250).map { it.trim() }.distinctUntilChanged(),
        hidden,
    ) { id, g, q, h -> Params(id, g, q, h.toList()) }
        .flatMapLatest { (id, g, q, h) ->
            if (id == null) {
                flowOf(PagingData.empty())
            } else {
                val dao = c.db.contentDao()
                Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false)) {
                    val customId = customGroupId(g)
                    when {
                        customId != null -> dao.pageCustom(id, customId, q, h)
                        g == FAVORITES -> dao.pageFavorites(id, kind, q, h)
                        else -> dao.page(id, kind, g, q, h)
                    }
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    private data class Params(val id: Long?, val group: String?, val query: String, val hidden: List<String>)

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
            val customId = customGroupId(group.value)
            val siblings = when {
                // Zapping stays inside the user's own group when it is selected.
                customId != null && item.kind == ContentKind.LIVE -> c.db.contentDao().listCustom(item.profileId, customId)
                item.kind == ContentKind.LIVE || item.kind == ContentKind.SERIES ->
                    c.db.contentDao().listGroup(item.profileId, item.kind, item.groupTitle)
                else -> emptyList()
            }
            val queue = siblings.takeIf { list -> list.any { it.id == item.id } } ?: listOf(item)
            val start = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            Playback.start(context, c, item.profileId, queue.map { it.toPlayable() }, start)
        }
    }

    // region Custom channel groups ("Mes groupes")

    val customGroups: StateFlow<List<CustomGroupCount>> = profileId
        .flatMapLatest { id -> if (id == null || kind != ContentKind.LIVE) flowOf(emptyList()) else c.db.customGroupDao().observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun groupIdsFor(channel: ContentEntity): Flow<List<Long>> = c.db.customGroupDao().groupIdsFor(channel.profileId, channel.streamRef)

    fun setInGroup(groupId: Long, channel: ContentEntity, member: Boolean) {
        viewModelScope.launch {
            val dao = c.db.customGroupDao()
            if (member) dao.addItem(CustomGroupItemEntity(groupId, channel.streamRef)) else dao.removeItem(groupId, channel.streamRef)
        }
    }

    fun createGroup(name: String, channel: ContentEntity?) {
        val clean = name.trim().take(40)
        val profile = this.profile.value.profile ?: return
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val dao = c.db.customGroupDao()
            val id = dao.findByName(profile.id, clean)?.id ?: dao.insert(CustomGroupEntity(profileId = profile.id, name = clean))
            channel?.let { dao.addItem(CustomGroupItemEntity(id, it.streamRef)) }
        }
    }

    fun renameGroup(id: Long, name: String) {
        val clean = name.trim().take(40)
        if (clean.isNotEmpty()) viewModelScope.launch { c.db.customGroupDao().rename(id, clean) }
    }

    fun deleteGroup(id: Long) {
        if (customGroupId(group.value) == id) group.value = null
        viewModelScope.launch { c.db.customGroupDao().delete(id) }
    }

    // endregion

    companion object {
        const val FAVORITES = "\u0000favorites"
        private const val CUSTOM_PREFIX = "\u0000custom:"

        fun customKey(id: Long) = CUSTOM_PREFIX + id
        fun customGroupId(selection: String?): Long? = selection?.takeIf { it.startsWith(CUSTOM_PREFIX) }?.removePrefix(CUSTOM_PREFIX)?.toLongOrNull()
    }
}
