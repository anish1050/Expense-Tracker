package com.anish1050.ledger.data.db

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A fresh install must contain the seed rows: every transaction references the
// cash account and a category by foreign key, so an empty seed breaks saving.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = LedgerDatabase.build(context)

    @After fun close() { db.close(); context.deleteDatabase(LedgerDatabase.FILE_NAME) }

    @Test
    fun freshDatabaseHasCategoriesAndCashAccount() = runTest {
        assertThat(db.categoryDao().all().first()).hasSize(Seed.categories.size)
        assertThat(db.accountDao().byId(Seed.CASH_ACCOUNT_ID)).isNotNull()
    }
}
