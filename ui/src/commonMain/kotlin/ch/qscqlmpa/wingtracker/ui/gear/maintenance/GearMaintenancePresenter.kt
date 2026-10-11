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
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.undo.UndoText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class GearMaintenanceState(
    val data: GearMaintenance? = null,
    val failure: Failure? = null,
    // Deleted checks and cancelled plans wait here until the pilot can no longer undo.
    val hiddenChecks: Set<CheckId> = emptySet(),
    val hiddenPlans: Set<PlannedCheckId> = emptySet(),
) {
    val loading: Boolean get() = data == null && failure == null

    /** The maintenance as the pilot should see it, without what is about to be deleted. */
    val shown: GearMaintenance?
        get() =
            data?.let { loaded ->
                val history = loaded.gear.history
                loaded.copy(
                    gear =
                        loaded.gear.withHistory(
                            history.copy(
                                checks = history.checks.filter { it.id !in hiddenChecks },
                                plannedChecks = history.plannedChecks.filter { it.id !in hiddenPlans },
                            ),
                        ),
                )
            }
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

    /** Hides the check at once and deletes it for real only when the pilot can no longer undo. */
    fun deleteCheck(
        id: CheckId,
        undo: UndoCoordinator,
        text: UndoText,
    ) = withUndo(
        undo,
        text,
        hide = { it.copy(hiddenChecks = it.hiddenChecks + id) },
        show = { it.copy(hiddenChecks = it.hiddenChecks - id) },
        kind = FailureKind.DeleteCheck,
    ) { useCases.deleteCheck(ref, id) }

    fun cancelPlan(
        id: PlannedCheckId,
        undo: UndoCoordinator,
        text: UndoText,
    ) = withUndo(
        undo,
        text,
        hide = { it.copy(hiddenPlans = it.hiddenPlans + id) },
        show = { it.copy(hiddenPlans = it.hiddenPlans - id) },
        kind = FailureKind.CancelPlan,
    ) { useCases.cancelPlannedCheck(ref, id) }

    private fun withUndo(
        undo: UndoCoordinator,
        text: UndoText,
        hide: (GearMaintenanceState) -> GearMaintenanceState,
        show: (GearMaintenanceState) -> GearMaintenanceState,
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
        state = hide(state)
        scope.launch {
            undo.offer(
                text,
                onUndo = { state = show(state) },
                onExpire = {
                    perform(kind, action)
                    state = show(state)
                },
            )
        }
    }

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
        scope.launch { perform(kind, action) }
    }

    private suspend fun perform(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
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
