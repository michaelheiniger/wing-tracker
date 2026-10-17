package ch.qscqlmpa.wingtracker.ui.airtime

import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeException
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportCandidate
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportDraft
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportResult
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeProblem
import ch.qscqlmpa.wingtracker.flights.airtime.ExportFlightsToAirtime
import ch.qscqlmpa.wingtracker.flights.airtime.PrepareAirtimeExport
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.settings.SaveAirtimeNick
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val NICK = "QSCQLMPA"
private const val TOKEN = "at_7f3c9e21b4d84a6f"

private fun aWing(
    id: String,
    name: String,
) = Wing(
    WingId(id),
    WingDetails(name, "Niviuk", name, size = "S", acquisition = Acquisition(AcquisitionCondition.New)),
)

private val BIRDY = aWing("7a9c3e15-2b6d-4f80-91e4-5d8b0c2a7f36", "Birdy")

private fun aCandidate(
    id: String,
    newGlider: Boolean = false,
) = AirtimeExportCandidate(
    flight =
        Flight(
            FlightId(id),
            FlightDetails(LocalDate(2026, 9, 20), 95.minutes, BIRDY.id, "Niederhorn", takeOffTime = LocalTime(13, 5)),
            Instant.parse("2026-09-20T17:00:00Z"),
        ),
    wing = BIRDY,
    gliderId = if (newGlider) null else "0b6f2c1e-93a4-4d7b-8e55-1c2f7a9d3e60",
    takeOffId = 412,
    newGlider = if (newGlider) BIRDY.id else null,
)

