package com.anish1050.ledger.insight

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anish1050.ledger.MainActivity
import com.anish1050.ledger.R
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.Period
import com.anish1050.ledger.data.Range
import com.anish1050.ledger.data.db.BudgetPeriod
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.LedgerDatabase

data class BudgetStatus(val period: BudgetPeriod, val spentMinor: Long, val limitMinor: Long, val windowStart: Long)

/**
 * Budgets that are over their limit and have not been alerted yet in their
 * current window (today, this week, ...). One alert per period per window: a
 * second expense in an already-blown week stays quiet.
 */
fun budgetsNewlyExceeded(statuses: List<BudgetStatus>, lastAlertedWindow: (BudgetPeriod) -> Long?): List<BudgetStatus> =
    statuses.filter { it.limitMinor > 0 && it.spentMinor > it.limitMinor && lastAlertedWindow(it.period) != it.windowStart }

class BudgetAlerts(private val context: Context, private val db: LedgerDatabase) {

    private val prefs by lazy { context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE) }

    /** Called after every confirmed spend is written. Cheap: four indexed sums. */
    suspend fun check(currency: String = "INR") {
        val statuses = BudgetPeriod.entries.mapNotNull { period ->
            val limit = db.budgetDao().byPeriod(period)?.amountMinor ?: return@mapNotNull null
            val range = rangeFor(period)
            BudgetStatus(period, db.transactionDao().sumBetween(Direction.debit, range.from, range.to), limit, range.from)
        }
        val due = budgetsNewlyExceeded(statuses) { period -> prefs.getLong(period.name, -1L).takeIf { it >= 0 } }
        if (due.isEmpty() || !canNotify()) return
        ensureChannel()
        due.forEach { status ->
            post(status, currency)
            prefs.edit().putLong(status.period.name, status.windowStart).apply()
        }
    }

    private fun post(status: BudgetStatus, currency: String) {
        val label = LABELS.getValue(status.period)
        val over = status.spentMinor - status.limitMinor
        val open = PendingIntent.getActivity(
            context, status.period.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_budget)
            .setContentTitle("Over your ${label.first} budget")
            .setContentText("${Money.format(status.spentMinor, currency)} spent of ${Money.format(status.limitMinor, currency)} ${label.second}. ${Money.format(over, currency)} over.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Permission checked in canNotify(); the lint can't see across calls.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + status.period.ordinal, notification)
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When spending goes over your daily, weekly, monthly or yearly budget"
                },
            )
        }
    }

    private fun rangeFor(period: BudgetPeriod): Range = when (period) {
        BudgetPeriod.day -> Period.day()
        BudgetPeriod.week -> Period.week()
        BudgetPeriod.month -> Period.month()
        BudgetPeriod.year -> Period.year()
    }

    private companion object {
        const val CHANNEL = "budget_alerts"
        const val NOTIFICATION_BASE = 4100
        val LABELS = mapOf(
            BudgetPeriod.day to ("daily" to "today"),
            BudgetPeriod.week to ("weekly" to "this week"),
            BudgetPeriod.month to ("monthly" to "this month"),
            BudgetPeriod.year to ("yearly" to "this year"),
        )
    }
}
