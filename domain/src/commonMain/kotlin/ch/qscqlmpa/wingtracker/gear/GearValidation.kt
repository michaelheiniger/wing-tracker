package ch.qscqlmpa.wingtracker.gear

import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval

internal fun requireIdentified(
    name: String,
    manufacturer: String,
    model: String,
) {
    require(name.isNotBlank()) { "Gear needs a name" }
    require(manufacturer.isNotBlank()) { "Gear needs a manufacturer" }
    require(model.isNotBlank()) { "Gear needs a model" }
}

/** Only wings fly in the logbook, so other gear can only be due by date. */
internal fun requireMonthsOnly(interval: MaintenanceInterval?) {
    require(interval?.hours == null) { "Only wings can have an interval in flight hours" }
}
