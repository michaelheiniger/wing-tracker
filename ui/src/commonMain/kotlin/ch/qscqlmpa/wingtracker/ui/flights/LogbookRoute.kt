package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.igc_drop_hint
import ch.qscqlmpa.wingtracker.ui.resources.igc_pick_folder_title
import ch.qscqlmpa.wingtracker.ui.resources.igc_pick_title
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_pick_title
import ch.qscqlmpa.wingtracker.ui.resources.undo_flight_discarded
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetImportDialog
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetImportPresenter
import ch.qscqlmpa.wingtracker.ui.syride.SyrideImportDialog
import ch.qscqlmpa.wingtracker.ui.syride.SyrideImportPresenter
import ch.qscqlmpa.wingtracker.ui.today
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private sealed interface LogbookDialog {
    // [restored] holds what the pilot had typed when they discarded the dialog and then took it back.
    data class New(
        val restored: FlightFormInput? = null,
    ) : LogbookDialog

    data class Edit(
        val flight: Flight,
    ) : LogbookDialog

    data object Syride : LogbookDialog

    data object Spreadsheet : LogbookDialog

    data class Import(
        val files: List<NewAttachment>,
        // Tracks read from a device are mostly known already, so those are left out of the review.
        val fromDevice: Boolean = false,
    ) : LogbookDialog
}

@Composable
fun LogbookRoute(
    useCases: LogbookUseCases,
    platform: PlatformServices,
    attention: Attention,
    search: LogbookSearch,
    onSearchChange: (LogbookSearch) -> Unit,
    onOpenGear: (GearRef) -> Unit,
    onGoToGear: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val presenter =
        remember {
            LogbookPresenter(
                scope,
                useCases.getLogbook,
                useCases.deleteFlight,
                useCases.readAttachment,
                platform.fileOpener,
                search,
                onSearchChange,
            )
        }
    val pickTitle = stringResource(Res.string.igc_pick_title)
    val pickFolderTitle = stringResource(Res.string.igc_pick_folder_title)
    val spreadsheetPickTitle = stringResource(Res.string.spreadsheet_pick_title)
    val importer =
        remember {
            IgcImportPresenter(
                scope,
                platform.filePicker,
                pickTitle,
                pickFolderTitle,
                useCases.getAppSettings,
                useCases.saveDeviceFolder,
            )
        }
    var dropHover by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<LogbookDialog?>(null) }

    LaunchedEffect(Unit) { presenter.refresh() }

    val dropTarget =
        platform.fileDrop.target(
            extension = IGC_EXTENSION,
            onHover = { dropHover = it },
            onDropped = { files, _ ->
                // Dropping only unrelated files would otherwise seem to do nothing.
                if (files.isEmpty()) importer.dropRejected() else dialog = LogbookDialog.Import(files)
            },
            onFailed = importer::dropFailed,
        )
    val wings =
        presenter.state.visible
            ?.wings
            .orEmpty()
    Box(modifier = Modifier.fillMaxSize().then(dropTarget)) {
        LogbookScreen(
            presenter = presenter,
            importFailure = importer.failure,
            onLogFlight = { dialog = LogbookDialog.New() },
            onEditFlight = { dialog = LogbookDialog.Edit(it) },
            onImportIgc = { importer.choose { files -> dialog = LogbookDialog.Import(files) } },
            onImportDevice = {
                importer.chooseFolder { files ->
                    dialog = LogbookDialog.Import(files, fromDevice = true)
                }
            },
            onImportSyride = { dialog = LogbookDialog.Syride },
            onImportSpreadsheet = { dialog = LogbookDialog.Spreadsheet },
            attention = attention,
            onOpenAttention = onOpenGear,
            onGoToGear = onGoToGear,
        )
        if (dropHover) DropOverlay()
    }

    val undo = LocalUndo.current
    val discardedText = undoText(Res.string.undo_flight_discarded)
    val closeDialog = {
        dialog = null
        presenter.refresh()
    }
    when (val current = dialog) {
        null -> {}

        LogbookDialog.Syride -> {
            val syride =
                remember {
                    SyrideImportPresenter(
                        scope,
                        useCases.prepareSyrideImport,
                        useCases.importSyrideFlights,
                        useCases.saveSyrideUsername,
                        initialUsername = "",
                    )
                }
            // The account used last time is filled in once the settings are read.
            LaunchedEffect(syride) { syride.suggestUsername(useCases.getAppSettings().syrideUsername) }
            SyrideImportDialog(syride, onClose = closeDialog)
        }

        LogbookDialog.Spreadsheet -> {
            val spreadsheet =
                remember {
                    SpreadsheetImportPresenter(
                        scope,
                        platform.filePicker,
                        spreadsheetPickTitle,
                        useCases.openSpreadsheet,
                        useCases.prepareSpreadsheetImport,
                        useCases.importSpreadsheetFlights,
                    )
                }
            // The file is chosen first; cancelling the chooser closes the import without a word.
            LaunchedEffect(spreadsheet) { spreadsheet.choose(onCancelled = { dialog = null }) }
            SpreadsheetImportDialog(spreadsheet, onClose = closeDialog)
        }

        is LogbookDialog.Import -> {
            val batch =
                remember(current) {
                    IgcBatchPresenter(
                        scope,
                        useCases.prepareIgcImport,
                        useCases.importIgcFlight,
                        // The wing flown last, like for a flight entered by hand.
                        checkNotNull(presenter.state.newFlightInput(today()).wingId ?: wings.firstOrNull()?.id),
                        hideAlreadyImported = current.fromDevice,
                    )
                }
            IgcBatchDialog(batch, current.files, wings, onClose = closeDialog)
        }

        else -> {
            val formPresenter =
                remember(current) {
                    val fresh = presenter.state.newFlightInput(today())
                    val editing = (current as? LogbookDialog.Edit)?.flight
                    val start = (current as? LogbookDialog.New)?.restored ?: fresh
                    FlightFormPresenter(
                        scope = scope,
                        logFlight = useCases.logFlight,
                        updateFlight = useCases.updateFlight,
                        initial = editing?.let { FlightFormInput.from(it) } ?: start,
                        editing = editing,
                    )
                }
            FlightDialog(
                presenter = formPresenter,
                wings = wings,
                onClose = {
                    closeDialog()
                    // Only typed-in input is worth taking back; an untouched dialog closes silently.
                    if (current is LogbookDialog.New && formPresenter.hasUnsavedInput) {
                        val typed = formPresenter.input
                        scope.launch {
                            undo.offer(discardedText, onUndo = { dialog = LogbookDialog.New(typed) }, onExpire = {})
                        }
                    }
                },
            )
        }
    }
}

/** Shown while files are held over the logbook, so the pilot knows the drop will be accepted. */
@Composable
private fun DropOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = OVERLAY_ALPHA), MaterialTheme.shapes.large)
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.igc_drop_hint),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val OVERLAY_ALPHA = 0.12f
