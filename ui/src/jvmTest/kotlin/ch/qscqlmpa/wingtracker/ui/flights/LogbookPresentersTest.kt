package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.undo.TEXT
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.undo.expire
import ch.qscqlmpa.wingtracker.ui.undo.pressUndo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val TODAY = LocalDate(2026, 10, 11)

private fun aWing(
    id: String,
    name: String,
    status: GearStatus = GearStatus.Active,
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
            name = name,
            manufacturer = "Advance",
            model = "Iota 3",
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = LocalDate(2024, 3, 9),
        ),
    status = status,
)

private fun aFlight(
    id: String,
    wingId: String,
    date: LocalDate = LocalDate(2026, 9, 20),
    loggedAt: String = "2026-09-20T17:00:00Z",
) = Flight(
    id = FlightId(id),
    details = FlightDetails(date = date, duration = 95.minutes, wingId = WingId(wingId)),
    loggedAt = Instant.parse(loggedAt),
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogbookPresentersTest {
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val wings = mockk<WingRepository>()
    private val attachments = mockk<AttachmentStore>(relaxUnitFun = true)
    private val opener = mockk<FileOpener>(relaxUnitFun = true)
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-11T18:30:00Z")
                },
            zone = TimeZone.UTC,
        )
    private val skyDancer = aWing("wing-1", "Sky Dancer")
    private val cloudSurfer = aWing("wing-2", "Cloud Surfer")

    private fun givenStored(
        flightList: List<Flight> = emptyList(),
        wingList: List<Wing> = listOf(skyDancer, cloudSurfer),
    ) {
        coEvery { flights.findAll() } returns LoadResult(flightList)
        coEvery { wings.findAll() } returns LoadResult(wingList)
    }

    private fun TestScope.logbookPresenter() =
        LogbookPresenter(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            getLogbook = GetLogbook(flights, wings),
            deleteFlight = DeleteFlight(flights, attachments),
            readAttachment = ReadAttachment(attachments),
            fileOpener = opener,
        )

    @Nested
    inner class Logbook {
        @Test
        fun `loads the logbook`() =
            runTest {
                // given
                givenStored(listOf(aFlight("f-1", "wing-1")))
                val presenter = logbookPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.loading).isFalse()
                assertThat(presenter.state.logbook?.months).hasSize(1)
            }

        @Test
        fun `shows the failure when loading fails`() =
            runTest {
                // given
                coEvery { flights.findAll() } throws IllegalStateException("disk unreadable")
                val presenter = logbookPresenter()

                // when
                presenter.refresh()

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.LoadLogbook, "disk unreadable"))
                assertThat(presenter.state.loading).isFalse()
            }

        @Test
        fun `deletes a flight and reloads`() =
            runTest {
                // given
                val flight = aFlight("f-1", "wing-1")
                givenStored(listOf(flight))
                coEvery { flights.findById(flight.id) } returns flight
                val presenter = logbookPresenter()

                // when
                val undo = UndoCoordinator()
                presenter.delete(flight.id, undo, TEXT)
                undo.expire()

                // then
                coVerify(exactly = 1) { flights.delete(flight.id) }
            }

        @Test
        fun `reports unreadable files until dismissed`() =
            runTest {
                // given
                val broken = UnreadableRecord("flight-9", "bad json", quarantined = true)
                coEvery { flights.findAll() } returns LoadResult(emptyList(), listOf(broken))
                coEvery { wings.findAll() } returns LoadResult(listOf(skyDancer))
                val presenter = logbookPresenter()

                // when
                presenter.refresh()
                presenter.refresh()

                // then
                assertThat(presenter.state.unreadable).containsExactly(broken)

                // when
                presenter.dismissUnreadable()

                // then
                assertThat(presenter.state.unreadable).isEmpty()
            }
    }

    @Nested
    inner class NewFlightInput {
        @Test
        fun `defaults to today and the wing of the most recently logged flight`() =
            runTest {
                // given
                givenStored(
                    listOf(
                        aFlight("f-1", "wing-1", loggedAt = "2026-09-20T17:00:00Z"),
                        aFlight("f-2", "wing-2", date = LocalDate(2026, 9, 1), loggedAt = "2026-10-05T12:00:00Z"),
                    ),
                )
                val presenter = logbookPresenter()
                presenter.refresh()

                // when
                val input = presenter.state.newFlightInput(TODAY)

                // then
                assertThat(input).usingRecursiveComparison().isEqualTo(FlightFormInput(TODAY, WingId("wing-2")))
            }

        @Test
        fun `falls back to the first active wing when nothing was flown yet`() =
            runTest {
                // given
                val retired = aWing("wing-0", "Aardvark", GearStatus.Retired(LocalDate(2025, 1, 1)))
                givenStored(wingList = listOf(retired, skyDancer))
                val presenter = logbookPresenter()
                presenter.refresh()

                // when
                val input = presenter.state.newFlightInput(TODAY)

                // then
                assertThat(input.wingId).isEqualTo(skyDancer.id)
            }

        @Test
        fun `ignores the last flown wing when it no longer exists`() =
            runTest {
                // given
                givenStored(listOf(aFlight("f-1", "deleted-wing")))
                val presenter = logbookPresenter()
                presenter.refresh()

                // when
                val input = presenter.state.newFlightInput(TODAY)

                // then
                assertThat(input.wingId).isEqualTo(cloudSurfer.id)
            }
    }

    @Nested
    inner class UndoingADelete {
        @Test
        fun `hides the flight at once but keeps it stored`() =
            runTest {
                // given
                val flight = aFlight("f-1", "wing-1")
                givenStored(listOf(flight))
                val presenter = logbookPresenter()
                presenter.refresh()

                // when
                presenter.delete(flight.id, UndoCoordinator(), TEXT)

                // then
                assertThat(presenter.state.shown?.months).isEmpty()
                coVerify(exactly = 0) { flights.delete(any()) }
            }

        @Test
        fun `shows the flight again and never deletes it when undone`() =
            runTest {
                // given
                val flight = aFlight("f-1", "wing-1")
                givenStored(listOf(flight))
                val presenter = logbookPresenter()
                presenter.refresh()
                val undo = UndoCoordinator()
                presenter.delete(flight.id, undo, TEXT)

                // when
                undo.pressUndo()

                // then
                assertThat(
                    presenter.state.shown
                        ?.months
                        ?.flatMap { it.flights },
                ).containsExactly(flight)
                coVerify(exactly = 0) { flights.delete(any()) }
            }

        @Test
        fun `takes the hidden flight out of the hours of its wing`() =
            runTest {
                // given
                val flight = aFlight("f-1", "wing-1")
                givenStored(listOf(flight))
                val presenter = logbookPresenter()
                presenter.refresh()
                val before =
                    presenter.state.shown
                        ?.hoursByWing
                        ?.get(WingId("wing-1"))

                // when
                presenter.delete(flight.id, UndoCoordinator(), TEXT)

                // then
                val after =
                    presenter.state.shown
                        ?.hoursByWing
                        ?.get(WingId("wing-1"))
                assertThat(after).isEqualTo(before?.minus(flight.details.duration))
            }
    }

    @Nested
    inner class DiscardingANewFlight {
        private fun TestScope.formPresenter(initial: FlightFormInput) =
            FlightFormPresenter(
                scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                logFlight = LogFlight(flights, wings, clock),
                updateFlight = UpdateFlight(flights, wings, clock),
                initial = initial,
            )

        private val fresh = FlightFormInput(date = LocalDate(2026, 10, 11), wingId = WingId("wing-1"))

        @Test
        fun `has nothing to take back while the form is untouched`() =
            runTest {
                // given / when
                val presenter = formPresenter(fresh)

                // then
                assertThat(presenter.hasUnsavedInput).isFalse()
            }

        @Test
        fun `has something to take back once the pilot typed`() =
            runTest {
                // given
                val presenter = formPresenter(fresh)

                // when
                presenter.change { it.copy(duration = "1:35") }

                // then
                assertThat(presenter.hasUnsavedInput).isTrue()
            }

        @Test
        fun `has nothing to take back after saving`() =
            runTest {
                // given
                coEvery { wings.findById(skyDancer.id) } returns skyDancer
                val presenter = formPresenter(fresh.copy(duration = "1:35"))

                // when
                presenter.submit {}

                // then
                assertThat(presenter.hasUnsavedInput).isFalse()
            }
    }

    @Nested
    inner class FlightFormPresenterBehaviour {
        private fun TestScope.formPresenter(
            initial: FlightFormInput,
            editing: Flight? = null,
        ) = FlightFormPresenter(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            logFlight = LogFlight(flights, wings, clock) { FlightId("new-flight") },
            updateFlight = UpdateFlight(flights, wings, clock),
            initial = initial,
            editing = editing,
        )

        private val validInput =
            FlightFormInput(date = LocalDate(2026, 9, 20), wingId = WingId("wing-1"), duration = "1:35")

        @Test
        fun `does not save and exposes errors when the form is invalid`() =
            runTest {
                // given
                val presenter = formPresenter(FlightFormInput())
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(presenter.errors).containsKey(FlightFormField.Duration)
                assertThat(saved).isFalse()
                coVerify(exactly = 0) { flights.save(any()) }
            }

        @Test
        fun `logs a new flight when the form is valid`() =
            runTest {
                // given
                coEvery { wings.findById(skyDancer.id) } returns skyDancer
                val presenter = formPresenter(validInput)
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isTrue()
                coVerify(exactly = 1) { flights.save(match { it.id == FlightId("new-flight") }) }
            }

        @Test
        fun `shows an error on the date when it lies in the future`() =
            runTest {
                // given
                coEvery { wings.findById(skyDancer.id) } returns skyDancer
                val presenter = formPresenter(validInput.copy(date = LocalDate(2026, 10, 12)))
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(presenter.errors).containsEntry(FlightFormField.Date, FlightFormError.DateInFuture)
                assertThat(saved).isFalse()
            }

        @Test
        fun `updates the flight when editing`() =
            runTest {
                // given
                val existing = aFlight("f-1", "wing-1")
                coEvery { wings.findById(skyDancer.id) } returns skyDancer
                coEvery { flights.findById(existing.id) } returns existing
                val presenter = formPresenter(FlightFormInput.from(existing), editing = existing)
                presenter.change { it.copy(duration = "2:00") }

                // when
                presenter.submit {}

                // then
                coVerify(
                    exactly = 1,
                ) { flights.save(match { it.id == existing.id && it.details.duration == 120.minutes }) }
            }

        @Test
        fun `keeps the dialog open and shows the failure when saving fails`() =
            runTest {
                // given
                coEvery { wings.findById(skyDancer.id) } returns skyDancer
                coEvery { flights.save(any()) } throws java.io.IOException("Disk full")
                val presenter = formPresenter(validInput)
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isFalse()
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.SaveFlight, "Disk full"))
                assertThat(presenter.saving).isFalse()
            }
    }
}
