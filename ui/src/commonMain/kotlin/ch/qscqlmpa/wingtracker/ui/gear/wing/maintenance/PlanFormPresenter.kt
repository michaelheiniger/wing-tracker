package ch.qscqlmpa.wingtracker.ui.gear.wing.maintenance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.PlannedDateInPastException
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Drives the plan dialog. [editing] is null when a new check is being planned. */
class PlanFormPresenter(
    private val scope: CoroutineScope,
    private val useCases: MaintenanceUseCases,
    private val wingId: WingId,
    val editing: PlannedCheck?,
    initial: PlanFormInput,
) {
    var input by mutableStateOf(initial)
        private set
    var errors by mutableStateOf<Map<CheckFormField, PlanFormError>>(emptyMap())
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    fun change(update: (PlanFormInput) -> PlanFormInput) {
        input = update(input)
    }

    fun submit(onSaved: () -> Unit) {
        when (val result = input.validate()) {
            is PlanFormResult.Invalid -> {
                errors = result.errors
            }

            is PlanFormResult.Valid -> {
                errors = emptyMap()
                saving = true
                scope.launch {
                    try {
                        if (editing == null) {
                            useCases.planCheck(wingId, result.details)
                        } else {
                            useCases.updatePlannedCheck(wingId, editing.id, result.details)
                        }
                        failure = null
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: PlannedDateInPastException) {
                        errors = mapOf(CheckFormField.Date to PlanFormError.DateInPast)
                        // Any storage failure must reach the user as a message; it must never crash the window.
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        failure = Failure(FailureKind.SavePlan, e.message)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }
}
