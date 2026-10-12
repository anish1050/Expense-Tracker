package com.anish1050.ledger.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anish1050.ledger.container
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        // Rule updates ride along with the periodic sync so parsers improve without a release.
        runCatching { container.ruleStore.refresh() }
        if (!container.authStore.isSignedIn || container.authStore.backupKey == null) return Result.success()
        val engine = SyncEngine(container.db, container.api, container.authStore)
        return runCatching { engine.push(); engine.pull() }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val PERIODIC = "sync-periodic"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
            )
            runNow(context)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build())
        }
    }
}
