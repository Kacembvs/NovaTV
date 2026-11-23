package com.nova.iptv.data

import androidx.room.withTransaction
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.source.M3uParser
import com.nova.iptv.data.source.XtreamClient
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.domain.XTREAM_SERIES_PREFIX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads and stores a profile catalogue in batches, so the UI (which observes Room) shows
 * categories and contents progressively. Playback never depends on a running sync.
 */
class SyncManager(
    private val db: NovaDatabase,
    private val profiles: ProfileRepository,
    private val http: OkHttpClient,
    private val xtream: XtreamClient,
    private val scope: CoroutineScope,
    /** Called after a successful catalogue sync (used to refresh the EPG). */
    private val onSynced: (Long) -> Unit = {},
) {
    sealed interface State {
        data class Running(val loaded: Int) : State
        data class Failed(val message: String) : State
    }

    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states.asStateFlow()

    private val jobs = ConcurrentHashMap<Long, Job>()

    fun sync(profileId: Long) {
        jobs.remove(profileId)?.cancel()
        val job = scope.launch {
            val profile = db.profileDao().get(profileId) ?: return@launch
            setState(profileId, State.Running(0))
            try {
                val count = when (profile.type) {
                    ProfileType.M3U -> syncM3u(profile)
                    ProfileType.XTREAM -> syncXtream(profile)
                }
                db.profileDao().markSynced(profileId, System.currentTimeMillis(), count)
                setState(profileId, null)
                onSynced(profileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.toUserMessage()
                db.profileDao().markSyncFailed(profileId, message)
                setState(profileId, State.Failed(message))
            }
        }
        jobs[profileId] = job
        job.invokeOnCompletion { jobs.remove(profileId, job) }
    }

    fun cancel(profileId: Long) {
        jobs.remove(profileId)?.cancel()
        setState(profileId, null)
    }

    /** Runs a sync and waits for it (background worker). */
    suspend fun syncAndWait(profileId: Long) {
        sync(profileId)
        jobs[profileId]?.join()
    }

    private fun setState(profileId: Long, state: State?) {
        _states.update { if (state == null) it - profileId else it + (profileId to state) }
    }

    private suspend fun syncM3u(profile: ProfileEntity): Int {
        val url = profile.url.trim().toHttpUrlOrNull() ?: throw IOException("Adresse de playlist invalide.")
        val batch = Batch(profile.id)
        val parser = M3uParser()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Le serveur a répondu HTTP ${response.code}.")
            val reader = (response.body ?: throw IOException("Playlist vide.")).charStream().buffered()
            var index = 0
            for (entry in parser.parse(reader)) {
                batch.add(
                    ContentEntity(
                        profileId = profile.id,
                        kind = entry.kind,
                        name = entry.name,
                        groupTitle = entry.group,
                        logo = entry.logo,
                        streamRef = entry.url,
                        tvgId = entry.tvgId,
                        sortIndex = index++,
                    )
                )
            }
        }
        val count = batch.finish()
        db.profileDao().setEpgUrl(profile.id, parser.epgUrl)
        return count
    }

    private suspend fun syncXtream(profile: ProfileEntity): Int {
        val credentials = profiles.credentials(profile)
        xtream.authenticate(credentials)
        val batch = Batch(profile.id)
        var index = 0

        val liveCategories = xtream.categories(credentials, "get_live_categories")
        xtream.forEachStream(credentials, "get_live_streams") { item ->
            val id = item["stream_id"] ?: return@forEachStream
            batch.add(
                ContentEntity(
                    profileId = profile.id,
                    kind = ContentKind.LIVE,
                    name = item["name"].orEmpty(),
                    groupTitle = liveCategories[item["category_id"]] ?: M3uParser.DEFAULT_GROUP,
                    logo = item["stream_icon"]?.ifBlank { null },
                    streamRef = "live/$id.ts",
                    tvgId = item["epg_channel_id"]?.ifBlank { null },
                    sortIndex = index++,
                )
            )
        }

        val vodCategories = xtream.categories(credentials, "get_vod_categories")
        xtream.forEachStream(credentials, "get_vod_streams") { item ->
            val id = item["stream_id"] ?: return@forEachStream
            val extension = item["container_extension"]?.ifBlank { null } ?: "mp4"
            batch.add(
                ContentEntity(
                    profileId = profile.id,
                    kind = ContentKind.MOVIE,
                    name = item["name"].orEmpty(),
                    groupTitle = vodCategories[item["category_id"]] ?: M3uParser.DEFAULT_GROUP,
                    logo = item["stream_icon"]?.ifBlank { null },
                    streamRef = "movie/$id.$extension",
                    sortIndex = index++,
                    year = item["year"]?.ifBlank { null },
                    rating = item["rating"]?.ifBlank { null },
                    addedAt = item["added"]?.toLongOrNull()?.times(1000),
                )
            )
        }

        val seriesCategories = xtream.categories(credentials, "get_series_categories")
        xtream.forEachStream(credentials, "get_series") { item ->
            val id = item["series_id"] ?: return@forEachStream
            batch.add(
                ContentEntity(
                    profileId = profile.id,
                    kind = ContentKind.SERIES,
                    name = item["name"].orEmpty(),
                    groupTitle = seriesCategories[item["category_id"]] ?: M3uParser.DEFAULT_GROUP,
                    logo = item["cover"]?.ifBlank { null },
                    streamRef = "$XTREAM_SERIES_PREFIX$id",
                    sortIndex = index++,
                    year = (item["releaseDate"] ?: item["release_date"])?.take(4)?.ifBlank { null },
                    rating = item["rating"]?.ifBlank { null },
                    description = item["plot"]?.ifBlank { null },
                    addedAt = item["last_modified"]?.toLongOrNull()?.times(1000),
                )
            )
        }
        return batch.finish()
    }

    /** Replaces the old catalogue only once the first new batch is ready, so the UI never stays empty. */
    private inner class Batch(private val profileId: Long) {
        private val buffer = ArrayList<ContentEntity>(BATCH_SIZE)
        private var replaced = false
        private var count = 0

        suspend fun add(item: ContentEntity) {
            buffer += item
            if (buffer.size >= BATCH_SIZE) flush()
        }

        suspend fun finish(): Int {
            flush()
            return count
        }

        private suspend fun flush() {
            currentCoroutineContext().ensureActive()
            val items = buffer.toList()
            buffer.clear()
            val dao = db.contentDao()
            if (!replaced) {
                db.withTransaction {
                    dao.deleteForProfile(profileId)
                    dao.insertAll(items)
                }
                replaced = true
            } else if (items.isNotEmpty()) {
                dao.insertAll(items)
            }
            count += items.size
            setState(profileId, State.Running(count))
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
