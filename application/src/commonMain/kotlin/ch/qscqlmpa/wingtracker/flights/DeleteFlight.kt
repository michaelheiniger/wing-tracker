package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions

class DeleteFlight(
    private val flights: FlightRepository,
    private val attachments: AttachmentStore,
    private val transactions: LogbookTransactions,
) {
    suspend operator fun invoke(id: FlightId) =
        transactions.run(LogbookChange.FlightDeleted(id)) {
            val flight = flights.findById(id) ?: throw FlightNotFoundException(id)
            flights.delete(id)
            // The track goes last: if deleting it fails, the logbook is already consistent and only a stray file
            // remains.
            flight.igcFile?.let { attachments.delete(it.attachment.id) }
        }
}
