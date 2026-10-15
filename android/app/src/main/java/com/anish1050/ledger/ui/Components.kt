package com.anish1050.ledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.ui.motion.Haptic
import com.anish1050.ledger.ui.motion.Motion
import com.anish1050.ledger.ui.motion.pressable
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Line
import com.anish1050.ledger.ui.theme.Mist
import com.anish1050.ledger.ui.theme.Paper
import com.anish1050.ledger.ui.theme.Pill
import kotlin.math.roundToLong

@Composable
fun PillChip(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: (() -> Unit)? = null) {
    val base = modifier
        .let { if (onClick != null) it.pressable(onClick = onClick) else it }
        .background(if (filled) Ink else Paper, Pill)
        .border(1.dp, if (filled) Ink else Line, Pill)
        .padding(horizontal = 12.dp, vertical = 6.dp)
    Text(text, modifier = base, color = if (filled) Paper else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

/**
 * iOS-style segmented control: a white thumb slides between options on a
 * spring instead of the selection jumping.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val offsets = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    var rowHeight by remember { mutableStateOf(0.dp) }
    val target = offsets[selected]
    val thumbX by animateDpAsState(target?.first ?: 0.dp, Motion.screenDp, label = "thumbX")
    val thumbW by animateDpAsState(target?.second ?: 0.dp, Motion.screenDp, label = "thumbW")

    Box(modifier.background(Mist, Pill).padding(3.dp).wrapContentSize()) {
        if (target != null) {
            Box(
                Modifier.offset(x = thumbX).width(thumbW).height(rowHeight)
                    .shadow(2.dp, Pill).background(Paper, Pill),
            )
        }
        Row(Modifier.onGloballyPositioned { c -> rowHeight = with(density) { c.size.height.toDp() } }) {
            options.forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier
                        .onGloballyPositioned { c ->
                            with(density) { offsets[index] = c.positionInParent().x.toDp() to c.size.width.toDp() }
                        }
                        .pressable(Haptic.Tick, pressedScale = 0.97f) { if (index != selected) onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
            }
        }
    }
}

/**
 * Money that counts to its new value instead of snapping, like the balance on
 * premium banking apps. Whole units while moving so paise never flicker.
 */
@Composable
fun AnimatedMoney(amountMinor: Long, currency: String, style: TextStyle, modifier: Modifier = Modifier, withSign: Boolean = false) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(amountMinor) { anim.animateTo(amountMinor.toFloat(), tween(650, easing = LinearOutSlowInEasing)) }
    val shown = if (anim.isRunning) (anim.value / 100).roundToLong() * 100 else amountMinor
    Text(Money.format(shown, currency, withSign), modifier, style = style)
}
