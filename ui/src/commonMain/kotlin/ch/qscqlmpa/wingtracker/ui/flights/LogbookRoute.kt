package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightFilter
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.ui.airtime.AirtimeExportDialog
import ch.qscqlmpa.wingtracker.ui.airtime.AirtimeExportPresenter
import ch.qscqlmpa.wingtracker.ui.airtime.AirtimeExportUseCases
import ch.qscqlmpa.wingtracker.ui.flights.detail.FlightDetailActions
import ch.qscqlmpa.wingtracker.ui.flights.detail.FlightDetailScreen
import ch.qscqlmpa.wingtracker.ui.flights.detail.FlightTrackPresenter
import ch.qscqlmpa.wingtracker.ui.flights.wizard.ImportSource
import ch.qscqlmpa.wingtracker.ui.flights.wizard.ImportWizard
import ch.qscqlmpa.wingtracker.ui.flights.wizard.ImportWizardPresenter
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.igc_drop_hint
import ch.qscqlmpa.wingtracker.ui.resources.igc_pick_folder_title
import ch.qscqlmpa.wingtracker.ui.resources.igc_pick_title
import ch.qscqlmpa.wingtracker.ui.resources.spreadsheet_pick_title
import ch.qscqlmpa.wingtracker.ui.resources.undo_flight_discarded
import ch.qscqlmpa.wingtracker.ui.spreadsheet.SpreadsheetImportPresenter
import ch.qscqlmpa.wingtracker.ui.syride.SyrideImportPresenter
import ch.qscqlmpa.wingtracker.ui.today
import ch.qscqlmpa.wingtracker.ui.undo.LocalUndo
import ch.qscqlmpa.wingtracker.ui.undo.undoText
import kotlinx.coroutines.CoroutineScope
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

    // [dropped] holds the tracks dropped on the logbook, which skip the choice of source.
    data class Import(
        val dropped: List<NewAttachment>? = null,
    ) : LogbookDialog

    data object ExportToAirtime : LogbookDialog
}

@Composable
fun LogbookRoute(
    useCases: LogbookUseCases,
    platform: PlatformServices,
    // Filled by the shell, which owns what needs attention; the logbook only gives it a place.
    attentionPanel: @Composable () -> Unit,
    search: LogbookSearch,
    onSearchChange: (LogbookSearch) -> Unit,
    onOpenStatistics: (FlightFilter) -> Unit,
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
                useCases.forgetFlightExport,
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
                useCases.getDeviceSettings,
                useCases.saveDeviceFolder,
            )
        }
    var dropHover by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<LogbookDialog?>(null) }
    // The flight shown on its own page instead of the list; the list keeps its search and scroll position meanwhile.
    var openFlight by remember { mutableStateOf<FlightId?>(null) }
    val listState = rememberLazyListState()
    val trackPresenter = remember { FlightTrackPresenter(useCases.readFlightTrack, useCases.readGroundProfile) }

    LaunchedEffect(Unit) { presenter.refresh() }

    val dropTarget =
        platform.fileDrop.target(
            extension = IGC_EXTENSION,
            onHover = { dropHover = it },
            onDropped = { files, _ ->
                // Dropping only unrelated files would otherwise seem to do nothing.
                if (files.isEmpty()) importer.dropRejected() else dialog = LogbookDialog.Import(dropped = files)
            },
            onFailed = importer::dropFailed,
        )
    val wings =
        presenter.state.visible
            ?.wings
            .orEmpty()
    val shownFlight = openFlight?.let { presenter.state.flight(it) }
    Box(modifier = Modifier.fillMaxSize().then(dropTarget)) {
        if (shownFlight == null) {
            LogbookScreen(
                presenter = presenter,
                importFailure = importer.failure,
                onLogFlight = { dialog = LogbookDialog.New() },
                onOpenFlight = { openFlight = it.id },
                onImport = { dialog = LogbookDialog.Import() },
                onExportToAirtime = { dialog = LogbookDialog.ExportToAirtime },
                onOpenStatistics = { onOpenStatistics(presenter.state.search.toFilter()) },
                attentionPanel = attentionPanel,
                onGoToGear = onGoToGear,
                listState = listState,
            )
        } else {
            FlightPage(
                presenter,
                trackPresenter,
                useCases,
                shownFlight,
                onOpen = { openFlight = it },
                onEdit = { dialog = LogbookDialog.Edit(shownFlight) },
            )
        }
        if (dropHover) DropOverlay()
    }

    val closeDialog = {
        dialog = null
        presenter.refresh()
    }
    when (val current = dialog) {
        null -> {}

        is LogbookDialog.Import -> {
            val wizard =
                remember(current) {
                    importWizard(
                        useCases,
                        platform,
                        scope,
                        importer,
                        spreadsheetPickTitle,
                        // The wing flown last, like for a flight entered by hand.
                        defaultWing = {
                            checkNotNull(presenter.state.newFlightInput(today()).wingId ?: wings.firstOrNull()?.id)
                        },
                        onSaved = presenter::refresh,
                        onClose = closeDialog,
                    ).also { wizard -> current.dropped?.let { wizard.openTracks(it, ImportSource.IgcFiles) } }
                }
            ImportWizard(wizard, wings)
        }

        LogbookDialog.ExportToAirtime -> {
            AirtimeExport(useCases.airtimeExport, useCases, scope, onClose = closeDialog)
        }

        else -> {
            FlightFormHost(
                current,
                presenter,
                useCases,
                wings,
                onClose = closeDialog,
                onRestore = { dialog = LogbookDialog.New(it) },
            )
        }
    }
}

