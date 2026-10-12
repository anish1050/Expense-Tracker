package com.anish1050.ledger.capture.gallery

import android.content.Context
import android.net.Uri
import com.anish1050.ledger.capture.parser.ParsedTransaction
import com.anish1050.ledger.capture.parser.RawCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.Source
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

sealed interface ScanOutcome {
    data class Parsed(val transaction: ParsedTransaction) : ScanOutcome
    /** No rule matched; the largest currency amount found, for prefilling the keypad. */
    data class AmountOnly(val amountMinor: Long) : ScanOutcome
    data object Nothing : ScanOutcome
}

// On-device ML Kit text recognition. The image and its text stay on the phone.
class ReceiptOcrScanner(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun scan(uri: Uri): ScanOutcome {
        val text = recognizer.process(InputImage.fromFilePath(context, uri)).await().text
        if (text.isBlank()) return ScanOutcome.Nothing

        val engine = context.container.ruleStore.load()
        engine.match(RawCapture(Source.gallery, "gallery", text, System.currentTimeMillis()))?.let { return ScanOutcome.Parsed(it) }

        val largest = AMOUNT.findAll(text).mapNotNull { Money.parseToMinor(it.groupValues[1]) }.maxOrNull()
        return if (largest != null && largest > 0) ScanOutcome.AmountOnly(largest) else ScanOutcome.Nothing
    }

    suspend fun ingest(parsed: ParsedTransaction) = context.container.transactions.ingest(parsed)

    private companion object {
        val AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""")
    }
}

// Convenience for the Add screen when only an amount was recognised.
fun ScanOutcome.AmountOnly.asManualDraft(direction: Direction = Direction.debit) = direction to amountMinor
