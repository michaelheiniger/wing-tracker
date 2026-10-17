package ch.qscqlmpa.wingtracker.flights.airtime

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightExport
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.IgcFile
import ch.qscqlmpa.wingtracker.flights.InMemoryWings
import ch.qscqlmpa.wingtracker.flights.NOW
import ch.qscqlmpa.wingtracker.flights.aFixedClock
import ch.qscqlmpa.wingtracker.flights.aFlight
import ch.qscqlmpa.wingtracker.flights.aFlightDetails
import ch.qscqlmpa.wingtracker.flights.aWingInService
import ch.qscqlmpa.wingtracker.flights.igc.FlightTrack
import ch.qscqlmpa.wingtracker.flights.igc.ReadTrack
import ch.qscqlmpa.wingtracker.flights.igc.TrackFix
import ch.qscqlmpa.wingtracker.flights.igc.TrackReader
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val TOKEN = "at_7f3c9e21b4d84a6f"
private const val NICK = "qscqlmpa"
private const val IOTA_GLIDER_ID = "0b6f2c1e-93a4-4d7b-8e55-1c2f7a9d3e60"
private const val NEW_GLIDER_ID = "c4e8a2d6-5f17-4b39-a0c2-7e9d1b3f6a84"
private val IGC_BYTES = "AXCT7LX\r\nHFDTE200926\r\n".encodeToByteArray()

private fun anIota(id: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"): Wing {
    val wing = aWingInService(id = id, name = "Sky Dancer", manufacturer = "Advance", model = "Iota 3")
    return wing.copy(details = wing.details.copy(size = "25"))
}

private fun aBirdy(): Wing {
    val wing =
        aWingInService(
            id = "7a9c3e15-2b6d-4f80-91e4-5d8b0c2a7f36",
            name = "Birdy",
            manufacturer = "Niviuk",
            model = "Birdy",
        )
    return wing.copy(details = wing.details.copy(size = "S"))
}

private fun anIotaGlider() = AirtimeGlider(IOTA_GLIDER_ID, brand = " ADVANCE", model = "iota 3 ", size = "25")

private fun aTrackedFlight(
    id: String = "flight-1",
    takeOffTime: LocalTime? = LocalTime(13, 5),
    wingId: WingId = anIota().id,
) = aFlight(id, aFlightDetails(takeOffTime = takeOffTime, wingId = wingId))
    .copy(igcFile = IgcFile(Attachment(AttachmentId("igc-1"), "2026-09-20-niederhorn.igc"), "3b7e72f0c1d9"))

private fun aManualFlight(
    id: String = "flight-2",
    takeOffTime: LocalTime? = LocalTime(13, 5),
    wingId: WingId = anIota().id,
) = aFlight(id, aFlightDetails(date = LocalDate(2026, 9, 21), takeOffTime = takeOffTime, wingId = wingId))

class AirtimeExportTest {
    private val account = mockk<AirtimeAccount>(relaxUnitFun = true)
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val wings = InMemoryWings()
    private val attachments = mockk<AttachmentStore>()
    private val trackReader = mockk<TrackReader>()
    private val saved = mutableListOf<Flight>()

    private fun givenStored(
        flightList: List<Flight>,
        wingList: List<Wing> = listOf(anIota()),
        gliders: List<AirtimeGlider> = listOf(anIotaGlider()),
        startsOnAirtime: List<Instant> = emptyList(),
        takeOffs: List<AirtimeTakeOff> = listOf(AirtimeTakeOff(412, "Niederhorn")),
    ) {
        wingList.forEach { wings.stored[it.id] = it }
        coEvery { flights.findAll() } returns LoadResult(flightList)
        flightList.forEach { flight -> coEvery { flights.findById(flight.id) } returns flight }
        coEvery { flights.save(any()) } coAnswers { saved += firstArg<Flight>() }
        coEvery { account.gliders(TOKEN) } returns gliders
        coEvery { account.flightStarts(NICK) } returns startsOnAirtime
        coEvery { account.takeOffs() } returns takeOffs
        coEvery { attachments.read(AttachmentId("igc-1")) } returns IGC_BYTES
        every { trackReader.read(IGC_BYTES) } returns aTrackStartingAt(Instant.parse("2026-09-20T13:05:12Z"))
        coEvery { account.flightsStartingBetween(NICK, any(), any()) } returns emptyList()
    }