@Composable
private fun AirtimeExport(
    airtime: AirtimeExportUseCases,
    useCases: LogbookUseCases,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val presenter =
        remember {
            AirtimeExportPresenter(scope, airtime.prepare, airtime.export, airtime.saveNick, airtime.token, "").also {
                it.start()
                scope.launch { it.suggestNick(useCases.getLogbookSettings().airtimeNick) }
            }
        }
    AirtimeExportDialog(presenter, airtime.pages, onClose)
}

/** The form for a new or an edited flight; [onRestore] reopens a new flight the pilot discarded and took back. */
@Composable
private fun FlightFormHost(
    current: LogbookDialog,
    presenter: LogbookPresenter,
    useCases: LogbookUseCases,
    wings: List<Wing>,
    onClose: () -> Unit,
    onRestore: (FlightFormInput) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val undo = LocalUndo.current
    val discardedText = undoText(Res.string.undo_flight_discarded)
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
            onClose()
            // Only typed-in input is worth taking back; an untouched dialog closes silently.
            if (current is LogbookDialog.New && formPresenter.hasUnsavedInput) {
                val typed = formPresenter.input
                scope.launch { undo.offer(discardedText, onUndo = { onRestore(typed) }, onExpire = {}) }
            }
        },
    )
}

private fun importWizard(
    useCases: LogbookUseCases,
    platform: PlatformServices,
    scope: CoroutineScope,
    importer: IgcImportPresenter,
    spreadsheetPickTitle: String,
    defaultWing: () -> WingId,
    onSaved: () -> Unit,
    onClose: () -> Unit,
) = ImportWizardPresenter(
    importer,
    newSyride = {
        SyrideImportPresenter(
            scope,
            useCases.prepareSyrideImport,
            useCases.importSyrideFlights,
            useCases.updateSyrideTakeOffTimes,
            useCases.completeSyrideFlights,
            useCases.countSyrideFlightsWithoutTrack,
            useCases.saveSyrideUsername,
            initialUsername = "",
        ).also { syride ->
            // The account used last time is filled in once the settings are read.
            scope.launch { syride.suggestUsername(useCases.getLogbookSettings().syrideUsername) }
        }
    },
    newSpreadsheet = {
        SpreadsheetImportPresenter(
            scope,
            platform.filePicker,
            spreadsheetPickTitle,
            useCases.openSpreadsheet,
            useCases.prepareSpreadsheetImport,
            useCases.importSpreadsheetFlights,
        )
    },
    newTracks = { fromDevice ->
        IgcBatchPresenter(
            scope,
            useCases.prepareIgcImport,
            useCases.importIgcFlight,
            useCases.attachIgcTrack,
            defaultWing(),
            hideAlreadyImported = fromDevice,
        )
    },
    onSaved = onSaved,
    onClose = onClose,
)

/** One flight on its own page; [onOpen] with null goes back to the list. */
@Composable
private fun FlightPage(
    presenter: LogbookPresenter,
    track: FlightTrackPresenter,
    useCases: LogbookUseCases,
    flight: Flight,
    onOpen: (FlightId?) -> Unit,
    onEdit: () -> Unit,
) {
    var deleting by remember { mutableStateOf(false) }
    val wingName = presenter.state.visible.wingNameOf(flight)
    // Read again when the pilot steps to another flight or attaches a track to this one.
    LaunchedEffect(flight.id, flight.igcFile) { track.load(flight) }
    val syridePage by produceState<String?>(null, flight.id) { value = useCases.getSyrideFlightPage(flight) }
    val uriHandler = LocalUriHandler.current
    val airtimePage = useCases.airtimeExport.pages.flightPage(flight)
    var forgettingExport by remember { mutableStateOf(false) }
    FlightDetailScreen(
        flight = flight,
        wingName = wingName,
        neighbours = presenter.state.neighboursOf(flight.id),
        track = track.state,
        readMapTile = useCases.readMapTile,
        failure = presenter.state.failure,
        actions =
            FlightDetailActions(
                onBack = { onOpen(null) },
                onOpen = { onOpen(it.id) },
                onEdit = onEdit,
                onOpenTrack = { presenter.openTrack(flight) },
                onDelete = { deleting = true },
                onOpenSyride = syridePage?.let { page -> { uriHandler.openUri(page) } },
                onOpenSyrideSite = useCases.getSyrideSitePage(flight)?.let { page -> { uriHandler.openUri(page) } },
                onOpenAirtime = airtimePage?.let { page -> { uriHandler.openUri(page) } },
                onForgetAirtimeExport = { forgettingExport = true },
            ),
    )
    if (forgettingExport) {
        ForgetExportDialog(flight, wingName, presenter, onDismiss = { forgettingExport = false })
    }
    if (deleting) {
        DeleteFlightDialog(
            flight,
            wingName,
            presenter,
            // A deleted flight has no page any more; Undo brings it back in the list.
            onDeleted = { onOpen(null) },
            onDismiss = { deleting = false },
        )
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
