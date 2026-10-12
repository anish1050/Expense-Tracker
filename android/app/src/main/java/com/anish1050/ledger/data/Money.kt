package com.anish1050.ledger.data

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// Money is always a Long in minor units (paise, cents). Formatting is the only
// place it becomes decimal, and formatters are cached per currency.
object Money {
    private val formatters = ConcurrentHashMap<String, NumberFormat>()

    fun format(amountMinor: Long, currency: String, withSign: Boolean = false): String {
        val formatter = formatters.getOrPut(currency) {
            NumberFormat.getCurrencyInstance(localeFor(currency)).apply {
                this.currency = Currency.getInstance(currency)
                maximumFractionDigits = if (amountMinor % 100L == 0L) 0 else 2
            }
        }
        val text = formatter.format(BigDecimal.valueOf(amountMinor, 2).abs())
        return when {
            !withSign -> text
            amountMinor < 0 -> "-$text"
            else -> "+$text"
        }
    }

    fun parseToMinor(text: String): Long? {
        val cleaned = text.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        return runCatching { BigDecimal(cleaned).movePointRight(2).toLong() }.getOrNull()
    }

    private fun localeFor(currency: String): Locale = when (currency) {
        "INR" -> Locale("en", "IN")
        "USD" -> Locale.US
        "GBP" -> Locale.UK
        "EUR" -> Locale.GERMANY
        else -> Locale.getDefault()
    }
}
