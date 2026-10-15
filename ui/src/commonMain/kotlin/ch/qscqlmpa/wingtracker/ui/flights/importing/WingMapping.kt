package ch.qscqlmpa.wingtracker.ui.flights.importing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.importing.ImportableFlight
import ch.qscqlmpa.wingtracker.flights.importing.NewWingSpec
import ch.qscqlmpa.wingtracker.flights.importing.SourceWing
import ch.qscqlmpa.wingtracker.flights.importing.WingGroup
import ch.qscqlmpa.wingtracker.flights.importing.WingTarget
import ch.qscqlmpa.wingtracker.gear.wing.WingId

/** What to do with the flights of one source wing. */
sealed interface WingChoice {
    data class Existing(
        val wingId: WingId,
    ) : WingChoice

    // [key] names a wing that is created when the import runs, so several source wings can share it.
    data class New(
        val key: String,
    ) : WingChoice

    data object Skip : WingChoice
}

fun NewWingSpec.isComplete(): Boolean = name.isNotBlank() && manufacturer.isNotBlank() && model.isNotBlank()

/** The pilot's decisions about the wings: which wing each source wing is, and which new wings to create. */
class WingMapping {
    var choices by mutableStateOf<Map<SourceWing, WingChoice>>(emptyMap())
        private set
    var newWings by mutableStateOf<Map<String, NewWingSpec>>(emptyMap())
        private set

    private var nextKey = 0

    /**
     * Wings that match an existing one are pre-selected; the others start as a new wing named like in the source, so
     * the usual case needs no typing at all.
     */
    fun start(groups: List<WingGroup<*>>) {
        val initial = mutableMapOf<SourceWing, WingChoice>()
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

    fun choiceOf(group: WingGroup<*>): WingChoice = choices[group.wing] ?: WingChoice.Skip

    fun isReady(group: WingGroup<*>): Boolean =
        when (val choice = choiceOf(group)) {
            is WingChoice.Existing -> true
            is WingChoice.New -> newWings[choice.key]?.isComplete() == true
            WingChoice.Skip -> false
        }

    fun choose(
        group: WingGroup<*>,
        choice: WingChoice,
    ) {
        choices = choices + (group.wing to choice)
    }

    /** Defines a wing to create, and sends the flights of [group] to it. */
    fun createWingFor(
        group: WingGroup<*>,
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

    fun suggestedSpec(wing: SourceWing) =
        NewWingSpec(name = wing.name, manufacturer = wing.brand.orEmpty(), model = wing.name)

    fun targetOf(group: WingGroup<*>): WingTarget =
        when (val choice = choiceOf(group)) {
            is WingChoice.Existing -> WingTarget.Existing(choice.wingId)
            is WingChoice.New -> WingTarget.New(choice.key)
            WingChoice.Skip -> WingTarget.Skip
        }

    /** The chosen flights of every group that is ready, each with where it goes. */
    fun <F : ImportableFlight> selection(groups: List<WingGroup<F>>): List<Pair<F, WingTarget>> =
        groups.filter { isReady(it) }.flatMap { group ->
            val target = targetOf(group)
            group.flights.map { it to target }
        }

    private fun newKey() = "new-${nextKey++}"
}
