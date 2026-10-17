package ch.qscqlmpa.wingtracker.ui.flights.wizard

import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.ui.flights.IgcBatchPresenter
import ch.qscqlmpa.wingtracker.ui.flights.IgcBatchRow
import ch.qscqlmpa.wingtracker.ui.flights.IgcImportPresenter
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetImportPresenter
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetStage
import ch.qscqlmpa.wingtracker.ui.syride.SyrideImportPresenter
import ch.qscqlmpa.wingtracker.ui.syride.SyrideStage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private val TRACKS =
    listOf(
        NewAttachment("3271596.igc", byteArrayOf(65, 88)),
        NewAttachment("20260926/114312.IGC", byteArrayOf(65, 89)),
    )

class ImportWizardPresenterTest {
    private val trackFiles = mockk<IgcImportPresenter>(relaxed = true)
    private val syride =
        mockk<SyrideImportPresenter>(relaxed = true).also {
            every { it.stage } returns SyrideStage.Account
            every { it.updatedTimes } returns null
            every { it.updatingTimes } returns false
            every { it.withoutTrack } returns 0
        }
    private val spreadsheet =
        mockk<SpreadsheetImportPresenter>(relaxed = true).also {
            every { it.stage } returns SpreadsheetStage.File
        }
    private val tracks =
        mockk<IgcBatchPresenter>(relaxed = true).also {
            every { it.rows } returns emptyList()
            every { it.importing } returns false
            every { it.importedCount } returns 0
        }
    private var hidesKnownTracks: Boolean? = null
    private var closed = false
    private var savedCount = 0

    private fun wizard() =
        ImportWizardPresenter(
            trackFiles,
            newSyride = { syride },
            newSpreadsheet = { spreadsheet },
            newTracks = { fromDevice ->
                hidesKnownTracks = fromDevice
                tracks
            },
            onSaved = { savedCount++ },
            onClose = { closed = true },
        )

    private fun reviewing(): ImportWizardPresenter = wizard().also { it.openTracks(TRACKS, ImportSource.IgcFiles) }

    private fun aRow() = IgcBatchRow(TRACKS.first(), draft = null, problem = null)

    @Nested
    inner class ChoosingTheSource {
        @Test
        fun `starts on the source step before the steps of a source are known`() {
            // given
            val wizard = wizard()

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Source)
            assertThat(wizard.steps).containsExactly(ImportStep.Source, ImportStep.Details, ImportStep.Done)
        }

