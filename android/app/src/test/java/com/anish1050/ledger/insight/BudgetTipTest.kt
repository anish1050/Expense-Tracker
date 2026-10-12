package com.anish1050.ledger.insight

import com.anish1050.ledger.data.db.CategoryTotal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetTipTest {

    private fun cat(id: String, name: String, total: Long, discretionary: Boolean = true) =
        CategoryTotal(categoryId = id, name = name, emoji = "x", discretionary = discretionary, totalMinor = total)

    @Test
    fun noBudgetAsksForOne() {
        assertThat(budgetTip(100, null, emptyList(), emptyList(), "INR").headline).isEqualTo("Set a budget to get tips")
    }

    @Test
    fun underBudgetIsOnTrack() {
        val tip = budgetTip(spentMinor = 800_000, limitMinor = 1_000_000, totals = emptyList(), prevTotals = emptyList(), currency = "INR")
        assertThat(tip.headline).isEqualTo("On track")
        assertThat(tip.detail).contains("2,000")
    }

    @Test
    fun overBudgetNamesFastestGrowingDiscretionaryCategory() {
        val totals = listOf(cat("fd", "food delivery", 427_000), cat("rent", "rent", 1_500_000, discretionary = false), cat("shop", "shopping", 213_000))
        val prev = listOf(cat("fd", "food delivery", 305_000), cat("shop", "shopping", 200_000))
        val tip = budgetTip(spentMinor = 1_124_000, limitMinor = 1_000_000, totals = totals, prevTotals = prev, currency = "INR")
        assertThat(tip.headline).isEqualTo("Over by ₹1,240")
        assertThat(tip.detail).startsWith("Food delivery is up 40%")
        assertThat(tip.detail).contains("Trim it by ₹1,240")
    }

    @Test
    fun cutNeverExceedsTheCategoryItself() {
        val totals = listOf(cat("fd", "food delivery", 50_000))
        val tip = budgetTip(spentMinor = 1_200_000, limitMinor = 1_000_000, totals = totals, prevTotals = emptyList(), currency = "INR")
        assertThat(tip.detail).contains("Trim it by ₹500")
    }

    @Test
    fun noDiscretionaryGrowthFallsBackToLargest() {
        val totals = listOf(cat("fd", "food delivery", 100_000), cat("shop", "shopping", 300_000))
        val prev = listOf(cat("fd", "food delivery", 100_000), cat("shop", "shopping", 300_000))
        val tip = budgetTip(spentMinor = 1_100_000, limitMinor = 1_000_000, totals = totals, prevTotals = prev, currency = "INR")
        assertThat(tip.detail).startsWith("Shopping is your biggest flexible spend")
    }
}
