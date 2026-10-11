package ch.qscqlmpa.wingtracker.ui.gear.wing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.gear.wing.DeleteWing
import ch.qscqlmpa.wingtracker.gear.wing.ListWings
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingInUseException
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.undo.UndoText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class WingListState(
    val wings: List<Wing> = emptyList(),
    val unreadable: List<UnreadableRecord> = emptyList(),
    val statuses: Map<GearRef, MaintenanceStatus?> = emptyMap(),
    val loading: Boolean = true,
    // Retired wings stay out of the way by default but remain reachable, so history is never hidden for good.
    val showRetired: Boolean = false,
    val failure: Failure? = null,
    // Deleted wings wait here until the pilot can no longer undo, so Undo only has to show them again.
    val hidden: Set<WingId> = emptySet(),
) {
    val visibleWings: List<Wing> get() =
        (if (showRetired) wings else wings.filter { it.isActive }).filter {
            it.id !in
                hidden
        }
    val retiredCount: Int get() = wings.count { !it.isActive }
}

class WingListPresenter(
    private val scope: CoroutineScope,
    private val listWings: ListWings,
    private val retireWing: RetireWing,
    private val deleteWing: DeleteWing,
    private val getMaintenanceStatuses: GetMaintenanceStatuses,
    private val today: () -> LocalDate,
) {
    var state by mutableStateOf(WingListState())
        private set

    fun refresh() = run(FailureKind.Load) { reload() }

    fun toggleShowRetired() {
        state = state.copy(showRetired = !state.showRetired)
    }

    fun retire(
        id: WingId,
        reason: String?,
    ) = run(FailureKind.Retire) {
        retireWing(id, today(), reason)
        reload()
    }

    /** Hides the wing at once and deletes it for real only when the pilot can no longer undo. */
    fun delete(
        id: WingId,
        undo: UndoCoordinator,
        text: UndoText,
    ) {
        state = state.copy(hidden = state.hidden + id)
        scope.launch {
            undo.offer(
                text,
                onUndo = { unhide(id) },
                onExpire = {
                    perform(FailureKind.Delete) {
                        deleteWing(id)
                        reload()
                    }
                    unhide(id)
                },
            )
        }
    }

    private fun unhide(id: WingId) {
        state = state.copy(hidden = state.hidden - id)
    }

    fun dismissUnreadable() {
        state = state.copy(unreadable = emptyList())
    }

    private suspend fun reload(): WingListState {
        val loaded = listWings()
        return state.copy(
            wings = loaded.items,
            statuses = getMaintenanceStatuses(),
            // A later reload must not re-announce a quarantined file that was already reported.
            unreadable = (state.unreadable + loaded.unreadable).distinct(),
            loading = false,
            failure = null,
        )
    }

    private fun run(
        kind: FailureKind,
        block: suspend () -> WingListState,
    ) {
        scope.launch { perform(kind, block) }
    }

    private suspend fun perform(
        kind: FailureKind,
        block: suspend () -> WingListState,
    ) {
        state =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: WingInUseException) {
                state.copy(loading = false, failure = Failure(FailureKind.DeleteInUse))
                // Any storage failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                state.copy(loading = false, failure = Failure(kind, e.message))
            }
    }
}
