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
import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveDeviceFolder
import ch.qscqlmpa.wingtracker.shared.LoadResult
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.platform.PickedFolder
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
private const val DEVICE = "/run/media/michael/SYRIDE1"

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
    private val getAppSettings = mockk<GetAppSettings>().also { coEvery { it() } returns AppSettings() }
    private val saveDeviceFolder = mockk<SaveDeviceFolder>(relaxUnitFun = true)
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

    private fun track(
        fileName: String,
        checksum: String,
        date: LocalDate = LocalDate(2026, 9, 20),
        moving: Boolean = true,
    ): NewAttachment {
        val file = NewAttachment(fileName, fileName.encodeToByteArray())
        // A track that never moves has a single fix, so no flight can be detected in it.
        val fixes =
            if (moving) {
                // One fix a minute at about 9 m/s: well above walking speed, so a flight is detected.
                (0..95).map { TrackFix(Instant.parse("2026-09-20T09:42:00Z") + it.minutes, 46.7 + it * 0.005, 7.8) }
            } else {
                listOf(TrackFix(Instant.parse("2026-09-20T09:42:00Z"), 46.7, 7.8))
            }
        every { reader.read(file.bytes) } returns ReadTrack(FlightTrack(date, fixes), checksum)
        return file
    }

    @Nested
    inner class ChoosingTheFiles {
        private fun TestScope.importer() =
            IgcImportPresenter(scope(), picker, "Choose", "Choose folder", getAppSettings, saveDeviceFolder)

        @Test
        fun `hands over every chosen file`() =
            runTest {
                // given
                coEvery { picker.pick("Choose", setOf("igc")) } returns listOf(FILE, FILE)
                var chosen = emptyList<NewAttachment>()

                // when
                importer().choose { chosen = it }

                // then
                assertThat(chosen).hasSize(2)
            }

        @Test
        fun `does nothing when the file dialog is cancelled`() =
            runTest {
                // given
                coEvery { picker.pick("Choose", setOf("igc")) } returns emptyList()
                val presenter = importer()
                var called = false

                // when
                presenter.choose { called = true }

                // then
                assertThat(called).isFalse()
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `shows why the files could not be read`() =
            runTest {
                // given
                coEvery { picker.pick("Choose", setOf("igc")) } throws IOException("Permission denied")
                val presenter = importer()

                // when
                presenter.choose {}

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.ImportIgc, "Permission denied"))
            }

        @Test
        fun `hands over every track found in the chosen folder`() =
            runTest {
                // given
                coEvery { picker.pickFolder("Choose folder", "igc", null) } returns
                    PickedFolder(DEVICE, listOf(FILE, FILE))
                var chosen = emptyList<NewAttachment>()

                // when
                importer().chooseFolder { chosen = it }

                // then
                assertThat(chosen).hasSize(2)
            }

        @Test
        fun `does nothing when the folder dialog is cancelled`() =
            runTest {
                // given
                coEvery { picker.pickFolder("Choose folder", "igc", null) } returns null
                val presenter = importer()
                var called = false

                // when
                presenter.chooseFolder { called = true }

                // then
                assertThat(called).isFalse()
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `reports a folder that holds no track`() =
            runTest {
                // given
                coEvery { picker.pickFolder("Choose folder", "igc", null) } returns PickedFolder(DEVICE, emptyList())
                val presenter = importer()

                // when
                presenter.chooseFolder {}

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.NoIgcInFolder, null))
            }

        @Test
        fun `remembers the chosen folder`() =
            runTest {
                // given
                coEvery { picker.pickFolder("Choose folder", "igc", null) } returns PickedFolder(DEVICE, listOf(FILE))

                // when
                importer().chooseFolder {}

                // then
                coVerify(exactly = 1) { saveDeviceFolder(DEVICE) }
            }

        @Test
        fun `opens the folder dialog where the last import came from`() =
            runTest {
                // given
                coEvery { getAppSettings() } returns AppSettings(deviceFolder = DEVICE)
                coEvery { picker.pickFolder("Choose folder", "igc", DEVICE) } returns null

                // when
                importer().chooseFolder {}

                // then
                coVerify(exactly = 1) { picker.pickFolder("Choose folder", "igc", DEVICE) }
            }

        @Test
        fun `does not remember anything when the folder dialog is cancelled`() =
            runTest {
                // given
                coEvery { picker.pickFolder("Choose folder", "igc", null) } returns null

                // when
                importer().chooseFolder {}

                // then
                coVerify(exactly = 0) { saveDeviceFolder(any()) }
            }

        @Test
        fun `reports a drop that holds no track`() =
            runTest {
                // given
                val presenter = importer()

                // when
                presenter.dropRejected()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.DropNoIgc, null))
            }
    }

    @Nested
    inner class ReviewingTheBatch {
        private fun TestScope.batch() =
            IgcBatchPresenter(
                scope(),
                PrepareIgcImport(reader, flights),
                ImportIgcFlight(flights, wings, attachments, clock) { FlightId("imported-${counter++}") },
                WING_ID,
            )

        private var counter = 0

        @Test
        fun `ticks every new track that holds a flight`() =
            runTest {
                // given
                val first = track("a.igc", "sum-a")
                val second = track("b.igc", "sum-b", LocalDate(2026, 9, 21))
                val presenter = batch()

                // when
                presenter.load(listOf(first, second))

                // then
                assertThat(presenter.loading).isFalse()
                assertThat(presenter.rows.map { it.selected }).containsExactly(true, true)
                assertThat(presenter.importableCount).isEqualTo(2)
            }

        @Test
        fun `leaves a track unticked when it was imported before`() =
            runTest {
                // given
                val file = track("a.igc", CHECKSUM)
                val earlier =
                    Flight(
                        FlightId("flight-0"),
                        FlightDetails(LocalDate(2026, 9, 20), 95.minutes, WING_ID),
                        Instant.parse("2026-09-20T17:00:00Z"),
                        IgcFile(Attachment(AttachmentId("igc-0"), "old.igc"), CHECKSUM),
                    )
                coEvery { flights.findAll() } returns LoadResult(listOf(earlier))
                val presenter = batch()

                // when
                presenter.load(listOf(file))

                // then
                assertThat(presenter.rows.single().problem).isEqualTo(IgcRowProblem.AlreadyImported)
                assertThat(presenter.rows.single().selected).isFalse()
                assertThat(presenter.rows.single().selectable).isTrue()
            }

        @Test
        fun `leaves known tracks out of the list and counts them when asked to`() =
            runTest {
                // given
                val known = track("a.igc", CHECKSUM)
                val fresh = track("b.igc", "sum-b", LocalDate(2026, 9, 21))
                val earlier =
                    Flight(
                        FlightId("flight-0"),
                        FlightDetails(LocalDate(2026, 9, 20), 95.minutes, WING_ID),
                        Instant.parse("2026-09-20T17:00:00Z"),
                        IgcFile(Attachment(AttachmentId("igc-0"), "old.igc"), CHECKSUM),
                    )
                coEvery { flights.findAll() } returns LoadResult(listOf(earlier))
                val presenter =
                    IgcBatchPresenter(
                        scope(),
                        PrepareIgcImport(reader, flights),
                        ImportIgcFlight(flights, wings, attachments, clock) { FlightId("imported-${counter++}") },
                        WING_ID,
                        hideAlreadyImported = true,
                    )

                // when
                presenter.load(listOf(known, fresh))

                // then
                assertThat(presenter.rows.map { it.file.fileName }).containsExactly("b.igc")
                assertThat(presenter.skippedCount).isEqualTo(1)
            }

        @Test
        fun `flags the second copy of a file that is twice in the selection`() =
            runTest {
                // given
                val first = track("a.igc", "same")
                val copy = track("a-copy.igc", "same")
                val presenter = batch()

                // when
                presenter.load(listOf(first, copy))

                // then
                assertThat(presenter.rows.map { it.problem }).containsExactly(null, IgcRowProblem.RepeatedInSelection)
            }

        @Test
        fun `shows an unreadable file on its row without stopping the others`() =
            runTest {
                // given
                val broken = NewAttachment("broken.igc", byteArrayOf(0))
                every { reader.read(broken.bytes) } throws InvalidTrackException("The file has no flight date")
                val good = track("a.igc", "sum-a")
                val presenter = batch()

                // when
                presenter.load(listOf(broken, good))

                // then
                assertThat(presenter.rows[0].problem).isEqualTo(IgcRowProblem.Unreadable)
                assertThat(presenter.rows[0].detail).isEqualTo("The file has no flight date")
                assertThat(presenter.rows[0].selectable).isFalse()
                assertThat(presenter.rows[1].selected).isTrue()
            }

        @Test
        fun `cannot tick a track without a detected flight`() =
            runTest {
                // given
                val presenter = batch()
                presenter.load(listOf(track("still.igc", "sum-s", moving = false)))
                val row = presenter.rows.single()

                // when
                presenter.select(row, true)

                // then
                assertThat(row.problem).isEqualTo(IgcRowProblem.NoFlightFound)
                assertThat(presenter.importableCount).isEqualTo(0)
            }

        @Test
        fun `follows the wing for all tracks unless a row has its own`() =
            runTest {
                // given
                val presenter = batch()
                presenter.load(listOf(track("a.igc", "sum-a"), track("b.igc", "sum-b")))
                val other = WingId("wing-2")

                // when
                presenter.changeWing(presenter.rows[0], other)
                presenter.changeDefaultWing(WingId("wing-3"))

                // then
                assertThat(presenter.wingOf(presenter.rows[0])).isEqualTo(other)
                assertThat(presenter.wingOf(presenter.rows[1])).isEqualTo(WingId("wing-3"))
            }
    }

    @Nested
    inner class ImportingTheBatch {
        private var counter = 0

        private fun TestScope.batch() =
            IgcBatchPresenter(
                scope(),
                PrepareIgcImport(reader, flights),
                ImportIgcFlight(flights, wings, attachments, clock) { FlightId("imported-${counter++}") },
                WING_ID,
            )

        @Test
        fun `saves every ticked track with its original file and closes`() =
            runTest {
                // given
                val presenter = batch()
                presenter.load(listOf(track("a.igc", "sum-a"), track("b.igc", "sum-b", LocalDate(2026, 9, 21))))
                var closed = false

                // when
                presenter.import { closed = true }

                // then
                assertThat(closed).isTrue()
                assertThat(presenter.importedCount).isEqualTo(2)
                coVerify(exactly = 2) { attachments.save(any(), any()) }
                coVerify {
                    flights.save(
                        match { it.igcFile?.checksum == "sum-a" && it.details.duration.isPositive() },
                    )
                }
            }

        @Test
        fun `skips tracks the pilot unticked`() =
            runTest {
                // given
                val presenter = batch()
                presenter.load(listOf(track("a.igc", "sum-a"), track("b.igc", "sum-b")))
                presenter.select(presenter.rows[1], false)
                var closed = false

                // when
                presenter.import { closed = true }

                // then
                assertThat(closed).isTrue()
                assertThat(presenter.importedCount).isEqualTo(1)
                coVerify(exactly = 0) { flights.save(match { it.igcFile?.checksum == "sum-b" }) }
            }

        @Test
        fun `keeps the failed tracks open with the reason and imports the others`() =
            runTest {
                // given
                val presenter = batch()
                presenter.load(
                    listOf(
                        track("future.igc", "sum-f", LocalDate(2027, 1, 1)),
                        track("ok.igc", "sum-ok"),
                    ),
                )
                var closed = false

                // when
                presenter.import { closed = true }

                // then
                assertThat(closed).isFalse()
                assertThat(presenter.importedCount).isEqualTo(1)
                assertThat(presenter.rows.map { it.file.fileName }).containsExactly("future.igc")
                assertThat(presenter.rows.map { it.problem })
                    .containsExactly(IgcRowProblem.DateInFuture)
                assertThat(presenter.rows).allMatch { it.selected }
            }

        @Test
        fun `shows a storage failure on its row and removes the stored track`() =
            runTest {
                // given
                coEvery { flights.save(any()) } throws IOException("Disk full")
                val presenter = batch()
                presenter.load(listOf(track("a.igc", "sum-a")))

                // when
                presenter.import {}

                // then
                assertThat(presenter.rows.single().problem).isEqualTo(IgcRowProblem.SaveFailed)
                assertThat(presenter.rows.single().detail).isEqualTo("Disk full")
                coVerify(exactly = 1) { attachments.delete(any()) }
            }

        @Test
        fun `imports an unticked duplicate once the pilot ticks it`() =
            runTest {
                // given
                val earlier =
                    Flight(
                        FlightId("flight-0"),
                        FlightDetails(LocalDate(2026, 9, 20), 95.minutes, WING_ID),
                        Instant.parse("2026-09-20T17:00:00Z"),
                        IgcFile(Attachment(AttachmentId("igc-0"), "old.igc"), "sum-a"),
                    )
                coEvery { flights.findAll() } returns LoadResult(listOf(earlier))
                val presenter = batch()
                presenter.load(listOf(track("a.igc", "sum-a")))
                presenter.select(presenter.rows.single(), true)

                // when
                presenter.import {}

                // then
                assertThat(presenter.importedCount).isEqualTo(1)
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
