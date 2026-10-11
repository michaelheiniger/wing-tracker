package ch.qscqlmpa.wingtracker.gear

import kotlinx.datetime.LocalDate

/** The purchase date is optional because second-hand gear often has no known one. */
data class Acquisition(
    val condition: AcquisitionCondition,
    val purchaseDate: LocalDate? = null,
    val price: Price? = null,
)

enum class AcquisitionCondition { New, SecondHand }
