package ch.qscqlmpa.wingtracker.gear.wing

class WingNotFoundException(
    id: WingId,
) : NoSuchElementException("No wing with id ${id.value}")
