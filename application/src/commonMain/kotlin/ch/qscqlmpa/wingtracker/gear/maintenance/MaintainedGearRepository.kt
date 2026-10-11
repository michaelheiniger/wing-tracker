package ch.qscqlmpa.wingtracker.gear.maintenance

import ch.qscqlmpa.wingtracker.gear.harness.Harness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessId
import ch.qscqlmpa.wingtracker.gear.harness.HarnessRepository
import ch.qscqlmpa.wingtracker.gear.reserve.Reserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveId
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveRepository
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository

class GearNotFoundException(
    ref: GearRef,
) : NoSuchElementException("No ${ref.kind} with id ${ref.id}")

/** Finds and saves gear of any kind, so maintenance is written once for wings, harnesses and reserves. */
interface MaintainedGearRepository {
    suspend fun find(ref: GearRef): MaintainedGear?

    suspend fun findAll(): List<MaintainedGear>

    suspend fun save(gear: MaintainedGear)
}

internal suspend fun MaintainedGearRepository.require(ref: GearRef): MaintainedGear =
    find(ref) ?: throw GearNotFoundException(ref)

/** Delegates to the repository of each kind; the kinds stay separate aggregates with their own storage. */
class CompositeMaintainedGearRepository(
    private val wings: WingRepository,
    private val harnesses: HarnessRepository,
    private val reserves: ReserveRepository,
) : MaintainedGearRepository {
    override suspend fun find(ref: GearRef): MaintainedGear? =
        when (ref.kind) {
            GearKind.Wing -> wings.findById(WingId(ref.id))
            GearKind.Harness -> harnesses.findById(HarnessId(ref.id))
            GearKind.Reserve -> reserves.findById(ReserveId(ref.id))
        }

    override suspend fun findAll(): List<MaintainedGear> =
        wings.findAll().items + harnesses.findAll().items + reserves.findAll().items

    override suspend fun save(gear: MaintainedGear) {
        when (gear) {
            is Wing -> wings.save(gear)
            is Harness -> harnesses.save(gear)
            is Reserve -> reserves.save(gear)
            else -> error("Unsupported gear ${gear.ref}")
        }
    }
}
