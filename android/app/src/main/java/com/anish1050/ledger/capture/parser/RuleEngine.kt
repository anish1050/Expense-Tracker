package com.anish1050.ledger.capture.parser

import com.anish1050.ledger.data.Money
import com.anish1050.ledger.data.db.Source
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
            val amount = m.groups["amount"]?.value ?: continue
            val amountMinor = Money.parseToMinor(amount) ?: continue
            if (amountMinor <= 0) continue
            return ParsedTransaction(
                ruleId = rule.id,
                direction = rule.direction,
                amountMinor = amountMinor,
                merchant = m.groups["merchant"]?.value?.trim().orEmpty(),
                last4 = m.groups["last4"]?.value,
                ref = m.groups["ref"]?.value,
                occurredAt = m.groups["date"]?.value?.let(::parseDate) ?: raw.receivedAt,
                source = raw.source,
                confidence = rule.confidence,
            )
        }
        return null
    }

    private fun parseDate(text: String): Long? {
        for (format in DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private companion object {
        val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("ddMMMyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
        )
    }
}
