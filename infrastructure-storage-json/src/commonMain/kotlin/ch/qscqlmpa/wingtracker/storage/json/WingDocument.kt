package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.CurrencyCode
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.Price
import ch.qscqlmpa.wingtracker.gear.wing.CertificationClass
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.MaintenanceInterval
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

/**
 * On-disk shape of a wing. Kept apart from the domain model so refactoring the domain never silently
 * changes stored files; enums are stored by name, so renaming one requires a schema migration.
 */
@Serializable
internal data class WingDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String? = null,
    val serialNumber: String? = null,
    val certificationClass: String? = null,
    val acquisitionCondition: String,
    val purchaseDate: LocalDate? = null,
    val priceMinorUnits: Long? = null,
    val priceCurrency: String? = null,
    val inServiceDate: LocalDate,
    val hoursBeforeTrackingMinutes: Long = 0,
    val retiredOn: LocalDate? = null,
    val retirementReason: String? = null,
    // Added in schema 2; files written before it simply have none.
    val maintenanceIntervalMonths: Int? = null,
    val maintenanceIntervalHoursMinutes: Long? = null,
    val checks: List<CheckDocument> = emptyList(),
    val plannedChecks: List<PlannedCheckDocument> = emptyList(),
) {
    fun toDomain(): Wing {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaException("Wing $id was written by a newer version of the application")
        }
        val price =
            if (priceMinorUnits != null && priceCurrency != null) {
                Price(priceMinorUnits, CurrencyCode(priceCurrency))
            } else {
                null
            }
        return Wing(
            id = WingId(id),
            details =
                WingDetails(
                    name = name,
                    manufacturer = manufacturer,
                    model = model,
                    size = size,
                    serialNumber = serialNumber,
                    certificationClass = certificationClass?.let { CertificationClass.valueOf(it) },
                    acquisition = Acquisition(AcquisitionCondition.valueOf(acquisitionCondition), purchaseDate, price),
                    inServiceDate = inServiceDate,
                    hoursBeforeTracking = hoursBeforeTrackingMinutes.minutes,
                    maintenanceInterval = interval(),
                ),
            status = retiredOn?.let { GearStatus.Retired(it, retirementReason) } ?: GearStatus.Active,
            checks = checks.map { it.toDomain() },
            plannedChecks = plannedChecks.map { it.toDomain() },
        )
    }

    private fun interval(): MaintenanceInterval? =
        if (maintenanceIntervalMonths == null && maintenanceIntervalHoursMinutes == null) {
            null
        } else {
            MaintenanceInterval(maintenanceIntervalMonths, maintenanceIntervalHoursMinutes?.minutes)
        }

    companion object {
        // Version 2 added maintenance. Older application versions treat it as newer data and leave it untouched.
        const val CURRENT_SCHEMA_VERSION = 2

        fun from(wing: Wing): WingDocument {
            val details = wing.details
            val retired = wing.status as? GearStatus.Retired
            return WingDocument(
                id = wing.id.value,
                name = details.name,
                manufacturer = details.manufacturer,
                model = details.model,
                size = details.size,
                serialNumber = details.serialNumber,
                certificationClass = details.certificationClass?.name,
                acquisitionCondition = details.acquisition.condition.name,
                purchaseDate = details.acquisition.purchaseDate,
                priceMinorUnits = details.acquisition.price?.minorUnits,
                priceCurrency =
                    details.acquisition.price
                        ?.currency
                        ?.value,
                inServiceDate = details.inServiceDate,
                hoursBeforeTrackingMinutes = details.hoursBeforeTracking.inWholeMinutes,
                retiredOn = retired?.on,
                retirementReason = retired?.reason,
                maintenanceIntervalMonths = details.maintenanceInterval?.months,
                maintenanceIntervalHoursMinutes = details.maintenanceInterval?.hours?.inWholeMinutes,
                checks = wing.checks.map { CheckDocument.from(it) },
                plannedChecks = wing.plannedChecks.map { PlannedCheckDocument.from(it) },
            )
        }
    }
}
