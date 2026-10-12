package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.reserve.Reserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveDetails
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** On-disk shape of a reserve parachute, kept apart from the domain model (see [WingDocument]). */
@Serializable
internal data class ReserveDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String? = null,
    val serialNumber: String? = null,
    val manufactureDate: LocalDate? = null,
    val acquisitionCondition: String,
    val purchaseDate: LocalDate? = null,
    val priceMinorUnits: Long? = null,
    val priceCurrency: String? = null,
    // Optional since the next schema version; older versions treat such files as newer data.
    val inServiceDate: LocalDate? = null,
    val maintenanceIntervalMonths: Int? = null,
    val retiredOn: LocalDate? = null,
    val retirementReason: String? = null,
    val checks: List<CheckDocument> = emptyList(),
    val plannedChecks: List<PlannedCheckDocument> = emptyList(),
) {
    fun toDomain(): Reserve {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaException("Reserve $id was written by a newer version of the application")
        }
        return Reserve(
            id = ReserveId(id),
            details =
                ReserveDetails(
                    name = name,
                    manufacturer = manufacturer,
                    model = model,
                    size = size,
                    serialNumber = serialNumber,
                    manufactureDate = manufactureDate,
                    acquisition = acquisition(acquisitionCondition, purchaseDate, priceMinorUnits, priceCurrency),
                    inServiceDate = inServiceDate,
                    maintenanceInterval = maintenanceIntervalMonths?.let { MaintenanceInterval(months = it) },
                ),
            status = retiredOn?.let { GearStatus.Retired(it, retirementReason) } ?: GearStatus.Active,
            checks = checks.map { it.toDomain() },
            plannedChecks = plannedChecks.map { it.toDomain() },
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2

        fun from(reserve: Reserve): ReserveDocument {
            val details = reserve.details
            val retired = reserve.status as? GearStatus.Retired
            return ReserveDocument(
                id = reserve.id.value,
                name = details.name,
                manufacturer = details.manufacturer,
                model = details.model,
                size = details.size,
                serialNumber = details.serialNumber,
                manufactureDate = details.manufactureDate,
                acquisitionCondition = details.acquisition.condition.name,
                purchaseDate = details.acquisition.purchaseDate,
                priceMinorUnits = details.acquisition.price?.minorUnits,
                priceCurrency =
                    details.acquisition.price
                        ?.currency
                        ?.value,
                inServiceDate = details.inServiceDate,
                maintenanceIntervalMonths = details.maintenanceInterval?.months,
                retiredOn = retired?.on,
                retirementReason = retired?.reason,
                checks = reserve.checks.map { CheckDocument.from(it) },
                plannedChecks = reserve.plannedChecks.map { PlannedCheckDocument.from(it) },
            )
        }
    }
}
