package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.harness.Harness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** On-disk shape of a harness, kept apart from the domain model (see [WingDocument]). */
@Serializable
internal data class HarnessDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String? = null,
    val serialNumber: String? = null,
    val acquisitionCondition: String,
    val purchaseDate: LocalDate? = null,
    val priceMinorUnits: Long? = null,
    val priceCurrency: String? = null,
    val inServiceDate: LocalDate,
    val maintenanceIntervalMonths: Int? = null,
    val retiredOn: LocalDate? = null,
    val retirementReason: String? = null,
    val checks: List<CheckDocument> = emptyList(),
    val plannedChecks: List<PlannedCheckDocument> = emptyList(),
) {
    fun toDomain(): Harness {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaException("Harness $id was written by a newer version of the application")
        }
        return Harness(
            id = HarnessId(id),
            details =
                HarnessDetails(
                    name = name,
                    manufacturer = manufacturer,
                    model = model,
                    size = size,
                    serialNumber = serialNumber,
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
        const val CURRENT_SCHEMA_VERSION = 1

        fun from(harness: Harness): HarnessDocument {
            val details = harness.details
            val retired = harness.status as? GearStatus.Retired
            return HarnessDocument(
                id = harness.id.value,
                name = details.name,
                manufacturer = details.manufacturer,
                model = details.model,
                size = details.size,
                serialNumber = details.serialNumber,
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
                checks = harness.checks.map { CheckDocument.from(it) },
                plannedChecks = harness.plannedChecks.map { PlannedCheckDocument.from(it) },
            )
        }
    }
}

internal fun acquisition(
    condition: String,
    purchaseDate: LocalDate?,
    priceMinorUnits: Long?,
    priceCurrency: String?,
): Acquisition {
    val price =
        if (priceMinorUnits != null &&
            priceCurrency != null
        ) {
            Price(priceMinorUnits, CurrencyCode(priceCurrency))
        } else {
            null
        }
    return Acquisition(AcquisitionCondition.valueOf(condition), purchaseDate, price)
}
