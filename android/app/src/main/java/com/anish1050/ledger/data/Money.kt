package com.anish1050.ledger.data

import java.math.BigDecimal
import kotlin.math.abs

// Money is always a Long in minor units (paise, cents). Formatting is the only
// place it becomes decimal. Grouping is done by hand so the output is identical
// on Android's ICU and the desktop JVM used by unit tests.
object Money {

    fun format(amountMinor: Long, currency: String, withSign: Boolean = false): String {
        val magnitude = abs(amountMinor)
        val whole = magnitude / 100
        val fraction = (magnitude % 100).toInt()
        val grouped = if (currency == "INR") groupIndian(whole.toString()) else groupWestern(whole.toString())
        val number = if (fraction == 0) grouped else "$grouped.${fraction.toString().padStart(2, '0')}"
        val text = symbolFor(currency) + number
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

    /** Plain decimal text for editing, e.g. 123456 -> "1234.56", 50000 -> "500". */
    fun toEditableText(amountMinor: Long): String = BigDecimal.valueOf(amountMinor, 2).stripTrailingZeros().toPlainString()

    private val EUROPEAN = Regex("""^\d{1,3}(?:\.\d{3})*,\d{1,2}$""")

    private fun symbolFor(currency: String) = when (currency) {
        "INR" -> "₹"
        "USD" -> "$"
        "GBP" -> "£"
        "EUR" -> "€"
        else -> "$currency "
    }

    // Lakh/crore style: last three digits, then pairs. 1234567 -> 12,34,567
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val pairs = head.reversed().chunked(2).joinToString(",") { it.reversed() }.split(",").reversed().joinToString(",")
        return "$pairs,$tail"
    }

    private fun groupWestern(digits: String): String =
        digits.reversed().chunked(3).joinToString(",").reversed()
}
