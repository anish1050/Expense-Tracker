package com.anish1050.ledger.data

import com.anish1050.ledger.data.db.Direction
import com.anish1050.ledger.data.repo.TransactionRepository
import com.anish1050.ledger.data.sync.RowCrypto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyAndDedupeTest {

    @Test
    fun parsesToMinorWithoutFloatDrift() {
        assertThat(Money.parseToMinor("1,23,456.7")).isEqualTo(12_345_670L)
        assertThat(Money.parseToMinor("0.1")).isEqualTo(10L)
        assertThat(Money.parseToMinor("1.234,56")).isEqualTo(123_456L)
        assertThat(Money.parseToMinor("89,90")).isEqualTo(8_990L)
        assertThat(Money.parseToMinor("abc")).isNull()
    }

    @Test
    fun formatsIndianGroupingAndSign() {
        assertThat(Money.format(12_345_600L, "INR")).isEqualTo("₹1,23,456")
        assertThat(Money.format(-29_000L, "INR", withSign = true)).isEqualTo("-₹290")
        assertThat(Money.format(500_000L, "INR", withSign = true)).isEqualTo("+₹5,000")
    }

    @Test
    fun sameUpiPaymentFromSmsAndNotificationCollapses() {
        val t = 1_700_000_000_000L
        val fromSms = TransactionRepository.dedupeKey(Direction.debit, 24_900, "1234", "burgerking@ybl", t)
        val fromNotification = TransactionRepository.dedupeKey(Direction.debit, 24_900, "1234", "BurgerKing@ybl ", t + 30_000)
        val differentAmount = TransactionRepository.dedupeKey(Direction.debit, 25_000, "1234", "burgerking@ybl", t)
        assertThat(fromSms).isEqualTo(fromNotification)
        assertThat(fromSms).isNotEqualTo(differentAmount)
    }

    @Test
    fun rowCryptoRoundTripsAndRejectsWrongKey() {
        val key = RowCrypto.deriveKey("correct horse".toCharArray(), "user-1")
        val sealed = RowCrypto(key).encrypt("""{"id":"x","amount_minor":100}""")
        assertThat(RowCrypto(key).decrypt(sealed.ciphertext, sealed.nonce)).contains("amount_minor")
        val wrong = RowCrypto.deriveKey("wrong".toCharArray(), "user-1")
        val failed = runCatching { RowCrypto(wrong).decrypt(sealed.ciphertext, sealed.nonce) }
        assertThat(failed.isFailure).isTrue()
    }
}
