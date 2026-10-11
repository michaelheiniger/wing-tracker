package ch.qscqlmpa.wingtracker.ui.gear.wing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.wing.DeleteWing
import ch.qscqlmpa.wingtracker.gear.wing.ListWings
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingInUseException
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class WingListState(
    val wings: List<Wing> = emptyList(),
    val unreadable: List<UnreadableRecord> = emptyList(),
    val statuses: Map<WingId, MaintenanceStatus?> = emptyMap(),
    val loading: Boolean = true,
    // Retired wings stay out of the way by default but remain reachable, so history is never hidden for good.
    val showRetired: Boolean = false,
    val failure: Failure? = null,
) {
    val visibleWings: List<Wing> get() = if (showRetired) wings else wings.filter { it.isActive }
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

    fun delete(id: WingId) =
        run(FailureKind.Delete) {
            deleteWing(id)
            reload()
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
        scope.launch {
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
}
