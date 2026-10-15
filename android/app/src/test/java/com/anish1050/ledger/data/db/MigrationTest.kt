package com.anish1050.ledger.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Installs from the first APK are at schema v1; upgrading must keep their rows
// and leave every row dirty so it uploads once backup is on.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LedgerDatabase::class.java)

    @Test
    fun v1ToV2KeepsRowsAndMarksThemDirty() {
        helper.createDatabase("migration-test", 1).use { v1 ->
            v1.execSQL("INSERT INTO accounts (id, name, kind, last4, currency, created_at, updated_at, version, deleted) VALUES ('a', 'cash', 'cash', NULL, 'INR', 1, 1, 1, 0)")
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, LedgerDatabase.MIGRATION_1_2).use { v2 ->
            v2.query("SELECT pushed_version FROM accounts WHERE id = 'a'").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
            }
            v2.query("SELECT COUNT(*) FROM categories").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(Seed.categories.size)
            }
        }
    }
}
