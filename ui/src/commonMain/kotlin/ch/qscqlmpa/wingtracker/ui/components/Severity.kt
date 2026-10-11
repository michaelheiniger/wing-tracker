package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import ch.qscqlmpa.wingtracker.gear.maintenance.DueState

/** What a badge tells the pilot. Colour is never the only signal: each severity also has its own icon. */
enum class Severity { Neutral, Success, Warning, Critical }

fun DueState.severity(): Severity =
    when (this) {
        DueState.Due -> Severity.Critical
        DueState.DueSoon -> Severity.Warning
        DueState.Ok -> Severity.Success
    }

/** The icon that goes with a severity, for places that show the meaning without a badge. */
fun Severity.icon(): ImageVector? =
    when (this) {
        Severity.Neutral -> null
        Severity.Success -> Icons.Default.CheckCircle
        Severity.Warning -> Icons.Default.Notifications
        Severity.Critical -> Icons.Default.Warning
    }
