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

    // "1,23,456.70", "1,234.56" and "1.234,56" all parse to the same minor units:
    // a trailing comma plus two digits is a decimal separator, other commas and
    // dots in group position are grouping. Mirrors toMinor() in the backend.
    fun parseToMinor(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = if (EUROPEAN.matches(trimmed)) trimmed.replace(".", "").replace(',', '.') else trimmed.replace(",", "")
        return runCatching { BigDecimal(cleaned).movePointRight(2).toLong() }.getOrNull()
    }

    private val EUROPEAN = Regex("""^\d{1,3}(?:\.\d{3})*,\d{1,2}$""")

    private fun localeFor(currency: String): Locale = when (currency) {
        "INR" -> Locale("en", "IN")
        "USD" -> Locale.US
        "GBP" -> Locale.UK
        "EUR" -> Locale.GERMANY
        else -> Locale.getDefault()
    }
}
