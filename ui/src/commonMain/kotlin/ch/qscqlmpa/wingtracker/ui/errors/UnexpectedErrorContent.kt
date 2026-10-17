package ch.qscqlmpa.wingtracker.ui.errors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_body
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_copy
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_logged
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_occurrences
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_ok
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_open_log
import ch.qscqlmpa.wingtracker.ui.resources.unexpected_error_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The title of the window that shows an unexpected error; the platform draws it, so it cannot be inside. */
@Composable
fun unexpectedErrorTitle(): String = stringResource(Res.string.unexpected_error_title)

/**
 * What an unexpected error shows. It fills its own window rather than being a dialog of the main window, so it
 * still shows when that window is what failed. Copying and opening the log are the platform's, so they come in as
 * [onCopy] and [onOpenLog]; without a log, [onOpenLog] is null.
 */
@Composable
fun UnexpectedErrorContent(
    error: UnexpectedError,
    onCopy: () -> Unit,
    onOpenLog: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Its own window, so its own selection: every text can be selected, like in the main window.
    Surface(modifier.fillMaxSize()) {
        SelectionContainer {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(unexpectedErrorTitle(), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.unexpected_error_body))
                // The log also holds what happened before the error, which the trace alone does not tell.
                if (onOpenLog != null) Text(stringResource(Res.string.unexpected_error_logged))
                if (error.occurrences > 1) {
                    Text(
                        pluralStringResource(
                            Res.plurals.unexpected_error_occurrences,
                            error.occurrences,
                            error.occurrences,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StackTrace(error.stackTrace, Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    onOpenLog?.let {
                        TextButton(onClick = it) { Text(stringResource(Res.string.unexpected_error_open_log)) }
                    }
                    TextButton(onClick = onCopy) { Text(stringResource(Res.string.unexpected_error_copy)) }
                    Button(onClick = onDismiss) { Text(stringResource(Res.string.unexpected_error_ok)) }
                }
            }
        }
    }
}

@Composable
private fun StackTrace(
    stackTrace: String,
    modifier: Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
        // Wrapped, so the whole trace reads without scrolling sideways; copying keeps the original lines.
        Box(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stackTrace,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
