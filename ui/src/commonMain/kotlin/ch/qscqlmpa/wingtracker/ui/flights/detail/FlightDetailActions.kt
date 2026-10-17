package ch.qscqlmpa.wingtracker.ui.flights.detail

import ch.qscqlmpa.wingtracker.flights.Flight

/** What the detail page can do; the route decides what each means. */
class FlightDetailActions(
    val onBack: () -> Unit,
    val onOpen: (Flight) -> Unit,
    val onEdit: () -> Unit,
    val onOpenTrack: () -> Unit,
    val onDelete: () -> Unit,
    // Null when the flight has no page on Syride, which hides the button.
    val onOpenSyride: (() -> Unit)? = null,
)
