package com.anish1050.ledger.ui.report

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anish1050.ledger.AppContainer
import com.anish1050.ledger.data.Period
import com.anish1050.ledger.data.db.BudgetEntity
import com.anish1050.ledger.data.db.BudgetPeriod
import com.anish1050.ledger.data.db.CategoryTotal
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.insight.Tip
import com.anish1050.ledger.insight.budgetTip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Immutable
data class BudgetUi(val period: BudgetPeriod, val spentMinor: Long, val limitMinor: Long?)

@Immutable
data class ReportState(
    val monthLabel: String = "",
    val currency: String = "INR",
    val directionIndex: Int = 0,
    val totals: List<CategoryTotal> = emptyList(),
    val totalMinor: Long = 0,
    val deltaPercent: Map<String, Long> = emptyMap(),
    val budgets: List<BudgetUi> = BudgetPeriod.entries.map { BudgetUi(it, 0, null) },
    val tip: Tip? = null,
)

class ReportViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.db.transactionDao()
    private val direction = MutableStateFlow(Direction.debit)
    private val month = Period.month()
    private val prevMonth = Period.previousMonth()

    // netBetween is observed only as a change signal so the report recomputes after every write.
    val state: StateFlow<ReportState> = combine(direction, container.budgets.all, dao.netBetween(month.from, month.to)) { dir, budgets, _ ->
        build(dir, budgets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    private suspend fun build(dir: Direction, budgets: List<BudgetEntity>): ReportState {
        val totals = dao.categoryTotals(dir, month.from, month.to)
        val prev = dao.categoryTotals(dir, prevMonth.from, prevMonth.to)
        val prevById = prev.associate { it.categoryId to it.totalMinor }
        val limits = budgets.associate { it.period to it.amountMinor }
        val week = Period.week()
        val year = Period.year()
        val spent = mapOf(
            BudgetPeriod.week to dao.sumBetween(Direction.debit, week.from, week.to),
            BudgetPeriod.month to dao.sumBetween(Direction.debit, month.from, month.to),
            BudgetPeriod.year to dao.sumBetween(Direction.debit, year.from, year.to),
        )
        return ReportState(
            monthLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM")).lowercase(),
            directionIndex = if (dir == Direction.debit) 0 else 1,
            totals = totals,
            totalMinor = totals.sumOf { it.totalMinor },
            deltaPercent = totals.mapNotNull { t ->
                val p = prevById[t.categoryId]
                if (p == null || p == 0L) null else t.categoryId to ((t.totalMinor - p) * 100 / p)
            }.toMap(),
            budgets = BudgetPeriod.entries.map { BudgetUi(it, spent.getValue(it), limits[it]) },
            tip = if (dir == Direction.debit) budgetTip(spent.getValue(BudgetPeriod.month), limits[BudgetPeriod.month], totals, prev, "INR") else null,
        )
    }

    fun setDirection(index: Int) {
        direction.value = if (index == 0) Direction.debit else Direction.credit
    }

    fun setBudget(period: BudgetPeriod, amountMinor: Long) {
        viewModelScope.launch { container.budgets.set(period, amountMinor) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { ReportViewModel(container) } }
    }
}
