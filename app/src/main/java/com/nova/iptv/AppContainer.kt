package com.nova.iptv

import android.content.Context
import com.nova.iptv.core.CredentialCipher
import com.nova.iptv.core.NovaHttp
import com.nova.iptv.data.BackupManager
import com.nova.iptv.data.EpgManager
import com.nova.iptv.data.LibraryRepository
import com.nova.iptv.data.ParentalControl
import com.nova.iptv.data.PlaybackQueue
import com.nova.iptv.data.ProfileRepository
import com.nova.iptv.data.SettingsStore
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.download.OfflineDownloads
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.source.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: one instance per process, owned by [NovaApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Work that must outlive a screen (sync, saving playback progress). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val http = NovaHttp.client()
    val db = NovaDatabase.create(appContext)
    val settings = SettingsStore(appContext)
    val cipher = CredentialCipher()
    val profiles = ProfileRepository(db.profileDao(), cipher, settings)
    val xtream = XtreamClient(http)
    val epg = EpgManager(db, profiles, http, appScope)
    val sync = SyncManager(db, profiles, http, xtream, appScope, onSynced = epg::refresh)
    val playbackQueue = PlaybackQueue()
    val library = LibraryRepository(db)
    val parental = ParentalControl(settings)
    val backup = BackupManager(db, cipher, settings)
    val downloads by lazy { OfflineDownloads(appContext, http, settings, profiles, appScope) }
}
