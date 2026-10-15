package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.import_menu
import ch.qscqlmpa.wingtracker.ui.resources.import_menu_device
import ch.qscqlmpa.wingtracker.ui.resources.import_menu_igc
import ch.qscqlmpa.wingtracker.ui.resources.import_menu_needs_wing
import ch.qscqlmpa.wingtracker.ui.resources.import_menu_syride
import org.jetbrains.compose.resources.stringResource

/** Every way to bring flights in, behind one button, so the logbook header stays calm as sources are added. */
@Composable
internal fun ImportMenu(
    hasWings: Boolean,
    onImportIgc: () -> Unit,
    onImportDevice: () -> Unit,
    onImportSyride: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(Res.string.import_menu))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val choose = { action: () -> Unit ->
                expanded = false
                action()
            }
            // A track is logged on a wing, like a flight entered by hand, so these wait until a wing exists.
            NeedsWingItem(stringResource(Res.string.import_menu_igc), hasWings) { choose(onImportIgc) }
            NeedsWingItem(stringResource(Res.string.import_menu_device), hasWings) { choose(onImportDevice) }
            // Syride can create the wings it needs.
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.import_menu_syride)) },
                onClick = { choose(onImportSyride) },
            )
        }
    }
}

@Composable
private fun NeedsWingItem(
    label: String,
    hasWings: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                // Said rather than only greyed out, so the pilot knows how to make it available.
                if (!hasWings) {
                    Text(stringResource(Res.string.import_menu_needs_wing), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        onClick = onClick,
        enabled = hasWings,
    )
}
