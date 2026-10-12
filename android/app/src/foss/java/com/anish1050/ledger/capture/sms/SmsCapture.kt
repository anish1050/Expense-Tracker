package com.anish1050.ledger.capture.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.anish1050.ledger.capture.parser.RawCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.db.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsCapture {
    const val AVAILABLE = true
    val permissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    fun isGranted(context: Context): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    // One-time import of recent inbox messages after the user opts in.
    suspend fun backfill(context: Context, days: Int = 90): Int = withContext(Dispatchers.IO) {
        if (!isGranted(context)) return@withContext 0
        val engine = context.container.ruleStore.load()
        val since = System.currentTimeMillis() - days * 86_400_000L
        var submitted = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val sender = cursor.getString(0) ?: continue
                if (!engine.acceptsSender(Source.sms, sender)) continue
                val body = cursor.getString(1) ?: continue
                context.container.capture.submit(RawCapture(Source.sms, sender, body, cursor.getLong(2)))
                submitted++
            }
        }
        submitted
    }
}
