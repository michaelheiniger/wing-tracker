package ch.qscqlmpa.wingtracker

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/** Single source of "now" and "today", so rules that depend on the date can be tested with a fixed clock. */
class AppClock(
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun now(): Instant = clock.now()

    fun today(): LocalDate = clock.todayIn(zone)

    /** The pilot's wall-clock time for a moment, in the computer's time zone (ADR 0026). */
    fun local(instant: Instant): LocalDateTime = instant.toLocalDateTime(zone)
}
