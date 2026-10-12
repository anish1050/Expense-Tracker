package com.anish1050.ledger.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anish1050.ledger.capture.notification.TransactionNotificationListener
import com.anish1050.ledger.capture.sms.SmsCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.export.DbExport
import com.anish1050.ledger.data.sync.RowCrypto
import com.anish1050.ledger.data.sync.SyncWorker
import com.anish1050.ledger.ui.PillChip
import com.anish1050.ledger.ui.onboarding.GhostButton
import com.anish1050.ledger.ui.onboarding.PrimaryButton
import com.anish1050.ledger.ui.theme.Paper
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    val auth = context.container.authStore

    var appLock by remember { mutableStateOf(prefs.getBoolean("app_lock", false)) }
    var backupOn by remember { mutableStateOf(auth.backupKey != null) }
    var passphrase by remember { mutableStateOf("") }
    val notifications = TransactionNotificationListener.isEnabled(context)
    var sms by remember { mutableStateOf(SmsCapture.isGranted(context)) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        sms = granted.values.all { it }
        if (sms) scope.launch { SmsCapture.backfill(context) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { scope.launch { DbExport.exportTo(context, it) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { DbExport.importFrom(context, it) } }
    }

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("settings", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.displaySmall)

        Section("capture")
        ToggleRow("payment notifications", notifications) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        if (SmsCapture.AVAILABLE) ToggleRow("bank sms", sms) { if (it) smsLauncher.launch(SmsCapture.permissions) }

        Section("security")
        ToggleRow("app lock (biometric)", appLock) { appLock = it; prefs.edit().putBoolean("app_lock", it).apply() }

        Section("backup")
        Text("Encrypted on this phone with a passphrase only you know. Losing it means losing the cloud copy.", style = MaterialTheme.typography.bodySmall)
        if (!auth.isSignedIn) {
            Text("Sign in with Google in onboarding to enable backup.", style = MaterialTheme.typography.bodySmall)
        } else if (!backupOn) {
            OutlinedTextField(passphrase, { passphrase = it }, label = { Text("backup passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Turn on backup") {
                if (passphrase.length < 8) return@PrimaryButton
                auth.backupKey = RowCrypto.deriveKey(passphrase.toCharArray(), auth.userId.orEmpty())
                passphrase = ""
                backupOn = true
                SyncWorker.schedule(context)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("sync now", filled = true) { SyncWorker.runNow(context) }
                PillChip("turn off") { auth.backupKey = null; backupOn = false }
            }
        }

        Section("data")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("export database") { exportLauncher.launch("ledger-backup.db") }
            PillChip("import") { importLauncher.launch(arrayOf("*/*")) }
        }

        Section("account")
        if (auth.isSignedIn) {
            GhostButton("Sign out") { auth.clear(); backupOn = false }
            GhostButton("Delete account and cloud data") {
                scope.launch { runCatching { context.container.api.deleteAccount() }; auth.clear(); backupOn = false }
            }
        }
        Text("v${com.anish1050.ledger.BuildConfig.VERSION_NAME} · ${com.anish1050.ledger.BuildConfig.FLAVOR}", Modifier.padding(bottom = 24.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.labelSmall)

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = onChange)
    }
}
