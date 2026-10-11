package ch.qscqlmpa.wingtracker

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
}
