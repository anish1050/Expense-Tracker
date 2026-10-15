package com.anish1050.ledger.capture

import android.util.Log
import com.anish1050.ledger.BuildConfig
import com.anish1050.ledger.capture.parser.RawCapture
import com.anish1050.ledger.capture.parser.RuleStore
import com.anish1050.ledger.data.repo.IngestResult
import com.anish1050.ledger.data.repo.TransactionRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

// Every capture source funnels through here. One thread keeps the regex engine
// warm and makes dedupe ordering deterministic. Raw text is never logged, and a
// failure in one capture must never take the listener process down.
class CaptureDispatcher(
    private val ruleStore: RuleStore,
    private val transactions: TransactionRepository,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "capture").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val swallow = CoroutineExceptionHandler { _, e -> if (BuildConfig.DEBUG) Log.w("capture", "dropped capture: ${e.javaClass.simpleName}") }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + swallow)

    fun submit(raw: RawCapture) {
        scope.launch { handle(raw) }
    }

    suspend fun handle(raw: RawCapture): IngestResult? = withContext(dispatcher) {
        val engine = ruleStore.load()
        val parsed = engine.match(raw) ?: return@withContext null
        transactions.ingest(parsed)
    }
}
