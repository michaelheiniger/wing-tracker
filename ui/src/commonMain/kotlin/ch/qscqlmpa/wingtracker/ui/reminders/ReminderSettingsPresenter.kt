package ch.qscqlmpa.wingtracker.ui.reminders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.reminders.CalendarEntry
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.calendar_calendar_name
import ch.qscqlmpa.wingtracker.ui.resources.settings_calendar_pick_title
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

sealed interface ExportOutcome {
    data class Saved(
        val fileName: String,
    ) : ExportOutcome

    data object NothingToExport : ExportOutcome
}

/** The wording of the exported file; injectable so the logic can be tested without resource files. */
class CalendarWording(
    val describe: suspend (CalendarEntry) -> IcsEvent = { it.toIcsEvent() },
    val calendarName: suspend () -> String = { getString(Res.string.calendar_calendar_name) },
    val dialogTitle: suspend () -> String = { getString(Res.string.settings_calendar_pick_title) },
)

private const val CALENDAR_FILE_NAME = "wing-tracker.ics"

// Long enough that typing "21" stores 21, not 2 and then 21.
private const val SAVE_DELAY_MILLIS = 500L

class ReminderSettingsPresenter(
    private val scope: CoroutineScope,
    private val useCases: RemindersUseCases,
    private val fileSaver: FileSaver,
    private val wording: CalendarWording = CalendarWording(),
    private val onLeadTimesSaved: () -> Unit = {},
) {
    var input by mutableStateOf(ReminderSettingsInput())
        private set
    var errors by mutableStateOf<Map<ReminderSettingsField, FormError>>(emptyMap())
        private set
    var saved by mutableStateOf(false)
        private set
    var export by mutableStateOf<ExportOutcome?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    private var pendingSave: Job? = null

    // Two saves are two read-modify-writes of the settings file; overlapping, one could undo the other.
    private val writing = Mutex()

    fun load() =
        launchGuarded(FailureKind.LoadSettings) {
            val settings = useCases.getAppSettings()
            input = ReminderSettingsInput.from(settings.reminderLeadTimes, settings.backupReminder)
        }

    /** Saves what is typed as soon as it is valid, like the other settings, which apply at once. */
    fun change(update: (ReminderSettingsInput) -> ReminderSettingsInput) {
        input = update(input)
        saved = false
        // A value typed since makes a save still waiting obsolete.
        pendingSave?.cancel()
        when (val result = input.validate()) {
            is ReminderSettingsResult.Invalid -> {
                errors = result.errors
            }

            is ReminderSettingsResult.Valid -> {
                errors = emptyMap()
                pendingSave =
                    launchGuarded(FailureKind.SaveSettings) {
                        delay(SAVE_DELAY_MILLIS)
                        // Once begun, a save is finished, so the file never holds half of it.
                        withContext(NonCancellable) {
                            writing.withLock {
                                useCases.saveReminderLeadTimes(result.leadTimes)
                                useCases.saveBackupReminder(result.backup)
                            }
                        }
                        saved = true
                        onLeadTimesSaved()
                    }
            }
        }
    }

    fun exportCalendar() =
        launchGuarded(FailureKind.ExportCalendar) {
            val calendar = useCases.getCalendarEntries()
            export =
                if (calendar.entries.isEmpty()) {
                    ExportOutcome.NothingToExport
                } else {
                    val text =
                        renderIcs(
                            calendar.entries.map {
                                wording.describe(it)
                            },
                            calendar.leadDays,
                            calendar.generatedAt,
                            wording.calendarName(),
                        )
                    // Cancelling the save dialog is not a result worth announcing.
                    fileSaver
                        .save(
                            wording.dialogTitle(),
                            CALENDAR_FILE_NAME,
                            text.encodeToByteArray(),
                        )?.let { ExportOutcome.Saved(it) }
                }
        }

    private fun launchGuarded(
        kind: FailureKind,
        block: suspend () -> Unit,
    ): Job =
        scope.launch {
            try {
                failure = null
                block()
            } catch (e: CancellationException) {
                throw e
                // Any storage or file failure must reach the user as a message; it must never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(kind)
            }
        }
}
