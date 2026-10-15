package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.InMemoryWings
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.flights.aFlightDetails
import ch.qscqlmpa.wingtracker.flights.aWingInService
import ch.qscqlmpa.wingtracker.flights.importing.ImportFailure
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

private val BIRDY = SourceWing("Birdy", "Niviuk")
private val HIKO = SourceWing("Hiko P", "Niviuk")

private fun aSyrideFlight(
    id: String = "flight:1",
    date: LocalDate = LocalDate(2026, 9, 26),
    wing: SourceWing = BIRDY,
    startTime: LocalTime = LocalTime(13, 43),
    name: String = "Course Tour du Bocal vol 3",
    siteName: String = "Dent De Jaman",
    earlierReading: EarlierReading? = null,
) = SyrideFlight(
    id = id,
    hasTrack = true,
    name = name,
    date = date,
    startTime = startTime,
    duration = 98.minutes,
    siteName = siteName,
    wing = wing,
    distanceKm = 15.0,
    topAltitudeMeters = 2464,
    instrument = "Nav XL",
    earlierReading = earlierReading,
)

// What the earlier reader made of a flight named "Open de Jaman (RVL)" at "Pléiades".
private val GARBLED = EarlierReading(name = "Open de Jaman", siteName = "Pl&eacute;iades")

private fun aGarbledSyrideFlight() =
    aSyrideFlight(name = "Open de Jaman (RVL)", siteName = "Pléiades", earlierReading = GARBLED)

private fun anImportedFlight(
    notes: String? = "Open de Jaman",
    takeOffSite: String? = "Pl&eacute;iades",
) = aFlight(id = "imported-1", details = aFlightDetails().copy(notes = notes, takeOffSite = takeOffSite))
    .copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:1"))

class SyrideImportTest {
    private val source = mockk<SyrideFlightSource>()
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val wings = InMemoryWings()
    private val saved = mutableListOf<Flight>()
    private val skyDancer = aWingInService(name = "Sky Dancer")

    private fun givenStored(
        wingList: List<Wing> = listOf(skyDancer),
        flightList: List<Flight> = emptyList(),
    ) {
        wingList.forEach { wings.stored[it.id] = it }
        coEvery { flights.findAll() } returns LoadResult(flightList)
        coEvery { flights.save(any()) } coAnswers { saved += firstArg<Flight>() }
    }

    @Nested
    inner class Preparing {
        private val prepare get() = PrepareSyrideImport(source, flights, wings)

        @Test
        fun `groups the new flights by Syride wing, most recently flown first`() =
            runTest {
                // given
                givenStored()
                coEvery { source.fetch("QSCQLMPA") } returns
                    listOf(
                        aSyrideFlight("flight:1", LocalDate(2025, 4, 4), HIKO),
                        aSyrideFlight("flight:2", LocalDate(2026, 9, 1), BIRDY),
                        aSyrideFlight("flight:3", LocalDate(2026, 9, 5), BIRDY),
                    )

                // when
                val draft = prepare("QSCQLMPA")

                // then
                assertThat(draft.groups.map { it.wing }).containsExactly(BIRDY, HIKO)
                assertThat(draft.groups.first().flights).hasSize(2)
                assertThat(draft.groups.first().firstDate).isEqualTo(LocalDate(2026, 9, 1))
                assertThat(draft.groups.first().totalDuration).isEqualTo(196.minutes)
            }

        @Test
        fun `leaves out flights an earlier import already brought in and counts them`() =
            runTest {
                // given
                val earlier = aFlight().copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:1"))
                givenStored(flightList = listOf(earlier))
                coEvery { source.fetch("QSCQLMPA") } returns
                    listOf(aSyrideFlight("flight:1"), aSyrideFlight("flight:2"))

                // when
                val draft = prepare("QSCQLMPA")

                // then
                assertThat(draft.alreadyImported).isEqualTo(1)
                assertThat(
                    draft.groups
                        .single()
                        .flights
                        .map { it.id },
                ).containsExactly("flight:2")
            }

        @Test
        fun `suggests the existing wing with the same name`() =
            runTest {
                // given
                givenStored(listOf(skyDancer, aWingInService(id = "w-2", name = "Birdy")))
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aSyrideFlight())

                // when
                val draft = prepare("QSCQLMPA")

                // then
                assertThat(draft.groups.single().suggestedWingId).isEqualTo(WingId("w-2"))
            }

        @Test
        fun `suggests nothing when no wing has that name`() =
            runTest {
                // given
                givenStored()
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aSyrideFlight())

