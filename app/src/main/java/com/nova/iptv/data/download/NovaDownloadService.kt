package com.nova.iptv.data.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.nova.iptv.NovaApp
import com.nova.iptv.R

/** Keeps downloads running in the background with a progress notification, and restarts them when Wi-Fi returns. */
@OptIn(UnstableApi::class)
class NovaDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    OfflineDownloads.CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, OfflineDownloads.CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = (application as NovaApp).container.downloads.manager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val current = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: downloads.firstOrNull()
        val meta = current?.let { DownloadMeta.fromBytes(it.request.data) }
        val message = when {
            notMetRequirements and Requirements.NETWORK_UNMETERED != 0 -> "En attente d'une connexion Wi-Fi"
            meta == null -> null
            meta.subtitle != null -> "${meta.title} · ${meta.subtitle}"
            else -> meta.title
        }
        return notifications.buildProgressNotification(
            this, R.drawable.ic_stat_download, OfflineDownloads.openDownloads(this), message, downloads, notMetRequirements,
        )
    }

    private companion object {
        const val FOREGROUND_NOTIFICATION_ID = 0x3000
        const val JOB_ID = 0x3001
    }
}
