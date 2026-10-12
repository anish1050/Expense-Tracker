package com.anish1050.ledger

import android.app.Application
import android.os.StrictMode
import androidx.work.Configuration

class LedgerApp : Application(), Configuration.Provider {

    // Built lazily so Application.onCreate stays empty and cold start stays short.
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build()
}
