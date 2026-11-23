package com.nova.iptv.data

import android.util.Xml
import androidx.room.withTransaction
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.db.EpgProgrammeEntity
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.source.XmltvParser
import com.nova.iptv.data.source.XtreamUrls
import com.nova.iptv.domain.ProfileType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

data class NowNext(val now: EpgProgrammeEntity?, val next: EpgProgrammeEntity?)

/**
 * Downloads the XMLTV guide independently of the channel list (channels never wait for the EPG)
 * and keeps only a time window for the profile's channels.
 */
class EpgManager(
    private val db: NovaDatabase,
    private val profiles: ProfileRepository,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data class Running(val programmes: Int) : State
        data class Failed(val message: String) : State
    }

    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states.asStateFlow()
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun epgUrl(profile: ProfileEntity): String? {
        profile.customEpgUrl?.trim()?.ifEmpty { null }?.let { return it }
        return when (profile.type) {
            ProfileType.M3U -> profile.epgUrl
            ProfileType.XTREAM -> {
                val c = profiles.credentials(profile)
                (XtreamUrls.normalizeBase(c.baseUrl) + "/xmltv.php").toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("username", c.username)
                    ?.addQueryParameter("password", c.password)
                    ?.build()?.toString()
            }
        }
    }

    fun refresh(profileId: Long) {
        jobs.remove(profileId)?.cancel()
        val job = scope.launch {
            val profile = db.profileDao().get(profileId) ?: return@launch
            val url = epgUrl(profile)
            if (url == null) {
                val message = "Aucune source de guide TV. Ajoutez une adresse XMLTV dans le profil."
                db.profileDao().markEpgFailed(profileId, message)
                setState(profileId, State.Failed(message))
                return@launch
            }
            setState(profileId, State.Running(0))
            try {
                download(profileId, url)
                db.profileDao().markEpgSynced(profileId, System.currentTimeMillis())
                setState(profileId, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.toUserMessage()
                db.profileDao().markEpgFailed(profileId, message)
                setState(profileId, State.Failed(message))
            }
        }
        jobs[profileId] = job
        job.invokeOnCompletion { jobs.remove(profileId, job) }
    }

    /** Called at start-up: refreshes the guide in the background when it is older than [maxAgeMs]. */
    fun refreshIfStale(profileId: Long, maxAgeMs: Long = 12 * 3_600_000L) {
        scope.launch {
            val profile = db.profileDao().get(profileId) ?: return@launch
            if (profile.contentCount == 0 || epgUrl(profile) == null) return@launch
            val syncedAt = profile.epgSyncedAt
            if (syncedAt == null || System.currentTimeMillis() - syncedAt > maxAgeMs) refresh(profileId)
        }
    }

    fun cancel(profileId: Long) {
        jobs.remove(profileId)?.cancel()
        setState(profileId, null)
    }

    /** Now / next programme per channel id, refreshed every minute and when the guide changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun nowNext(profileId: Long): Flow<Map<String, NowNext>> = minuteTicker().flatMapLatest { now ->
        db.epgDao().window(profileId, now, now + 4 * 3_600_000L).map { list ->
            list.groupBy { it.channelId }.mapValues { (_, programmes) ->
                NowNext(
                    now = programmes.firstOrNull { it.startMs <= now },
                    next = programmes.firstOrNull { it.startMs > now },
                )
            }
        }
    }

    private fun setState(profileId: Long, state: State?) {
        _states.update { if (state == null) it - profileId else it + (profileId to state) }
    }

    private suspend fun download(profileId: Long, url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IOException("Adresse du guide TV invalide.")
        http.newCall(Request.Builder().url(httpUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Le serveur du guide TV a répondu HTTP ${response.code}.")
            val body = response.body ?: throw IOException("Guide TV vide.")
            val raw = BufferedInputStream(body.byteStream(), 64 * 1024)
            raw.mark(4)
            val gzip = raw.read() == 0x1f && raw.read() == 0x8b
            raw.reset()
            val input = if (gzip) GZIPInputStream(raw, 64 * 1024) else raw

            val xpp = Xml.newPullParser()
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            xpp.setInput(input, null)

            val now = System.currentTimeMillis()
            val from = now - KEEP_PAST_MS
            val to = now + KEEP_FUTURE_MS
            val namesToId = HashMap<String, String>()
            var wanted: Map<String, String>? = null
            val batch = Batch(profileId)

            for (item in XmltvParser().parse(xpp)) {
                when (item) {
                    is XmltvParser.Item.Channel -> item.names.forEach { namesToId.putIfAbsent(normalizeName(it), item.id) }
                    is XmltvParser.Item.Programme -> {
                        val channels = wanted ?: matchChannels(profileId, namesToId).also { wanted = it }
                        val channelId = channels[item.channelId.lowercase()] ?: continue
                        if (item.endMs <= from || item.startMs >= to) continue
                        batch.add(EpgProgrammeEntity(profileId, channelId, item.startMs, item.endMs, item.title, item.description))
                    }
                }
            }
            if (wanted == null) matchChannels(profileId, namesToId)
            batch.finish()
        }
    }

    /**
     * Gives an EPG id to channels that have none by matching display names, then returns
     * lower-cased XMLTV id -> stored channel id for every channel of the profile.
     */
    private suspend fun matchChannels(profileId: Long, namesToId: Map<String, String>): Map<String, String> {
        val dao = db.contentDao()
        if (namesToId.isNotEmpty()) {
            db.withTransaction {
                dao.liveWithoutEpgId(profileId).forEach { channel ->
                    namesToId[normalizeName(channel.name)]?.let { dao.setTvgId(channel.id, it) }
                }
            }
        }
        return dao.liveEpgIds(profileId).associateBy { it.lowercase() }
    }

    private inner class Batch(private val profileId: Long) {
        private val buffer = ArrayList<EpgProgrammeEntity>(BATCH_SIZE)
        private var replaced = false
        private var count = 0

        suspend fun add(item: EpgProgrammeEntity) {
            buffer += item
            if (buffer.size >= BATCH_SIZE) flush()
        }

        suspend fun finish() = flush()

        private suspend fun flush() {
            currentCoroutineContext().ensureActive()
            val items = buffer.toList()
            buffer.clear()
            val dao = db.epgDao()
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

    companion object {
        private const val BATCH_SIZE = 1_000
        private const val KEEP_PAST_MS = 2 * 3_600_000L
        private const val KEEP_FUTURE_MS = 3 * 24 * 3_600_000L
        private val QUALITY_TAGS = Regex("""\b(hd|fhd|uhd|sd|4k|hevc|h265|1080p|720p)\b""")
        private val NON_ALNUM = Regex("[^a-z0-9]")
        private val DIACRITICS = Regex("\\p{Mn}+")

        fun normalizeName(name: String): String {
            val ascii = DIACRITICS.replace(Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD), "")
            return NON_ALNUM.replace(QUALITY_TAGS.replace(ascii, ""), "")
        }

        private fun minuteTicker(): Flow<Long> = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000)
            }
        }
    }
}
