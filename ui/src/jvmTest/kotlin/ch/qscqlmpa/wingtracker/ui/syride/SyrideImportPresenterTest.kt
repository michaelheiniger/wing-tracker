package ch.qscqlmpa.wingtracker.ui.syride

import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.importing.ImportResult
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.flights.syride.CountSyrideFlightsWithoutTrack
import ch.qscqlmpa.wingtracker.flights.syride.ImportSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.PrepareSyrideImport
import ch.qscqlmpa.wingtracker.flights.syride.SyrideException
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlight
import ch.qscqlmpa.wingtracker.flights.syride.SyrideImportDraft
import ch.qscqlmpa.wingtracker.flights.syride.SyrideProblem
import ch.qscqlmpa.wingtracker.flights.syride.TakeOffTimeUpdate
import ch.qscqlmpa.wingtracker.flights.syride.UpdateSyrideTakeOffTimes
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.settings.SaveSyrideUsername
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.flights.importing.WingChoice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

private val BIRDY = SourceWing("Birdy", "Niviuk")
private val HIKO = SourceWing("Hiko P", "Niviuk")
private val NO_BRAND = SourceWing("Test", null)

private fun aFlight(
    id: String,
    wing: SourceWing,
) = SyrideFlight(id, true, null, LocalDate(2026, 9, 26), null, 98.minutes, "Dent De Jaman", wing, null, null, null)

private fun aGroup(
    wing: SourceWing,
    suggested: WingId? = null,
    ids: List<String> = listOf("${wing.name}-1"),
) = WingGroup(wing, ids.map { aFlight(it, wing) }, suggested)

