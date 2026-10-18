package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.SiteNames
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.flights.aFlightDetails
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val PAGE = "https://syride.com/en/flying-sites/Dent_De_Jaman"

class GetSyrideSitePageTest {
    private val source = mockk<SyrideFlightSource>()
    private val getSyrideSitePage = GetSyrideSitePage(source)

    init {
        every { source.sitePage("Dent_De_Jaman") } returns PAGE
    }

    private fun takingOffAt(site: SiteNames) = aFlight(details = aFlightDetails().copy(takeOffSite = site))

    @Nested
    inner class Invoke {
        @Test
        fun `gives the page of the take-off site Syride gave`() {
            // given
            val flight =
                takingOffAt(
                    SiteNames.of(SiteSource.Syride, "Dent De Jaman", "Dent_De_Jaman").with(SiteSource.Manual, "Jaman"),
                )

            // when
            val page = getSyrideSitePage(flight)

            // then
            assertThat(page).isEqualTo(PAGE)
        }

        @Test
        fun `has no page for a Syride site without an id`() {
            // given
            val flight = takingOffAt(SiteNames.of(SiteSource.Syride, "Sonchaux"))

            // when
            val page = getSyrideSitePage(flight)

            // then
            assertThat(page).isNull()
        }
    }
}
