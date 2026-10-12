package com.anish1050.ledger.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.TransactionRow
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.theme.Credit
import com.anish1050.ledger.ui.theme.Graphite
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Mist
import com.anish1050.ledger.ui.theme.Paper

@Composable
fun HomeScreen(onReview: () -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(context.container))
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            PillChip(state.periodLabel)
        }
        Text(
            Money.format(state.balanceMinor, state.currency),
            Modifier.fillMaxWidth().padding(top = 10.dp),
            style = MaterialTheme.typography.displayLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        BalanceChart(state.points, Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 18.dp, vertical = 8.dp))
        if (state.reviewCount > 0) {
            Text(
                "${state.reviewCount} to review",
                Modifier.align(Alignment.CenterHorizontally).background(Mist, androidx.compose.foundation.shape.RoundedCornerShape(50)).clickable(onClick = onReview).padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = Ink,
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            state.groups.forEach { group ->
                item(key = "h${group.dayStart}") { DayHeader(group.label, group.netMinor, state.currency) }
                items(group.rows, key = { it.id }) { TransactionRowView(it) }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, netMinor: Long, currency: String) {
    Column(Modifier.padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(Money.format(netMinor, currency, withSign = true), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TransactionRowView(row: TransactionRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(Mist, CircleShape), contentAlignment = Alignment.Center) { Text(row.emoji ?: "💸") }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.merchant, style = MaterialTheme.typography.titleMedium)
            row.subLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        val signed = if (row.direction == Direction.credit) row.amountMinor else -row.amountMinor
        Text(
            Money.format(signed, row.currency, withSign = true),
            style = MaterialTheme.typography.titleMedium,
            color = if (row.direction == Direction.credit) Credit else Ink,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// Daily running balance as a smooth line; at most 60 points reach here.
@Composable
fun BalanceChart(points: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        fun at(i: Int) = Offset(i * stepX, size.height - (points[i] - min) / span * (size.height - 8f) - 4f)

        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) {
                val prev = at(i - 1)
                val cur = at(i)
                val mid = (prev.x + cur.x) / 2
                cubicTo(mid, prev.y, mid, cur.y, cur.x, cur.y)
            }
        }
        drawPath(path, Ink, style = Stroke(width = 4f))
        val last = at(points.lastIndex)
        drawCircle(Color.White, radius = 8f, center = last)
        drawCircle(Ink, radius = 8f, center = last, style = Stroke(width = 4f))
    }
}
