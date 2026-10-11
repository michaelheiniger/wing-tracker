package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.Flight
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private sealed interface LogbookDialog {
    data object New : LogbookDialog

    data class Edit(
        val flight: Flight,
    ) : LogbookDialog
}

@Composable
fun LogbookRoute(
    useCases: LogbookUseCases,
    onGoToGear: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember { LogbookPresenter(scope, useCases.getLogbook, useCases.deleteFlight) }
    var dialog by remember { mutableStateOf<LogbookDialog?>(null) }

    LaunchedEffect(Unit) { presenter.refresh() }

    LogbookScreen(
        presenter = presenter,
        onLogFlight = { dialog = LogbookDialog.New },
        onEditFlight = { dialog = LogbookDialog.Edit(it) },
        onGoToGear = onGoToGear,
    )

    dialog?.let { current ->
        val formPresenter =
            remember(current) {
                val initial =
                    when (current) {
                        LogbookDialog.New -> {
                            presenter.state.newFlightInput(Clock.System.todayIn(TimeZone.currentSystemDefault()))
                        }

                        is LogbookDialog.Edit -> {
                            FlightFormInput.from(current.flight)
                        }
                    }
                FlightFormPresenter(
                    scope = scope,
                    logFlight = useCases.logFlight,
                    updateFlight = useCases.updateFlight,
                    initial = initial,
                    editing = (current as? LogbookDialog.Edit)?.flight,
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