private fun aDraft(vararg candidates: AirtimeExportCandidate) =
    AirtimeExportDraft(
        NICK,
        candidates.toList(),
        newGliders = if (candidates.any { it.newGlider != null }) listOf(BIRDY) else emptyList(),
        skipped = emptyList(),
        alreadyExported = 0,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class AirtimeExportPresenterTest {
    private val prepare = mockk<PrepareAirtimeExport>()
    private val export = mockk<ExportFlightsToAirtime>()
    private val saveNick = mockk<SaveAirtimeNick>(relaxUnitFun = true)
    private val store = mockk<CredentialStore>(relaxUnitFun = true).also { coEvery { it.read(any()) } returns null }

    private fun TestScope.presenter(nick: String = NICK) =
        AirtimeExportPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            prepare,
            export,
            saveNick,
            AirtimeToken(store),
            nick,
        )

    @Nested
    inner class Account {
        @Test
        fun `asks for a token only when none is kept`() =
            runTest {
                // given
                coEvery { store.read("airtime-token") } returns TOKEN
                val presenter = presenter()

                // when
                presenter.start()

                // then
                assertThat(presenter.hasToken).isTrue()
                assertThat(presenter.canRead).isTrue()
            }

        @Test
        fun `cannot read without a nick or a token`() =
            runTest {
                // given
                val presenter = presenter(nick = "")
                presenter.start()

                // when
                presenter.changeToken(TOKEN)

                // then
                assertThat(presenter.canRead).isFalse()
            }
    }

    @Nested
    inner class Reading {
        @Test
        fun `keeps the token and the nick once air-time accepted them, and chooses every flight`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } returns aDraft(aCandidate("flight-1"), aCandidate("flight-2"))
                val presenter = presenter()
                presenter.changeToken(" $TOKEN ")

                // when
                presenter.read()

                // then
                assertThat(presenter.stage).isEqualTo(AirtimeStage.Review)
                assertThat(
                    presenter.selection.chosen,
                ).containsExactlyInAnyOrder(FlightId("flight-1"), FlightId("flight-2"))
                assertThat(presenter.keeping).isEqualTo(TokenKeeping.System)
                coVerify(exactly = 1) { store.write("airtime-token", TOKEN) }
                coVerify(exactly = 1) { saveNick(NICK) }
            }

        @Test
        fun `never keeps a token air-time refused`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } throws AirtimeException(AirtimeProblem.TokenRefused)
                val presenter = presenter()
                presenter.changeToken(TOKEN)

                // when
                presenter.read()

                // then
                assertThat(presenter.stage).isEqualTo(AirtimeStage.Account)
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.AirtimeTokenRefused)
                coVerify(exactly = 0) { store.write(any(), any()) }
            }

        @Test
        fun `forgets a kept token that air-time no longer accepts`() =
            runTest {
                // given
                coEvery { store.read("airtime-token") } returns TOKEN
                coEvery { prepare(NICK, TOKEN) } throws AirtimeException(AirtimeProblem.TokenRefused)
                val presenter = presenter()
                presenter.start()

                // when
                presenter.read()

                // then
                assertThat(presenter.hasToken).isFalse()
                coVerify(exactly = 1) { store.delete("airtime-token") }
            }

        @Test
        fun `says the token is kept for the session when the system has no store`() =
            runTest {
                // given
                coEvery { store.write(any(), any()) } throws CredentialStoreException("secret-tool is not available")
                coEvery { prepare(NICK, TOKEN) } returns aDraft(aCandidate("flight-1"))
                val presenter = presenter()
                presenter.changeToken(TOKEN)

                // when
                presenter.read()

                // then
                assertThat(presenter.keeping).isEqualTo(TokenKeeping.Session)
            }

        @Test
        fun `goes back to the account step when the pilot cancels a read that hangs`() =
            runTest {
                // given
                coEvery { prepare(any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()

                // when
                presenter.cancelReading()

                // then
                assertThat(presenter.stage).isEqualTo(AirtimeStage.Account)
            }
    }

    @Nested
    inner class Review {
        @Test
        fun `asks the colour only of gliders a chosen flight needs`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } returns
                    aDraft(aCandidate("flight-1"), aCandidate("flight-2", newGlider = true))
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()

                // when
                presenter.selection.toggle(FlightId("flight-2"))

                // then
                assertThat(presenter.selection.gliderNeeded).isEmpty()
                assertThat(presenter.canSend).isTrue()
            }

        @Test
        fun `cannot send before every needed glider has a colour`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } returns aDraft(aCandidate("flight-1", newGlider = true))
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()

                // when
                val before = presenter.canSend
                presenter.selection.changeColour(BIRDY.id, "Blue / white")

                // then
                assertThat(before).isFalse()
                assertThat(presenter.canSend).isTrue()
            }

        @Test
        fun `chooses all flights or none`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } returns aDraft(aCandidate("flight-1"), aCandidate("flight-2"))
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()

                // when
                presenter.selection.chooseAll(false)
                val none = presenter.selection.chosen
                presenter.selection.chooseAll(true)

                // then
                assertThat(none).isEmpty()
                assertThat(presenter.selection.chosen).hasSize(2)
            }
    }

    @Nested
    inner class Sending {
        @Test
        fun `sends the chosen flights with the trimmed colours and shows the result`() =
            runTest {
                // given
                val draft = aDraft(aCandidate("flight-1", newGlider = true))
                coEvery { prepare(NICK, TOKEN) } returns draft
                coEvery { export(TOKEN, draft, setOf(FlightId("flight-1")), mapOf(BIRDY.id to "Blue"), any()) } returns
                    AirtimeExportResult(exported = 1, glidersCreated = 1)
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()
                presenter.selection.changeColour(BIRDY.id, " Blue ")

                // when
                presenter.send()

                // then
                assertThat(presenter.stage).isEqualTo(AirtimeStage.Done)
                assertThat(presenter.result).isEqualTo(AirtimeExportResult(1, 1))
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `explains why sending stopped early`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } returns aDraft(aCandidate("flight-1"))
                coEvery { export(any(), any(), any(), any(), any()) } returns
                    AirtimeExportResult(0, 0, AirtimeProblem.NoConnection)
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()

                // when
                presenter.send()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.NoConnection)
            }

        @Test
        fun `stops sending when the pilot asks`() =
            runTest {
                // given
                coEvery { prepare(NICK, TOKEN) } returns aDraft(aCandidate("flight-1"))
                coEvery { export(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter()
                presenter.changeToken(TOKEN)
                presenter.read()
                presenter.send()

                // when
                presenter.stopSending()

                // then
                assertThat(presenter.stage).isEqualTo(AirtimeStage.Done)
                assertThat(presenter.stopped).isTrue()
            }
    }
}
