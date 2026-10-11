package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.attachments.AttachmentStore

class DeleteFlight(
    private val flights: FlightRepository,
    private val attachments: AttachmentStore,
) {
    suspend operator fun invoke(id: FlightId) {
        val flight = flights.findById(id) ?: throw FlightNotFoundException(id)
        flights.delete(id)
        // The track goes last: if deleting it fails, the logbook is already consistent and only a stray file remains.
        flight.igcFile?.let { attachments.delete(it.attachment.id) }
    }
}
