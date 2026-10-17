package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val PAGE = "https://syride.com/en/pilots/michael/3720061"

class GetSyrideFlightPageTest {
    private val source = mockk<SyrideFlightSource>()
    private val settings = mockk<SettingsRepository>()
    private val getSyrideFlightPage = GetSyrideFlightPage(source, settings)

    init {
        coEvery { settings.load() } returns AppSettings(syrideUsername = "michael")
        every { source.flightPage("michael", "3720061") } returns PAGE
    }

    private fun importedAs(id: String) = aFlight().copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, id))

    @Nested
    inner class Invoke {
        @Test
        fun `gives the page of a Syride flight with a track for the saved account`() =
            runTest {
                // given
                val flight = importedAs(syrideFlightId("3720061"))

                // when
                val page = getSyrideFlightPage(flight)

                // then
                assertThat(page).isEqualTo(PAGE)
            }

        @Test
        fun `has no page for a Syride flight without a track`() =
            runTest {
                // given
                val flight = importedAs(syrideUntrackedFlightId(5182044))

                // when
                val page = getSyrideFlightPage(flight)

                // then
                assertThat(page).isNull()
            }

        @Test
        fun `has no page for a flight not imported from Syride`() =
            runTest {
                // given
                val flight = aFlight()

                // when
                val page = getSyrideFlightPage(flight)

                // then
                assertThat(page).isNull()
            }

        @Test
        fun `has no page while no Syride account is saved`() =
            runTest {
                // given
                coEvery { settings.load() } returns AppSettings()
                val flight = importedAs(syrideFlightId("3720061"))

                // when
                val page = getSyrideFlightPage(flight)

                // then
                assertThat(page).isNull()
            }
    }
}
