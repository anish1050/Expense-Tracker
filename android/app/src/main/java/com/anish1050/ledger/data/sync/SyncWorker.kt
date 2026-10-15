package com.anish1050.ledger.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anish1050.ledger.container
import com.anish1050.ledger.data.api.ApiException
import java.util.concurrent.TimeUnit

// Periodic job for every install: refreshes parser rules, and syncs rows when
// backup is on. Scheduled from MainActivity so offline-only users still get
// rule updates.
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        runCatching { container.ruleStore.refresh() }
        if (!container.authStore.isSignedIn || container.authStore.backupKey == null) return Result.success()

        val engine = SyncEngine(container.db, container.api, container.authStore)
        return try {
            engine.push()
            engine.pull()
            Result.success()
        } catch (e: BackupKeyMismatch) {
            // Retrying cannot help; the user has to re-enter the passphrase.
            container.authStore.backupKey = null
            Result.failure()
        } catch (e: ApiException) {
            if (e.status == 401) { container.authStore.clear(); Result.failure() } else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "sync-periodic"
        private const val NOW = "sync-now"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
            )
        }
    }
}
