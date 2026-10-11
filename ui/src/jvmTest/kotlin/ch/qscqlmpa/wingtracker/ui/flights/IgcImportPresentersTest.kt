package ch.qscqlmpa.wingtracker.ui.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.flights.igc.FlightTrack
import ch.qscqlmpa.wingtracker.flights.igc.IgcImportDraft
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.InvalidTrackException
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.flights.igc.ReadTrack
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.flights.igc.TrackReader
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val WING_ID = WingId("wing-1")
private val FILE = NewAttachment("2026-09-20-niederhorn.igc", byteArrayOf(65, 88))
private const val CHECKSUM = "3b7e72f0c1d9"

private fun aWing() =
    Wing(
        id = WING_ID,
        details =
            WingDetails(
                name = "Sky Dancer",
                manufacturer = "Advance",
                model = "Iota 3",
                acquisition = Acquisition(AcquisitionCondition.New),
                inServiceDate = LocalDate(2024, 3, 9),
            ),
    )

private fun aDraft(duplicateOf: Flight? = null) =
    IgcImportDraft(
        fileName = FILE.fileName,
        checksum = CHECKSUM,
        date = LocalDate(2026, 9, 20),
        takeOff = Instant.parse("2026-09-20T09:42:00Z"),
        landing = Instant.parse("2026-09-20T11:17:00Z"),
        duration = 95.minutes,
        duplicateOf = duplicateOf,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class IgcImportPresentersTest {
    private val flights =
        mockk<FlightRepository>(relaxUnitFun = true).also {
            coEvery { it.findAll() } returns
                LoadResult(emptyList())
        }
    private val wings = mockk<WingRepository>().also { coEvery { it.findById(WING_ID) } returns aWing() }
    private val attachments = mockk<AttachmentStore>(relaxUnitFun = true)
    private val picker = mockk<FilePicker>()
    private val reader = mockk<TrackReader>()
    private val opener = mockk<FileOpener>(relaxUnitFun = true)
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-11T18:30:00Z")
                },
            zone = TimeZone.UTC,
        )

    private fun TestScope.scope() = TestScope(UnconfinedTestDispatcher(testScheduler))

    @Nested
    inner class ChoosingTheFile {
        private fun TestScope.importer() =
            IgcImportPresenter(scope(), PrepareIgcImport(reader, flights), picker, "Choose")

        @Test
        fun `reads the chosen file and hands over what was found`() =
            runTest {
                // given
                coEvery { picker.pick("Choose", "igc") } returns listOf(FILE)
                every { reader.read(FILE.bytes) } returns
                    ReadTrack(
                        FlightTrack(
                            LocalDate(2026, 9, 20),
                            listOf(TrackFix(Instant.parse("2026-09-20T09:42:00Z"), 46.7, 7.8)),
                        ),
                        CHECKSUM,
                    )
                var prepared: PreparedIgc? = null

                // when
                importer().start { prepared = it }

                // then
                assertThat(prepared?.draft?.date).isEqualTo(LocalDate(2026, 9, 20))
                assertThat(prepared?.file).isEqualTo(FILE)
            }

        @Test
        fun `does nothing when the file dialog is cancelled`() =
            runTest {
                // given
                coEvery { picker.pick("Choose", "igc") } returns emptyList()
                val presenter = importer()
                var prepared: PreparedIgc? = null

                // when
                presenter.start { prepared = it }

                // then
                assertThat(prepared).isNull()
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `shows why a file could not be read`() =
            runTest {
                // given
                coEvery { picker.pick("Choose", "igc") } returns listOf(FILE)
                every { reader.read(FILE.bytes) } throws InvalidTrackException("The file has no flight date")
                val presenter = importer()

                // when
                presenter.start {}

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.ImportIgc, "The file has no flight date"))
            }
    }

    @Nested
    inner class ConfirmingTheImport {
        private fun TestScope.form(
            draft: IgcImportDraft,
            input: FlightFormInput = FlightFormInput(date = draft.date, wingId = WING_ID, duration = "1:35"),
        ) = FlightFormPresenter(
            scope = scope(),
            logFlight = LogFlight(flights, wings, clock),
            updateFlight = UpdateFlight(flights, wings, clock),
            importIgcFlight = ImportIgcFlight(flights, wings, attachments, clock) { FlightId("imported") },
            initial = input,
            igc = PreparedIgc(FILE, draft),
        )

        @Test
        fun `saves the flight with the original track attached`() =
            runTest {
                // given
                val presenter = form(aDraft())
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isTrue()
                coVerify(exactly = 1) { attachments.save(any(), FILE.bytes) }
                coVerify(exactly = 1) {
                    flights.save(
                        match {
                            it.id == FlightId("imported") &&
                                it.igcFile?.checksum == CHECKSUM
                        },
                    )
                }
            }

        @Test
        fun `lets the pilot import straight away when the file is new`() =
            runTest {
                // given
                val presenter = form(aDraft(duplicateOf = null))

                // then
                assertThat(presenter.mayImport).isTrue()
            }

        @Test
        fun `holds back a file that was imported before until the pilot confirms`() =
            runTest {
                // given
                val earlier =
                    Flight(
                        FlightId("flight-0"),
                        FlightDetails(LocalDate(2026, 9, 20), 95.minutes, WING_ID),
                        Instant.parse("2026-09-20T17:00:00Z"),
                        IgcFile(Attachment(AttachmentId("igc-0"), "old.igc"), CHECKSUM),
                    )
                val presenter = form(aDraft(duplicateOf = earlier))

                // then
                assertThat(presenter.mayImport).isFalse()

                // when
                presenter.confirmImportAnyway(true)

                // then
                assertThat(presenter.mayImport).isTrue()
            }

        @Test
        fun `validates the form like any flight, so a missing duration is not saved`() =
            runTest {
                // given
                val presenter =
                    form(aDraft(), FlightFormInput(date = LocalDate(2026, 9, 20), wingId = WING_ID, duration = ""))
                var saved = false

                // when
                presenter.submit { saved = true }

                // then
                assertThat(saved).isFalse()
                assertThat(presenter.errors).containsKey(FlightFormField.Duration)
                coVerify(exactly = 0) { attachments.save(any(), any()) }
            }

        @Test
        fun `shows the failure and removes the stored track when saving fails`() =
            runTest {
                // given
                coEvery { flights.save(any()) } throws IOException("Disk full")
                val presenter = form(aDraft())

                // when
                presenter.submit {}

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.SaveFlight, "Disk full"))
                coVerify(exactly = 1) { attachments.delete(any()) }
            }
    }

    @Nested
    inner class OpeningTheTrack {
        @Test
        fun `opens the stored track in the system viewer`() =
            runTest {
                // given
                val igc = IgcFile(Attachment(AttachmentId("igc-1"), "flight.igc"), CHECKSUM)
                val imported =
                    Flight(
                        FlightId("f-1"),
                        FlightDetails(LocalDate(2026, 9, 20), 95.minutes, WING_ID),
                        Instant.parse("2026-09-20T17:00:00Z"),
                        igc,
                    )
                coEvery { attachments.read(igc.attachment.id) } returns byteArrayOf(1, 2, 3)
                val presenter =
                    LogbookPresenter(
                        scope(),
                        ch.qscqlmpa.wingtracker.flights
                            .GetLogbook(flights, wings),
                        ch.qscqlmpa.wingtracker.flights
                            .DeleteFlight(flights, attachments),
                        ReadAttachment(attachments),
                        opener,
                    )

                // when
                presenter.openTrack(imported)

                // then
                coVerify(exactly = 1) { opener.open("flight.igc", byteArrayOf(1, 2, 3)) }
            }

        @Test
        fun `shows a failure when the track file is missing`() =
            runTest {
                // given
                val igc = IgcFile(Attachment(AttachmentId("igc-1"), "flight.igc"), CHECKSUM)
                val imported =
                    Flight(
                        FlightId("f-1"),
                        FlightDetails(LocalDate(2026, 9, 20), 95.minutes, WING_ID),
                        Instant.parse("2026-09-20T17:00:00Z"),
                        igc,
                    )
                coEvery { attachments.read(igc.attachment.id) } throws IOException("file is gone")
                val presenter =
                    LogbookPresenter(
                        scope(),
                        ch.qscqlmpa.wingtracker.flights
                            .GetLogbook(flights, wings),
                        ch.qscqlmpa.wingtracker.flights
                            .DeleteFlight(flights, attachments),
                        ReadAttachment(attachments),
                        opener,
                    )

                // when
                presenter.openTrack(imported)

                // then
                assertThat(presenter.state.failure).isEqualTo(Failure(FailureKind.OpenTrack, "file is gone"))
            }
    }
}
