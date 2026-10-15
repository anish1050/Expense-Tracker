package com.anish1050.ledger.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.TransactionRow
import com.anish1050.ledger.ui.AnimatedMoney
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.motion.Motion
import com.anish1050.ledger.ui.motion.pressable
import com.anish1050.ledger.ui.theme.Credit
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Mist
import com.anish1050.ledger.ui.theme.Paper

@Composable
fun HomeScreen(onReview: () -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(context.container))
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerPx = with(LocalDensity.current) { 220.dp.toPx() }

    // 0 when the header is fully visible, 1 once it has scrolled away. Drives
    // the iOS-style large-title collapse without recomposing the list.
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / headerPx).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        // Content runs edge to edge, under the status bar.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBar + 12.dp, bottom = 24.dp),
        ) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().graphicsLayer {
                        val s = 1f - collapse * 0.12f
                        scaleX = s; scaleY = s
                        alpha = 1f - collapse * 0.7f
                        translationY = collapse * 40f
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PillChip(state.periodLabel)
                    AnimatedMoney(
                        state.balanceMinor, state.currency,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    BalanceChart(state.points, Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 18.dp, vertical = 8.dp))
                    if (state.reviewCount > 0) {
                        Text(
                            "${state.reviewCount} to review",
                            Modifier.pressable(onClick = onReview).background(Mist, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = Ink,
                        )
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = "h${group.dayStart}") {
                    DayHeader(group.label, group.netMinor, state.currency, Modifier.animateItem().padding(horizontal = 18.dp))
                }
                items(group.rows, key = { it.id }) {
                    TransactionRowView(it, Modifier.animateItem().padding(horizontal = 18.dp))
                }
            }
        }

        // Soft fade under the status bar so rows dissolve as they scroll up,
        // instead of colliding with the clock and icons.
        Box(
            Modifier.fillMaxWidth().height(statusBar + 20.dp)
                .background(Brush.verticalGradient(listOf(Paper, Paper.copy(alpha = 0.85f), Color.Transparent))),
        )
        // Compact title that appears once the big balance has scrolled away.
        Text(
            Money.format(state.balanceMinor, state.currency),
            Modifier.align(Alignment.TopCenter).padding(top = statusBar)
                .graphicsLayer { alpha = ((collapse - 0.6f) / 0.4f).coerceIn(0f, 1f) },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DayHeader(label: String, netMinor: Long, currency: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(Money.format(netMinor, currency, withSign = true), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TransactionRowView(row: TransactionRow, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Mist, CircleShape), contentAlignment = Alignment.Center) { Text(row.emoji ?: "💸") }
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
            textAlign = TextAlign.End,
        )
    }
}

// Daily running balance as a smooth line that draws itself in, left to right.
@Composable
fun BalanceChart(points: List<Float>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.chart)
    }
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        fun at(i: Int) = Offset(i * stepX, size.height - (points[i] - min) / span * (size.height - 12f) - 6f)

        val full = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) {
                val prev = at(i - 1)
                val cur = at(i)
                val mid = (prev.x + cur.x) / 2
                cubicTo(mid, prev.y, mid, cur.y, cur.x, cur.y)
            }
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val visible = Path()
        val end = measure.length * progress.value
        measure.getSegment(0f, end, visible, true)
        drawPath(visible, Ink, style = Stroke(width = 4f))

        if (progress.value > 0.98f) {
            val last = at(points.lastIndex)
            drawCircle(Color.White, radius = 9f, center = last)
            drawCircle(Ink, radius = 9f, center = last, style = Stroke(width = 4f))
        }
    }
}
