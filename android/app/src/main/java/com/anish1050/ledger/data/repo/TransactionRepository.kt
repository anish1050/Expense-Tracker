package com.anish1050.ledger.data.repo

import android.content.Context
import com.anish1050.ledger.capture.parser.ParsedTransaction
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.Seed
import com.anish1050.ledger.data.db.Source
import com.anish1050.ledger.data.db.TransactionDao
import com.anish1050.ledger.data.db.TransactionEntity
import com.anish1050.ledger.data.db.TxState
import com.anish1050.ledger.insight.BudgetAlerts
import com.anish1050.ledger.widget.WidgetRefresh
import java.security.MessageDigest
import java.util.UUID

sealed interface IngestResult {
    data class Inserted(val id: String, val state: TxState) : IngestResult
    data object Duplicate : IngestResult
}

class TransactionRepository(
    private val context: Context,
    private val dao: TransactionDao,
    private val budgetAlerts: BudgetAlerts,
) {
    suspend fun ingest(parsed: ParsedTransaction, accountId: String = Seed.CASH_ACCOUNT_ID, currency: String = "INR"): IngestResult {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            categoryId = Seed.UNCATEGORISED_ID,
            amountMinor = parsed.amountMinor,
            currency = currency,
            direction = parsed.direction,
            merchant = parsed.merchant.ifBlank { "unknown" },
            note = null,
            occurredAt = parsed.occurredAt,
            source = parsed.source,
            confidence = parsed.confidence,
            state = if (parsed.confidence >= CONFIRM_THRESHOLD) TxState.confirmed else TxState.review,
            dedupeKey = dedupeKey(parsed.direction, parsed.amountMinor, parsed.occurredAt),
            createdAt = now,
            updatedAt = now,
        )
        val rowId = dao.insertIgnore(entity)
        if (rowId == -1L) return IngestResult.Duplicate
        afterWrite(entity)
        return IngestResult.Inserted(entity.id, entity.state)
    }

    suspend fun add(amountMinor: Long, direction: Direction, categoryId: String?, merchant: String, occurredAt: Long, accountId: String = Seed.CASH_ACCOUNT_ID, currency: String = "INR"): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = TransactionEntity(
            id = id,
            accountId = accountId,
            categoryId = categoryId ?: Seed.UNCATEGORISED_ID,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchant = merchant.ifBlank { "untitled" },
            note = null,
            occurredAt = occurredAt,
            source = Source.manual,
            confidence = 1f,
            state = TxState.confirmed,
            // Manual entries are never deduped against captures, so the key is the id itself.
            dedupeKey = "manual:$id",
            createdAt = now,
            updatedAt = now,
        )
        dao.insertIgnore(entity)
        afterWrite(entity)
        return entity.id
    }

    suspend fun confirm(id: String, categoryId: String? = null) {
        edit(id) { it.copy(state = TxState.confirmed, categoryId = categoryId ?: it.categoryId) }
        dao.byId(id)?.let { afterWrite(it) }
    }

    // Widgets always refresh; budgets are only re-checked when confirmed spend
    // was added, since only that can push a total over a limit.
    private suspend fun afterWrite(t: TransactionEntity) {
        WidgetRefresh.requestUpdate(context)
        if (t.direction == Direction.debit && t.state == TxState.confirmed) runCatching { budgetAlerts.check(t.currency) }
    }

    suspend fun discard(id: String) = edit(id) { it.copy(deleted = true) }

    private suspend fun edit(id: String, change: (TransactionEntity) -> TransactionEntity) {
        val current = dao.byId(id) ?: return
        dao.update(change(current).copy(version = current.version + 1, updatedAt = System.currentTimeMillis()))
        WidgetRefresh.requestUpdate(context)
    }

    companion object {
        const val CONFIRM_THRESHOLD = 0.8f

        // Same payment seen from SMS and from a notification collapses to one
        // row. The two alerts share only direction, amount and time (the SMS
        // has a ref and last4, the notification has a merchant), so the key is
        // direction + amount + 2-minute bucket. ponytail: two distinct payments
        // of the same amount inside two minutes merge, and alerts straddling a
        // bucket edge show twice; add a +-1 bucket probe if users report it.
        fun dedupeKey(direction: Direction, amountMinor: Long, occurredAt: Long): String {
            val bucket = occurredAt / 120_000L
            val raw = "${direction.name}|$amountMinor|$bucket"
            return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
