package com.anish1050.ledger.capture.parser

import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.db.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuleSet(
    val version: Int,
    @SerialName("notification_packages") val notificationPackages: List<String>,
    @SerialName("sms_sender_patterns") val smsSenderPatterns: List<String>,
    val rules: List<Rule>,
)

@Serializable
data class Rule(
    val id: String,
    /** "notification", "sms", "gallery" or "any". */
    val source: String,
    val match: String,
    val direction: Direction,
    val confidence: Float,
)

data class RawCapture(
    val source: Source,
    /** Package name for notifications, sender id for SMS, "gallery" for OCR. */
    val sender: String,
    val text: String,
    val receivedAt: Long,
)

data class ParsedTransaction(
    val ruleId: String,
    val direction: Direction,
    val amountMinor: Long,
    val merchant: String,
    val last4: String?,
    val ref: String?,
    val occurredAt: Long,
    val source: Source,
    val confidence: Float,
)
