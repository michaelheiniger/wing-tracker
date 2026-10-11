package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.theme.extraColors

/** A non-clickable status label (a chip would suggest it can be pressed). */
@Composable
fun StatusBadge(
    text: String,
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    val (container, content, icon) = severity.look()
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private data class BadgeLook(
    val container: Color,
    val content: Color,
    val icon: ImageVector?,
)

@Composable
private fun Severity.look(): BadgeLook {
    val scheme = MaterialTheme.colorScheme
    val status = MaterialTheme.extraColors
    return when (this) {
        Severity.Neutral -> BadgeLook(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, icon())
        Severity.Success -> BadgeLook(status.successContainer, status.onSuccessContainer, icon())
        Severity.Warning -> BadgeLook(status.warningContainer, status.onWarningContainer, icon())
        Severity.Critical -> BadgeLook(scheme.errorContainer, scheme.onErrorContainer, icon())
    }
}