        @Test
        fun `shows the steps of Syride once it is chosen`() {
            // given
            val wizard = wizard()

            // when
            wizard.chooseSyride()

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Account)
            assertThat(wizard.steps)
                .containsExactly(ImportStep.Source, ImportStep.Account, ImportStep.Wings, ImportStep.Done)
        }

        @Test
        fun `opens the review of the chosen track files`() {
            // given
            every { trackFiles.choose(any()) } answers { firstArg<(List<NewAttachment>) -> Unit>()(TRACKS) }
            val wizard = wizard()

            // when
            wizard.chooseTracks(ImportSource.IgcFiles)

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Tracks)
            assertThat(wizard.flow?.source).isEqualTo(ImportSource.IgcFiles)
            assertThat(hidesKnownTracks).isFalse()
            verify(exactly = 1) { tracks.load(TRACKS) }
        }

        @Test
        fun `leaves out tracks imported before when reading a device`() {
            // given
            every { trackFiles.chooseFolder(any()) } answers { firstArg<(List<NewAttachment>) -> Unit>()(TRACKS) }
            val wizard = wizard()

            // when
            wizard.chooseTracks(ImportSource.Device)

            // then
            assertThat(wizard.flow?.source).isEqualTo(ImportSource.Device)
            assertThat(hidesKnownTracks).isTrue()
        }

        @Test
        fun `stays on the source step when no track file was chosen`() {
            // given
            val wizard = wizard()

            // when
            wizard.chooseTracks(ImportSource.IgcFiles)

            // then
            assertThat(wizard.flow).isNull()
        }

        @Test
        fun `returns to the sources when the spreadsheet chooser is closed`() {
            // given
            every { spreadsheet.choose(any()) } answers { firstArg<() -> Unit>()() }
            val wizard = wizard()

            // when
            wizard.chooseSpreadsheet()

            // then
            assertThat(wizard.flow).isNull()
        }
    }

    @Nested
    inner class OpeningDroppedTracks {
        @Test
        fun `goes straight to the review and shows the tracks as files`() {
            // when
            val wizard = reviewing()

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Tracks)
            assertThat(wizard.steps).containsExactly(ImportStep.Source, ImportStep.Tracks, ImportStep.Done)
            verify(exactly = 1) { tracks.load(TRACKS) }
        }

        @Test
        fun `clears a failure of an earlier choice of files`() {
            // when
            reviewing()

            // then
            verify(exactly = 1) { trackFiles.dismissFailure() }
        }
    }

    @Nested
    inner class GoingBack {
        @Test
        fun `returns from the first step of a source to the sources`() {
            // given
            val wizard = wizard().also { it.chooseSyride() }

            // when
            wizard.back()

            // then
            assertThat(wizard.flow).isNull()
        }

        @Test
        fun `is not offered from a later step, which has its own way back`() {
            // given
            every { syride.stage } returns SyrideStage.Wings
            val wizard = wizard().also { it.chooseSyride() }

            // then
            assertThat(wizard.canGoBack).isFalse()
        }

        @Test
        fun `is not offered once something is saved`() {
            // given
            every { syride.updatedTimes } returns 3
            val wizard = wizard().also { it.chooseSyride() }

            // when
            wizard.back()

            // then
            assertThat(wizard.canGoBack).isFalse()
            assertThat(wizard.flow).isNotNull()
        }

        @Test
        fun `is not offered after some tracks were saved and others failed`() {
            // given
            every { tracks.importedCount } returns 2
            val wizard = reviewing()

            // then
            assertThat(wizard.canGoBack).isFalse()
        }

        @Test
        fun `is offered from the columns of a spreadsheet, its first question`() {
            // given
            every { spreadsheet.stage } returns SpreadsheetStage.Columns
            val wizard = wizard().also { it.chooseSpreadsheet() }

            // then
            assertThat(wizard.canGoBack).isTrue()
        }
    }

    @Nested
    inner class ImportingTracks {
        @Test
        fun `shows what was done once every track is saved`() {
            // given
            every { tracks.import(any()) } answers { firstArg<() -> Unit>()() }
            val wizard = reviewing()

            // when
            wizard.importTracks()

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Done)
        }

        @Test
        fun `stays on the review while some tracks failed`() {
            // given
            val wizard = reviewing()

            // when
            wizard.importTracks()

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Tracks)
        }
    }

    @Nested
    inner class Cancelling {
        @Test
        fun `closes right away when no choice would be lost`() {
            // given
            val wizard = wizard().also { it.chooseSyride() }

            // when
            wizard.cancel()

            // then
            assertThat(closed).isTrue()
            assertThat(wizard.confirmingCancel).isFalse()
        }

        @Test
        fun `asks before throwing away the review of tracks`() {
            // given
            every { tracks.rows } returns listOf(aRow())
            val wizard = reviewing()

            // when
            wizard.cancel()

            // then
            assertThat(closed).isFalse()
            assertThat(wizard.confirmingCancel).isTrue()
        }

        @Test
        fun `asks before throwing away the wings chosen for Syride flights`() {
            // given
            every { syride.stage } returns SyrideStage.Wings
            val wizard = wizard().also { it.chooseSyride() }

            // when
            wizard.cancel()

            // then
            assertThat(wizard.confirmingCancel).isTrue()
        }

        @Test
        fun `closes once the pilot confirms`() {
            // given
            every { tracks.rows } returns listOf(aRow())
            val wizard = reviewing().also { it.cancel() }

            // when
            wizard.confirmCancel()

            // then
            assertThat(closed).isTrue()
            assertThat(wizard.confirmingCancel).isFalse()
        }

        @Test
        fun `keeps the review when the pilot changes their mind`() {
            // given
            every { tracks.rows } returns listOf(aRow())
            val wizard = reviewing().also { it.cancel() }

            // when
            wizard.keepImporting()

            // then
            assertThat(closed).isFalse()
            assertThat(wizard.confirmingCancel).isFalse()
        }

        @Test
        fun `waits while tracks are being saved`() {
            // given
            every { tracks.importing } returns true
            every { tracks.rows } returns listOf(aRow())
            val wizard = reviewing()

            // when
            wizard.cancel()

            // then
            assertThat(closed).isFalse()
            assertThat(wizard.confirmingCancel).isFalse()
        }

        @Test
        fun `closes without asking once the import is done`() {
            // given
            every { tracks.import(any()) } answers { firstArg<() -> Unit>()() }
            every { tracks.rows } returns listOf(aRow())
            val wizard = reviewing().also { it.importTracks() }

            // when
            wizard.cancel()

            // then
            assertThat(closed).isTrue()
        }
    }

    @Nested
    inner class AddingTheTracksOfSyrideFlights {
        private fun afterSyride(): ImportWizardPresenter {
            every { syride.stage } returns SyrideStage.Done
            every { syride.updatedTimes } returns null
            every { syride.withoutTrack } returns 184
            return wizard().also { it.chooseSyride() }
        }

        @Test
        fun `offers the tracks as a step of the Syride import when flights still lack them`() {
            // when
            val wizard = afterSyride()

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Tracks)
            assertThat(wizard.steps).containsExactly(
                ImportStep.Source,
                ImportStep.Account,
                ImportStep.Wings,
                ImportStep.Tracks,
                ImportStep.Done,
            )
        }

        @Test
        fun `ends the Syride import when every flight has its track`() {
            // given
            every { syride.stage } returns SyrideStage.Done
            every { syride.withoutTrack } returns 0

            // when
            val wizard = wizard().also { it.chooseSyride() }

            // then
            assertThat(wizard.step).isEqualTo(ImportStep.Done)
            assertThat(wizard.steps).doesNotContain(ImportStep.Tracks)
        }

        @Test
        fun `reviews the tracks read from the device in the steps of the Syride import`() {
            // given
            every { trackFiles.chooseFolder(any()) } answers { firstArg<(List<NewAttachment>) -> Unit>()(TRACKS) }
            val wizard = afterSyride()

            // when
            wizard.chooseTracks(ImportSource.Device)

            // then
            assertThat(wizard.flow?.source).isEqualTo(ImportSource.Device)
            assertThat(wizard.step).isEqualTo(ImportStep.Tracks)
            assertThat(wizard.steps).contains(ImportStep.Account, ImportStep.Wings)
            assertThat(hidesKnownTracks).isTrue()
        }

        @Test
        fun `goes back from the review to the offer, not to the sources`() {
            // given
            every { trackFiles.choose(any()) } answers { firstArg<(List<NewAttachment>) -> Unit>()(TRACKS) }
            val wizard = afterSyride().also { it.chooseTracks(ImportSource.IgcFiles) }

            // when
            wizard.back()

            // then
            assertThat(wizard.flow).isInstanceOf(ImportFlow.Syride::class.java)
            assertThat(wizard.step).isEqualTo(ImportStep.Tracks)
        }

        @Test
        fun `offers no way back from the offer, since the flights are saved`() {
            // when
            val wizard = afterSyride()

            // then
            assertThat(wizard.canGoBack).isFalse()
        }

        @Test
        fun `lets the logbook show what was saved once the flights are imported`() {
            // given
            val wizard = afterSyride()

            // when
            wizard.imported()

            // then
            assertThat(savedCount).isEqualTo(1)
        }
    }
}
