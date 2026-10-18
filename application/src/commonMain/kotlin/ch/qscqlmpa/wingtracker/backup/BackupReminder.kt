package ch.qscqlmpa.wingtracker.backup

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * When to remind the pilot to back up (ADR 0031). It can be turned off, e.g. by a pilot whose data folder already
 * sits in a synchronised or backed-up folder (ADR 0020).
 */
data class BackupReminder(
    val enabled: Boolean = true,
    val days: Int = DEFAULT_DAYS,
) {
    init {
        require(days > 0) { "The days between backups must be positive" }
    }

    /**
     * Whether a backup is due [today], given the day the last one was made. Without any backup, only once there is
     * something to lose, so a fresh installation does not nag.
     */
    fun isDue(
        lastMadeOn: LocalDate?,
        today: LocalDate,
        hasData: Boolean,
    ): Boolean =
        when {
            !enabled -> false
            lastMadeOn == null -> hasData
            else -> lastMadeOn.plus(days, DateTimeUnit.DAY) <= today
        }

    companion object {
        const val DEFAULT_DAYS = 30
    }
}

/** A backup the pilot should make; [lastMadeOn] is null when none was made yet. */
data class BackupDue(
    val lastMadeOn: LocalDate?,
)
