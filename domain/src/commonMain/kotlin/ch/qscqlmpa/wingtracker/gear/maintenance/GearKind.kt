package ch.qscqlmpa.wingtracker.gear.maintenance

/**
 * The kinds of gear that are maintained. Each kind accepts its own check types, and only wings are linked to
 * flights, so only wings can have an interval counted in flight hours.
 */
enum class GearKind(
    val allowedChecks: List<CheckType>,
    val countsFlightHours: Boolean,
) {
    Wing(listOf(CheckType.Partial, CheckType.Full, CheckType.Other), countsFlightHours = true),
    Harness(listOf(CheckType.Check, CheckType.Other), countsFlightHours = false),
    Reserve(listOf(CheckType.Repack, CheckType.Other), countsFlightHours = false),
}

/** Identifies one item of gear across kinds, so maintenance use cases work for all of them. */
data class GearRef(
    val kind: GearKind,
    val id: String,
)
