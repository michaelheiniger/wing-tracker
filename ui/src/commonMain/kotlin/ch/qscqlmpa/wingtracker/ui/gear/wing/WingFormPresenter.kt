package ch.qscqlmpa.wingtracker.ui.gear.wing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.UpdateWingDetails
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Drives the add/edit form. [editing] is null when a new wing is being registered. */
class WingFormPresenter(
    private val scope: CoroutineScope,
    private val registerWing: RegisterWing,
    private val updateWingDetails: UpdateWingDetails,
    val editing: Wing?,
) {
    var input by mutableStateOf(editing?.let { WingFormInput.from(it) } ?: WingFormInput())
        private set
    var errors by mutableStateOf<Map<WingFormField, FormError>>(emptyMap())
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    fun change(update: (WingFormInput) -> WingFormInput) {
        input = update(input)
    }

    fun submit(onSaved: () -> Unit) {
        when (val result = input.validate()) {
            is WingFormResult.Invalid -> {
                errors = result.errors
            }

            is WingFormResult.Valid -> {
                errors = emptyMap()
                saving = true
                scope.launch {
                    try {
                        save(result.details)
                        failure = null
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
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

    private suspend fun save(details: WingDetails) {
        if (editing == null) registerWing(details) else updateWingDetails(editing.id, details)
    }
}
