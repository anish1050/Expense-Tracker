package com.anish1050.ledger.capture.parser

import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Source
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pure function over a compiled ruleset. First matching rule wins, so precise
// bank rules sit above the generic fallbacks in rules.json.
class RuleEngine(ruleSet: RuleSet) {

    private class Compiled(val rule: Rule, val regex: Regex)

    private val rules: List<Compiled> = ruleSet.rules.map { Compiled(it, Regex(it.match)) }
    private val senderPatterns: List<Regex> = ruleSet.smsSenderPatterns.map { Regex(it) }
    private val packages: Set<String> = ruleSet.notificationPackages.toSet()

    fun acceptsSender(source: Source, sender: String): Boolean = when (source) {
        Source.sms -> senderPatterns.any { it.matches(sender) }
        Source.notification -> sender in packages
        else -> true
    }

    fun match(raw: RawCapture): ParsedTransaction? {
        if (!acceptsSender(raw.source, raw.sender)) return null
        for (compiled in rules) {
            val rule = compiled.rule
            if (rule.source != "any" && rule.source != raw.source.name) continue
            val m = compiled.regex.find(raw.text) ?: continue
            val amount = m.group("amount") ?: continue
            val amountMinor = Money.parseToMinor(amount) ?: continue
            if (amountMinor <= 0) continue
            return ParsedTransaction(
                ruleId = rule.id,
                direction = rule.direction,
                amountMinor = amountMinor,
                merchant = m.group("merchant")?.trim().orEmpty(),
                last4 = m.group("last4"),
                ref = m.group("ref"),
                occurredAt = occurredAt(m.group("date"), raw.receivedAt),
                source = raw.source,
                confidence = rule.confidence,
            )
        }
        return null
    }

    // Unlike JS, Kotlin throws when a pattern has no group of that name, and
    // most rules only define a subset of the five groups.
    private fun MatchResult.group(name: String): String? = runCatching { groups[name]?.value }.getOrNull()

    // A date-only group would put the transaction at midnight and widen the
    // 2-minute dedupe bucket to a whole day, so keep the delivery time whenever
    // the SMS date is today; only backdated alerts fall back to the date.
    private fun occurredAt(dateText: String?, receivedAt: Long): Long {
        val dateMidnight = dateText?.let(::parseDate) ?: return receivedAt
        val zone = ZoneId.systemDefault()
        val receivedDay = java.time.Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
        val smsDay = java.time.Instant.ofEpochMilli(dateMidnight).atZone(zone).toLocalDate()
        return if (smsDay == receivedDay) receivedAt else dateMidnight
    }

    private fun parseDate(text: String): Long? = DATE_FORMATS.firstNotNullOfOrNull { format ->
        runCatching { LocalDate.parse(text, format) }.getOrNull()
    }?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    private companion object {
        val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("ddMMMyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
        )
    }
}
