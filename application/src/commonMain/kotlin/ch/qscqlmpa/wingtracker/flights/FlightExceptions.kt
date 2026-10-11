package ch.qscqlmpa.wingtracker.flights

class FlightNotFoundException(
    id: FlightId,
) : NoSuchElementException("No flight with id ${id.value}")

class FlightDateInFutureException :
    IllegalArgumentException("A flight cannot be logged for a day that has not happened yet")

class FlightBeforeWingInServiceException :
    IllegalArgumentException("A flight cannot be dated before its wing entered service")
