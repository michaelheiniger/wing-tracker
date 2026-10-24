package ch.qscqlmpa.wingtracker.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.platform.SystemNotifier
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.notification_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Long enough to read a sentence or two, short enough not to linger over the screen. */
val NOTIFICATION_DURATION = 10.seconds

/** Where a notification shows (ADR 0034). */
enum class NotificationChannel {
    // In the window, over whatever the pilot is doing.
    InApp,

    // In the desktop's notification area, also seen with the window hidden; in the window when the system has none.
    System,
}

/** Something to tell the pilot in passing; the text is a resource, so it is worded in the pilot's language. */
data class AppNotification(
    val message: StringResource,
    val severity: Severity = Severity.Neutral,
    val channel: NotificationChannel = NotificationChannel.InApp,
    // Null keeps it until the pilot closes it.
    val duration: Duration? = NOTIFICATION_DURATION,
)

data class ShownNotification(
    val id: Long,
    val notification: AppNotification,
)

/**
 * Shows notifications to the pilot (ADR 0034). One serves the whole window, so any part of the app can tell
 * something without a place of its own on screen. A notification disappears after its duration, which pauses while
 * the pilot holds it, e.g. with the mouse over it.
 */
class NotificationCenter(
    private val scope: CoroutineScope,
    private val system: SystemNotifier,
    // Injectable so tests control time; the scope's delays must follow the same clock.
    private val timeSource: TimeSource = TimeSource.Monotonic,
    // Resolves a resource outside of a composable, for the system's notification area.
    private val text: suspend (StringResource) -> String = { getString(it) },
) {
    /** Newest last. */
    var shown by mutableStateOf<List<ShownNotification>>(emptyList())
        private set

    private var nextId = 0L
    private val timers = mutableMapOf<Long, Timer>()

    fun post(notification: AppNotification) {
        if (notification.channel == NotificationChannel.System) {
            scope.launch {
                // A desktop without a notification area still gets the message, in the window.
                if (!system.show(
                        text(Res.string.notification_title),
                        text(notification.message),
                    )
                ) {
                    showInApp(notification)
                }
            }
        } else {
            showInApp(notification)
        }
    }

    fun dismiss(id: Long) {
        timers.remove(id)?.job?.cancel()
        shown = shown.filterNot { it.id == id }
    }

    /** Stops the countdown, e.g. while the pilot reads it. */
    fun hold(id: Long) {
        val timer = timers[id] ?: return
        val job = timer.job ?: return
        job.cancel()
        timer.job = null
        timer.remaining -= timer.started.elapsedNow()
    }

    /** Lets the countdown go on with the time that was left. */
    fun release(id: Long) {
        val timer = timers[id] ?: return
        if (timer.job == null) start(id, timer.remaining)
    }

    private fun showInApp(notification: AppNotification) {
        val id = nextId++
        shown = shown + ShownNotification(id, notification)
        notification.duration?.let { start(id, it) }
    }

    private fun start(
        id: Long,
        duration: Duration,
    ) {
        val job =
            scope.launch {
                delay(duration)
                dismiss(id)
            }
        timers[id] = Timer(duration, timeSource.markNow(), job)
    }

    private class Timer(
        var remaining: Duration,
        val started: TimeMark,
        var job: Job?,
    )
}
