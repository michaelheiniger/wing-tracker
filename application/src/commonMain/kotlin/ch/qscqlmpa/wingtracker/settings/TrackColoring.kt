package ch.qscqlmpa.wingtracker.settings

/** How a flight's track is coloured on the map; remembered, so every flight opens the way the pilot prefers. */
enum class TrackColoring {
    // One colour for the flight, so its course reads best.
    Flight,

    // Coloured by how fast the pilot climbed or sank, to find the thermals.
    ClimbSink,
}
