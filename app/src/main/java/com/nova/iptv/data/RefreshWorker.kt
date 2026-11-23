package com.nova.iptv.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nova.iptv.NovaApp
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh (every 12 h, network required, battery not low): catalogue of every
 * profile, then its XMLTV guide. Errors are stored on the profile and shown in Settings.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as NovaApp).container
        container.db.profileDao().all().forEach { profile ->
            container.sync.syncAndWait(profile.id)
            // A successful sync starts the guide refresh; wait for it too.
            container.epg.join(profile.id)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "nova-periodic-refresh"

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
