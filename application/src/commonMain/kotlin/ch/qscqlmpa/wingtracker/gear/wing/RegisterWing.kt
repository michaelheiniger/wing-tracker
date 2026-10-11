package ch.qscqlmpa.wingtracker.gear.wing

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RegisterWing(
    private val wings: WingRepository,
    // Injectable so tests get deterministic ids.
    private val newId: () -> WingId = { WingId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(details: WingDetails): Wing {
        val wing = Wing(id = newId(), details = details)
        wings.save(wing)
        return wing
    }
}
