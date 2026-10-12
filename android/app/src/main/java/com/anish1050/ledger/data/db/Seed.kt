package com.anish1050.ledger.data.db

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase

// Default categories and the cash account. Fixed ids so every device seeds the
// same rows and sync never duplicates them.
object Seed {
    const val CASH_ACCOUNT_ID = "00000000-0000-4000-8000-000000000001"
    const val UNCATEGORISED_ID = "00000000-0000-4000-8000-0000000000ff"

    data class Category(val id: String, val name: String, val emoji: String, val discretionary: Boolean)

    val categories: List<Category> = listOf(
        Category("00000000-0000-4000-8000-000000000101", "food delivery", "🍔", true),
        Category("00000000-0000-4000-8000-000000000102", "groceries", "🛒", false),
        Category("00000000-0000-4000-8000-000000000103", "commute", "🚕", false),
        Category("00000000-0000-4000-8000-000000000104", "shopping", "🛍️", true),
        Category("00000000-0000-4000-8000-000000000105", "entertainment", "🎬", true),
        Category("00000000-0000-4000-8000-000000000106", "subscriptions", "📺", true),
        Category("00000000-0000-4000-8000-000000000107", "travel", "✈️", true),
        Category("00000000-0000-4000-8000-000000000108", "rent", "🏠", false),
        Category("00000000-0000-4000-8000-000000000109", "utilities", "💡", false),
        Category("00000000-0000-4000-8000-000000000110", "health", "💊", false),
        Category("00000000-0000-4000-8000-000000000111", "education", "📚", false),
        Category("00000000-0000-4000-8000-000000000112", "salary", "💼", false),
        Category(UNCATEGORISED_ID, "other", "💸", false),
    )

    fun insert(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        categories.forEachIndexed { index, c ->
            db.insert(
                "categories", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", c.id); put("name", c.name); put("emoji", c.emoji); put("sort_order", index)
                    put("discretionary", c.discretionary); put("created_at", now); put("updated_at", now)
                    put("version", 1); put("deleted", false)
                },
            )
        }
        db.insert(
            "accounts", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
            ContentValues().apply {
                put("id", CASH_ACCOUNT_ID); put("name", "cash"); put("kind", AccountKind.cash.name)
                putNull("last4"); put("currency", "INR"); put("created_at", now); put("updated_at", now)
                put("version", 1); put("deleted", false)
            },
        )
    }
}
