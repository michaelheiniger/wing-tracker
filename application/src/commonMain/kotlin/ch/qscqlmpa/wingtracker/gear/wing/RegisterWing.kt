package ch.qscqlmpa.wingtracker.gear.wing

import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RegisterWing(
    private val wings: WingRepository,
    private val transactions: LogbookTransactions,
    // Injectable so tests get deterministic ids.
    private val newId: () -> WingId = { WingId(Uuid.random().toString()) },
) {
    suspend operator fun invoke(details: WingDetails): Wing {
        val wing = Wing(id = newId(), details = details)
        transactions.run(LogbookChange.GearRegistered(wing.ref)) { wings.save(wing) }
        return wing
    }
}
