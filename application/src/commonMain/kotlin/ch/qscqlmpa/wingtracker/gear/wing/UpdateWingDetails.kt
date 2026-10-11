package ch.qscqlmpa.wingtracker.gear.wing

class UpdateWingDetails(
    private val wings: WingRepository,
) {
    suspend operator fun invoke(
        id: WingId,
        details: WingDetails,
    ): Wing {
        val updated = (wings.findById(id) ?: throw WingNotFoundException(id)).withDetails(details)
        wings.save(updated)
        return updated
    }
}
