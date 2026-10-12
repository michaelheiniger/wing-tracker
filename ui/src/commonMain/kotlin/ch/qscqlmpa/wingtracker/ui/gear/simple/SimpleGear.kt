package ch.qscqlmpa.wingtracker.ui.gear.simple

import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.GearStatus
import ch.qscqlmpa.wingtracker.gear.harness.DeleteHarness
import ch.qscqlmpa.wingtracker.gear.harness.Harness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessDetails
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.harness.ListHarnesses
import ch.qscqlmpa.wingtracker.gear.harness.RegisterHarness
import ch.qscqlmpa.wingtracker.gear.harness.RetireHarness
import ch.qscqlmpa.wingtracker.gear.harness.UpdateHarnessDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.GearKind
import ch.qscqlmpa.wingtracker.gear.maintenance.GearRef
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintenanceInterval
import ch.qscqlmpa.wingtracker.gear.maintenance.PlannedCheck
import ch.qscqlmpa.wingtracker.gear.reserve.DeleteReserve
import ch.qscqlmpa.wingtracker.gear.reserve.ListReserves
import ch.qscqlmpa.wingtracker.gear.reserve.RegisterReserve
import ch.qscqlmpa.wingtracker.gear.reserve.Reserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveDetails
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import ch.qscqlmpa.wingtracker.gear.reserve.RetireReserve
import ch.qscqlmpa.wingtracker.gear.reserve.UpdateReserveDetails
import ch.qscqlmpa.wingtracker.shared.LoadResult
import kotlinx.datetime.LocalDate

/**
 * What harnesses and reserves have in common as far as the screens go. They differ only by the reserve's
 * manufacture date, so one list, one form and one set of presenters serve both.
 */
data class SimpleGearDetails(
    val name: String,
    val manufacturer: String,
    val model: String,
    val size: String? = null,
    val serialNumber: String? = null,
    // Only reserves have one; ignored for harnesses.
    val manufactureDate: LocalDate? = null,
    val acquisition: Acquisition,
    val inServiceDate: LocalDate? = null,
    val intervalMonths: Int? = null,
)

data class SimpleGearItem(
    val ref: GearRef,
    val details: SimpleGearDetails,
    val status: GearStatus,
    val hasChecks: Boolean,
    val nextPlannedCheck: PlannedCheck?,
) {
    val isActive: Boolean get() = status == GearStatus.Active
}

/** Operations the screens need, implemented per kind on top of that kind's use cases. */
interface SimpleGearOps {
    val kind: GearKind

    suspend fun list(): LoadResult<SimpleGearItem>

    suspend fun register(details: SimpleGearDetails)

    suspend fun update(
        ref: GearRef,
        details: SimpleGearDetails,
    )

    suspend fun retire(
        ref: GearRef,
        on: LocalDate,
        reason: String?,
    )

    suspend fun delete(ref: GearRef)
}

private fun interval(months: Int?) = months?.let { MaintenanceInterval(months = it) }

class HarnessOps(
    private val listHarnesses: ListHarnesses,
    private val registerHarness: RegisterHarness,
    private val updateHarnessDetails: UpdateHarnessDetails,
    private val retireHarness: RetireHarness,
    private val deleteHarness: DeleteHarness,
) : SimpleGearOps {
    override val kind = GearKind.Harness

    override suspend fun list(): LoadResult<SimpleGearItem> =
        listHarnesses().let { loaded -> LoadResult(loaded.items.map { it.toItem() }, loaded.unreadable) }

    override suspend fun register(details: SimpleGearDetails) {
        registerHarness(details.toHarnessDetails())
    }

    override suspend fun update(
        ref: GearRef,
        details: SimpleGearDetails,
    ) {
        updateHarnessDetails(HarnessId(ref.id), details.toHarnessDetails())
    }

    override suspend fun retire(
        ref: GearRef,
        on: LocalDate,
        reason: String?,
    ) {
        retireHarness(HarnessId(ref.id), on, reason)
    }

    override suspend fun delete(ref: GearRef) = deleteHarness(HarnessId(ref.id))

    private fun SimpleGearDetails.toHarnessDetails() =
        HarnessDetails(
            name,
            manufacturer,
            model,
            size,
            serialNumber,
            acquisition,
            inServiceDate,
            interval(intervalMonths),
        )

    private fun Harness.toItem() =
        SimpleGearItem(
            ref = ref,
            details =
                SimpleGearDetails(
                    name = details.name,
                    manufacturer = details.manufacturer,
                    model = details.model,
                    size = details.size,
                    serialNumber = details.serialNumber,
                    acquisition = details.acquisition,
                    inServiceDate = details.inServiceDate,
                    intervalMonths = details.maintenanceInterval?.months,
                ),
            status = status,
            hasChecks = checks.isNotEmpty(),
            nextPlannedCheck = history.nextPlannedCheck,
        )
}

class ReserveOps(
    private val listReserves: ListReserves,
    private val registerReserve: RegisterReserve,
    private val updateReserveDetails: UpdateReserveDetails,
    private val retireReserve: RetireReserve,
    private val deleteReserve: DeleteReserve,
) : SimpleGearOps {
    override val kind = GearKind.Reserve

    override suspend fun list(): LoadResult<SimpleGearItem> =
        listReserves().let { loaded -> LoadResult(loaded.items.map { it.toItem() }, loaded.unreadable) }

    override suspend fun register(details: SimpleGearDetails) {
        registerReserve(details.toReserveDetails())
    }

    override suspend fun update(
        ref: GearRef,
        details: SimpleGearDetails,
    ) {
        updateReserveDetails(ReserveId(ref.id), details.toReserveDetails())
    }

    override suspend fun retire(
        ref: GearRef,
        on: LocalDate,
        reason: String?,
    ) {
        retireReserve(ReserveId(ref.id), on, reason)
    }

    override suspend fun delete(ref: GearRef) = deleteReserve(ReserveId(ref.id))

    private fun SimpleGearDetails.toReserveDetails() =
        ReserveDetails(
            name,
            manufacturer,
            model,
            size,
            serialNumber,
            manufactureDate,
            acquisition,
            inServiceDate,
            interval(intervalMonths),
        )

    private fun Reserve.toItem() =
        SimpleGearItem(
            ref = ref,
            details =
                SimpleGearDetails(
                    name = details.name,
                    manufacturer = details.manufacturer,
                    model = details.model,
                    size = details.size,
                    serialNumber = details.serialNumber,
                    manufactureDate = details.manufactureDate,
                    acquisition = details.acquisition,
                    inServiceDate = details.inServiceDate,
                    intervalMonths = details.maintenanceInterval?.months,
                ),
            status = status,
            hasChecks = checks.isNotEmpty(),
            nextPlannedCheck = history.nextPlannedCheck,
        )
}
