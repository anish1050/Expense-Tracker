package com.anish1050.ledger.data.sync

import android.util.Base64
import com.anish1050.ledger.data.api.AuthStore
import com.anish1050.ledger.data.api.LedgerApi
import com.anish1050.ledger.data.api.SyncRow
import com.anish1050.ledger.data.db.AccountEntity
import com.anish1050.ledger.data.db.BudgetEntity
import com.anish1050.ledger.data.db.CategoryEntity
import com.anish1050.ledger.data.db.LedgerDatabase
import com.anish1050.ledger.data.db.SyncCursorEntity
import com.anish1050.ledger.data.db.TransactionEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

// Last-writer-wins row sync over opaque encrypted blobs. Four tables, one
// shape: serialize entity -> encrypt -> push; pull -> decrypt -> upsert if the
// incoming version is newer.
class SyncEngine(
    private val db: LedgerDatabase,
    private val api: LedgerApi,
    private val auth: AuthStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val crypto get() = RowCrypto(auth.backupKey ?: error("backup key missing"))

    suspend fun push() {
        val since = cursor(PUSHED_AT)
        val rows = buildList {
            db.accountDao().changedSince(since, LIMIT).forEach { add(seal("accounts", it.id, it.version, it.updatedAt, it.deleted, AccountEntity.serializer(), it)) }
            db.categoryDao().changedSince(since, LIMIT).forEach { add(seal("categories", it.id, it.version, it.updatedAt, it.deleted, CategoryEntity.serializer(), it)) }
            db.budgetDao().changedSince(since, LIMIT).forEach { add(seal("budgets", budgetRowId(it), it.version, it.updatedAt, it.deleted, BudgetEntity.serializer(), it)) }
            db.transactionDao().changedSince(since, LIMIT).forEach { add(seal("transactions", it.id, it.version, it.updatedAt, it.deleted, TransactionEntity.serializer(), it)) }
        }.take(LIMIT)
        if (rows.isEmpty()) return
        api.push(auth.deviceId, rows)
        // Rejected rows have a newer server copy; the next pull brings it down.
        setCursor(PUSHED_AT, rows.maxOf { it.updatedAt })
    }

    suspend fun pull() {
        var since = cursor(PULLED_AT)
        while (true) {
            val page = api.pull(since)
            if (page.rows.isEmpty()) break
            apply(page.rows)
            since = page.nextSince
            setCursor(PULLED_AT, since)
            if (page.rows.size < LIMIT) break
        }
    }

    private suspend fun apply(rows: List<SyncRow>) {
        val accounts = mutableListOf<AccountEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val budgets = mutableListOf<BudgetEntity>()
        val transactions = mutableListOf<TransactionEntity>()
        for (row in rows) {
            val plain = runCatching { crypto.decrypt(Base64.decode(row.ciphertext, Base64.NO_WRAP), Base64.decode(row.nonce, Base64.NO_WRAP)) }.getOrNull() ?: continue
            when (row.table) {
                "accounts" -> accounts += json.decodeFromString(AccountEntity.serializer(), plain)
                "categories" -> categories += json.decodeFromString(CategoryEntity.serializer(), plain)
                "budgets" -> budgets += json.decodeFromString(BudgetEntity.serializer(), plain)
                "transactions" -> transactions += json.decodeFromString(TransactionEntity.serializer(), plain)
            }
        }
        // Keep local rows that are at least as new; LWW by version.
        if (accounts.isNotEmpty()) db.accountDao().upsertAll(accounts.filter { incoming -> (db.accountDao().byId(incoming.id)?.version ?: 0) < incoming.version })
        if (categories.isNotEmpty()) db.categoryDao().upsertAll(categories.filter { incoming -> (db.categoryDao().byId(incoming.id)?.version ?: 0) < incoming.version })
        if (budgets.isNotEmpty()) db.budgetDao().upsertAll(budgets.filter { incoming -> (db.budgetDao().byPeriod(incoming.period)?.version ?: 0) < incoming.version })
        if (transactions.isNotEmpty()) db.transactionDao().upsertAll(transactions.filter { incoming -> (db.transactionDao().byId(incoming.id)?.version ?: 0) < incoming.version })
    }

    private fun <T> seal(table: String, rowId: String, version: Int, updatedAt: Long, deleted: Boolean, serializer: KSerializer<T>, entity: T): SyncRow {
        val sealed = crypto.encrypt(json.encodeToString(serializer, entity))
        return SyncRow(
            table = table,
            rowId = rowId,
            version = version,
            updatedAt = updatedAt,
            ciphertext = Base64.encodeToString(sealed.ciphertext, Base64.NO_WRAP),
            nonce = Base64.encodeToString(sealed.nonce, Base64.NO_WRAP),
            deleted = deleted,
        )
    }

    private suspend fun cursor(key: String) = db.syncCursorDao().get(key)?.toLongOrNull() ?: 0L
    private suspend fun setCursor(key: String, value: Long) = db.syncCursorDao().set(SyncCursorEntity(key, value.toString()))

    private companion object {
        const val LIMIT = 500
        const val PUSHED_AT = "pushed_at"
        const val PULLED_AT = "pulled_at"

        // Budgets are keyed by period, not UUID; map to a stable UUID per period so the server's row_id validation holds.
        fun budgetRowId(b: BudgetEntity) = when (b.period) {
            com.anish1050.ledger.data.db.BudgetPeriod.week -> "00000000-0000-4000-8000-0000000000b1"
            com.anish1050.ledger.data.db.BudgetPeriod.month -> "00000000-0000-4000-8000-0000000000b2"
            com.anish1050.ledger.data.db.BudgetPeriod.year -> "00000000-0000-4000-8000-0000000000b3"
        }
    }
}
