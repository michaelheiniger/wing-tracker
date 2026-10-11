package ch.qscqlmpa.wingtracker.desktop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DesktopScaleTest {
    @Nested
    inner class Detect {
        @Test
        fun `reads the scale from the X11 DPI that a scaled desktop sets`() {
            // given / when
            val scale = DesktopScale.detect(emptyMap()) { "Xft.antialias:\t1\nXft.dpi:\t163\nXft.hinting:\t1" }

            // then
            assertThat(scale).isBetween(1.69, 1.70)
        }

        @Test
        fun `is 1 on a desktop with the standard DPI`() {
            assertThat(DesktopScale.detect(emptyMap()) { "Xft.dpi:\t96" }).isEqualTo(1.0)
        }

        @Test
        fun `prefers the toolkit scale factor over the DPI`() {
            // given / when
            val scale = DesktopScale.detect(mapOf("QT_SCALE_FACTOR" to "2")) { "Xft.dpi:\t163" }

            // then
            assertThat(scale).isEqualTo(2.0)
        }

        @Test
        fun `uses the GTK scale when only that is set`() {
            assertThat(DesktopScale.detect(mapOf("GDK_SCALE" to "2")) { null }).isEqualTo(2.0)
        }

        @Test
        fun `is 1 when nothing can be detected`() {
            assertThat(DesktopScale.detect(emptyMap()) { null }).isEqualTo(1.0)
        }

        @Test
        fun `ignores values that are not numbers`() {
            // given / when
            val scale = DesktopScale.detect(mapOf("QT_SCALE_FACTOR" to "big", "GDK_SCALE" to "-1")) { "garbage" }

            // then
            assertThat(scale).isEqualTo(1.0)
        }

        @Test
        fun `never goes below 1 or above a sane limit`() {
            assertThat(DesktopScale.detect(emptyMap()) { "Xft.dpi:\t48" }).isEqualTo(1.0)
            assertThat(DesktopScale.detect(emptyMap()) { "Xft.dpi:\t960" }).isEqualTo(4.0)
        }
    }

    @Nested
    inner class Factor {
        @Test
        fun `applies the whole detected scale when the JVM applied none`() {
            assertThat(DesktopScale.factor(percent = null, detected = 1.7, jvmScale = 1.0)).isEqualTo(1.7f)
        }

        @Test
        fun `applies only what the JVM has not applied yet`() {
            assertThat(DesktopScale.factor(percent = null, detected = 2.0, jvmScale = 2.0)).isEqualTo(1.0f)
            assertThat(DesktopScale.factor(percent = null, detected = 1.5, jvmScale = 1.0)).isEqualTo(1.5f)
        }

        @Test
        fun `never shrinks what the JVM scaled when the desktop reports nothing`() {
            // given: a system where the JVM scales itself but there is no X11 DPI to read
            assertThat(DesktopScale.factor(percent = null, detected = 1.0, jvmScale = 1.5)).isEqualTo(1.0f)
        }

        @Test
        fun `a fixed size is an absolute size, whatever the desktop says`() {
            assertThat(DesktopScale.factor(percent = 125, detected = 1.7, jvmScale = 1.0)).isEqualTo(1.25f)
            assertThat(DesktopScale.factor(percent = 100, detected = 1.7, jvmScale = 1.0)).isEqualTo(1.0f)
        }

        @Test
        fun `a fixed size accounts for the scale the JVM applied`() {
            assertThat(DesktopScale.factor(percent = 150, detected = 1.0, jvmScale = 1.5)).isEqualTo(1.0f)
        }
    }
}
