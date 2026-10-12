package com.anish1050.ledger.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.anish1050.ledger.MainActivity
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.Period
import com.anish1050.ledger.data.db.BudgetPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Ink = ColorProvider(com.anish1050.ledger.ui.theme.Ink)
private val Paper = ColorProvider(com.anish1050.ledger.ui.theme.Paper)
private val Graphite = ColorProvider(com.anish1050.ledger.ui.theme.Graphite)
private val Mist = ColorProvider(com.anish1050.ledger.ui.theme.Mist)
private val OverBudget = ColorProvider(com.anish1050.ledger.ui.theme.Over)

object WidgetRefresh {
    private val scope = CoroutineScope(Dispatchers.Default)
    fun requestUpdate(context: Context) {
        scope.launch { BalanceWidget().updateAll(context) }
    }
}

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("ledger://add"), context, MainActivity::class.java)
        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(Ink).cornerRadius(28.dp).clickable(actionStartActivity(intent)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

class BalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val month = Period.month()
        val snapshot = context.container.db.transactionDao().widgetSnapshot(month.from, month.to)
        val budget = context.container.db.budgetDao().all().first().firstOrNull { it.period == BudgetPeriod.month }
        val progress = budget?.let { (snapshot.monthSpentMinor.toFloat() / it.amountMinor).coerceIn(0f, 1f) }
        val over = budget != null && snapshot.monthSpentMinor > budget.amountMinor

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().background(Paper).cornerRadius(20.dp).padding(14.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                ) {
                    Text("this month", style = TextStyle(color = Graphite, fontSize = 10.sp))
                    Text(Money.format(snapshot.balanceMinor, "INR", withSign = true), style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(8.dp))
                    if (budget != null && progress != null) {
                        Text(
                            "budget ${Money.format(snapshot.monthSpentMinor, "INR")} / ${Money.format(budget.amountMinor, "INR")}",
                            style = TextStyle(color = Graphite, fontSize = 10.sp),
                        )
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = GlanceModifier.height(3.dp),
                            color = if (over) OverBudget else Ink,
                            backgroundColor = Mist,
                        )
                    } else {
                        Text("set a budget in Report", style = TextStyle(color = Graphite, fontSize = 10.sp))
                    }
                }
            }
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
