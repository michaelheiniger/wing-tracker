package ch.qscqlmpa.wingtracker.ui.gear.maintenance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.gear.maintenance.CheckId
import ch.qscqlmpa.wingtracker.gear.maintenance.GearMaintenance
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheckId
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class GearMaintenanceState(
    val data: GearMaintenance? = null,
    val failure: Failure? = null,
) {
    val loading: Boolean get() = data == null && failure == null
}

class GearMaintenancePresenter(
    private val scope: CoroutineScope,
    private val ref: GearRef,
    private val useCases: MaintenanceUseCases,
    private val fileOpener: FileOpener,
) {
    var state by mutableStateOf(GearMaintenanceState())
        private set

    fun refresh() = run(FailureKind.LoadMaintenance) { }

    fun deleteCheck(id: CheckId) = run(FailureKind.DeleteCheck) { useCases.deleteCheck(ref, id) }

    fun cancelPlan(id: PlannedCheckId) = run(FailureKind.CancelPlan) { useCases.cancelPlannedCheck(ref, id) }

    fun openReport(report: Attachment) {
        scope.launch {
            try {
                fileOpener.open(report.fileName, useCases.readAttachment(report.id))
            } catch (e: CancellationException) {
                throw e
                // A missing file or viewer must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                state = state.copy(failure = Failure(FailureKind.OpenReport, e.message))
            }
        }
    }

    private fun run(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
        scope.launch {
            state =
                try {
                    action()
                    state.copy(data = useCases.getGearMaintenance(ref), failure = null)
                } catch (e: CancellationException) {
                    throw e
                    // Any storage failure must reach the user as a message; it must never crash the window.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    state.copy(failure = Failure(kind, e.message))
                }
        }
    }
}
