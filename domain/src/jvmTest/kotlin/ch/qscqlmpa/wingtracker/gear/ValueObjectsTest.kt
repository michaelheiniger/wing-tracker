package ch.qscqlmpa.wingtracker.gear

import ch.qscqlmpa.wingtracker.gear.wing.WingId
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValueObjectsTest {
    @Nested
    inner class CurrencyCodeCreation {
        @Test
        fun `accepts three uppercase letters`() {
            assertThatCode { CurrencyCode("CHF") }.doesNotThrowAnyException()
        }

        @Test
        fun `rejects lowercase letters`() {
            assertThatThrownBy { CurrencyCode("chf") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects a code that is not three letters long`() {
            assertThatThrownBy { CurrencyCode("EURO") }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class PriceCreation {
        @Test
        fun `rejects a negative amount`() {
            assertThatThrownBy { Price(minorUnits = -1, currency = CurrencyCode("CHF")) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `accepts a free item`() {
            assertThatCode { Price(minorUnits = 0, currency = CurrencyCode("CHF")) }.doesNotThrowAnyException()
        }
    }

    @Nested
    inner class WingIdCreation {
        @Test
        fun `accepts a uuid`() {
            assertThatCode { WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47") }.doesNotThrowAnyException()
        }

        @Test
        fun `rejects path separators so ids are safe as file names`() {
            assertThatThrownBy { WingId("../secrets") }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `rejects an empty id`() {
            assertThatThrownBy { WingId("") }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
