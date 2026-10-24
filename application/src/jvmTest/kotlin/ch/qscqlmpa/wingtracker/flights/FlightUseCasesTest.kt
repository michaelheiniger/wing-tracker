package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingNotFoundException
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.RecordingTransactions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FlightUseCasesTest {
    private val transactions = RecordingTransactions()
    private val flights = mockk<FlightRepository>(relaxUnitFun = true)
    private val wings = mockk<WingRepository>()
    private val attachments = mockk<AttachmentStore>(relaxUnitFun = true)
    private val wing = aWingInService()

    private fun givenTheWingExists() {
        coEvery { wings.findById(wing.id) } returns wing
    }

    @Nested
    inner class LogFlightUseCase {
        private val logFlight = LogFlight(flights, wings, aFixedClock(), transactions) { FlightId("new-flight") }

        @Test
        fun `records the flight as one change to the logbook`() =
            runTest {
                // given
                givenTheWingExists()

                // when
                logFlight(aFlightDetails())

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.FlightLogged(FlightId("new-flight")))
            }

        @Test
        fun `saves the flight with a generated id and the time it was logged`() =
            runTest {
                // given
                givenTheWingExists()
                val details = aFlightDetails()

                // when
                val flight = logFlight(details)

                // then
                val expected = Flight(FlightId("new-flight"), details, NOW)
                assertThat(flight).usingRecursiveComparison().isEqualTo(expected)
                coVerify(exactly = 1) { flights.save(expected) }
            }

        @Test
        fun `accepts a flight dated today`() =
            runTest {
                // given
                givenTheWingExists()

                // when
                val flight = logFlight(aFlightDetails(date = TODAY))

                // then
                assertThat(flight.details.date).isEqualTo(TODAY)
            }

        @Test
        fun `accepts a flight dated on the day the wing entered service`() =
            runTest {
                // given
                givenTheWingExists()

                // when
                val inService = checkNotNull(wing.details.inServiceDate)
                val flight = logFlight(aFlightDetails(date = inService))

                // then
                assertThat(flight.details.date).isEqualTo(inService)
            }

        @Test
        fun `rejects a flight dated tomorrow`() =
            runTest {
                // given
                givenTheWingExists()

                // when
                val failure =
                    runCatching {
                        logFlight(
                            aFlightDetails(date = LocalDate(2026, 10, 12)),
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(FlightDateInFutureException::class.java)
                coVerify(exactly = 0) { flights.save(any()) }
            }

        @Test
        fun `saves a flight dated before the wing entered service`() =
            runTest {
                // given
                givenTheWingExists()
                val details = aFlightDetails(date = LocalDate(2024, 3, 8))

                // when
                logFlight(details)

                // then
                coVerify(exactly = 1) { flights.save(match { it.details == details }) }
            }

        @Test
        fun `rejects a flight on a wing that does not exist`() =
            runTest {
                // given
                val missing = WingId("missing-wing")
                coEvery { wings.findById(missing) } returns null

                // when
                val failure = runCatching { logFlight(aFlightDetails(wingId = missing)) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(WingNotFoundException::class.java)
            }
    }

    @Nested
    inner class UpdateFlightUseCase {
        private val updateFlight = UpdateFlight(flights, wings, aFixedClock(), transactions)

        @Test
        fun `records the update as one change to the logbook`() =
            runTest {
                // given
                givenTheWingExists()
                val existing = aFlight()
                coEvery { flights.findById(existing.id) } returns existing

                // when
                updateFlight(existing.id, aFlightDetails(date = LocalDate(2026, 9, 21)))

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.FlightUpdated(existing.id))
            }

        @Test
        fun `replaces the details and keeps the id and the logging time`() =
            runTest {
                // given
                givenTheWingExists()
                val existing = aFlight()
                coEvery { flights.findById(existing.id) } returns existing
                val newDetails = aFlightDetails(date = LocalDate(2026, 9, 21))

                // when
                val updated = updateFlight(existing.id, newDetails)

                // then
                assertThat(updated).usingRecursiveComparison().isEqualTo(existing.copy(details = newDetails))
                coVerify(exactly = 1) { flights.save(updated) }
            }

        @Test
        fun `applies the same date rules as logging`() =
            runTest {
                // given
                givenTheWingExists()
                val existing = aFlight()
                coEvery { flights.findById(existing.id) } returns existing

                // when
                val failure =
                    runCatching {
                        updateFlight(
                            existing.id,
                            aFlightDetails(date = LocalDate(2027, 1, 1)),
                        )
                    }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(FlightDateInFutureException::class.java)
                coVerify(exactly = 0) { flights.save(any()) }
            }

        @Test
        fun `fails when the flight does not exist`() =
            runTest {
                // given
                val id = FlightId("missing-flight")
                coEvery { flights.findById(id) } returns null

                // when
                val failure = runCatching { updateFlight(id, aFlightDetails()) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(FlightNotFoundException::class.java)
            }
    }

    @Nested
    inner class DeleteFlightUseCase {
        @Test
        fun `records the deletion as one change to the logbook`() =
            runTest {
                // given
                val existing = aFlight()
                coEvery { flights.findById(existing.id) } returns existing

                // when
                DeleteFlight(flights, attachments, transactions)(existing.id)

                // then
                assertThat(transactions.changes).containsExactly(LogbookChange.FlightDeleted(existing.id))
            }

        @Test
        fun `deletes an existing flight`() =
            runTest {
                // given
                val existing = aFlight()
                coEvery { flights.findById(existing.id) } returns existing

                // when
                DeleteFlight(flights, attachments, transactions)(existing.id)

                // then
                coVerify(exactly = 1) { flights.delete(existing.id) }
            }

        @Test
        fun `fails when the flight does not exist`() =
            runTest {
                // given
                val id = FlightId("missing-flight")
                coEvery { flights.findById(id) } returns null

                // when
                val failure = runCatching { DeleteFlight(flights, attachments, transactions)(id) }.exceptionOrNull()

                // then
                assertThat(failure).isInstanceOf(FlightNotFoundException::class.java)
            }
    }

    @Nested
    inner class DeleteImportedFlight {
        @Test
        fun `deletes the stored track together with the flight`() =
            runTest {
                // given
                val attachment =
                    ch.qscqlmpa.wingtracker.attachments.Attachment(
                        ch.qscqlmpa.wingtracker.attachments
                            .AttachmentId("igc-1"),
                        "flight.igc",
                    )
                val imported = aFlight().copy(igcFile = IgcFile(attachment, "3b7e72f0c1d9"))
                coEvery { flights.findById(imported.id) } returns imported

                // when
                DeleteFlight(flights, attachments, transactions)(imported.id)

                // then
                coVerifyOrder {
                    flights.delete(imported.id)
                    attachments.delete(attachment.id)
                }
            }

        @Test
        fun `deletes no file for a flight that was entered by hand`() =
            runTest {
                // given
                val manual = aFlight()
                coEvery { flights.findById(manual.id) } returns manual

                // when
                DeleteFlight(flights, attachments, transactions)(manual.id)

                // then
                coVerify(exactly = 0) { attachments.delete(any()) }
            }
    }

    @Nested
    inner class FlightsOfWingLookup {
        @Test
        fun `reports flights for a wing that has some`() =
            runTest {
                // given
                coEvery { flights.findAll() } returns
                    ch.qscqlmpa.wingtracker.shared
                        .LoadResult(listOf(aFlight()))

                // when
                val has = FlightsOfWing(flights).hasFlights(wing.id)

                // then
                assertThat(has).isTrue()
            }

        @Test
        fun `reports no flights for a wing that has none`() =
            runTest {
                // given
                val other = aFlight(details = aFlightDetails(wingId = WingId("other-wing")))
                coEvery { flights.findAll() } returns
                    ch.qscqlmpa.wingtracker.shared
                        .LoadResult(listOf(other))

                // when
                val has = FlightsOfWing(flights).hasFlights(wing.id)

                // then
                assertThat(has).isFalse()
            }
    }
}
