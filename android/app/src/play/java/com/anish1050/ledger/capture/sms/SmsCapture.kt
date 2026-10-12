package com.anish1050.ledger.capture.sms

import android.content.Context

// Play build has no SMS permission, so SMS capture does not exist here. Same
// API as the foss flavor so Settings compiles identically.
object SmsCapture {
    const val AVAILABLE = false
    val permissions: Array<String> = emptyArray()
    fun isGranted(context: Context): Boolean = false
    suspend fun backfill(context: Context, days: Int = 90): Int = 0
}
