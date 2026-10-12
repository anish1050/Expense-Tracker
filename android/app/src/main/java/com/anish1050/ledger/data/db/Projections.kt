package com.anish1050.ledger.data.db

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo

// Small row models the UI reads; never the full entity on the hot path.

@Immutable
data class TransactionRow(
    val id: String,
    val emoji: String?,
    val merchant: String,
    @ColumnInfo(name = "sub_label") val subLabel: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)

@Immutable
data class DayNet(
    @ColumnInfo(name = "day_start") val dayStart: Long,
    @ColumnInfo(name = "day_net_minor") val dayNetMinor: Long,
)

@Immutable
data class CategoryTotal(
    @ColumnInfo(name = "category_id") val categoryId: String,
    val name: String,
    val emoji: String,
    val discretionary: Boolean,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

@Immutable
data class WidgetSnapshot(
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    @ColumnInfo(name = "month_spent_minor") val monthSpentMinor: Long,
)
