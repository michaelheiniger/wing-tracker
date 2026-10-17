package ch.qscqlmpa.wingtracker.flights

class FlightNotFoundException(
    id: FlightId,
) : NoSuchElementException("No flight with id ${id.value}")

class FlightDateInFutureException :
    IllegalArgumentException("A flight cannot be logged for a day that has not happened yet")

class FlightHasTrackException(
    id: FlightId,
) : IllegalStateException("Flight ${id.value} already has a track")
