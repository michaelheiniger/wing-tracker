package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.action_cancel
import ch.qscqlmpa.wingtracker.ui.resources.date_clear
import ch.qscqlmpa.wingtracker.ui.resources.date_ok
import ch.qscqlmpa.wingtracker.ui.resources.date_pick
import ch.qscqlmpa.wingtracker.ui.toDisplay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Read-only text field that opens a calendar, so dates can never be mistyped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value?.toDisplay().orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = errorText != null,
        supportingText = { errorText?.let { Text(it) } },
        singleLine = true,
        trailingIcon = {
            Row {
                if (clearable && value != null) {
                    IconButton(onClick = { onValueChange(null) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.date_clear))
                    }
                }
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.date_pick))
                }
            }
        },
        modifier = modifier,
    )

    if (open) {
        // The picker works in UTC milliseconds; UTC on both sides keeps the chosen day from shifting.
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onValueChange(it.toUtcDate()) }
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
