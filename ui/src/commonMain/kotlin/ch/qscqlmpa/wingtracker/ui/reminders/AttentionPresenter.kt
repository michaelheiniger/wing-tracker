package ch.qscqlmpa.wingtracker.ui.reminders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.reminders.Attention
import ch.qscqlmpa.wingtracker.reminders.GetAttention
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class AttentionState(
    val attention: Attention = Attention(emptyList()),
    val failure: Failure? = null,
)

/** Shared by the badge and the logbook panel, so both always show the same count. */
class AttentionPresenter(
    private val scope: CoroutineScope,
    private val getAttention: GetAttention,
) {
    var state by mutableStateOf(AttentionState())
        private set

    fun refresh() {
        scope.launch {
            state =
                try {
                    AttentionState(getAttention())
                } catch (e: CancellationException) {
                    throw e
                    // A broken reminder must never take the window down with it.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    state.copy(failure = Failure(FailureKind.LoadAttention, e.message))
                }
        }
    }
}
