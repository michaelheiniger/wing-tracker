package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.gear.GearStatus
import kotlinx.datetime.LocalDate

data class Wing(
    val id: WingId,
    val details: WingDetails,
    val status: GearStatus = GearStatus.Active,
) {
    init {
        if (status is GearStatus.Retired) {
            require(status.on >= details.inServiceDate) { "A wing cannot be retired before it entered service" }
        }
    }

    val isActive: Boolean get() = status == GearStatus.Active

    fun withDetails(details: WingDetails): Wing = copy(details = details)

    fun retire(
        on: LocalDate,
        reason: String? = null,
    ): Wing {
        check(isActive) { "Wing ${id.value} is already retired" }
        return copy(status = GearStatus.Retired(on, reason?.takeIf { it.isNotBlank() }))
    }
}
