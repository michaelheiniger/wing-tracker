package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/** What a pilot edits about a wing; separated from identity and status so updates cannot retire a wing. */
data class WingDetails(
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String? = null,
    val serialNumber: String? = null,
    val certificationClass: CertificationClass? = null,
    val acquisition: Acquisition,
    // Optional: a second-hand wing often has no known start. Flights and maintenance then work from other dates.
    val inServiceDate: LocalDate? = null,
    // Hours flown before tracking started; keeps hour-based due rules right for used wings.
    val hoursBeforeTracking: Duration = Duration.ZERO,
    // Flights made before tracking started; lets the flight count match the hours for used wings.
    val flightsBeforeTracking: Int = 0,
    // Null means no rule yet, so the wing is never reported as due.
    val maintenanceInterval: MaintenanceInterval? = null,
) {
    init {
        require(name.isNotBlank()) { "A wing needs a name" }
        require(manufacturer.isNotBlank()) { "A wing needs a manufacturer" }
        require(model.isNotBlank()) { "A wing needs a model" }
        require(!hoursBeforeTracking.isNegative()) { "Hours before tracking cannot be negative" }
        require(flightsBeforeTracking >= 0) { "Flights before tracking cannot be negative" }
    }
}
