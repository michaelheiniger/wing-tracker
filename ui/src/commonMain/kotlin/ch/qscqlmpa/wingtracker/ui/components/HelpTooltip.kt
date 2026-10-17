package ch.qscqlmpa.wingtracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.help_tooltip_icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** An info icon that explains a field. It opens on hover and on click, so it also works without a mouse. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTooltip(
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Int = DEFAULT_ICON_SIZE_DP,
) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        // Laid out apart from the window, so it has its own selection (see Popups.kt).
        tooltip = { PlainTooltip { SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) } } },
        state = state,
        modifier = modifier,
    ) {
        IconButton(
            onClick = { scope.launch { state.show() } },
            modifier = Modifier.size((iconSize + ICON_PADDING_DP).dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(Res.string.help_tooltip_icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

private const val DEFAULT_ICON_SIZE_DP = 20
private const val ICON_PADDING_DP = 8
