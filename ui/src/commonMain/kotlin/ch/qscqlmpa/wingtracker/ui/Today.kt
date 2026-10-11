package ch.qscqlmpa.wingtracker.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The pilot's current day, used to pre-fill dates in forms; rules that depend on it run in the application layer. */
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
