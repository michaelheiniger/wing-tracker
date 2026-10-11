package ch.qscqlmpa.wingtracker.ui

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.duration_hours_minutes
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

private const val MINUTES_PER_HOUR = 60

@Composable
fun formatDuration(duration: Duration): String {
    val totalMinutes = duration.inWholeMinutes
    return stringResource(
        Res.string.duration_hours_minutes,
        totalMinutes / MINUTES_PER_HOUR,
        totalMinutes % MINUTES_PER_HOUR,
    )
}
