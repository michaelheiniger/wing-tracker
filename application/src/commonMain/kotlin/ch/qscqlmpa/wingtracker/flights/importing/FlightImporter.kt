package ch.qscqlmpa.wingtracker.flights.importing

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.flights.Flight
import ch.qscqlmpa.wingtracker.flights.FlightDateInFutureException
import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightRules
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

enum class ImportFailure { DateInFuture, SaveFailed }

data class FailedImport<F : ImportableFlight>(
    val flight: F,
    val failure: ImportFailure,
    val detail: String? = null,
)

data class ImportResult<F : ImportableFlight>(
    val imported: Int,
    val createdWings: List<Wing>,
    val skipped: Int,
    val failed: List<FailedImport<F>>,
)

/**
 * Saves flights from another source on the wings the pilot chose, creating the new wings asked for. Flights that
 * break the usual flight rules are reported and do not stop the others. Each source decides which flights to bring
 * and in which order; this is shared so every import behaves the same.
 */
internal class FlightImporter(
    private val flights: FlightRepository,
    wings: WingRepository,
    private val clock: AppClock,
    private val registerWing: RegisterWing,
    private val newId: () -> FlightId,
) {
    private val rules = FlightRules(wings, clock)

    /** [ordered] holds no skipped flight, oldest first: flights of one day are listed by when they were logged. */
    suspend fun <F : ImportableFlight> import(
        ordered: List<Pair<F, WingTarget>>,
        newWings: Map<String, NewWingSpec>,
        skipped: Int,
    ): ImportResult<F> {
        val created = createWings(ordered.map { it.second }, newWings)
        val failed = mutableListOf<FailedImport<F>>()
        var imported = 0
        ordered.forEachIndexed { index, (flight, target) ->
            val failure = import(flight, wingIdOf(target, created), index)
            if (failure == null) imported++ else failed += failure
        }
        return ImportResult(imported, created.values.toList(), skipped, failed)
    }

    private suspend fun createWings(
        targets: List<WingTarget>,
        newWings: Map<String, NewWingSpec>,
    ): Map<String, Wing> {
        // A wing nobody flew on in this selection is not created.
        val keys = targets.filterIsInstance<WingTarget.New>().map { it.key }.distinct()
        return keys.associateWith { key ->
            val spec = newWings.getValue(key)
            val details =
                WingDetails(
                    name = spec.name,
                    manufacturer = spec.manufacturer,
                    model = spec.model,
                    acquisition = Acquisition(AcquisitionCondition.New),
                    // The source does not know when the pilot started using the wing, so the pilot sets the date.
                )
            registerWing(details)
        }
    }

    private fun wingIdOf(
        target: WingTarget,
        created: Map<String, Wing>,
    ): WingId =
        when (target) {
            is WingTarget.Existing -> target.wingId
            is WingTarget.New -> created.getValue(target.key).id
            WingTarget.Skip -> error("Skipped flights are not imported")
        }

    private suspend fun <F : ImportableFlight> import(
        flight: F,
        wingId: WingId,
        index: Int,
    ): FailedImport<F>? {
        val details = flight.toDetails(wingId)
        return try {
            rules.ensureAllowed(details)
            // A millisecond apart, so the logbook keeps the order of the day instead of showing a tie.
            val loggedAt = clock.now() + index.milliseconds
            flights.save(Flight(newId(), details, loggedAt, importedFrom = flight.externalReference))
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: FlightDateInFutureException) {
            FailedImport(flight, ImportFailure.DateInFuture)
            // A storage failure on one flight is reported on that flight; the others still go through.
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            FailedImport(flight, ImportFailure.SaveFailed, e.message)
        }
    }
}