                // when / then
                assertThat(prepare("QSCQLMPA").groups.single().suggestedWingId).isNull()
            }
    }

    @Nested
    inner class FindingCorrections {
        private val prepare get() = PrepareSyrideImport(source, flights, wings)

        @Test
        fun `offers to correct texts that are still what the earlier reader stored`() =
            runTest {
                // given
                givenStored(flightList = listOf(anImportedFlight()))
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aGarbledSyrideFlight())

                // when
                val draft = prepare("QSCQLMPA")

                // then
                assertThat(draft.corrections).containsExactly(
                    SyrideCorrection(
                        FlightId("imported-1"),
                        notes = TextCorrection("Open de Jaman", "Open de Jaman (RVL)"),
                        takeOffSite = TextCorrection("Pl&eacute;iades", "Pléiades"),
                    ),
                )
            }

        @Test
        fun `keeps a text the pilot changed and corrects the other one`() =
            runTest {
                // given
                givenStored(flightList = listOf(anImportedFlight(notes = "Open de Jaman, 3rd place")))
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aGarbledSyrideFlight())

                // when
                val correction = prepare("QSCQLMPA").corrections.single()

                // then
                assertThat(correction.notes).isNull()
                assertThat(correction.takeOffSite).isEqualTo(TextCorrection("Pl&eacute;iades", "Pléiades"))
            }

        @Test
        fun `offers nothing for a flight the earlier reader read correctly`() =
            runTest {
                // given
                givenStored(flightList = listOf(anImportedFlight(notes = "Course Tour du Bocal vol 3")))
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aSyrideFlight())

                // when / then
                assertThat(prepare("QSCQLMPA").corrections).isEmpty()
            }

        @Test
        fun `offers nothing for a flight that is not imported yet`() =
            runTest {
                // given
                givenStored()
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aGarbledSyrideFlight())

                // when / then
                assertThat(prepare("QSCQLMPA").corrections).isEmpty()
            }
    }

    @Nested
    inner class Correcting {
        private val correct get() = CorrectSyrideImports(flights)
        private val correction =
            SyrideCorrection(
                FlightId("imported-1"),
                notes = TextCorrection("Open de Jaman", "Open de Jaman (RVL)"),
                takeOffSite = TextCorrection("Pl&eacute;iades", "Pléiades"),
            )

        @Test
        fun `corrects the texts and keeps everything else`() =
            runTest {
                // given
                val stored = anImportedFlight()
                givenStored(flightList = listOf(stored))
                coEvery { flights.findById(FlightId("imported-1")) } returns stored

                // when
                val corrected = correct(listOf(correction))

                // then
                assertThat(corrected).isEqualTo(1)
                assertThat(saved.single())
                    .usingRecursiveComparison()
                    .isEqualTo(
                        stored.withDetails(
                            stored.details.copy(notes = "Open de Jaman (RVL)", takeOffSite = "Pléiades"),
                        ),
                    )
            }

        @Test
        fun `keeps a text the pilot changed after the account was read`() =
            runTest {
                // given
                val stored = anImportedFlight(takeOffSite = "Les Pléiades, north take-off")
                givenStored(flightList = listOf(stored))
                coEvery { flights.findById(FlightId("imported-1")) } returns stored

                // when
                correct(listOf(correction))

                // then
                assertThat(saved.single().details.notes).isEqualTo("Open de Jaman (RVL)")
                assertThat(saved.single().details.takeOffSite).isEqualTo("Les Pléiades, north take-off")
            }

        @Test
        fun `skips a flight deleted meanwhile or already corrected`() =
            runTest {
                // given
                val alreadyCorrected = anImportedFlight(notes = "Open de Jaman (RVL)", takeOffSite = "Pléiades")
                givenStored(flightList = listOf(alreadyCorrected))
                coEvery { flights.findById(FlightId("imported-1")) } returns alreadyCorrected
                coEvery { flights.findById(FlightId("deleted")) } returns null

                // when
                val corrected = correct(listOf(correction, correction.copy(flightId = FlightId("deleted"))))

                // then
                assertThat(corrected).isZero()
                coVerify(exactly = 0) { flights.save(any()) }
            }
    }

    @Nested
    inner class Importing {
        private var next = 0
        private val import get() =
            ImportSyrideFlights(flights, wings, aFixedClock(), RegisterWing(wings) { WingId("new-wing-${next++}") }) {
                FlightId("imported-${next++}")
            }

        @Test
        fun `imports a flight on an existing wing with everything Syride knows`() =
            runTest {
                // given
                givenStored()

                // when
                val result = import(listOf(aSyrideFlight() to WingTarget.Existing(skyDancer.id)), emptyMap())

                // then
                assertThat(result.imported).isEqualTo(1)
                val flight = saved.single()
                assertThat(flight.details.wingId).isEqualTo(skyDancer.id)
                assertThat(flight.details.takeOffSite).isEqualTo("Dent De Jaman")
                assertThat(flight.details.notes).isEqualTo("Course Tour du Bocal vol 3")
                assertThat(flight.details.distanceKm).isEqualTo(15.0)
                assertThat(flight.details.topAltitudeMeters).isEqualTo(2464)
                assertThat(flight.details.instrument).isEqualTo("Nav XL")
                assertThat(flight.importedFrom).isEqualTo(ExternalReference(SYRIDE_SYSTEM, "flight:1"))
            }

        @Test
        fun `creates a new wing starting with its first flight and puts the flights on it`() =
            runTest {
                // given
                givenStored()
                val selection =
                    listOf(
                        aSyrideFlight("flight:1", LocalDate(2026, 9, 26)) to WingTarget.New("birdy"),
                        aSyrideFlight("flight:2", LocalDate(2025, 4, 4)) to WingTarget.New("birdy"),
                    )

                // when
                val result = import(selection, mapOf("birdy" to NewWingSpec("Birdy", "Niviuk", "Birdy")))

                // then
                val wing = result.createdWings.single()
                assertThat(wing.details.name).isEqualTo("Birdy")
                assertThat(wing.details.inServiceDate).isNull()
                assertThat(saved.map { it.details.wingId }).containsOnly(wing.id)
                assertThat(result.imported).isEqualTo(2)
            }

        @Test
        fun `lets several Syride wings share one new wing`() =
            runTest {
                // given
                givenStored()
                val selection =
                    listOf(
                        aSyrideFlight("flight:1", wing = BIRDY) to WingTarget.New("shared"),
                        aSyrideFlight("flight:2", wing = HIKO) to WingTarget.New("shared"),
                    )

                // when
                val result = import(selection, mapOf("shared" to NewWingSpec("Birdy", "Niviuk", "Birdy")))

                // then
                assertThat(result.createdWings).hasSize(1)
            }

        @Test
        fun `does not create a wing nobody flew on`() =
            runTest {
                // given
                givenStored()

                // when
                val result =
                    import(
                        listOf(aSyrideFlight() to WingTarget.Skip),
                        mapOf("unused" to NewWingSpec("Birdy", "Niviuk", "Birdy")),
                    )

                // then
                assertThat(result.createdWings).isEmpty()
                assertThat(result.skipped).isEqualTo(1)
                coVerify(exactly = 0) { flights.save(any()) }
            }

        @Test
        fun `skips a flight that was imported meanwhile`() =
            runTest {
                // given
                val earlier = aFlight().copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:1"))
                givenStored(flightList = listOf(earlier))

                // when
                val result = import(listOf(aSyrideFlight() to WingTarget.Existing(skyDancer.id)), emptyMap())

                // then
                assertThat(result.imported).isZero()
                assertThat(result.skipped).isEqualTo(1)
            }

        @Test
        fun `imports a flight dated before the wing was in service`() =
            runTest {
                // given
                givenStored()
                val selection =
                    listOf(aSyrideFlight("flight:1", LocalDate(2020, 1, 1)) to WingTarget.Existing(skyDancer.id))

                // when
                val result = import(selection, emptyMap())

                // then
                assertThat(result.imported).isEqualTo(1)
                assertThat(result.failed).isEmpty()
            }

        @Test
        fun `reports a flight dated in the future`() =
            runTest {
                // given
                givenStored()

                // when
                val result =
                    import(
                        listOf(aSyrideFlight(date = LocalDate(2027, 1, 1)) to WingTarget.Existing(skyDancer.id)),
                        emptyMap(),
                    )

                // then
                assertThat(result.failed.single().failure).isEqualTo(ImportFailure.DateInFuture)
            }

        @Test
        fun `reports a storage failure on its flight and goes on`() =
            runTest {
                // given
                givenStored()
                coEvery { flights.save(match { it.importedFrom?.id == "flight:1" }) } throws IOException("Disk full")
                val selection =
                    listOf(
                        aSyrideFlight("flight:1") to WingTarget.Existing(skyDancer.id),
                        aSyrideFlight("flight:2") to WingTarget.Existing(skyDancer.id),
                    )

                // when
                val result = import(selection, emptyMap())

                // then
                assertThat(result.failed.single().failure).isEqualTo(ImportFailure.SaveFailed)
                assertThat(result.failed.single().detail).isEqualTo("Disk full")
                assertThat(result.imported).isEqualTo(1)
            }

        @Test
        fun `keeps the order of a day by logging earlier flights first`() =
            runTest {
                // given
                givenStored()
                val selection =
                    listOf(
                        aSyrideFlight("flight:late", startTime = LocalTime(15, 0)) to WingTarget.Existing(skyDancer.id),
                        aSyrideFlight("flight:early", startTime = LocalTime(9, 0)) to WingTarget.Existing(skyDancer.id),
                    )

                // when
                import(selection, emptyMap())

                // then
                assertThat(saved.map { it.importedFrom?.id }).containsExactly("flight:early", "flight:late")
                assertThat(saved[0].loggedAt).isLessThan(saved[1].loggedAt)
            }
    }
}
