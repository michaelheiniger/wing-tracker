package ch.qscqlmpa.wingtracker.desktop

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.util.LogbackMDCAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.readText

class DesktopLoggingTest {
    @TempDir
    lateinit var folder: Path

    // Its own context, so the test reads the shipped configuration without touching the one of the test run.
    private val loggerContext = LoggerContext().apply { mdcAdapter = LogbackMDCAdapter() }

    @AfterEach
    fun tearDown() {
        loggerContext.stop()
        System.clearProperty(DesktopLogging.DIRECTORY_PROPERTY)
        System.clearProperty(DevMode.PROPERTY)
    }

    // The shipped configuration, not the test one, so a broken logback.xml fails here instead of silently logging
    // nowhere.
    private fun configureShipped() {
        JoranConfigurator().apply { setContext(loggerContext) }.doConfigure(javaClass.getResource("/logback.xml"))
    }

    // Redirected, so the test never writes to a real log folder.
    private fun startIn(devMode: Boolean) {
        System.setProperty(DesktopLogging.DIRECTORY_PROPERTY, folder.toString())
        System.setProperty(DevMode.PROPERTY, devMode.toString())
    }

    @Nested
    inner class ShippedConfiguration {
        @Test
        fun `logs debug details to the log folder in development mode`() {
            // given
            startIn(devMode = true)
            configureShipped()

            // when
            loggerContext.getLogger("ch.qscqlmpa.wingtracker.Test").debug("Read 12 flights")

            // then
            assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).level).isEqualTo(Level.DEBUG)
            assertThat(folder.resolve("wing-tracker.log").readText()).contains("DEBUG", "Read 12 flights")
        }

        @Test
        fun `logs from info upwards in the packaged app`() {
            // given
            startIn(devMode = false)
            configureShipped()

            // when
            loggerContext.getLogger("ch.qscqlmpa.wingtracker.Test").debug("Read 12 flights")
            loggerContext.getLogger("ch.qscqlmpa.wingtracker.Test").info("Starting Wing Tracker")

            // then
            assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).level).isEqualTo(Level.INFO)
            assertThat(folder.resolve("wing-tracker.log").readText())
                .contains("Starting Wing Tracker")
                .doesNotContain("Read 12 flights")
        }
    }

    @Nested
    inner class Directory {
        @Test
        fun `is the platform log folder by default`() {
            // given / when
            val directory = DesktopLogging.directory(devMode = true)

            // then
            assertThat(directory).isEqualTo(LogDirectory.resolve(devMode = true))
        }

        @Test
        fun `is the folder given as a system property when there is one`() {
            // given
            System.setProperty(DesktopLogging.DIRECTORY_PROPERTY, folder.toString())

            // when
            val directory = DesktopLogging.directory(devMode = true)

            // then
            assertThat(directory).isEqualTo(folder)
        }
    }
}
