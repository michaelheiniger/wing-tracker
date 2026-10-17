package ch.qscqlmpa.wingtracker.flights.igc

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDateInFutureException
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.NOW
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.flights.aFlightDetails
import ch.qscqlmpa.wingtracker.flights.aWingInService
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

private val FILE = NewAttachment("2026-09-20-niederhorn.igc", byteArrayOf(65, 88, 88, 88))
private const val CHECKSUM = "3b7e72f0c1d9"

class IgcImportTest {
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val wings = mockk<WingRepository>()
    private val attachments = mockk<AttachmentStore>(relaxUnitFun = true)
    private val reader = mockk<TrackReader>()
    private val wing = aWingInService()

    private fun givenTrack(legs: Array<Leg>) {
        every { reader.read(FILE.bytes) } returns
            ReadTrack(FlightTrack(LocalDate(2026, 9, 20), trackOf(*legs)), CHECKSUM)
    }

    private fun givenStoredFlights(vararg list: Flight) {
        coEvery { flights.findAll() } returns LoadResult(list.toList())
    }

    @Nested
    inner class Preparing {
        @Test
        fun `offers the date and the detected times and duration`() =
            runTest {
                // given
                givenTrack(arrayOf(Leg.Still(10.minutes), Leg.Moving(75.minutes, 32.0), Leg.Still(5.minutes)))
                givenStoredFlights()

                // when
                val draft = PrepareIgcImport(reader, flights, aFixedClock())(FILE)

                // then
                assertThat(draft.date).isEqualTo(LocalDate(2026, 9, 20))
                assertThat(draft.fileName).isEqualTo(FILE.fileName)
                assertThat(draft.checksum).isEqualTo(CHECKSUM)
                assertThat(draft.duration!!).isBetween(74.minutes, 76.minutes)
                assertThat(draft.takeOffTime).isNotNull()
                assertThat(draft.landingTime).isNotNull()
                assertThat(draft.duplicateOf).isNull()
            }

        @Test
        fun `offers the detected take-off and landing as the pilot's local time in whole minutes`() =
            runTest {
                // given
                givenTrack(arrayOf(Leg.Still(10.minutes), Leg.Moving(75.minutes, 32.0), Leg.Still(5.minutes)))
                givenStoredFlights()
                val zurich = AppClock(zone = TimeZone.of("Europe/Zurich"))

                // when
                val draft = PrepareIgcImport(reader, flights, zurich)(FILE)

                // then
                assertThat(draft.takeOffTime!!).isBetween(LocalTime(11, 9), LocalTime(11, 11))
                assertThat(draft.takeOffTime.second).isZero()
                assertThat(draft.landingTime!!).isBetween(LocalTime(12, 24), LocalTime(12, 26))
            }

        @Test
        fun `offers no duration when the track never moves`() =
            runTest {
                // given
                givenTrack(arrayOf(Leg.Still(30.minutes)))
                givenStoredFlights()

                // when
                val draft = PrepareIgcImport(reader, flights, aFixedClock())(FILE)

                // then
                assertThat(draft.duration).isNull()
                assertThat(draft.takeOffTime).isNull()
                assertThat(draft.landingTime).isNull()
                assertThat(draft.date).isEqualTo(LocalDate(2026, 9, 20))
            }

        @Test
        fun `points at the flight this exact file was imported as before`() =
            runTest {
                // given
                givenTrack(arrayOf(Leg.Moving(30.minutes, 32.0)))
                val earlier =
                    aFlight(
                        "flight-1",
                    ).copy(
                        igcFile =
                            IgcFile(
                                ch.qscqlmpa.wingtracker.attachments.Attachment(
                                    ch.qscqlmpa.wingtracker.attachments
                                        .AttachmentId("igc-1"),
                                    "old.igc",
                                ),
                                CHECKSUM,
                            ),
                    )
                givenStoredFlights(aFlight("flight-0"), earlier)

                // when
                val draft = PrepareIgcImport(reader, flights, aFixedClock())(FILE)

                // then
                assertThat(draft.duplicateOf?.id).isEqualTo(FlightId("flight-1"))
            }

        @Test
        fun `passes on the failure when the file is not a readable track`() =
            runTest {
                // given
                every { reader.read(FILE.bytes) } throws InvalidTrackException("no date")
                givenStoredFlights()

                // when
                val failure = runCatching { PrepareIgcImport(reader, flights, aFixedClock())(FILE) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(InvalidTrackException::class.java)
            }
    }

    @Nested
    inner class Importing {
        private val import = ImportIgcFlight(flights, wings, attachments, aFixedClock()) { FlightId("imported") }

        @Test
        fun `saves the flight with the original track attached`() =
            runTest {
                // given
                coEvery { wings.findById(wing.id) } returns wing
                val details = aFlightDetails()

                // when
                val flight = import(details, FILE, CHECKSUM)

                // then
                assertThat(flight.id).isEqualTo(FlightId("imported"))
                assertThat(flight.loggedAt).isEqualTo(NOW)
                assertThat(flight.igcFile?.checksum).isEqualTo(CHECKSUM)
                assertThat(flight.igcFile?.attachment?.fileName).isEqualTo(FILE.fileName)
                coVerify(exactly = 1) { attachments.save(any(), FILE.bytes) }
                coVerify(exactly = 1) { flights.save(flight) }
            }

        @Test
        fun `applies the same date rules as logging by hand and stores nothing when refused`() =
            runTest {
                // given
                coEvery { wings.findById(wing.id) } returns wing

                // when
                val failure =
                    runCatching {
                        import(
                            aFlightDetails(date = LocalDate(2027, 1, 1)),
                            FILE,
                            CHECKSUM,
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(FlightDateInFutureException::class.java)
                coVerify(exactly = 0) { attachments.save(any(), any()) }
                coVerify(exactly = 0) { flights.save(any()) }
            }

        @Test
        fun `removes the stored track again when saving the flight fails`() =
            runTest {
                // given
                coEvery { wings.findById(wing.id) } returns wing
                coEvery { flights.save(any()) } throws IOException("Disk full")

                // when
                val failure = runCatching { import(aFlightDetails(), FILE, CHECKSUM) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(IOException::class.java)
                coVerify(exactly = 1) { attachments.delete(any()) }
            }
    }
}
