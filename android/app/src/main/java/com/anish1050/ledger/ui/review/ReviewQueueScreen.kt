package com.anish1050.ledger.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.theme.Paper
import kotlinx.coroutines.launch

// Low-confidence captures wait here until the user confirms or discards them.
@Composable
fun ReviewQueueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue by context.container.db.transactionDao().reviewQueue().collectAsStateWithLifecycle(emptyList())
    val repo = context.container.transactions

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) { PillChip("← back", onClick = onBack) }
        Text("to review", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.displaySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(queue, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    val signed = if (t.direction == Direction.credit) t.amountMinor else -t.amountMinor
                    Text("${Money.format(signed, t.currency, withSign = true)}  ${t.merchant}", style = MaterialTheme.typography.titleMedium)
                    Text("${t.source.name} · ${(t.confidence * 100).toInt()}% sure", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillChip("looks right", filled = true) { scope.launch { repo.confirm(t.id) } }
                        PillChip("not a transaction") { scope.launch { repo.discard(t.id) } }
                    }
                }
            }
        }
    }
}
