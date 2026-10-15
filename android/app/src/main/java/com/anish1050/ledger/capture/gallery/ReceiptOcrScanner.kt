package com.anish1050.ledger.capture.gallery

import android.content.Context
import android.net.Uri
import com.anish1050.ledger.capture.parser.ParsedTransaction
import com.anish1050.ledger.capture.parser.RawCapture
import com.anish1050.ledger.container
import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Source
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ScanOutcome {
    data class Parsed(val transaction: ParsedTransaction) : ScanOutcome
    /** No rule matched; the largest currency amount found, for prefilling the keypad. */
    data class AmountOnly(val amountMinor: Long) : ScanOutcome
    data object NoMatch : ScanOutcome
}

// On-device ML Kit text recognition. The image and its text stay on the phone.
class ReceiptOcrScanner(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Decoding the bitmap is disk + CPU work; it must not run on the caller's
    // (main) thread.
    suspend fun scan(uri: Uri): ScanOutcome {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = recognizer.process(image).await().text
        if (text.isBlank()) return ScanOutcome.NoMatch

        val engine = context.container.ruleStore.load()
        engine.match(RawCapture(Source.gallery, "gallery", text, System.currentTimeMillis()))?.let { return ScanOutcome.Parsed(it) }

        val largest = AMOUNT.findAll(text).mapNotNull { m -> Money.parseToMinor(m.groupValues[1].ifEmpty { m.groupValues[2] }) }.maxOrNull()
        return if (largest != null && largest > 0) ScanOutcome.AmountOnly(largest) else ScanOutcome.NoMatch
    }

    private companion object {
        // OCR usually drops or misreads the rupee sign, so a bare two-decimal
        // number also counts as an amount for the keypad prefill.
        val AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)|\b(\d{1,3}(?:,\d{2,3})*\.\d{2})\b""")
    }
}
