package ch.qscqlmpa.wingtracker.ui.errors

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun renderFailure() = IllegalStateException("Wing 'Ozone Rush 6' has no in-service date")

private fun saveFailure() = IllegalArgumentException("Cannot write flight 2026-10-15T09:12")

class UnexpectedErrorsTest {
    @Nested
    inner class Report {
        @Test
        fun `shows the stack trace of the first error`() {
            // given
            val errors = UnexpectedErrors()
            val failure = renderFailure()

            // when
            errors.report(failure)

            // then
            assertThat(errors.current).isEqualTo(UnexpectedError(failure.stackTraceToString(), occurrences = 1))
        }

        @Test
        fun `keeps showing the first error when a different one follows`() {
            // given
            val errors = UnexpectedErrors()
            val first = renderFailure()

            // when
            errors.report(first)
            errors.report(saveFailure())

            // then
            assertThat(errors.current?.stackTrace).isEqualTo(first.stackTraceToString())
        }

        @Test
        fun `counts an error that repeats from the same place instead of queueing it again`() {
            // given
            val errors = UnexpectedErrors()
            repeat(3) { errors.report(renderFailure()) }

            // when
            errors.dismiss()

            // then
            assertThat(errors.current).isNull()
        }

        @Test
        fun `shows how often the error happened`() {
            // given
            val errors = UnexpectedErrors()

            // when
            repeat(3) { errors.report(renderFailure()) }

            // then
            assertThat(errors.current?.occurrences).isEqualTo(3)
        }

        @Test
        fun `tells a new error from a repeat`() {
            // given
            val errors = UnexpectedErrors()
            val failure = renderFailure()

            // when
            val first = errors.report(failure)
            val repeat = errors.report(failure)

            // then
            assertThat(first.occurrences).isEqualTo(1)
            assertThat(repeat.occurrences).isEqualTo(2)
        }

        @Test
        fun `counts a repeat of an error that waits behind the current one`() {
            // given
            val errors = UnexpectedErrors()
            errors.report(renderFailure())
            val waiting = saveFailure()
            errors.report(waiting)
            errors.report(waiting)

            // when
            errors.dismiss()

            // then
            assertThat(errors.current).isEqualTo(UnexpectedError(waiting.stackTraceToString(), occurrences = 2))
        }
    }

    @Nested
    inner class Dismiss {
        @Test
        fun `brings up the next error`() {
            // given
            val errors = UnexpectedErrors()
            errors.report(renderFailure())
            val next = saveFailure()
            errors.report(next)

            // when
            errors.dismiss()

            // then
            assertThat(errors.current?.stackTrace).isEqualTo(next.stackTraceToString())
        }

        @Test
        fun `leaves nothing to show after the last error`() {
            // given
            val errors = UnexpectedErrors()
            errors.report(renderFailure())

            // when
            errors.dismiss()

            // then
            assertThat(errors.current).isNull()
        }

        @Test
        fun `does nothing when no error is shown`() {
            // given
            val errors = UnexpectedErrors()

            // when
            errors.dismiss()

            // then
            assertThat(errors.current).isNull()
        }
    }
}
