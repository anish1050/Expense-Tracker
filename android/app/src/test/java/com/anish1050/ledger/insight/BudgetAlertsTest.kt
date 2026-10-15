package com.anish1050.ledger.insight

import com.anish1050.ledger.data.db.BudgetPeriod
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetAlertsTest {

    private fun status(period: BudgetPeriod, spent: Long, limit: Long, window: Long = 100L) = BudgetStatus(period, spent, limit, window)

    @Test
    fun alertsOnlyBudgetsOverTheirLimit() {
        val due = budgetsNewlyExceeded(
            listOf(status(BudgetPeriod.day, 600, 500), status(BudgetPeriod.week, 2_000, 2_500), status(BudgetPeriod.month, 10_000, 10_000)),
        ) { null }
        assertThat(due.map { it.period }).containsExactly(BudgetPeriod.day)
    }

    @Test
    fun staysQuietForAWindowAlreadyAlerted() {
        val due = budgetsNewlyExceeded(listOf(status(BudgetPeriod.week, 3_000, 2_500, window = 100L))) { 100L }
        assertThat(due).isEmpty()
    }

    @Test
    fun alertsAgainInANewWindow() {
        val due = budgetsNewlyExceeded(listOf(status(BudgetPeriod.week, 3_000, 2_500, window = 200L))) { 100L }
        assertThat(due).hasSize(1)
    }

    @Test
    fun ignoresZeroLimits() {
        assertThat(budgetsNewlyExceeded(listOf(status(BudgetPeriod.year, 5, 0))) { null }).isEmpty()
    }
}
