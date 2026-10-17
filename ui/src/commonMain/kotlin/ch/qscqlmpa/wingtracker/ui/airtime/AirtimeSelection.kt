package ch.qscqlmpa.wingtracker.ui.airtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportDraft
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingId

/** Which flights of a draft the pilot sends, and the colour given to each glider to create. */
class AirtimeSelection {
    private var draft: AirtimeExportDraft? = null

    var chosen by mutableStateOf<Set<FlightId>>(emptySet())
        private set
    var colours by mutableStateOf<Map<WingId, String>>(emptyMap())
        private set

    /** Every flight starts chosen: the pilot came to send them. */
    fun start(draft: AirtimeExportDraft) {
        this.draft = draft
        chosen = draft.candidates.map { it.flight.id }.toSet()
        colours = emptyMap()
    }

    fun toggle(id: FlightId) {
        chosen = if (id in chosen) chosen - id else chosen + id
    }

    fun chooseAll(all: Boolean) {
        chosen =
            if (all) {
                draft
                    ?.candidates
                    .orEmpty()
                    .map { it.flight.id }
                    .toSet()
            } else {
                emptySet()
            }
    }

    fun changeColour(
        wing: WingId,
        colour: String,
    ) {
        colours = colours + (wing to colour)
    }

    /** The gliders the chosen flights need; flights of a glider no longer chosen create nothing. */
    val gliderNeeded: List<Wing>
        get() {
            val current = draft ?: return emptyList()
            val needed =
                current.candidates
                    .filter { it.flight.id in chosen }
                    .mapNotNull { it.newGlider }
                    .toSet()
            return current.newGliders.filter { it.id in needed }
        }

    val isComplete: Boolean get() = chosen.isNotEmpty() && gliderNeeded.all { !colours[it.id].isNullOrBlank() }

    /** The colours as air-time gets them. */
    val trimmedColours: Map<WingId, String> get() = colours.mapValues { it.value.trim() }
}
