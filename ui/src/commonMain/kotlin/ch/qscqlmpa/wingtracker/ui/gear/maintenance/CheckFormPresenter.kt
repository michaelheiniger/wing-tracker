package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckDateInFutureException
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the check dialog is doing: a new check, changing a recorded one, or finishing a planned one. */
sealed interface CheckFormMode {
    data object Record : CheckFormMode

    data class Edit(
        val check: MaintenanceCheck,
    ) : CheckFormMode

    data class Complete(
        val planned: PlannedCheck,
    ) : CheckFormMode
}

class CheckFormPresenter(
    private val scope: CoroutineScope,
    private val useCases: MaintenanceUseCases,
    private val ref: GearRef,
    val mode: CheckFormMode,
    initial: CheckFormInput,
    private val filePicker: FilePicker,
    private val pickTitle: String,
) {
    val kind get() = ref.kind

    var input by mutableStateOf(initial)
        private set
    var errors by mutableStateOf<Map<CheckFormField, CheckFormError>>(emptyMap())
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    fun change(update: (CheckFormInput) -> CheckFormInput) {
        input = update(input)
    }

    fun addReports() {
        scope.launch {
            try {
                val picked = filePicker.pick(pickTitle, setOf("pdf"))
                input = input.copy(newReports = input.newReports + picked)
            } catch (e: CancellationException) {
                throw e
                // A broken file dialog must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(FailureKind.SaveCheck)
            }
        }
    }

    fun removeKept(report: Attachment) {
        input = input.copy(keptReports = input.keptReports - report)
    }

    fun removeNew(index: Int) {
        input = input.copy(newReports = input.newReports.filterIndexed { i, _ -> i != index })
    }

    fun submit(onSaved: () -> Unit) {
        when (val result = input.validate()) {
            is CheckFormResult.Invalid -> {
                errors = result.errors
            }

            is CheckFormResult.Valid -> {
                errors = emptyMap()
                saving = true
                scope.launch {
                    try {
                        save(result)
                        failure = null
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: CheckDateInFutureException) {
                        errors = mapOf(CheckFormField.Date to CheckFormError.DateInFuture)
                        // Any storage failure must reach the user as a message; it must never crash the window.
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        failure = Failure(FailureKind.SaveCheck, e.message)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    private suspend fun save(valid: CheckFormResult.Valid) {
        when (mode) {
            CheckFormMode.Record -> {
                useCases.recordCheck(ref, valid.details, valid.newFiles)
            }

            is CheckFormMode.Complete -> {
                useCases.recordCheck(ref, valid.details, valid.newFiles, completes = mode.planned.id)
            }

            is CheckFormMode.Edit -> {
                useCases.updateCheck(ref, mode.check.id, valid.details, valid.newFiles, valid.removedFiles)
            }
        }
    }
}
