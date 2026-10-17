package ch.qscqlmpa.wingtracker.flights.syride

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.flights.ExternalReference
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.IgcFile
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
) = SyrideFlight(
    id = id,
    hasTrack = true,
    name = "Course Tour du Bocal vol 3",
    date = date,
    startTime = startTime,
    duration = 98.minutes,
    siteName = "Dent De Jaman",
    wing = wing,
    distanceKm = 15.0,
    topAltitudeMeters = 2464,
    instrument = "Nav XL",
)

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
        fun `offers Syride's start time for earlier flights whose take-off time is missing or differs`() =
            runTest {
                // given
                val missing =
                    aFlight("f-1", aFlightDetails(takeOffTime = null))
                        .copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:1"))
                val different =
                    aFlight("f-2", aFlightDetails(takeOffTime = LocalTime(13, 40)))
                        .copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:2"))
                val same =
                    aFlight("f-3", aFlightDetails(takeOffTime = LocalTime(13, 43)))
                        .copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:3"))
                givenStored(flightList = listOf(missing, different, same))
                coEvery { source.fetch("QSCQLMPA") } returns
                    listOf(aSyrideFlight("flight:1"), aSyrideFlight("flight:2"), aSyrideFlight("flight:3"))

                // when
                val draft = prepare("QSCQLMPA")

                // then
                assertThat(draft.takeOffTimeUpdates).containsExactly(
                    TakeOffTimeUpdate(FlightId("f-1"), LocalDate(2026, 9, 20), null, LocalTime(13, 43)),
                    TakeOffTimeUpdate(FlightId("f-2"), LocalDate(2026, 9, 20), LocalTime(13, 40), LocalTime(13, 43)),
                )
            }

        @Test
        fun `offers no take-off time when Syride gives no start time`() =
            runTest {
                // given
                val earlier =
                    aFlight("f-1", aFlightDetails(takeOffTime = null))
                        .copy(importedFrom = ExternalReference(SYRIDE_SYSTEM, "flight:1"))
                givenStored(flightList = listOf(earlier))
                coEvery { source.fetch("QSCQLMPA") } returns listOf(aSyrideFlight("flight:1").copy(startTime = null))

                // when
                val draft = prepare("QSCQLMPA")

                // then
                assertThat(draft.takeOffTimeUpdates).isEmpty()
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
                assertThat(flight.details.takeOffTime).isEqualTo(LocalTime(13, 43))
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

    @Nested
    inner class UpdatingTakeOffTimes {
        private val update = UpdateSyrideTakeOffTimes(flights)

        @Test
        fun `replaces the take-off time the pilot was shown with Syride's`() =
            runTest {
                // given
                val stored = aFlight("f-1", aFlightDetails(takeOffTime = LocalTime(13, 40)))
                givenStored(flightList = listOf(stored))
                coEvery { flights.findById(FlightId("f-1")) } returns stored

                // when
                val count =
                    update(
                        listOf(TakeOffTimeUpdate(stored.id, stored.details.date, LocalTime(13, 40), LocalTime(13, 43))),
                    )

                // then
                assertThat(count).isEqualTo(1)
                assertThat(saved.single())
                    .usingRecursiveComparison()
                    .isEqualTo(stored.withDetails(stored.details.copy(takeOffTime = LocalTime(13, 43))))
            }

        @Test
        fun `leaves a flight whose take-off time changed since the account was read`() =
            runTest {
                // given
                val stored = aFlight("f-1", aFlightDetails(takeOffTime = LocalTime(13, 45)))
                givenStored(flightList = listOf(stored))
                coEvery { flights.findById(FlightId("f-1")) } returns stored

                // when
                val count =
                    update(
                        listOf(TakeOffTimeUpdate(stored.id, stored.details.date, LocalTime(13, 40), LocalTime(13, 43))),
                    )

                // then
                assertThat(count).isEqualTo(0)
                assertThat(saved).isEmpty()
            }

        @Test
        fun `skips a flight deleted since the account was read`() =
            runTest {
                // given
                givenStored()
                coEvery { flights.findById(FlightId("f-1")) } returns null

                // when
                val count =
                    update(listOf(TakeOffTimeUpdate(FlightId("f-1"), LocalDate(2026, 9, 20), null, LocalTime(13, 43))))

                // then
                assertThat(count).isEqualTo(0)
            }
    }

    @Nested
    inner class CountingFlightsWithoutTrack {
        private val count get() = CountSyrideFlightsWithoutTrack(flights)

        private fun fromSyride(
            id: String,
            syrideId: String,
            withTrack: Boolean = false,
        ) = aFlight(id).copy(
            importedFrom = ExternalReference(SYRIDE_SYSTEM, syrideId),
            igcFile =
                if (withTrack) {
                    IgcFile(
                        Attachment(AttachmentId("igc-1"), "3271596.igc"),
                        "9f0c41aa77e2",
                    )
                } else {
                    null
                },
        )

        @Test
        fun `counts only Syride flights that have a track on Syride and none stored`() =
            runTest {
                // given
                givenStored(
                    flightList =
                        listOf(
                            fromSyride("flight-1", "flight:3271596"),
                            fromSyride("flight-2", "flight:3271597", withTrack = true),
                            fromSyride("flight-3", "untracked-flight:88412"),
                            aFlight("flight-4"),
                        ),
                )

                // when
                val waiting = count()

                // then
                assertThat(waiting).isEqualTo(1)
            }
    }

    @Nested
    inner class RenamingUntrackedFlightIds {
        private val rename get() = RenameUntrackedSyrideFlightIds(flights)

        private fun importedAs(
            id: String,
            system: String,
            externalId: String,
        ) = aFlight(id).copy(importedFrom = ExternalReference(system, externalId))

        @Test
        fun `gives Syride flights without a track the id the import uses now, keeping their number`() =
            runTest {
                // given
                givenStored(flightList = listOf(importedAs("flight-1", SYRIDE_SYSTEM, "activity:3770736")))

                // when
                val renamed = rename()

                // then
                assertThat(renamed).isEqualTo(1)
                assertThat(saved.single().importedFrom)
                    .isEqualTo(ExternalReference(SYRIDE_SYSTEM, "untracked-flight:3770736"))
                assertThat(saved.single().details).isEqualTo(aFlight("flight-1").details)
            }

        @Test
        fun `leaves flights with a track, flights of other sources and renamed flights alone`() =
            runTest {
                // given
                givenStored(
                    flightList =
                        listOf(
                            importedAs("flight-1", SYRIDE_SYSTEM, "flight:3271596"),
                            importedAs("flight-2", SYRIDE_SYSTEM, "untracked-flight:3770736"),
                            importedAs("flight-3", "spreadsheet", "activity:12"),
                            aFlight("flight-4"),
                        ),
                )

                // when
                val renamed = rename()

                // then
                assertThat(renamed).isEqualTo(0)
                assertThat(saved).isEmpty()
            }
    }
}