private fun aDraft(vararg groups: WingGroup<SyrideFlight>) =
    SyrideImportDraft("QSCQLMPA", groups.toList(), 0, emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class SyrideImportPresenterTest {
    private val prepare = mockk<PrepareSyrideImport>()
    private val import = mockk<ImportSyrideFlights>()
    private val updateTimes = mockk<UpdateSyrideTakeOffTimes>()
    private val countWithoutTrack = mockk<CountSyrideFlightsWithoutTrack>().also { coEvery { it() } returns 0 }
    private val saveUsername = mockk<SaveSyrideUsername>(relaxUnitFun = true)

    private fun TestScope.presenter(username: String = "QSCQLMPA") =
        SyrideImportPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            prepare,
            import,
            updateTimes,
            countWithoutTrack,
            saveUsername,
            username,
        )

    private fun givenAccount(vararg groups: WingGroup<SyrideFlight>) {
        coEvery { prepare("QSCQLMPA") } returns aDraft(*groups)
    }

    @Nested
    inner class ReadingTheAccount {
        @Test
        fun `reads the account, remembers the name and moves on to the wings`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY))
                val presenter = presenter()

                // when
                presenter.read()

                // then
                assertThat(presenter.stage).isEqualTo(SyrideStage.Wings)
                coVerify(exactly = 1) { saveUsername("QSCQLMPA") }
            }

        @Test
        fun `fills in the remembered account only when nothing was typed yet`() =
            runTest {
                // given
                val presenter = presenter(username = "")

                // when
                presenter.suggestUsername("QSCQLMPA")
                presenter.suggestUsername("other")

                // then
                assertThat(presenter.username).isEqualTo("QSCQLMPA")
            }

        @Test
        fun `explains that the pilot was not found and stays on the account step`() =
            runTest {
                // given
                coEvery { prepare(any()) } throws SyrideException(SyrideProblem.UserNotFound)
                val presenter = presenter()

                // when
                presenter.read()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SyrideUserNotFound)
                assertThat(presenter.stage).isEqualTo(SyrideStage.Account)
                coVerify(exactly = 0) { saveUsername(any()) }
            }

        @Test
        fun `says so when the pages changed or Syride cannot be reached`() =
            runTest {
                // given
                val presenter = presenter()

                // when / then
                coEvery { prepare(any()) } throws SyrideException(SyrideProblem.PagesChanged, "3 of 4")
                presenter.read()
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SyridePagesChanged)

                coEvery { prepare(any()) } throws SyrideException(SyrideProblem.NoConnection)
                presenter.read()
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.NoConnection)

                coEvery { prepare(any()) } throws IOException("offline")
                presenter.read()
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SyrideUnreachable)
            }

        @Test
        fun `stops reading when the pilot cancels and goes back to the account step`() =
            runTest {
                // given
                coEvery { prepare(any()) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.read() }

                // when
                presenter.cancelReading()

                // then
                assertThat(presenter.stage).isEqualTo(SyrideStage.Account)
                assertThat(presenter.failure).isNull()
                assertThat(presenter.canRead).isTrue()
                coVerify(exactly = 0) { saveUsername(any()) }
            }

        @Test
        fun `cannot read without a name`() =
            runTest {
                assertThat(presenter(username = " ").canRead).isFalse()
            }
    }

    @Nested
    inner class ChoosingWings {
        @Test
        fun `preselects the existing wing that matches`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY, suggested = WingId("w-1")))
                val presenter = presenter()

                // when
                presenter.read()

                // then
                assertThat(
                    presenter.mapping.choiceOf(presenter.draft!!.groups.single()),
                ).isEqualTo(WingChoice.Existing(WingId("w-1")))
                assertThat(presenter.allReady).isTrue()
            }

        @Test
        fun `starts a wing without a match as a new wing named like on Syride`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY))
                val presenter = presenter()

                // when
                presenter.read()

                // then
                val choice = presenter.mapping.choiceOf(presenter.draft!!.groups.single()) as WingChoice.New
                assertThat(presenter.mapping.newWings[choice.key]).isEqualTo(NewWingSpec("Birdy", "Niviuk", "Birdy"))
                assertThat(presenter.allReady).isTrue()
            }

        @Test
        fun `holds the import back while a new wing lacks its manufacturer`() =
            runTest {
                // given
                givenAccount(aGroup(NO_BRAND))
                val presenter = presenter()

                // when
                presenter.read()

                // then
                assertThat(presenter.allReady).isFalse()
                assertThat(presenter.importableCount).isZero()
            }

        @Test
        fun `lets a completed new wing be used by another Syride wing`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY), aGroup(HIKO))
                val presenter = presenter()
                presenter.read()
                val (birdy, hiko) = presenter.draft!!.groups
                val shared = presenter.mapping.choiceOf(birdy)

                // when
                presenter.mapping.choose(hiko, shared)

                // then
                assertThat(presenter.mapping.choiceOf(hiko)).isEqualTo(shared)
            }

        @Test
        fun `creates a wing from the dialog and sends the flights to it`() =
            runTest {
                // given
                givenAccount(aGroup(NO_BRAND))
                val presenter = presenter()
                presenter.read()
                val group = presenter.draft!!.groups.single()

                // when
                presenter.mapping.createWingFor(group, NewWingSpec("Test", "Ozone", "Test"))

                // then
                val choice = presenter.mapping.choiceOf(group) as WingChoice.New
                assertThat(presenter.mapping.newWings[choice.key]?.manufacturer).isEqualTo("Ozone")
                assertThat(presenter.allReady).isTrue()
            }

        @Test
        fun `skipping a wing leaves its flights out`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY, ids = listOf("a", "b")), aGroup(HIKO))
                val presenter = presenter()
                presenter.read()

                // when
                presenter.mapping.choose(presenter.draft!!.groups.first(), WingChoice.Skip)

                // then
                assertThat(presenter.importableCount).isEqualTo(1)
                assertThat(presenter.allReady).isTrue()
            }
    }

    @Nested
    inner class Importing {
        @Test
        fun `imports the flights of every wing that is not skipped, each on its target`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY, suggested = WingId("w-1")), aGroup(HIKO), aGroup(NO_BRAND))
                val presenter = presenter()
                presenter.read()
                presenter.mapping.choose(presenter.draft!!.groups[2], WingChoice.Skip)
                val selection = slot<List<Pair<SyrideFlight, WingTarget>>>()
                coEvery { import(capture(selection), any()) } returns ImportResult(2, emptyList(), 0, emptyList())

                // when
                presenter.import {}

                // then
                assertThat(selection.captured.map { it.first.wing }).containsExactly(BIRDY, HIKO)
                assertThat(selection.captured[0].second).isEqualTo(WingTarget.Existing(WingId("w-1")))
                assertThat(selection.captured[1].second).isInstanceOf(WingTarget.New::class.java)
                assertThat(presenter.stage).isEqualTo(SyrideStage.Done)
            }

        @Test
        fun `goes back to the wings with a message when importing fails`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY))
                val presenter = presenter()
                presenter.read()
                coEvery { import(any(), any()) } throws IOException("Disk full")

                // when
                presenter.import {}

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.ImportSyride)
                assertThat(presenter.stage).isEqualTo(SyrideStage.Wings)
            }
    }

    @Nested
    inner class CountingTracksToAdd {
        @Test
        fun `counts the Syride flights still without a track once the import is done`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY, suggested = WingId("w-1")))
                val presenter = presenter()
                presenter.read()
                coEvery { import(any(), any()) } returns ImportResult(2, emptyList(), 0, emptyList())
                coEvery { countWithoutTrack() } returns 184

                // when
                presenter.import {}

                // then
                assertThat(presenter.withoutTrack).isEqualTo(184)
            }

        @Test
        fun `still reports the import as done when the flights cannot be counted`() =
            runTest {
                // given
                givenAccount(aGroup(BIRDY, suggested = WingId("w-1")))
                val presenter = presenter()
                presenter.read()
                coEvery { import(any(), any()) } returns ImportResult(2, emptyList(), 0, emptyList())
                coEvery { countWithoutTrack() } throws IOException("Disk full")

                // when
                presenter.import {}

                // then
                assertThat(presenter.stage).isEqualTo(SyrideStage.Done)
                assertThat(presenter.withoutTrack).isEqualTo(0)
                assertThat(presenter.failure).isNull()
            }
    }

    @Nested
    inner class UpdatingTakeOffTimes {
        private val update = TakeOffTimeUpdate(FlightId("flight-1"), LocalDate(2026, 9, 26), null, LocalTime(13, 43))

        private fun TestScope.readWithUpdates(): SyrideImportPresenter {
            coEvery { prepare("QSCQLMPA") } returns aDraft().copy(takeOffTimeUpdates = listOf(update))
            return presenter().also { it.read() }
        }

        @Test
        fun `updates the flights the account offered and reports how many`() =
            runTest {
                // given
                coEvery { updateTimes(listOf(update)) } returns 1
                val presenter = readWithUpdates()

                // when
                presenter.updateTakeOffTimes()

                // then
                assertThat(presenter.updatedTimes).isEqualTo(1)
                assertThat(presenter.updatingTimes).isFalse()
            }

        @Test
        fun `reports a failure and stays ready to try again`() =
            runTest {
                // given
                coEvery { updateTimes(any()) } throws IOException("Disk full")
                val presenter = readWithUpdates()

                // when
                presenter.updateTakeOffTimes()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.UpdateTakeOffTimes)
                assertThat(presenter.updatedTimes).isNull()
                assertThat(presenter.updatingTimes).isFalse()
            }
    }
}
