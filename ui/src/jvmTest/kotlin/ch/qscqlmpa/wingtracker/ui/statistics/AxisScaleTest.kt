package ch.qscqlmpa.wingtracker.ui.statistics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AxisScaleTest {
    @Nested
    inner class NiceScale {
        @Test
        fun `rounds the maximum up to a round number`() {
            // given / when
            val scale = niceScale(13.7)

            // then
            assertThat(scale.max).isEqualTo(15.0)
            assertThat(scale.step).isEqualTo(5.0)
        }

        @Test
        fun `lists ticks from zero to the maximum`() {
            // given / when
            val scale = niceScale(42.0)

            // then
            assertThat(scale.ticks).containsExactly(0.0, 10.0, 20.0, 30.0, 40.0, 50.0)
        }

        @Test
        fun `handles large values`() {
            // given / when
            val scale = niceScale(1_234.0)

            // then
            assertThat(scale.max).isEqualTo(1_500.0)
            assertThat(scale.ticks.first()).isEqualTo(0.0)
            assertThat(scale.ticks.last()).isEqualTo(1_500.0)
        }

        @Test
        fun `handles values below one hour`() {
            // given / when
            val scale = niceScale(0.4)

            // then
            assertThat(scale.max).isGreaterThanOrEqualTo(0.4)
            assertThat(scale.ticks.size).isBetween(2, 7)
        }

        @Test
        fun `never steps by a fraction when counting whole numbers`() {
            // given / when
            val scale = niceScale(3.0, wholeNumbers = true)

            // then
            assertThat(scale.step).isEqualTo(1.0)
            assertThat(scale.ticks).containsExactly(0.0, 1.0, 2.0, 3.0)
        }

        @Test
        fun `falls back to a usable scale for zero`() {
            // given / when
            val scale = niceScale(0.0)

            // then
            assertThat(scale.max).isGreaterThan(0.0)
        }

        @Test
        fun `keeps a maximum that is already round`() {
            // given / when
            val scale = niceScale(50.0)

            // then
            assertThat(scale.max).isEqualTo(50.0)
        }
    }
}
