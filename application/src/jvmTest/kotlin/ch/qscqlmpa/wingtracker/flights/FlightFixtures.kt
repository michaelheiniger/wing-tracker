package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.gear.Acquisition
import ch.qscqlmpa.wingtracker.gear.AcquisitionCondition
import ch.qscqlmpa.wingtracker.gear.wing.Wing
import ch.qscqlmpa.wingtracker.gear.wing.WingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.shared.LoadResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Own factories for the flights use cases; they do not depend on other modules' test helpers.
val NOW: Instant = Instant.parse("2026-10-11T18:30:00Z")
val TODAY: LocalDate = LocalDate(2026, 10, 11)

fun aFixedClock() =
    AppClock(
        clock =
            object : Clock {
                override fun now(): Instant = NOW
            },
        zone = TimeZone.UTC,
    )

fun aWingInService(
    id: String = "3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47",
    name: String = "Sky Dancer",
    inServiceDate: LocalDate = LocalDate(2024, 3, 9),
    hoursBeforeTracking: Duration = 42.hours,
    manufacturer: String = "Advance",
    model: String = "Iota 3",
    flightsBeforeTracking: Int = 0,
) = Wing(
    id = WingId(id),
    details =
        WingDetails(
            name = name,
            manufacturer = manufacturer,
            model = model,
            acquisition = Acquisition(AcquisitionCondition.New),
            inServiceDate = inServiceDate,
            hoursBeforeTracking = hoursBeforeTracking,
            flightsBeforeTracking = flightsBeforeTracking,
        ),
)

fun aFlightDetails(
    date: LocalDate = LocalDate(2026, 9, 20),
    duration: Duration = 95.minutes,
    wingId: WingId = WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"),
) = FlightDetails(
    date = date,
    duration = duration,
    wingId = wingId,
    takeOffSite = "Niederhorn",
    landingSite = "Beatenbucht",
    notes = "Thermic, smooth air",
)

fun aFlight(
    id: String = "flight-1",
    details: FlightDetails = aFlightDetails(),
    loggedAt: Instant = Instant.parse("2026-09-20T17:00:00Z"),
) = Flight(FlightId(id), details, loggedAt)

// A mock cannot hold the wings a use case creates, and the flight rules look the wing up again afterwards.
class InMemoryWings : WingRepository {
    val stored = linkedMapOf<WingId, Wing>()

    override suspend fun findAll() = LoadResult(stored.values.toList())

    override suspend fun findById(id: WingId) = stored[id]

    override suspend fun save(wing: Wing) {
        stored[wing.id] = wing
    }

    override suspend fun delete(id: WingId) {
        stored.remove(id)
    }
}
