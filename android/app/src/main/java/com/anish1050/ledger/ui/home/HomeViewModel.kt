package com.anish1050.ledger.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anish1050.ledger.AppContainer
import com.anish1050.ledger.data.Period
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.TransactionRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class DayGroup(val dayStart: Long, val label: String, val netMinor: Long, val rows: List<TransactionRow>)

@Immutable
data class HomeState(
    val periodLabel: String = "this month",
    val currency: String = "INR",
    val balanceMinor: Long = 0,
    val points: List<Float> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val reviewCount: Int = 0,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.db.transactionDao()
    private val month = Period.month()

    val state: StateFlow<HomeState> = combine(
        dao.rows(month.from, month.to),
        dao.dailyNet(month.from, month.to),
        dao.reviewCount(),
    ) { rows, days, review ->
        val running = days.runningFold(0L) { acc, day -> acc + day.dayNetMinor }.drop(1)
        HomeState(
            balanceMinor = running.lastOrNull() ?: 0L,
            points = downsample(running),
            groups = groupByDay(rows),
            reviewCount = review,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun downsample(values: List<Long>): List<Float> {
        if (values.size <= 60) return values.map { it.toFloat() }
        val step = values.size / 60f
        return (0 until 60).map { values[(it * step).toInt()].toFloat() }
    }

    private fun groupByDay(rows: List<TransactionRow>): List<DayGroup> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return rows.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, dayRows) ->
                val label = when (date) {
                    today -> "today"
                    today.minusDays(1) -> "yesterday"
                    else -> date.format(DAY)
                }
                val net = dayRows.sumOf { if (it.direction == Direction.credit) it.amountMinor else -it.amountMinor }
                DayGroup(date.atStartOfDay(zone).toInstant().toEpochMilli(), label, net, dayRows)
            }
    }

    companion object {
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { HomeViewModel(container) } }
    }
}
