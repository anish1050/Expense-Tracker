package com.anish1050.ledger.ui.lock

import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.anish1050.ledger.ui.theme.Paper

private const val RELOCK_AFTER_MS = 60_000L

// Biometric or device-credential gate. Relocks when the app was in the
// background for longer than a minute.
@Composable
fun LockGate(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val activity = LocalContext.current as FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by remember { mutableStateOf(false) }
    var leftAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> leftAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (leftAt > 0 && System.currentTimeMillis() - leftAt > RELOCK_AFTER_MS) unlocked = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(unlocked) {
        if (unlocked) return@LaunchedEffect
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock")
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                .build(),
        )
    }

    if (unlocked) content() else Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) { Text("Locked") }
}
