package ch.qscqlmpa.wingtracker.ui.flights.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.ui.flights.IgcBatchPresenter
import ch.qscqlmpa.wingtracker.ui.flights.IgcImportPresenter
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetImportPresenter
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetStage
import ch.qscqlmpa.wingtracker.ui.syride.SyrideImportPresenter
import ch.qscqlmpa.wingtracker.ui.syride.SyrideStage

/** Where flights come from; each source card of the wizard starts one. */
enum class ImportSource { Syride, IgcFiles, Device, Spreadsheet }

/** The steps the stepper names. [Details] stands for the source's own steps until a source is chosen. */
enum class ImportStep { Source, Details, Account, File, Columns, Wings, Tracks, Done }

/** One import under way, seen by the wizard only through the steps it shows and whether leaving it loses anything. */
sealed interface ImportFlow {
    val source: ImportSource
    val steps: List<ImportStep>
    val step: ImportStep

    // Something is stored; going back to the sources would hide what this import already did.
    val saved: Boolean

    // Choices the pilot made that cancelling would throw away.
    val hasUnsavedChoices: Boolean

    // Saving is under way and must finish, so the wizard cannot be closed.
    val busy: Boolean

    // The pilot has not got past the source's first choices, so picking another source loses little.
    val atStart: Boolean

    // Where Back on the first step leads; null leads to the sources.
    val previous: ImportFlow? get() = null

    class Syride(
        val presenter: SyrideImportPresenter,
    ) : ImportFlow {
        override val source = ImportSource.Syride

        // Flights whose track is still on Syride lead on to adding the tracks, which then attach to them (ADR 0026).
        val offersTracks: Boolean get() = presenter.stage == SyrideStage.Done && presenter.withoutTrack > 0

        override val steps: List<ImportStep>
            get() =
                listOfNotNull(
                    ImportStep.Source,
                    ImportStep.Account,
                    ImportStep.Wings,
                    ImportStep.Tracks.takeIf { offersTracks },
                    ImportStep.Done,
                )
        override val step: ImportStep
            get() =
                when (presenter.stage) {
                    SyrideStage.Account, SyrideStage.Reading -> ImportStep.Account
                    SyrideStage.Wings, SyrideStage.Importing -> ImportStep.Wings
                    SyrideStage.Done -> if (offersTracks) ImportStep.Tracks else ImportStep.Done
                }
        override val saved: Boolean get() = presenter.stage == SyrideStage.Done || presenter.updatedTimes != null
        override val hasUnsavedChoices: Boolean get() = presenter.stage == SyrideStage.Wings
        override val busy: Boolean get() = presenter.stage == SyrideStage.Importing || presenter.updatingTimes
        override val atStart: Boolean get() = presenter.stage == SyrideStage.Account
    }

    class Spreadsheet(
        val presenter: SpreadsheetImportPresenter,
    ) : ImportFlow {
        override val source = ImportSource.Spreadsheet
        override val steps =
            listOf(ImportStep.Source, ImportStep.File, ImportStep.Columns, ImportStep.Wings, ImportStep.Done)
        override val step: ImportStep
            get() =
                when (presenter.stage) {
                    SpreadsheetStage.File -> ImportStep.File
                    SpreadsheetStage.Columns -> ImportStep.Columns
                    SpreadsheetStage.Wings, SpreadsheetStage.Importing -> ImportStep.Wings
                    SpreadsheetStage.Done -> ImportStep.Done
                }
        override val saved: Boolean get() = presenter.stage == SpreadsheetStage.Done
        override val hasUnsavedChoices: Boolean
            get() = presenter.stage == SpreadsheetStage.Columns || presenter.stage == SpreadsheetStage.Wings
        override val busy: Boolean get() = presenter.stage == SpreadsheetStage.Importing

        // The columns are the first thing asked about the chosen file.
        override val atStart: Boolean
            get() = presenter.stage == SpreadsheetStage.File || presenter.stage == SpreadsheetStage.Columns
    }

