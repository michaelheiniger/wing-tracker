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
    // Null when Syride gave no take-off site with a page.
    val onOpenSyrideSite: (() -> Unit)? = null,
    // Null when the flight's page on air-time is not known (ADR 0029).
    val onOpenAirtime: (() -> Unit)? = null,
    val onForgetAirtimeExport: () -> Unit = {},
)
