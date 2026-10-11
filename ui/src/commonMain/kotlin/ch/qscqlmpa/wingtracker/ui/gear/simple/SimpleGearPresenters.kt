package ch.qscqlmpa.wingtracker.ui.gear.simple

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceStatus
import ch.qscqlmpa.wingtracker.gear.wing.GearInUseException
import ch.qscqlmpa.wingtracker.shared.UnreadableRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class SimpleGearListState(
    val items: List<SimpleGearItem> = emptyList(),
    val unreadable: List<UnreadableRecord> = emptyList(),
    val statuses: Map<GearRef, MaintenanceStatus?> = emptyMap(),
    val loading: Boolean = true,
    // Retired items stay out of the way by default but remain reachable, so history is never hidden for good.
    val showRetired: Boolean = false,
    val failure: Failure? = null,
) {
    val visibleItems: List<SimpleGearItem> get() = if (showRetired) items else items.filter { it.isActive }
    val retiredCount: Int get() = items.count { !it.isActive }
}

class SimpleGearListPresenter(
    private val scope: CoroutineScope,
    private val ops: SimpleGearOps,
    private val getMaintenanceStatuses: GetMaintenanceStatuses,
    private val today: () -> LocalDate,
) {
    var state by mutableStateOf(SimpleGearListState())
        private set

    fun refresh() = run(FailureKind.Load) { reload() }

    fun toggleShowRetired() {
        state = state.copy(showRetired = !state.showRetired)
    }

    fun retire(
        ref: GearRef,
        reason: String?,
    ) = run(FailureKind.Retire) {
        ops.retire(ref, today(), reason)
        reload()
    }

    fun delete(ref: GearRef) =
        run(FailureKind.Delete) {
            ops.delete(ref)
            reload()
        }

    fun dismissUnreadable() {
        state = state.copy(unreadable = emptyList())
    }

    private suspend fun reload(): SimpleGearListState {
        val loaded = ops.list()
        return state.copy(
            items = loaded.items,
            statuses = getMaintenanceStatuses(),
            // A later reload must not re-announce a quarantined file that was already reported.
            unreadable = (state.unreadable + loaded.unreadable).distinct(),
            loading = false,
            failure = null,
        )
    }

    private fun run(
        kind: FailureKind,
        block: suspend () -> SimpleGearListState,
    ) {
        scope.launch {
            state =
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: GearInUseException) {
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

/** Drives the add/edit form. [editing] is null when a new item is being registered. */
class SimpleGearFormPresenter(
    private val scope: CoroutineScope,
    private val ops: SimpleGearOps,
    val editing: SimpleGearItem?,
) {
    var input by mutableStateOf(editing?.let { SimpleGearFormInput.from(it) } ?: SimpleGearFormInput())
        private set
    var errors by mutableStateOf<Map<SimpleGearField, FormError>>(emptyMap())
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    val kind get() = ops.kind

    fun change(update: (SimpleGearFormInput) -> SimpleGearFormInput) {
        input = update(input)
    }

    fun submit(onSaved: () -> Unit) {
        when (val result = input.validate()) {
            is SimpleGearFormResult.Invalid -> {
                errors = result.errors
            }

            is SimpleGearFormResult.Valid -> {
                errors = emptyMap()
                saving = true
                scope.launch {
                    try {
                        if (editing == null) ops.register(result.details) else ops.update(editing.ref, result.details)
                        failure = null
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
                        // Any storage failure must reach the user as a message; it must never crash the window.
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        failure = Failure(FailureKind.Save, e.message)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }
}
