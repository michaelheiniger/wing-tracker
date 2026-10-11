package ch.qscqlmpa.wingtracker.gear.wing

import kotlinx.datetime.LocalDate

class RetireWing(
    private val wings: WingRepository,
) {
    suspend operator fun invoke(
        id: WingId,
        on: LocalDate,
        reason: String?,
    ): Wing {
        val retired = (wings.findById(id) ?: throw WingNotFoundException(id)).retire(on, reason)
        wings.save(retired)
        return retired
    }
}
