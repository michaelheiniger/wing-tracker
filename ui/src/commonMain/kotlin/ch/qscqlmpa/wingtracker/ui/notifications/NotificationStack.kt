package ch.qscqlmpa.wingtracker.ui.notifications

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.components.look
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.notification_close
import org.jetbrains.compose.resources.stringResource

/** The notifications shown in the window, newest at the bottom; placed by the caller, e.g. in the top-right corner. */
@Composable
fun NotificationStack(
    center: NotificationCenter,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        center.shown.forEach { shown ->
            key(shown.id) { NotificationCard(shown, center) }
        }
    }
}

@Composable
private fun NotificationCard(
    shown: ShownNotification,
    center: NotificationCenter,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    // Held while the pointer is over it, so a pilot reading it is not cut off.
    LaunchedEffect(hovered) { if (hovered) center.hold(shown.id) else center.release(shown.id) }
    val look = shown.notification.severity.look()
    Surface(
        modifier =
            Modifier
                .hoverable(interactions)
                // Read out by a screen reader when it appears, without interrupting.
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = look.container,
        contentColor = look.content,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            look.icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            Text(
                stringResource(shown.notification.message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = { center.dismiss(shown.id) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.notification_close))
            }
        }
    }
}