    private fun aTrackStartingAt(firstFix: Instant) =
        ReadTrack(
            FlightTrack(LocalDate(2026, 9, 20), listOf(TrackFix(firstFix, 46.6876, 7.7987, 1932))),
            "3b7e72f0c1d9",
        )

    private suspend fun prepare() =
        PrepareAirtimeExport(account, flights, wings, attachments, trackReader, aFixedClock())(" $NICK ", TOKEN)

    @Nested
    inner class Preparing {
        @Test
        fun `finds the glider of each wing by brand, model and size, ignoring case and spaces`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight()))

                // when
                val draft = prepare()

                // then
                assertThat(draft.candidates.single().gliderId).isEqualTo(IOTA_GLIDER_ID)
                assertThat(draft.newGliders).isEmpty()
                assertThat(draft.nick).isEqualTo(NICK)
            }

        @Test
        fun `lists a wing without a glider once, however many flights it has`() =
            runTest {
                // given
                givenStored(
                    listOf(
                        aTrackedFlight("flight-1", wingId = aBirdy().id),
                        aManualFlight("flight-2", wingId = aBirdy().id),
                    ),
                    wingList = listOf(anIota(), aBirdy()),
                )

                // when
                val draft = prepare()

                // then
                assertThat(draft.newGliders.map { it.id }).containsExactly(aBirdy().id)
                assertThat(draft.candidates.map { it.gliderId }).containsOnlyNulls()
                assertThat(draft.candidates.map { it.newGlider }).containsOnly(aBirdy().id)
            }

        @Test
        fun `creates one glider for two wings of the same brand, model and size`() =
            runTest {
                // given
                val secondBirdy = aBirdy().copy(id = WingId("e1b7d3a9-4c62-4f05-8a3e-9b2d6c1f4e70"))
                givenStored(
                    listOf(
                        aTrackedFlight("flight-1", wingId = aBirdy().id),
                        aManualFlight("flight-2", wingId = secondBirdy.id),
                    ),
                    wingList = listOf(aBirdy(), secondBirdy),
                )

                // when
                val draft = prepare()

                // then
                assertThat(draft.newGliders).hasSize(1)
                assertThat(draft.candidates.map { it.newGlider }).containsOnly(draft.newGliders.single().id)
            }

        @Test
        fun `leaves out the flights an earlier export sent and counts them`() =
            runTest {
                // given
                val sent = aTrackedFlight().markExported(AIRTIME_SYSTEM, Instant.parse("2026-10-01T19:00:00Z"))
                givenStored(listOf(sent, aManualFlight()))

                // when
                val draft = prepare()

                // then
                assertThat(draft.alreadyExported).isEqualTo(1)
                assertThat(draft.candidates.map { it.flight.id }).containsExactly(FlightId("flight-2"))
            }

        @Test
        fun `skips a flight that starts in the same minute as one already on air-time`() =
            runTest {
                // given
                givenStored(
                    listOf(aTrackedFlight(), aManualFlight()),
                    startsOnAirtime = listOf(Instant.parse("2026-09-20T13:05:42Z")),
                )

                // when
                val draft = prepare()

                // then
                assertThat(draft.skipped).containsExactly(
                    SkippedAirtimeFlight(aTrackedFlight(), AirtimeSkipReason.AlreadyOnAirtime),
                )
                assertThat(draft.candidates.map { it.flight.id }).containsExactly(FlightId("flight-2"))
            }

        @Test
        fun `compares a flight with a track by its first fix, which air-time takes as its start`() =
            runTest {
                // given
                val flight = aTrackedFlight(takeOffTime = LocalTime(13, 8))
                givenStored(listOf(flight), startsOnAirtime = listOf(Instant.parse("2026-09-20T13:04:10Z")))
                every { trackReader.read(IGC_BYTES) } returns aTrackStartingAt(Instant.parse("2026-09-20T13:04:55Z"))

                // when
                val draft = prepare()

                // then
                assertThat(draft.skipped.single().reason).isEqualTo(AirtimeSkipReason.AlreadyOnAirtime)
            }

        @Test
        fun `skips a flight with neither a track nor a take-off time`() =
            runTest {
                // given
                val flight = aManualFlight(takeOffTime = null)
                givenStored(listOf(flight))

                // when
                val draft = prepare()

                // then
                assertThat(draft.skipped).containsExactly(SkippedAirtimeFlight(flight, AirtimeSkipReason.NoTakeOffTime))
                assertThat(draft.candidates).isEmpty()
            }

        @Test
        fun `gives a manual entry air-time's take-off site of the same name, ignoring case`() =
            runTest {
                // given
                givenStored(listOf(aManualFlight()), takeOffs = listOf(AirtimeTakeOff(412, " niederhorn")))

                // when
                val draft = prepare()

                // then
                assertThat(draft.candidates.single().takeOffId).isEqualTo(412)
            }

        @Test
        fun `leaves the take-off site out when air-time has none of that name`() =
            runTest {
                // given
                givenStored(listOf(aManualFlight()), takeOffs = listOf(AirtimeTakeOff(97, "Amisbühl")))

                // when
                val draft = prepare()

                // then
                assertThat(draft.candidates.single().takeOffId).isNull()
            }

        @Test
        fun `does not look up take-off sites when every flight has a track`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight()))

                // when
                val draft = prepare()

                // then
                assertThat(draft.candidates.single().takeOffId).isNull()
                coVerify(exactly = 0) { account.takeOffs() }
            }
    }

    @Nested
    inner class Exporting {
        private val export get() =
            ExportFlightsToAirtime(
                account,
                flights,
                attachments,
                aFixedClock(),
            ) { NEW_GLIDER_ID }

        @Test
        fun `uploads a flight's track with its glider and marks it exported`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight()))
                val draft = prepare()

                // when
                val result = export(TOKEN, draft, setOf(FlightId("flight-1")), emptyMap())

                // then
                coVerify(exactly = 1) {
                    account.uploadTrack(TOKEN, IGC_BYTES, "2026-09-20-niederhorn.igc", IOTA_GLIDER_ID)
                }
                assertThat(saved.single().exports).containsExactly(FlightExport(AIRTIME_SYSTEM, NOW))
                assertThat(result).isEqualTo(AirtimeExportResult(exported = 1, glidersCreated = 0))
            }

        @Test
        fun `registers a flight without a track from its take-off time and duration in the computer's time zone`() =
            runTest {
                // given
                givenStored(listOf(aManualFlight()))
                val draft = prepare()

                // when
                export(TOKEN, draft, setOf(FlightId("flight-2")), emptyMap())

                // then
                coVerify(exactly = 1) {
                    account.registerManualFlight(
                        TOKEN,
                        ManualAirtimeFlight(
                            start = Instant.parse("2026-09-21T13:05:00Z"),
                            end = Instant.parse("2026-09-21T13:05:00Z") + 95.minutes,
                            gliderId = IOTA_GLIDER_ID,
                            takeOffId = 412,
                        ),
                    )
                }
            }

        @Test
        fun `creates a missing glider with the colour given, then sends its flights with it`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight(wingId = aBirdy().id)), wingList = listOf(aBirdy()))
                val draft = prepare()

                // when
                val result = export(TOKEN, draft, setOf(FlightId("flight-1")), mapOf(aBirdy().id to "Blue / white"))

                // then
                coVerify(exactly = 1) {
                    account.createGlider(
                        TOKEN,
                        NewAirtimeGlider(NEW_GLIDER_ID, "Birdy", "Niviuk", "Birdy", "S", "Blue / white"),
                    )
                }
                coVerify(exactly = 1) { account.uploadTrack(TOKEN, any(), any(), NEW_GLIDER_ID) }
                assertThat(result.glidersCreated).isEqualTo(1)
            }

        @Test
        fun `sends only the chosen flights and creates only the gliders they need`() =
            runTest {
                // given
                givenStored(
                    listOf(aTrackedFlight(), aManualFlight(wingId = aBirdy().id)),
                    wingList = listOf(anIota(), aBirdy()),
                )
                val draft = prepare()

                // when
                val result = export(TOKEN, draft, setOf(FlightId("flight-1")), emptyMap())

                // then
                coVerify(exactly = 0) { account.createGlider(any(), any()) }
                coVerify(exactly = 0) { account.registerManualFlight(any(), any()) }
                assertThat(result).isEqualTo(AirtimeExportResult(exported = 1, glidersCreated = 0))
            }

        @Test
        fun `stops at the first refusal and keeps the flights sent before it marked`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight(), aManualFlight()))
                val draft = prepare()
                coEvery { account.registerManualFlight(TOKEN, any()) } throws
                    AirtimeException(AirtimeProblem.NoConnection)

                // when
                val result = export(TOKEN, draft, setOf(FlightId("flight-1"), FlightId("flight-2")), emptyMap())

                // then
                assertThat(saved.map { it.id }).containsExactly(FlightId("flight-1"))
                assertThat(result).isEqualTo(AirtimeExportResult(1, 0, AirtimeProblem.NoConnection))
            }

        @Test
        fun `keeps the id air-time gave the flight just sent, looked up in the minute it starts`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight()))
                val draft = prepare()
                coEvery {
                    account.flightsStartingBetween(
                        NICK,
                        Instant.parse("2026-09-20T13:05:00Z"),
                        Instant.parse("2026-09-20T13:05:59Z"),
                    )
                } returns
                    listOf(
                        AirtimeFlight(986, Instant.parse("2026-09-20T13:05:12Z")),
                        AirtimeFlight(1204, Instant.parse("2026-09-20T13:05:12Z")),
                    )

                // when
                export(TOKEN, draft, setOf(FlightId("flight-1")), emptyMap())

                // then
                assertThat(saved.single().exports).containsExactly(FlightExport(AIRTIME_SYSTEM, NOW, "1204"))
            }

        @Test
        fun `still marks the flight sent when its id cannot be looked up`() =
            runTest {
                // given
                givenStored(listOf(aManualFlight()))
                val draft = prepare()
                coEvery { account.flightsStartingBetween(any(), any(), any()) } throws
                    AirtimeException(AirtimeProblem.Unreachable)

                // when
                val result = export(TOKEN, draft, setOf(FlightId("flight-2")), emptyMap())

                // then
                assertThat(saved.single().exports).containsExactly(FlightExport(AIRTIME_SYSTEM, NOW))
                assertThat(result.failure).isNull()
            }

        @Test
        fun `reports progress after each chosen flight`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight(), aManualFlight()))
                val draft = prepare()
                val progress = mutableListOf<Pair<Int, Int>>()

                // when
                export(TOKEN, draft, setOf(FlightId("flight-1"), FlightId("flight-2")), emptyMap()) { done, total ->
                    progress += done to total
                }

                // then
                assertThat(progress).containsExactly(1 to 2, 2 to 2)
            }

        @Test
        fun `does not send a flight exported since the preview again`() =
            runTest {
                // given
                givenStored(listOf(aTrackedFlight()))
                val draft = prepare()
                val sentMeanwhile = aTrackedFlight().markExported(AIRTIME_SYSTEM, Instant.parse("2026-10-11T18:00:00Z"))
                coEvery { flights.findById(FlightId("flight-1")) } returns sentMeanwhile

                // when
                val result = export(TOKEN, draft, setOf(FlightId("flight-1")), emptyMap())

                // then
                coVerify(exactly = 0) { account.uploadTrack(any(), any(), any(), any()) }
                assertThat(result.exported).isZero()
            }
    }
}
