package ch.qscqlmpa.wingtracker.ui.errors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A failure nothing in the app handled, with how often it happened while it waited to be seen. */
data class UnexpectedError(
    val stackTrace: String,
    val occurrences: Int = 1,
)

/**
 * Failures nothing in the app handled, shown to the pilot one at a time. A failure can repeat on every frame, so
 * one that is already waiting is counted instead of queued again. Not thread-safe: platforms report on their UI
 * thread.
 */
class UnexpectedErrors {
    private var waiting: List<UnexpectedError> by mutableStateOf(emptyList())

    /** The error to show now; null when there is none. */
    val current: UnexpectedError? get() = waiting.firstOrNull()

    /** Returns the waiting entry the error ended up in, so a caller can tell a new failure from a repeat. */
    fun report(error: Throwable): UnexpectedError {
        val stackTrace = error.stackTraceToString()
        val index = waiting.indexOfFirst { it.stackTrace == stackTrace }
        val entry =
            if (index <
                0
            ) {
                UnexpectedError(stackTrace)
            } else {
                waiting[index].copy(occurrences = waiting[index].occurrences + 1)
            }
        waiting = if (index < 0) waiting + entry else waiting.toMutableList().also { it[index] = entry }
        return entry
    }

    /** Closes the current error, which brings up the next one. */
    fun dismiss() {
        waiting = waiting.drop(1)
    }
}