    class Tracks(
        override val source: ImportSource,
        val presenter: IgcBatchPresenter,
        // The import that offered these tracks; its steps stay in the stepper and Back returns to its offer.
        override val previous: ImportFlow? = null,
    ) : ImportFlow {
        override val steps = previous?.steps ?: listOf(ImportStep.Source, ImportStep.Tracks, ImportStep.Done)

        var done by mutableStateOf(false)
            internal set

        override val step: ImportStep get() = if (done) ImportStep.Done else ImportStep.Tracks

        // Tracks that failed leave the others imported, so a review can hold saved tracks too.
        override val saved: Boolean get() = done || presenter.importedCount > 0
        override val hasUnsavedChoices: Boolean get() = !done && presenter.rows.isNotEmpty()
        override val busy: Boolean get() = presenter.importing
        override val atStart: Boolean get() = !done
    }
}

/**
 * Leads through an import: first the source, then that source's own steps. Each step saves before the next one
 * starts, so going back is offered only while nothing is saved and cancelling asks before choices are lost.
 */
class ImportWizardPresenter(
    private val trackFiles: IgcImportPresenter,
    private val newSyride: () -> SyrideImportPresenter,
    private val newSpreadsheet: () -> SpreadsheetImportPresenter,
    private val newTracks: (fromDevice: Boolean) -> IgcBatchPresenter,
    // The logbook shows what was saved, and the wings a source created are offered for the tracks that follow.
    private val onSaved: () -> Unit,
    private val onClose: () -> Unit,
) {
    var flow by mutableStateOf<ImportFlow?>(null)
        private set

    /** Set while the pilot is asked whether to throw away the choices made. */
    var confirmingCancel by mutableStateOf(false)
        private set

    val steps: List<ImportStep>
        get() = flow?.steps ?: listOf(ImportStep.Source, ImportStep.Details, ImportStep.Done)
    val step: ImportStep get() = flow?.step ?: ImportStep.Source

    /** Only the first steps of a source go back, to the sources or to the offer of tracks. */
    val canGoBack: Boolean
        get() = flow?.let { !it.saved && !it.busy && it.atStart } == true

    /** Why picking tracks failed, such as a folder without any; shown on the source step. */
    val pickFailure get() = trackFiles.failure

    fun chooseSyride() {
        flow = ImportFlow.Syride(newSyride())
    }

    fun chooseSpreadsheet() {
        val spreadsheet = newSpreadsheet()
        flow = ImportFlow.Spreadsheet(spreadsheet)
        // The file chooser is the spreadsheet's first step; closing it returns to the sources.
        spreadsheet.choose(onCancelled = { flow = null })
    }

    /**
     * Picks track files, or a logger's folder for [ImportSource.Device]. Chosen from the offer after an import, the
     * review continues the steps of that import.
     */
    fun chooseTracks(source: ImportSource) {
        val offering = flow
        val open = { files: List<NewAttachment> -> openTracks(files, source, offering) }
        if (source == ImportSource.Device) trackFiles.chooseFolder(open) else trackFiles.choose(open)
    }

    /** Tracks dropped on the logbook skip the source step, which still shows where they came from. */
    fun openTracks(
        files: List<NewAttachment>,
        source: ImportSource,
        offeredBy: ImportFlow? = null,
    ) {
        trackFiles.dismissFailure()
        // A logger holds every track it ever recorded; listing the known ones again would bury the new ones.
        val tracks = newTracks(source == ImportSource.Device)
        tracks.load(files)
        flow = ImportFlow.Tracks(source, tracks, offeredBy)
    }

    fun importTracks() {
        val tracks = flow as? ImportFlow.Tracks ?: return
        tracks.presenter.import(
            onDone = {
                tracks.done = true
                onSaved()
            },
        )
    }

    /** Called once a source saved its flights. */
    fun imported() = onSaved()

    fun back() {
        if (canGoBack) {
            trackFiles.dismissFailure()
            flow = flow?.previous
        }
    }

    fun cancel() {
        val current = flow
        when {
            current?.busy == true -> {}

            current?.hasUnsavedChoices == true -> {
                confirmingCancel = true
            }

            else -> {
                close()
            }
        }
    }

    fun keepImporting() {
        confirmingCancel = false
    }

    fun confirmCancel() {
        confirmingCancel = false
        close()
    }

    /** Closes without asking, for steps where nothing is left to lose. */
    fun close() {
        trackFiles.dismissFailure()
        onClose()
    }
}
