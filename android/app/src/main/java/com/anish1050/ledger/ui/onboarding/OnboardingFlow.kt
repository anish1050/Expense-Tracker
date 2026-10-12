package com.anish1050.ledger.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anish1050.ledger.R
import com.anish1050.ledger.capture.notification.TransactionNotificationListener
import com.anish1050.ledger.capture.sms.SmsCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.BudgetPeriod
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.theme.Ink
import com.anish1050.ledger.ui.theme.Line
import com.anish1050.ledger.ui.theme.Paper
import com.anish1050.ledger.ui.theme.Pill
import kotlinx.coroutines.launch

@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    fun finish() {
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
        onDone()
    }
    when (step) {
        0 -> WelcomeStep(onContinue = { step = 1 })
        1 -> BudgetSetupStep(onNext = { step = 2 })
        else -> PermissionsStep(onDone = ::finish)
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signIn = remember { GoogleSignIn(context) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            PillChip("balance", Modifier.align(Alignment.TopCenter))
            PillChip("income", Modifier.align(Alignment.TopStart).padding(top = 36.dp))
            PillChip("expenses", Modifier.align(Alignment.TopEnd).padding(top = 36.dp))
            PillChip("fun", Modifier.align(Alignment.CenterStart))
            PillChip("food", Modifier.align(Alignment.CenterEnd))
            PillChip("retire", Modifier.align(Alignment.BottomStart).padding(bottom = 36.dp))
            PillChip("travel", Modifier.align(Alignment.BottomEnd).padding(bottom = 36.dp))
        }
        Text(stringResource(R.string.app_name).lowercase(), fontSize = 48.sp, color = Ink, style = androidx.compose.material3.MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.tagline), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.continue_with_google)) {
            scope.launch { signIn.run(); onContinue() }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton(stringResource(R.string.skip_use_offline), onContinue)
    }
}

@Composable
private fun BudgetSetupStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var week by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.budget_title), style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        AmountField(stringResource(R.string.budget_week), week) { week = it }
        AmountField(stringResource(R.string.budget_month), month) { month = it }
        AmountField(stringResource(R.string.budget_year), year) { year = it }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.next)) {
            scope.launch {
                val budgets = context.container.budgets
                Money.parseToMinor(week)?.let { budgets.set(BudgetPeriod.week, it) }
                Money.parseToMinor(month)?.let { budgets.set(BudgetPeriod.month, it) }
                Money.parseToMinor(year)?.let { budgets.set(BudgetPeriod.year, it) }
                onNext()
            }
        }
        GhostButton(stringResource(R.string.skip_for_now), onNext)
    }
}

@Composable
private fun PermissionsStep(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf(TransactionNotificationListener.isEnabled(context)) }
    var sms by remember { mutableStateOf(SmsCapture.isGranted(context)) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        sms = granted.values.all { it }
        if (sms) scope.launch { SmsCapture.backfill(context) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.permissions_title), style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        PermissionCard(stringResource(R.string.perm_notifications), stringResource(R.string.perm_notifications_why), notifications) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            notifications = true
        }
        if (SmsCapture.AVAILABLE) {
            PermissionCard(stringResource(R.string.perm_sms), stringResource(R.string.perm_sms_why), sms) { smsLauncher.launch(SmsCapture.permissions) }
        }
        PermissionCard(stringResource(R.string.perm_gallery), stringResource(R.string.perm_gallery_why), true) {}
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.done), onDone)
    }
}

@Composable
private fun PermissionCard(title: String, why: String, on: Boolean, onEnable: () -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, Line, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(why, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        Switch(checked = on, onCheckedChange = { if (it) onEnable() })
    }
}

@Composable
private fun AmountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().background(Ink, Pill).clickable(onClick = onClick).padding(vertical = 14.dp),
        color = Paper, textAlign = TextAlign.Center, style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().border(1.dp, Line, Pill).clickable(onClick = onClick).padding(vertical = 14.dp),
        color = Ink, textAlign = TextAlign.Center, style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    )
}
