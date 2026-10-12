package com.anish1050.ledger.insight

import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.CategoryTotal

data class Tip(val headline: String, val detail: String?)

// Rule-based, offline, deterministic. See spec 4.5 for the three cases.
// ponytail: swap for an on-device model only if users ask for smarter advice.
fun budgetTip(
    spentMinor: Long,
    limitMinor: Long?,
    totals: List<CategoryTotal>,
    prevTotals: List<CategoryTotal>,
    currency: String,
    periodLabel: String = "this month",
): Tip {
    if (limitMinor == null || limitMinor <= 0) return Tip("Set a budget to get tips", null)

    val overspend = spentMinor - limitMinor
    if (overspend <= 0) return Tip("On track", "${Money.format(-overspend, currency)} left $periodLabel.")

    val prevById = prevTotals.associate { it.categoryId to it.totalMinor }
    val discretionary = totals.filter { it.discretionary && it.totalMinor > 0 }
    if (discretionary.isEmpty()) return Tip("Over by ${Money.format(overspend, currency)}", "No discretionary spending to trim $periodLabel.")

    val grown = discretionary
        .map { it to (it.totalMinor - (prevById[it.categoryId] ?: 0L)) }
        .filter { (_, growth) -> growth > 0 }
        .maxByOrNull { (_, growth) -> growth }

    val (target, growth) = grown ?: (discretionary.maxBy { it.totalMinor } to 0L)
    val cut = minOf(overspend, target.totalMinor)
    val prev = prevById[target.categoryId] ?: 0L
    val change = if (growth > 0 && prev > 0) " is up ${(growth * 100 / prev)}%." else " is your biggest flexible spend."

    return Tip(
        headline = "Over by ${Money.format(overspend, currency)}",
        detail = "${target.name.replaceFirstChar { it.uppercase() }}$change Trim it by ${Money.format(cut, currency)} to stay in budget.",
    )
}
