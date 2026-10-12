package com.anish1050.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Line
import com.anish1050.ledger.ui.theme.Mist
import com.anish1050.ledger.ui.theme.Paper
import com.anish1050.ledger.ui.theme.Pill

@Composable
fun PillChip(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: (() -> Unit)? = null) {
    val base = modifier
        .background(if (filled) Ink else Paper, Pill)
        .border(1.dp, if (filled) Ink else Line, Pill)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 12.dp, vertical = 6.dp)
    Text(text, modifier = base, color = if (filled) Paper else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.background(Mist, Pill).padding(3.dp)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                label,
                modifier = Modifier
                    .background(if (on) Paper else Mist, Pill)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
        }
    }
}
