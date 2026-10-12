package ch.qscqlmpa.wingtracker.ui.syride

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.syride.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.syride.SyrideWing
import ch.qscqlmpa.wingtracker.flights.syride.SyrideWingGroup
import ch.qscqlmpa.wingtracker.flights.syride.WingTarget
import ch.qscqlmpa.wingtracker.gear.wing.WingId

/** What to do with the flights of one Syride wing. */
sealed interface WingChoice {
    data class Existing(
        val wingId: WingId,
    ) : WingChoice

    // [key] names a wing that is created when the import runs, so several Syride wings can share it.
    data class New(
        val key: String,
    ) : WingChoice

    data object Skip : WingChoice
}

fun NewWingSpec.isComplete(): Boolean = name.isNotBlank() && manufacturer.isNotBlank() && model.isNotBlank()

/** The pilot's decisions about the wings: which wing each Syride wing is, and which new wings to create. */
class WingMapping {
    var choices by mutableStateOf<Map<SyrideWing, WingChoice>>(emptyMap())
        private set
    var newWings by mutableStateOf<Map<String, NewWingSpec>>(emptyMap())
        private set

    private var nextKey = 0

    /**
     * Wings that match an existing one are pre-selected; the others start as a new wing named like on Syride, so
     * the usual case needs no typing at all.
     */
    fun start(groups: List<SyrideWingGroup>) {
        val initial = mutableMapOf<SyrideWing, WingChoice>()
        val created = mutableMapOf<String, NewWingSpec>()
        groups.forEach { group ->
            val suggested = group.suggestedWingId
            if (suggested != null) {
                initial[group.wing] = WingChoice.Existing(suggested)
            } else {
                val key = newKey()
                created[key] = suggestedSpec(group.wing)
                initial[group.wing] = WingChoice.New(key)
            }
        }
        choices = initial
        newWings = created
    }

    fun choiceOf(group: SyrideWingGroup): WingChoice = choices[group.wing] ?: WingChoice.Skip

    fun isReady(group: SyrideWingGroup): Boolean =
        when (val choice = choiceOf(group)) {
            is WingChoice.Existing -> true
            is WingChoice.New -> newWings[choice.key]?.isComplete() == true
            WingChoice.Skip -> false
        }

    fun choose(
        group: SyrideWingGroup,
        choice: WingChoice,
    ) {
        choices = choices + (group.wing to choice)
    }

    /** Defines a wing to create, and sends the flights of [group] to it. */
    fun createWingFor(
        group: SyrideWingGroup,
        spec: NewWingSpec,
    ) {
        val key = newKey()
        newWings = newWings + (key to spec)
        choose(group, WingChoice.New(key))
    }

    fun editNewWing(
        key: String,
        spec: NewWingSpec,
    ) {
        newWings = newWings + (key to spec)
    }

    fun suggestedSpec(wing: SyrideWing) =
        NewWingSpec(name = wing.name, manufacturer = wing.brand.orEmpty(), model = wing.name)

    fun targetOf(group: SyrideWingGroup): WingTarget =
        when (val choice = choiceOf(group)) {
            is WingChoice.Existing -> WingTarget.Existing(choice.wingId)
            is WingChoice.New -> WingTarget.New(choice.key)
            WingChoice.Skip -> WingTarget.Skip
        }

    private fun newKey() = "new-${nextKey++}"
}
