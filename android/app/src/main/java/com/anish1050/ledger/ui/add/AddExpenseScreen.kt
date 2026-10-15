package com.anish1050.ledger.ui.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.ledger.capture.gallery.ReceiptOcrScanner
import com.anish1050.ledger.capture.gallery.ScanOutcome
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.Segmented
import com.anish1050.ledger.ui.theme.Chip
import com.anish1050.ledger.ui.theme.Graphite
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Mist
import com.anish1050.ledger.ui.theme.Paper
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "✓")

@Composable
fun AddExpenseScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by context.container.categories.all.collectAsStateWithLifecycle(emptyList())

    var directionIndex by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var dayOffset by remember { mutableIntStateOf(0) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var merchant by remember { mutableStateOf("") }

    val scanner = remember { ReceiptOcrScanner(context) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            // An unreadable image is a no-op, not a crash.
            when (val outcome = runCatching { scanner.scan(uri) }.getOrDefault(ScanOutcome.NoMatch)) {
                is ScanOutcome.Parsed -> { context.container.transactions.ingest(outcome.transaction); onDone() }
                is ScanOutcome.AmountOnly -> amount = Money.toEditableText(outcome.amountMinor)
                ScanOutcome.NoMatch -> Unit
            }
        }
    }

    val days = remember { (-2..2).map { LocalDate.now().plusDays(it.toLong()) } }
    val category = categories.firstOrNull { it.id == categoryId }

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            Segmented(listOf("expenses", "income"), directionIndex) { directionIndex = it }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, date ->
                val on = i - 2 == dayOffset
                Column(
                    Modifier.background(if (on) Ink else Paper, RoundedCornerShape(12.dp)).clickable { dayOffset = i - 2 }.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(date.dayOfMonth.toString(), color = if (on) Paper else Ink, fontWeight = FontWeight.SemiBold)
                    Text(date.format(DateTimeFormatter.ofPattern("EEE")).lowercase(), color = if (on) Paper else Graphite, fontSize = 10.sp)
                }
            }
        }
        Text(
            if (amount.isEmpty()) "₹0" else "₹$amount",
            Modifier.fillMaxWidth().padding(top = 28.dp),
            style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center,
        )
        LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            items(categories, key = { it.id }) { c ->
                val on = c.id == categoryId
                Box(Modifier.background(if (on) Chip else Mist, CircleShape).clickable { categoryId = c.id }.padding(8.dp)) { Text(c.emoji) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            PillChip(merchant.ifEmpty { category?.name ?: "note" }, filled = true) { merchant = category?.name.orEmpty() }
            Spacer(Modifier.width(6.dp))
            PillChip("scan") { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        Spacer(Modifier.weight(1f))
        Keypad(
            onKey = { key ->
                when (key) {
                    "✓" -> {
                        val minor = Money.parseToMinor(amount) ?: return@Keypad
                        if (minor <= 0) return@Keypad
                        scope.launch {
                            context.container.transactions.add(
                                amountMinor = minor,
                                direction = if (directionIndex == 0) Direction.debit else Direction.credit,
                                categoryId = categoryId,
                                merchant = merchant.ifEmpty { category?.name.orEmpty() },
                                occurredAt = LocalDate.now().plusDays(dayOffset.toLong()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            )
                            onDone()
                        }
                    }
                    "." -> if (!amount.contains('.')) amount += if (amount.isEmpty()) "0." else "."
                    else -> if (amount.substringAfter('.', "").length < 2 && amount.length < 10) amount += key
                }
            },
        )
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KEYS.chunked(3).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowKeys.forEach { key ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1.3f).background(Mist, CircleShape).clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) { Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink) }
                }
            }
        }
    }
}
