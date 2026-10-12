package com.anish1050.ledger

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.anish1050.ledger.ui.BottomBar
import com.anish1050.ledger.ui.LedgerNavHost
import com.anish1050.ledger.ui.Routes
import com.anish1050.ledger.ui.lock.LockGate
import com.anish1050.ledger.ui.theme.LedgerTheme

// FragmentActivity so BiometricPrompt can attach.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean("app_lock", false)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val onboarded = prefs.getBoolean("onboarded", false)

        setContent {
            LedgerTheme {
                LockGate(enabled = prefs.getBoolean("app_lock", false)) {
                    val nav = rememberNavController()
                    Scaffold(bottomBar = { BottomBar(nav) }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            LedgerNavHost(nav, startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING)
                        }
                    }
                }
            }
        }
    }
}
