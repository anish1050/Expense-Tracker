package com.anish1050.ledger.data.sync

import android.util.Base64
import com.anish1050.ledger.data.api.AuthStore
import com.anish1050.ledger.data.api.LedgerApi
import com.anish1050.ledger.data.api.SyncRow
import com.anish1050.ledger.data.db.AccountEntity
import com.anish1050.ledger.data.db.BudgetEntity
import com.anish1050.ledger.data.db.BudgetPeriod
import com.anish1050.ledger.data.db.CategoryEntity
import com.anish1050.ledger.data.db.LedgerDatabase
import com.anish1050.ledger.data.db.SyncCursorEntity
import com.anish1050.ledger.data.db.TransactionEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class BackupKeyMismatch : Exception("backup passphrase does not decrypt the cloud rows")

// Row sync over opaque encrypted blobs.
//  push: rows with version > pushed_version are dirty; after the server accepts
//        them pushed_version catches up. No clocks involved.
//  pull: paged by the server-assigned seq; a row is applied when its version is
//        >= ours, because the server already arbitrated equal versions.
class SyncEngine(
    private val db: LedgerDatabase,
    private val api: LedgerApi,
    private val auth: AuthStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val crypto = RowCrypto(auth.backupKey ?: error("backup key missing"))

    suspend fun push() {
        while (true) {
            val accounts = db.accountDao().dirty(LIMIT)
            val categories = db.categoryDao().dirty(LIMIT)
            val budgets = db.budgetDao().dirty(LIMIT)
            val transactions = db.transactionDao().dirty(LIMIT)

            val rows = buildList {
                accounts.forEach { add(seal("accounts", it.id, it.version, it.updatedAt, it.deleted, AccountEntity.serializer(), it)) }
                categories.forEach { add(seal("categories", it.id, it.version, it.updatedAt, it.deleted, CategoryEntity.serializer(), it)) }
                budgets.forEach { add(seal("budgets", budgetRowId(it.period), it.version, it.updatedAt, it.deleted, BudgetEntity.serializer(), it)) }
                transactions.forEach { add(seal("transactions", it.id, it.version, it.updatedAt, it.deleted, TransactionEntity.serializer(), it)) }
            }.take(LIMIT)
            if (rows.isEmpty()) return

            val sent = rows.map { it.rowId }.toSet()
            val rejected = api.push(auth.deviceId, rows).rejected.map { it.rowId }.toSet()
            // Rejected rows have a newer server copy; the next pull replaces them
            // and marks them pushed. Everything else is now on the server.
            val accepted = sent - rejected
            db.accountDao().markPushed(accounts.map { it.id }.filter { it in accepted })
            db.categoryDao().markPushed(categories.map { it.id }.filter { it in accepted })
            db.budgetDao().markPushed(budgets.map { it.period }.filter { budgetRowId(it) in accepted })
            db.transactionDao().markPushed(transactions.map { it.id }.filter { it in accepted })

            if (rows.size < LIMIT) return
        }
    }

    suspend fun pull() {
        var since = db.syncCursorDao().get(PULLED_SEQ)?.toLongOrNull() ?: 0L
        while (true) {
            val page = api.pull(since)
            if (page.rows.isEmpty()) return
            applyPulledRows(page.rows)
            since = page.nextSince
            db.syncCursorDao().set(SyncCursorEntity(PULLED_SEQ, since.toString()))
            if (page.rows.size < LIMIT) return
        }
    }

    private suspend fun applyPulledRows(rows: List<SyncRow>) {
        val accounts = mutableListOf<AccountEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val budgets = mutableListOf<BudgetEntity>()
        val transactions = mutableListOf<TransactionEntity>()
        for (row in rows) {
            // A row we cannot decrypt means the key is wrong. Stop without moving
            // the cursor so the data is still there once the passphrase is fixed.
            val plain = runCatching { crypto.decrypt(Base64.decode(row.ciphertext, Base64.NO_WRAP), Base64.decode(row.nonce, Base64.NO_WRAP)) }
                .getOrElse { throw BackupKeyMismatch() }
            when (row.table) {
                "accounts" -> accounts += json.decodeFromString(AccountEntity.serializer(), plain).copy(pushedVersion = row.version)
                "categories" -> categories += json.decodeFromString(CategoryEntity.serializer(), plain).copy(pushedVersion = row.version)
                "budgets" -> budgets += json.decodeFromString(BudgetEntity.serializer(), plain).copy(pushedVersion = row.version)
                "transactions" -> transactions += json.decodeFromString(TransactionEntity.serializer(), plain).copy(pushedVersion = row.version)
            }
        }
        db.accountDao().upsertAll(accounts.filter { incoming -> (db.accountDao().byId(incoming.id)?.version ?: 0) <= incoming.version })
        db.categoryDao().upsertAll(categories.filter { incoming -> (db.categoryDao().byId(incoming.id)?.version ?: 0) <= incoming.version })
        db.budgetDao().upsertAll(budgets.filter { incoming -> (db.budgetDao().byPeriod(incoming.period)?.version ?: 0) <= incoming.version })
        db.transactionDao().upsertAll(transactions.filter { incoming -> (db.transactionDao().byId(incoming.id)?.version ?: 0) <= incoming.version })
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

    companion object {
        const val LIMIT = 500
        private const val PULLED_SEQ = "pulled_seq"

        // Budgets are keyed by period, not UUID; map to a stable UUID per period so the server's row_id validation holds.
        fun budgetRowId(period: BudgetPeriod) = when (period) {
            BudgetPeriod.day -> "00000000-0000-4000-8000-0000000000b0"
            BudgetPeriod.week -> "00000000-0000-4000-8000-0000000000b1"
            BudgetPeriod.month -> "00000000-0000-4000-8000-0000000000b2"
            BudgetPeriod.year -> "00000000-0000-4000-8000-0000000000b3"
        }

        // Sign-out, account switch or a new passphrase: every row must be
        // re-uploaded under the new identity and the pull starts from scratch.
        suspend fun resetLocalSyncState(db: LedgerDatabase) {
            db.syncCursorDao().clear()
            db.accountDao().resetPushed()
            db.categoryDao().resetPushed()
            db.budgetDao().resetPushed()
            db.transactionDao().resetPushed()
        }
    }
}
