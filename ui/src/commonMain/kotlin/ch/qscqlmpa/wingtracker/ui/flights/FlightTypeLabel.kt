package ch.qscqlmpa.wingtracker.ui.flights

import androidx.compose.runtime.Composable
import ch.qscqlmpa.wingtracker.flights.FlightType
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.flight_type_hike_and_fly
import ch.qscqlmpa.wingtracker.ui.resources.flight_type_site
import org.jetbrains.compose.resources.stringResource

@Composable
fun FlightType.label(): String =
    when (this) {
        FlightType.Site -> stringResource(Res.string.flight_type_site)
        FlightType.HikeAndFly -> stringResource(Res.string.flight_type_hike_and_fly)
    }
