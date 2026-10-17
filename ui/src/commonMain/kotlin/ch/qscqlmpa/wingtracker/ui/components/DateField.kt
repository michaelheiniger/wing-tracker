package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import ch.qscqlmpa.wingtracker.ui.parseDate
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.date_clear
import ch.qscqlmpa.wingtracker.ui.resources.date_ok
import ch.qscqlmpa.wingtracker.ui.resources.date_pick
import ch.qscqlmpa.wingtracker.ui.resources.date_placeholder
import ch.qscqlmpa.wingtracker.ui.resources.error_date
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Text field for a date, typed day first or chosen in a calendar. It holds the text, not a date, so the form can
 * report a mistyped day on save instead of silently keeping an older value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Only judged once the pilot leaves the field, so a half-typed date is not flagged on every keystroke.
    var unreadable by remember { mutableStateOf(false) }
    val shownError = errorText ?: stringResource(Res.string.error_date).takeIf { unreadable }

    OutlinedTextField(
        value = value,
        onValueChange = {
            unreadable = false
            onValueChange(it)
        },
        label = { Text(label) },
        placeholder = { Text(stringResource(Res.string.date_placeholder)) },
        isError = shownError != null,
        supportingText = { shownError?.let { Text(it) } },
        singleLine = true,
        trailingIcon = {
            Row {
                if (clearable && value.isNotEmpty()) {
                    IconButton(onClick = {
                        unreadable = false
                        onValueChange("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.date_clear))
                    }
                }
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.date_pick))
                }
            }
        },
        modifier =
            modifier.onFocusChanged { state ->
                if (focused && !state.isFocused) {
                    val date = parseDate(value)
                    unreadable = value.isNotBlank() && date == null
                    // Shown the same way everywhere, whichever separator the pilot typed.
                    if (date != null && date.toDisplay() != value) onValueChange(date.toDisplay())
                }
                focused = state.isFocused
            },
    )

    if (open) {
        // The picker works in UTC milliseconds; UTC on both sides keeps the chosen day from shifting.
        val state = rememberDatePickerState(initialSelectedDateMillis = parseDate(value)?.toUtcMillis())
        AppDatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            unreadable = false
                            onValueChange(it.toUtcDate().toDisplay())
                        }
                        open = false
                    },
                ) { Text(stringResource(Res.string.date_ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { open = false },
                ) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
