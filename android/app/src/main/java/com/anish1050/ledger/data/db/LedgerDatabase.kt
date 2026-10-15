package com.anish1050.ledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, BudgetEntity::class, TransactionEntity::class, SyncCursorEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun syncCursorDao(): SyncCursorDao

    companion object {
        const val FILE_NAME = "ledger.db"

        // v2: per-row pushed_version replaces the timestamp push cursor. Rows
        // start at 0 (dirty), so everything uploads once on the next sync.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("accounts", "categories", "budgets", "transactions")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN pushed_version INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("DELETE FROM sync_cursor")
                // v1 installs could have been seeded before categories existed;
                // re-running the seed is a no-op where rows are already there.
                Seed.insert(db)
            }
        }

        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Seed.insert(db)
                    }
                })
                .build()
    }
}
