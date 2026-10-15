package com.anish1050.ledger.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.BudgetPeriod
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.Segmented
import com.anish1050.ledger.ui.onboarding.PrimaryButton
import com.anish1050.ledger.ui.theme.Credit
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Line
import com.anish1050.ledger.ui.theme.Mist
import com.anish1050.ledger.ui.theme.Over
import com.anish1050.ledger.ui.theme.Paper
import kotlinx.coroutines.launch
import kotlin.math.atan2

private val GREYS = listOf(Color(0xFF111111), Color(0xFF555555), Color(0xFF999999), Color(0xFFCCCCCC), Color(0xFFE2E2E2))

@Composable
fun ReportScreen() {
    val context = LocalContext.current
    val vm: ReportViewModel = viewModel(factory = ReportViewModel.factory(context.container))
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(-1) }
    var editing by remember { mutableStateOf<BudgetPeriod?>(null) }

    LazyColumn(Modifier.background(Paper).statusBarsPadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PillChip(state.monthLabel)
                Segmented(listOf("expenses", "income"), state.directionIndex, onSelect = vm::setDirection)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.budgets.forEach { b -> BudgetPill(b, state.currency, Modifier.weight(1f)) { editing = b.period } }
            }
        }
        item {
            // Donut on the left, tip in the right-hand corner beside it: they
            // share a row so the tip can never cover a segment.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    DonutChart(state.totals.map { it.totalMinor.toFloat() }, selected, Modifier.size(170.dp)) { selected = it }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selected in state.totals.indices) {
                            Text("${state.totals[selected].totalMinor * 100 / maxOf(1, state.totalMinor)}%", style = MaterialTheme.typography.titleMedium)
                            Text(state.totals[selected].name, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("total", style = MaterialTheme.typography.labelSmall)
                            Text(Money.format(state.totalMinor, state.currency), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                state.tip?.let { tip ->
                    Column(Modifier.weight(1f).background(Mist, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Text("tip", style = MaterialTheme.typography.labelSmall)
                        Text(tip.headline, fontSize = 13.sp, color = Ink, style = MaterialTheme.typography.titleMedium)
                        tip.detail?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp) }
                    }
                }
            }
        }
        items(state.totals.size) { i ->
            val t = state.totals[i]
            val share = if (state.totalMinor > 0) t.totalMinor * 100 / state.totalMinor else 0
            val delta = state.deltaPercent[t.categoryId]
            Row(Modifier.fillMaxWidth().clickable { selected = if (selected == i) -1 else i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(GREYS[minOf(i, GREYS.lastIndex)], RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${t.emoji} ${t.name}", style = MaterialTheme.typography.titleMedium)
                    Text("$share% of total", style = MaterialTheme.typography.bodySmall)
                }
                if (delta != null) {
                    Text(
                        (if (delta > 0) "+" else "") + "$delta%",
                        Modifier.background(if (delta > 0) Color(0xFFFDE8E6) else if (delta < 0) Color(0xFFE3F3E8) else Mist, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp, color = if (delta > 0) Over else if (delta < 0) Credit else Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(Money.format(t.totalMinor, state.currency), style = MaterialTheme.typography.titleMedium)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { period ->
        BudgetEditSheet(period, state.budgets.first { it.period == period }.limitMinor, onDismiss = { editing = null }) { minor ->
            vm.setBudget(period, minor); editing = null
        }
    }
}

@Composable
private fun BudgetPill(b: BudgetUi, currency: String, modifier: Modifier, onClick: () -> Unit) {
    val limit = b.limitMinor?.takeIf { it > 0 }
    val progress = limit?.let { (b.spentMinor.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val over = limit != null && b.spentMinor > limit
    Column(modifier.border(1.dp, Line, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp)) {
        Text(b.period.name, style = MaterialTheme.typography.labelSmall)
        Text(
            if (b.limitMinor == null) "set" else "${Money.format(b.spentMinor, currency)} / ${Money.format(b.limitMinor, currency)}",
            fontSize = 11.sp, color = Ink, style = MaterialTheme.typography.titleMedium,
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(Line)) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(if (over) Over else Ink))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditSheet(period: BudgetPeriod, current: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(current?.let(Money::toEditableText) ?: "") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${period.name} budget", style = MaterialTheme.typography.displaySmall)
            OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Save") { Money.parseToMinor(text)?.takeIf { it > 0 }?.let(onSave) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DonutChart(values: List<Float>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val total = values.sum().takeIf { it > 0f } ?: return
    Canvas(
        modifier.pointerInput(values) {
            detectTapGestures { tap ->
                val center = Offset(size.width / 2f, size.height / 2f)
                var angle = Math.toDegrees(atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())).toFloat() + 90f
                if (angle < 0) angle += 360f
                var start = 0f
                values.forEachIndexed { i, v ->
                    val sweep = v / total * 360f
                    if (angle in start..(start + sweep)) { onSelect(i); return@detectTapGestures }
                    start += sweep
                }
            }
        },
    ) {
        val stroke = 40f
        val gap = 3f
        var start = -90f
        val rect = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        values.forEachIndexed { i, v ->
            val sweep = v / total * 360f
            drawArc(
                color = if (selected == i) Ink else GREYS[minOf(i, GREYS.lastIndex)],
                startAngle = start + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = rect,
                style = Stroke(width = if (selected == i) stroke + 8f else stroke),
            )
            start += sweep
        }
    }
}
