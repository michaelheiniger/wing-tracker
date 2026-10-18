package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.qscqlmpa.wingtracker.ui.message
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.backup_create
import ch.qscqlmpa.wingtracker.ui.resources.backup_hint
import ch.qscqlmpa.wingtracker.ui.resources.backup_in_progress
import ch.qscqlmpa.wingtracker.ui.resources.backup_last
import ch.qscqlmpa.wingtracker.ui.resources.backup_never
import ch.qscqlmpa.wingtracker.ui.resources.backup_saved
import ch.qscqlmpa.wingtracker.ui.resources.backup_saved_content
import ch.qscqlmpa.wingtracker.ui.resources.backup_title
import ch.qscqlmpa.wingtracker.ui.resources.flight_date_time
import ch.qscqlmpa.wingtracker.ui.toDisplay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val BYTES_PER_MB = 1024.0 * 1024.0

@Composable
fun BackupSection(presenter: BackupPresenter) {
    Text(
        stringResource(Res.string.backup_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        stringResource(Res.string.backup_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        presenter.lastMadeAt?.let {
            stringResource(
                Res.string.backup_last,
                stringResource(Res.string.flight_date_time, it.date.toDisplay(), it.time.toDisplay()),
            )
        } ?: stringResource(Res.string.backup_never),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = presenter::create, enabled = !presenter.busy) {
            Text(stringResource(Res.string.backup_create))
        }
        if (presenter.busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.backup_in_progress))
        }
    }
    presenter.saved?.let { saved ->
        // The path is selectable, so the pilot can find the file again or copy it elsewhere.
        Text(stringResource(Res.string.backup_saved, saved.path))
        Text(
            pluralStringResource(
                Res.plurals.backup_saved_content,
                saved.summary.files,
                saved.summary.files,
                megabytes(saved.summary.bytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    presenter.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
}

// Rounded up to one, so a small logbook does not read as an empty backup.
private fun megabytes(bytes: Long): String {
    val rounded = (bytes / BYTES_PER_MB).roundToInt()
    return (if (bytes > 0 && rounded == 0) 1 else rounded).toString()
}
