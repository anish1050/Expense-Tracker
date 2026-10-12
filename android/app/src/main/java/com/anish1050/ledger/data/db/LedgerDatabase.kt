package com.anish1050.ledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, BudgetEntity::class, TransactionEntity::class, SyncCursorEntity::class],
    version = 1,
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

        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context, LedgerDatabase::class.java, FILE_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Seed.insert(db)
                    }
                })
                .build()
    }
}
