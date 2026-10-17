package ch.qscqlmpa.wingtracker.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FailureTest {
    @Nested
    inner class IsWorthRetrying {
        @ParameterizedTest
        @CsvSource(
            "NoConnection, true",
            "SyrideUnreachable, true",
            "UpdateUnreachable, true",
            "UpdateRefused, false",
            "SyridePagesChanged, false",
            "UpdateChecksumMismatch, false",
        )
        fun `offers to try again only when the connection may be back soon`(
            kind: FailureKind,
            expected: Boolean,
        ) {
            // given
            val failure = Failure(kind, "detail")

            // when
            val worthRetrying = failure.isWorthRetrying

            // then
            assertThat(worthRetrying).isEqualTo(expected)
        }
    }
}
