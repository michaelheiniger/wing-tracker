package ch.qscqlmpa.wingtracker.ui.statistics

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.statistics.SeriesKey
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aWing(
    id: String,
    inService: LocalDate,
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
            name = "Wing $id",
            manufacturer = "Advance",
            model = "Iota 3",
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = inService,
        ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsPresenterTest {
    private val flights = mockk<FlightRepository>()
    private val wings = mockk<WingRepository>()
    private val first = aWing("wing-1", LocalDate(2023, 1, 1))
    private val second = aWing("wing-2", LocalDate(2024, 1, 1))

    private fun TestScope.presenter() =
        StatisticsPresenter(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            getStatistics = GetStatistics(flights, wings, AppClock()),
        )

    private fun givenStored() {
        coEvery { flights.findAll() } returns LoadResult(emptyList())
        coEvery { wings.findAll() } returns LoadResult(listOf(second, first))
    }

    @Nested
    inner class Loading {
        @Test
        fun `loads the statistics`() =
            runTest {
                // given
                givenStored()
                val presenter = presenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.loading).isFalse()
                assertThat(presenter.state.statistics?.series).hasSize(2)
            }

        @Test
        fun `shows the failure when loading fails`() =
            runTest {
                // given
                coEvery { flights.findAll() } throws IllegalStateException("disk unreadable")
                val presenter = presenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.LoadStatistics, "disk unreadable"))
            }
    }

    @Nested
    inner class TogglingSeries {
        @Test
        fun `hides and shows a wing`() =
            runTest {
                // given
                givenStored()
                val presenter = presenter()
                presenter.refresh()
                val key = SeriesKey.OfWing(first.id)

                // when
                presenter.toggleSeries(key)

                // then
                assertThat(presenter.state.visibleSeries.map { it.key }).containsExactly(SeriesKey.OfWing(second.id))

                // when
                presenter.toggleSeries(key)

                // then
                assertThat(presenter.state.visibleSeries).hasSize(2)
            }

        @Test
        fun `keeps the color slot of a wing while another one is hidden`() =
            runTest {
                // given
                givenStored()
                val presenter = presenter()
                presenter.refresh()
                val slotBefore = presenter.state.slotOf(SeriesKey.OfWing(second.id))

                // when
                presenter.toggleSeries(SeriesKey.OfWing(first.id))

                // then
                assertThat(presenter.state.slotOf(SeriesKey.OfWing(second.id))).isEqualTo(slotBefore)
                assertThat(slotBefore).isEqualTo(1)
            }
    }

    @Nested
    inner class Table {
        @Test
        fun `switches between charts and table`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.toggleTable()

                // then
                assertThat(presenter.state.showTable).isTrue()
            }
    }

    @Nested
    inner class SelectUnit {
        @Test
        fun `shows hours on every card by default`() =
            runTest {
                // given / when
                val presenter = presenter()

                // then
                assertThat(ChartCardKind.entries.map { presenter.state.unitOf(it) }).containsOnly(ChartUnit.Hours)
            }

        @Test
        fun `switches only the chosen card to flights`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.selectUnit(ChartCardKind.Yearly, ChartUnit.Flights)

                // then
                assertThat(presenter.state.unitOf(ChartCardKind.Yearly)).isEqualTo(ChartUnit.Flights)
                assertThat(presenter.state.unitOf(ChartCardKind.Cumulative)).isEqualTo(ChartUnit.Hours)
            }
    }
}
