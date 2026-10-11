package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightBeforeWingInServiceException
import ch.qscqlmpa.wingtracker.flights.FlightDateInFutureException
import ch.qscqlmpa.wingtracker.flights.FlightDetails
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Drives the log/edit dialog. [editing] is null when a new flight is being logged. */
class FlightFormPresenter(
    private val scope: CoroutineScope,
    private val logFlight: LogFlight,
    private val updateFlight: UpdateFlight,
    private val initial: FlightFormInput,
    val editing: Flight? = null,
) {
    var input by mutableStateOf(initial)
        private set
    var errors by mutableStateOf<Map<FlightFormField, FlightFormError>>(emptyMap())
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    private var saved = false

    /** True when the pilot typed something that closing the dialog would now throw away. */
    val hasUnsavedInput: Boolean get() = !saved && input != initial

    fun change(update: (FlightFormInput) -> FlightFormInput) {
        input = update(input)
    }

    fun submit(onSaved: () -> Unit) {
        when (val result = input.validate()) {
            is FlightFormResult.Invalid -> {
                errors = result.errors
            }

            is FlightFormResult.Valid -> {
                errors = emptyMap()
                saving = true
                scope.launch {
                    try {
                        save(result.details)
                        failure = null
                        saved = true
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: FlightDateInFutureException) {
                        errors = mapOf(FlightFormField.Date to FlightFormError.DateInFuture)
                    } catch (_: FlightBeforeWingInServiceException) {
                        errors = mapOf(FlightFormField.Date to FlightFormError.DateBeforeWingInService)
                        // Any storage failure must reach the user as a message; it must never crash the window.
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        failure = Failure(FailureKind.SaveFlight, e.message)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    private suspend fun save(details: FlightDetails) {
        if (editing == null) logFlight(details) else updateFlight(editing.id, details)
    }
}
