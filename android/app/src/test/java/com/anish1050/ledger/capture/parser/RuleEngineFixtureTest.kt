package com.anish1050.ledger.capture.parser

import com.anish1050.ledger.data.db.Source
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Test

// Runs the same fixtures as the backend's rule-engine.test.ts, so a rule can
// never parse differently on the two platforms.
class RuleEngineFixtureTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val engine = RuleEngine(json.decodeFromString(RuleSet.serializer(), resource("rules.json")))

    @Test
    fun everyFixtureParsesAsExpected() {
        val fixtures = json.parseToJsonElement(resource("fixtures/india-upi.json")).jsonArray
        assertThat(fixtures).isNotEmpty()
        fixtures.forEach { element ->
            val f = element.jsonObject
            val raw = RawCapture(
                source = Source.valueOf(f.str("source")),
                sender = f.str("sender"),
                text = f.str("text"),
                receivedAt = 1_700_000_000_000L,
            )
            val parsed = engine.match(raw)
            val expect = f["expect"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
            if (expect == null) {
                assertThat(parsed).named(f.str("text")).isNull()
                return@forEach
            }
            assertThat(parsed).named(f.str("text")).isNotNull()
            assertThat(parsed!!.ruleId).isEqualTo(f.str("rule"))
            assertThat(parsed.direction.name).isEqualTo(expect.str("direction"))
            assertThat(parsed.amountMinor).isEqualTo(expect["amount_minor"]!!.jsonPrimitive.longOrNull)
            assertThat(parsed.merchant).isEqualTo(expect.str("merchant"))
            expect["last4"]?.jsonPrimitive?.contentOrNull?.let { assertThat(parsed.last4).isEqualTo(it) }
            expect["ref"]?.jsonPrimitive?.contentOrNull?.let { assertThat(parsed.ref).isEqualTo(it) }
            assertThat(parsed.confidence).isWithin(0.001f).of(expect["confidence"]!!.jsonPrimitive.content.toFloat())
        }
    }

    @Test
    fun smsDateGroupSetsOccurredAt() {
        val raw = RawCapture(Source.sms, "VM-HDFCBK", "Rs.10.00 debited from a/c **1234 on 01-10-26 to VPA x@ybl ref 1", receivedAt = 0L)
        val parsed = engine.match(raw)!!
        assertThat(parsed.occurredAt).isGreaterThan(0L)
    }

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing test resource $path" }.bufferedReader().use { it.readText() }
}
