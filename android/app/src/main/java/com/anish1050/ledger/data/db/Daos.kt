package com.anish1050.ledger.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT t.id, c.emoji, t.merchant, COALESCE(t.note, a.name) AS sub_label,
               t.amount_minor, t.currency, t.direction, t.occurred_at
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.deleted = 0 AND t.state = 'confirmed'
          AND t.occurred_at BETWEEN :from AND :to
        ORDER BY t.occurred_at DESC
        """,
    )
    fun rows(from: Long, to: Long): Flow<List<TransactionRow>>

    // Daily running balance computed in SQL with a window function; the chart
    // only ever receives one point per day.
    @Query(
        """
        SELECT day_start,
               SUM(day_net) OVER (ORDER BY day_start ROWS UNBOUNDED PRECEDING) AS running_balance_minor
        FROM (
            SELECT (occurred_at / 86400000) * 86400000 AS day_start,
                   SUM(CASE direction WHEN 'credit' THEN amount_minor ELSE -amount_minor END) AS day_net
            FROM transactions
            WHERE deleted = 0 AND state = 'confirmed' AND occurred_at BETWEEN :from AND :to
            GROUP BY day_start
        )
        ORDER BY day_start
        """,
    )
    fun dailyBalance(from: Long, to: Long): Flow<List<DaySum>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE direction WHEN 'credit' THEN amount_minor ELSE -amount_minor END), 0)
        FROM transactions WHERE deleted = 0 AND state = 'confirmed' AND occurred_at BETWEEN :from AND :to
        """,
    )
    fun netBetween(from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT c.id AS category_id, c.name, c.emoji, c.discretionary, SUM(t.amount_minor) AS total_minor
        FROM transactions t JOIN categories c ON c.id = t.category_id
        WHERE t.deleted = 0 AND t.state = 'confirmed' AND t.direction = :direction
          AND t.occurred_at BETWEEN :from AND :to
        GROUP BY c.id ORDER BY total_minor DESC
        """,
    )
    suspend fun categoryTotals(direction: Direction, from: Long, to: Long): List<CategoryTotal>

    @Query(
        """
        SELECT COALESCE(SUM(amount_minor), 0) FROM transactions
        WHERE deleted = 0 AND state = 'confirmed' AND direction = :direction AND occurred_at BETWEEN :from AND :to
        """,
    )
    suspend fun sumBetween(direction: Direction, from: Long, to: Long): Long

    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN occurred_at BETWEEN :monthFrom AND :monthTo
                            THEN CASE direction WHEN 'credit' THEN amount_minor ELSE -amount_minor END ELSE 0 END), 0) AS balance_minor,
          COALESCE(SUM(CASE WHEN occurred_at BETWEEN :monthFrom AND :monthTo AND direction = 'debit' THEN amount_minor ELSE 0 END), 0) AS month_spent_minor
        FROM transactions WHERE deleted = 0 AND state = 'confirmed'
        """,
    )
    suspend fun widgetSnapshot(monthFrom: Long, monthTo: Long): WidgetSnapshot

    @Query("SELECT * FROM transactions WHERE state = 'review' AND deleted = 0 ORDER BY occurred_at DESC")
    fun reviewQueue(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE state = 'review' AND deleted = 0")
    fun reviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Upsert
    suspend fun upsertAll(rows: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE updated_at > :since ORDER BY updated_at LIMIT :limit")
    suspend fun changedSince(since: Long, limit: Int): List<TransactionEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sort_order")
    fun all(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<CategoryEntity>)

    @Upsert
    suspend fun upsertAll(rows: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE updated_at > :since ORDER BY updated_at LIMIT :limit")
    suspend fun changedSince(since: Long, limit: Int): List<CategoryEntity>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY created_at")
    fun all(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE last4 = :last4 AND deleted = 0 LIMIT 1")
    suspend fun byLast4(last4: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE kind = :kind AND deleted = 0 ORDER BY created_at LIMIT 1")
    suspend fun firstOfKind(kind: AccountKind): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: AccountEntity)

    @Upsert
    suspend fun upsertAll(rows: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE updated_at > :since ORDER BY updated_at LIMIT :limit")
    suspend fun changedSince(since: Long, limit: Int): List<AccountEntity>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun all(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE period = :period AND deleted = 0")
    suspend fun byPeriod(period: BudgetPeriod): BudgetEntity?

    @Upsert
    suspend fun upsert(row: BudgetEntity)

    @Upsert
    suspend fun upsertAll(rows: List<BudgetEntity>)

    @Query("SELECT * FROM budgets WHERE updated_at > :since ORDER BY updated_at LIMIT :limit")
    suspend fun changedSince(since: Long, limit: Int): List<BudgetEntity>
}

@Dao
interface SyncCursorDao {
    @Query("SELECT value FROM sync_cursor WHERE key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun set(row: SyncCursorEntity)
}
