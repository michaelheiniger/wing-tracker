package ch.qscqlmpa.wingtracker.ui.flights.detail.map

import ch.qscqlmpa.wingtracker.maps.ReadMapTile
import ch.qscqlmpa.wingtracker.settings.TrackColoring

/** What the map needs from outside the page: where tiles come from and how the pilot wants the track coloured. */
class MapSetup(
    val readMapTile: ReadMapTile,
    val coloring: TrackColoring,
    val onColoringChange: (TrackColoring) -> Unit,
)
