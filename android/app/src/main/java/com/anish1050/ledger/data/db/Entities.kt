package com.anish1050.ledger.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable enum class Direction { debit, credit }
@Serializable enum class Source { manual, notification, sms, gallery }
@Serializable enum class TxState { confirmed, review }
@Serializable enum class AccountKind { bank, upi, card, cash }
@Serializable enum class BudgetPeriod { day, week, month, year }

// Entities double as the sync wire format (snake_case JSON), so the Swift
// client can decode the same rows after decrypting them.

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: AccountKind,
    val last4: String?,
    val currency: String,
    @ColumnInfo(name = "created_at") @SerialName("created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") @SerialName("updated_at") val updatedAt: Long,
    val version: Int = 1,
    val deleted: Boolean = false,
    // Local only: the version the server has accepted. version > pushed_version means dirty.
    @ColumnInfo(name = "pushed_version", defaultValue = "0") @Transient val pushedVersion: Int = 0,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    @ColumnInfo(name = "sort_order") @SerialName("sort_order") val sortOrder: Int,
    val discretionary: Boolean,
    @ColumnInfo(name = "created_at") @SerialName("created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") @SerialName("updated_at") val updatedAt: Long,
    val version: Int = 1,
    val deleted: Boolean = false,
    // Local only: the version the server has accepted. version > pushed_version means dirty.
    @ColumnInfo(name = "pushed_version", defaultValue = "0") @Transient val pushedVersion: Int = 0,
)

@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val period: BudgetPeriod,
    @ColumnInfo(name = "amount_minor") @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "created_at") @SerialName("created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") @SerialName("updated_at") val updatedAt: Long,
    val version: Int = 1,
    val deleted: Boolean = false,
    // Local only: the version the server has accepted. version > pushed_version means dirty.
    @ColumnInfo(name = "pushed_version", defaultValue = "0") @Transient val pushedVersion: Int = 0,
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("occurred_at"),
        Index("state"),
        Index(value = ["dedupe_key"], unique = true),
        Index(value = ["category_id", "occurred_at"]),
        Index("account_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") @SerialName("account_id") val accountId: String,
    @ColumnInfo(name = "category_id") @SerialName("category_id") val categoryId: String?,
    @ColumnInfo(name = "amount_minor") @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val merchant: String,
    val note: String?,
    @ColumnInfo(name = "occurred_at") @SerialName("occurred_at") val occurredAt: Long,
    val source: Source,
    val confidence: Float,
    val state: TxState,
    @ColumnInfo(name = "dedupe_key") @SerialName("dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "created_at") @SerialName("created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") @SerialName("updated_at") val updatedAt: Long,
    val version: Int = 1,
    val deleted: Boolean = false,
    // Local only: the version the server has accepted. version > pushed_version means dirty.
    @ColumnInfo(name = "pushed_version", defaultValue = "0") @Transient val pushedVersion: Int = 0,
)

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val key: String,
    val value: String,
)
