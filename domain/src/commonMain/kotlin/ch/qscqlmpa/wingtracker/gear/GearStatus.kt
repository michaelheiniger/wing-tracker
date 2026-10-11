package ch.qscqlmpa.wingtracker.gear

import kotlinx.datetime.LocalDate

/** Retired gear keeps its history so past statistics stay correct (ADR 0013). */
sealed interface GearStatus {
    data object Active : GearStatus

    data class Retired(
        val on: LocalDate,
        val reason: String? = null,
    ) : GearStatus
}
