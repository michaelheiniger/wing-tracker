package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.ui.formatHoursAndMinutes
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.igc_pick_title
import ch.qscqlmpa.wingtracker.ui.today
import org.jetbrains.compose.resources.stringResource

private sealed interface LogbookDialog {
    data object New : LogbookDialog

    data class Edit(
        val flight: Flight,
    ) : LogbookDialog

    data class Import(
        val prepared: PreparedIgc,
    ) : LogbookDialog
}

@Composable
fun LogbookRoute(
    useCases: LogbookUseCases,
    platform: PlatformServices,
    attention: Attention,
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
            )
        }
    val pickTitle = stringResource(Res.string.igc_pick_title)
    val importer = remember { IgcImportPresenter(scope, useCases.prepareIgcImport, platform.filePicker, pickTitle) }
    var dialog by remember { mutableStateOf<LogbookDialog?>(null) }

    LaunchedEffect(Unit) { presenter.refresh() }

    LogbookScreen(
        presenter = presenter,
        importFailure = importer.failure,
        onLogFlight = { dialog = LogbookDialog.New },
        onEditFlight = { dialog = LogbookDialog.Edit(it) },
        onImportIgc = { importer.start { prepared -> dialog = LogbookDialog.Import(prepared) } },
        attention = attention,
        onOpenAttention = onOpenGear,
        onGoToGear = onGoToGear,
    )

    dialog?.let { current ->
        val formPresenter =
            remember(current) {
                val fresh = presenter.state.newFlightInput(today())
                FlightFormPresenter(
                    scope = scope,
                    logFlight = useCases.logFlight,
                    updateFlight = useCases.updateFlight,
                    importIgcFlight = useCases.importIgcFlight,
                    initial =
                        when (current) {
                            LogbookDialog.New -> {
                                fresh
                            }

                            is LogbookDialog.Edit -> {
                                FlightFormInput.from(current.flight)
                            }

                            // Date and duration come from the track, the wing is the one flown last; all stay editable.
                            is LogbookDialog.Import -> {
                                fresh.copy(
                                    date = current.prepared.draft.date,
                                    duration =
                                        current.prepared.draft.duration
                                            ?.let {
                                                formatHoursAndMinutes(
                                                    it.inWholeMinutes,
                                                )
                                            }.orEmpty(),
                                )
                            }
                        },
                    editing = (current as? LogbookDialog.Edit)?.flight,
                    igc = (current as? LogbookDialog.Import)?.prepared,
                )
            }
        FlightDialog(
            presenter = formPresenter,
            wings =
                presenter.state.logbook
                    ?.wings
                    .orEmpty(),
            onClose = {
                dialog = null
                presenter.refresh()
            },
        )
    }
}
