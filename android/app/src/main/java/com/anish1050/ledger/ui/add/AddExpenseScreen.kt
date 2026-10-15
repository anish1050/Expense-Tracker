package com.anish1050.ledger.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.anish1050.ledger.ui.motion.Haptic
import com.anish1050.ledger.ui.motion.Motion
import com.anish1050.ledger.ui.motion.pressable
import com.anish1050.ledger.ui.theme.Pill
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

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

    val view = LocalView.current
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

    Column(Modifier.fillMaxSize().background(Paper).systemBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            Segmented(listOf("expenses", "income"), directionIndex) { directionIndex = it }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, date ->
                val on = i - 2 == dayOffset
                val bg by animateColorAsState(if (on) Ink else Paper, label = "day")
                val fg by animateColorAsState(if (on) Paper else Ink, label = "dayText")
                Column(
                    Modifier.pressable(Haptic.Tick) { dayOffset = i - 2 }.background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(date.dayOfMonth.toString(), color = fg, fontWeight = FontWeight.SemiBold)
                    Text(date.format(DateTimeFormatter.ofPattern("EEE")).lowercase(), color = if (on) Paper else Graphite, fontSize = 10.sp)
                }
            }
        }
        // Each new digit rolls up into place, like an odometer.
        AnimatedContent(
            targetState = if (amount.isEmpty()) "₹0" else "₹$amount",
            transitionSpec = {
                val growing = targetState.length >= initialState.length
                (slideInVertically(Motion.screen) { if (growing) it / 2 else -it / 2 } + fadeIn(Motion.screenFade)) togetherWith
                    (slideOutVertically(Motion.screen) { if (growing) -it / 2 else it / 2 } + fadeOut(Motion.screenFade))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            contentAlignment = Alignment.Center,
            label = "amount",
        ) { text -> Text(text, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center) }
        LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            items(categories, key = { it.id }) { c ->
                val on = c.id == categoryId
                val bg by animateColorAsState(if (on) Chip else Mist, label = "category")
                val scale by animateFloatAsState(if (on) 1.15f else 1f, Motion.press, label = "categoryScale")
                Box(
                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                        .pressable(Haptic.Tick) { categoryId = c.id }.background(bg, CircleShape).padding(8.dp),
                ) { Text(c.emoji) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            PillChip(merchant.ifEmpty { category?.name ?: "note" }, filled = true) { merchant = category?.name.orEmpty() }
            Spacer(Modifier.width(6.dp))
            PillChip("scan") { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        Spacer(Modifier.weight(1f))
        Keypad(
            canSave = (Money.parseToMinor(amount) ?: 0) > 0,
            onKey = { key ->
                when (key) {
                    "✓" -> {
                        val minor = Money.parseToMinor(amount)?.takeIf { it > 0 }
                        if (minor == null) { Haptic.Reject.play(view); return@Keypad }
                        Haptic.Confirm.play(view)
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
                    "⌫" -> amount = amount.dropLast(1)
                    "." -> if (!amount.contains('.')) amount += if (amount.isEmpty()) "0." else "."
                    "0" -> if (amount.isNotEmpty() && amount.substringAfter('.', "").length < 2 && amount.length < 10) amount += key
                    else -> if (amount.substringAfter('.', "").length < 2 && amount.length < 10) amount = (if (amount == "0") "" else amount) + key
                }
            },
        )
    }
}

@Composable
private fun Keypad(canSave: Boolean, onKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KEYS.chunked(3).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowKeys.forEach { key ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1.6f)
                            .pressable(Haptic.Tap, pressedScale = 0.9f) { onKey(key) }
                            .background(Mist, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink) }
                }
            }
        }
        // Save is its own control so it is never hit by accident mid-number;
        // it dims until the amount is valid. Haptics are played by onKey.
        val saveBg by animateColorAsState(if (canSave) Ink else Graphite.copy(alpha = 0.35f), label = "save")
        Text(
            "Save",
            Modifier.fillMaxWidth().pressable(Haptic.Tick, pressedScale = 0.97f) { onKey("✓") }
                .background(saveBg, Pill).padding(vertical = 16.dp),
            color = Paper, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        )
    }
}
