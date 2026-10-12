package com.anish1050.ledger.data.repo

import com.anish1050.ledger.data.db.BudgetDao
import com.anish1050.ledger.data.db.BudgetEntity
import com.anish1050.ledger.data.db.BudgetPeriod
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {
    val all: Flow<List<BudgetEntity>> = dao.all()

    suspend fun set(period: BudgetPeriod, amountMinor: Long, currency: String = "INR") {
        val now = System.currentTimeMillis()
        val existing = dao.byPeriod(period)
        dao.upsert(
            BudgetEntity(
                period = period,
                amountMinor = amountMinor,
                currency = currency,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                version = (existing?.version ?: 0) + 1,
            ),
        )
    }
}
