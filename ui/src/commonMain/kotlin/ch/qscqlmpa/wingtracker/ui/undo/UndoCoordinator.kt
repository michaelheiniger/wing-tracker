package ch.qscqlmpa.wingtracker.ui.undo

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.undo_action
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the notification says; texts are resolved by the screen because presenters never touch resources. */
data class UndoText(
    val message: String,
    val actionLabel: String,
)

/**
 * Lets the pilot take back a destructive action for a while. The action is only applied once the notification
 * goes away without Undo, so Undo never has to rebuild anything. Only one action waits at a time: starting
 * another applies the earlier one at once, which keeps "what does Undo undo?" unambiguous.
 */
class UndoCoordinator {
    val snackbarHost = SnackbarHostState()

    private var pending: Pending? = null

    private class Pending(
        val onExpire: suspend () -> Unit,
    )

    /**
     * Shows [text] and suspends until the notification is gone. [onUndo] runs when the pilot takes the action
     * back; otherwise [onExpire] applies it. It is also applied when the caller is cancelled, e.g. because the
     * pilot left the screen, so an action is never silently dropped.
     */
    suspend fun offer(
        text: UndoText,
        onUndo: () -> Unit,
        onExpire: suspend () -> Unit,
    ) {
        flush()
        val entry = Pending(onExpire).also { pending = it }
        try {
            val result =
                snackbarHost.showSnackbar(
                    message = text.message,
                    actionLabel = text.actionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed && pending === entry) {
                pending = null
                onUndo()
            }
        } finally {
            withContext(NonCancellable) { apply(entry) }
        }
    }

    /** Applies the waiting action now, e.g. before the application closes. */
    suspend fun flush() {
        pending?.let { apply(it) }
        snackbarHost.currentSnackbarData?.dismiss()
    }

    private suspend fun apply(entry: Pending) {
        if (pending !== entry) return
        pending = null
        entry.onExpire()
    }
}

/** Provided once by the app shell, so any screen can offer Undo without it being passed through every layer. */
val LocalUndo = staticCompositionLocalOf<UndoCoordinator> { error("No UndoCoordinator provided") }

/** Texts for the notification, resolved here because presenters never touch resources. */
@Composable
fun undoText(message: StringResource): UndoText =
    UndoText(stringResource(message), stringResource(Res.string.undo_action))

@Composable
fun undoText(
    message: StringResource,
    vararg args: Any,
): UndoText = UndoText(stringResource(message, *args), stringResource(Res.string.undo_action))
