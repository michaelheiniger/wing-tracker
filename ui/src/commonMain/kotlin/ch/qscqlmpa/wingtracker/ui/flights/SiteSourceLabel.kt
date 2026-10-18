package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.flights.SiteSource
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.site_source_manual
import ch.qscqlmpa.wingtracker.ui.resources.site_source_spreadsheet
import ch.qscqlmpa.wingtracker.ui.resources.site_source_syride
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiteSource.label(): String =
    when (this) {
        SiteSource.Manual -> stringResource(Res.string.site_source_manual)
        SiteSource.Syride -> stringResource(Res.string.site_source_syride)
        SiteSource.Spreadsheet -> stringResource(Res.string.site_source_spreadsheet)
    }
