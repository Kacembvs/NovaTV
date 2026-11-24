package com.nova.iptv.data.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.nova.iptv.MainActivity
import com.nova.iptv.R
import com.nova.iptv.data.DownloadQuality
import com.nova.iptv.data.ProfileRepository
import com.nova.iptv.data.SettingsStore
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class OfflineStatus { QUEUED, WAITING_FOR_WIFI, WAITING_FOR_NETWORK, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class OfflineItem(
    val meta: DownloadMeta,
    val status: OfflineStatus,
    /** 0..100, or null while the size is still unknown. */
    val percent: Float?,
    val bytes: Long,
    val updatedAt: Long,
) {
    val id get() = meta.id
    val active get() = status != OfflineStatus.COMPLETED && status != OfflineStatus.FAILED && status != OfflineStatus.PAUSED
}

data class OfflineStorage(val usedBytes: Long, val freeBytes: Long)

/**
 * Offline movies and episodes, built on Media3's download stack (the same one used by the
 * major streaming apps on Android): resumable background downloads in a foreground service,
 * a persistent index, and a local cache that the player reads without any network.
 */
@OptIn(UnstableApi::class)
class OfflineDownloads(
    context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsStore,
    private val profiles: ProfileRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _items = MutableStateFlow<List<OfflineItem>>(emptyList())
    val items: StateFlow<List<OfflineItem>> = _items.asStateFlow()
    private val changes = MutableStateFlow(0)
    private val knownTerminal = HashSet<String>()
    private var started = false

    /** Tests drive the manager directly instead of going through the foreground service. */
    internal var viaService = true

    val cache: SimpleCache get() = cache(appContext)

    val manager: DownloadManager by lazy {
        DownloadManager(appContext, database(appContext), cache, OkHttpDataSource.Factory(http), Executors.newFixedThreadPool(MAX_PARALLEL)).apply {
            maxParallelDownloads = MAX_PARALLEL
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                    changes.value++
                    notifyTerminal(download)
                }

                override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                    changes.value++
                }

                override fun onRequirementsStateChanged(manager: DownloadManager, requirements: Requirements, notMetRequirements: Int) {
                    changes.value++
                }
            })
        }
    }

    /** Called once by the application: applies the Wi-Fi preference and keeps [items] up to date. */
    fun start() {
        if (started) return
        started = true
        NotificationUtil.createNotificationChannel(
            appContext, CHANNEL_ID, R.string.download_channel_name, R.string.download_channel_description, NotificationUtil.IMPORTANCE_LOW,
        )
        scope.launch {
            settings.downloads.distinctUntilChanged { a, b -> a.wifiOnly == b.wifiOnly }.collect {
                val requirements = Requirements(if (it.wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
                withContext(Dispatchers.Main) { manager.requirements = requirements }
                changes.value++
            }
        }
        scope.launch {
            // Media3 expects the manager to be created and driven from the main thread.
            withContext(Dispatchers.Main) { manager }
            while (isActive) {
                val seen = changes.value
                refresh()
                // Progress is not pushed by Media3: poll every second while something downloads.
                withTimeoutOrNull(if (_items.value.any { it.active }) 1_000 else 30_000) {
                    changes.first { it != seen }
                }
            }
        }
    }

    fun refresh() {
        val notMet = manager.notMetRequirements
        val list = ArrayList<OfflineItem>()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.state == Download.STATE_REMOVING) continue
                val meta = DownloadMeta.fromBytes(download.request.data) ?: continue
                list += OfflineItem(meta, statusOf(download, notMet), download.percentDownloaded.takeIf { it >= 0f }, download.bytesDownloaded, download.updateTimeMs)
            }
        }
        // The index is persisted, so completed downloads stay known after a restart.
        if (knownTerminal.isEmpty()) list.filter { !it.active }.mapTo(knownTerminal) { it.id }
        _items.value = list.sortedByDescending { it.updatedAt }
    }

    fun item(profileId: Long, ref: String): OfflineItem? = _items.value.firstOrNull { it.id == DownloadMeta.downloadId(profileId, ref) }

    /** Requests ready to play from local storage, keyed by item ref. */
    fun completedRequests(profileId: Long, refs: List<String>): Map<String, DownloadRequest> = refs.mapNotNull { ref ->
        manager.downloadIndex.getDownload(DownloadMeta.downloadId(profileId, ref))
            ?.takeIf { it.state == Download.STATE_COMPLETED }
            ?.let { ref to it.request }
    }.toMap()

    /** Data sources for playback: downloaded media is read from disk, anything else streams normally. */
    fun playbackDataSourceFactory(context: Context): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http)))
        .setCacheWriteDataSinkFactory(null)

    suspend fun download(profileId: Long, items: List<DownloadMeta>) {
        val profile = profiles.get(profileId) ?: throw IOException("Profil introuvable.")
        val pending = items.filter { meta -> item(profileId, meta.ref).let { it == null || it.status == OfflineStatus.FAILED } }
        if (pending.isEmpty()) return
        val urls = profiles.streamUrls(profile, pending.map { it.ref })
        val quality = settings.downloads.first().quality
        pending.zip(urls).forEach { (meta, url) ->
            val request = buildRequest(meta, url, quality)
            if (viaService) withContext(Dispatchers.Main) {
                DownloadService.sendAddDownload(appContext, NovaDownloadService::class.java, request, false)
            } else manager.addDownload(request)
        }
        changes.value++
    }

    suspend fun download(profileId: Long, item: PlayableItem) = download(profileId, listOf(DownloadMeta.from(profileId, item)))

    fun pause(id: String) = setStopReason(id, STOP_REASON_PAUSED)

    fun resume(id: String) = setStopReason(id, Download.STOP_REASON_NONE)

    private fun setStopReason(id: String, reason: Int) {
        if (viaService) DownloadService.sendSetStopReason(appContext, NovaDownloadService::class.java, id, reason, false)
        else manager.setStopReason(id, reason)
        changes.value++
    }

    fun remove(id: String) {
        knownTerminal.remove(id)
        if (viaService) DownloadService.sendRemoveDownload(appContext, NovaDownloadService::class.java, id, false)
        else manager.removeDownload(id)
        changes.value++
    }

    fun removeAll() {
        knownTerminal.clear()
        if (viaService) DownloadService.sendRemoveAllDownloads(appContext, NovaDownloadService::class.java, false)
        else manager.removeAllDownloads()
        changes.value++
    }

    /** A deleted profile must not leave its videos on the device. */
    fun removeProfile(profileId: Long) {
        _items.value.filter { it.meta.profileId == profileId }.forEach { remove(it.id) }
    }

    fun storage(): OfflineStorage {
        val dir = directory(appContext)
        val free = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
        return OfflineStorage(runCatching { cache.cacheSpace }.getOrDefault(0L), free)
    }

    private suspend fun buildRequest(meta: DownloadMeta, url: String, quality: DownloadQuality): DownloadRequest {
        val uri = Uri.parse(url)
        if (!isHls(uri, url)) {
            // The cache key does not depend on the URL, so a changed Xtream password keeps the file playable.
            return DownloadRequest.Builder(meta.id, uri).setCustomCacheKey(meta.id).setData(meta.toBytes()).build()
        }
        val mediaItem = MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        return withContext(Dispatchers.Main) {
            val helper = DownloadHelper.forMediaItem(appContext, mediaItem, DefaultRenderersFactory(appContext), OkHttpDataSource.Factory(http))
            try {
                suspendCancellableCoroutine { continuation ->
                    helper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(helper: DownloadHelper) {
                            val parameters = DownloadHelper.getDefaultTrackSelectorParameters(appContext).buildUpon().apply {
                                if (quality == DownloadQuality.STANDARD) setMaxVideoSize(1280, 720) else setForceHighestSupportedBitrate(true)
                            }.build()
                            for (period in 0 until helper.periodCount) {
                                helper.clearTrackSelections(period)
                                helper.addTrackSelection(period, parameters)
                            }
                            continuation.resume(helper.getDownloadRequest(meta.id, meta.toBytes()))
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    })
                }
            } finally {
                helper.release()
            }
        }
    }

    private suspend fun isHls(uri: Uri, url: String): Boolean {
        when (Util.inferContentType(uri)) {
            C.CONTENT_TYPE_HLS -> return true
            C.CONTENT_TYPE_OTHER -> Unit
            else -> return false
        }
        if (uri.lastPathSegment?.contains('.') == true) return false
        // Extension-less links (frequent in M3U playlists) may be HLS: ask the server.
        return withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                    response.header("Content-Type").orEmpty().contains("mpegurl", ignoreCase = true)
                }
            }.getOrDefault(false)
        }
    }

    private fun statusOf(download: Download, notMet: Int) = when (download.state) {
        Download.STATE_COMPLETED -> OfflineStatus.COMPLETED
        Download.STATE_FAILED -> OfflineStatus.FAILED
        Download.STATE_STOPPED -> OfflineStatus.PAUSED
        Download.STATE_DOWNLOADING -> OfflineStatus.DOWNLOADING
        else -> when {
            notMet and Requirements.NETWORK_UNMETERED != 0 -> OfflineStatus.WAITING_FOR_WIFI
            notMet and Requirements.NETWORK != 0 -> OfflineStatus.WAITING_FOR_NETWORK
            else -> OfflineStatus.QUEUED
        }
    }

    private fun notifyTerminal(download: Download) {
        val terminal = download.state == Download.STATE_COMPLETED || download.state == Download.STATE_FAILED
        if (!terminal || !knownTerminal.add(download.request.id)) return
        val meta = DownloadMeta.fromBytes(download.request.data) ?: return
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        val name = if (meta.kind == ContentKind.EPISODE) listOfNotNull(meta.title, meta.subtitle).joinToString(" · ") else meta.title
        val helper = DownloadNotificationHelper(appContext, CHANNEL_ID)
        val notification = if (download.state == Download.STATE_COMPLETED) {
            helper.buildDownloadCompletedNotification(appContext, R.drawable.ic_stat_download, openDownloads(appContext), "Prêt à regarder hors ligne : $name")
        } else {
            helper.buildDownloadFailedNotification(appContext, R.drawable.ic_stat_download, openDownloads(appContext), "Échec du téléchargement : $name")
        }
        NotificationUtil.setNotification(appContext, NOTIFICATION_TERMINAL_BASE + (download.request.id.hashCode() and 0xffff), notification)
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val STOP_REASON_PAUSED = 1
        const val MAX_PARALLEL = 2
        const val EXTRA_OPEN = "com.nova.iptv.OPEN"
        const val OPEN_DOWNLOADS = "downloads"
        private const val NOTIFICATION_TERMINAL_BASE = 0x4000

        private var database: StandaloneDatabaseProvider? = null
        private var cache: SimpleCache? = null
        private var cacheDir: File? = null

        fun directory(context: Context) = File(context.filesDir, "offline")

        /** Releases the process-wide cache (tests recreate the whole environment between cases). */
        @Synchronized
        internal fun releaseShared() {
            cache?.release()
            cache = null
            cacheDir = null
            database?.close()
            database = null
        }

        @Synchronized
        private fun database(context: Context) =
            database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

        /** A single cache instance per folder is allowed by Media3. */
        @Synchronized
        fun cache(context: Context): SimpleCache {
            val dir = directory(context)
            cache?.let {
                if (cacheDir == dir) return it
                it.release()
                database?.close()
                database = null
            }
            return SimpleCache(dir, NoOpCacheEvictor(), database(context)).also { cache = it; cacheDir = dir }
        }

        fun openDownloads(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
