package com.anish1050.ledger.capture.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.anish1050.ledger.capture.parser.RawCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.db.Source

class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = parts.firstOrNull()?.displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return
        context.container.capture.submit(RawCapture(Source.sms, sender, body, System.currentTimeMillis()))
    }
}
