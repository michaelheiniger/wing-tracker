package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.reminders.GetStartNotification
import ch.qscqlmpa.wingtracker.reminders.MarkStartNotificationShown
import ch.qscqlmpa.wingtracker.ui.platform.SystemNotifier
import ch.qscqlmpa.wingtracker.ui.reminders.toText
import java.awt.Color
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.Timer
import javax.imageio.ImageIO
import kotlin.concurrent.schedule

/** Shows a message through the system tray, if the desktop has one. */
class TrayNotifier : SystemNotifier {
    override fun show(
        title: String,
        message: String,
    ): Boolean {
        if (!SystemTray.isSupported()) return false
        return try {
            val tray = SystemTray.getSystemTray()
            val icon = TrayIcon(iconImage(), title)
            tray.add(icon)
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
            // The icon only exists to carry the message, so it is removed again shortly after.
            Timer(true).schedule(REMOVE_AFTER_MILLIS) { tray.remove(icon) }
            true
        } catch (_: java.awt.AWTException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    /** The application icon; a plain dot only if the resource is missing, so a notification is never lost over it. */
    private fun iconImage(): Image =
        TrayNotifier::class.java.getResource(ICON_RESOURCE)?.let { ImageIO.read(it) } ?: fallbackIcon()

    private fun fallbackIcon(): BufferedImage =
        BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            graphics.color = Color(ICON_COLOR)
            graphics.fillOval(0, 0, ICON_SIZE, ICON_SIZE)
            graphics.dispose()
        }

    private companion object {
        const val ICON_RESOURCE = "/tray-icon.png"
        const val ICON_SIZE = 16
        const val ICON_COLOR = 0x2A78D6
        const val REMOVE_AFTER_MILLIS = 15_000L
    }
}

/**
 * Runs once at start: if something needs attention and nothing was shown today, show one notification. A day is only
 * marked as notified when the system accepted the message, so a failure is tried again at the next start.
 */
class StartupReminder(
    private val getStartNotification: GetStartNotification,
    private val markShown: MarkStartNotificationShown,
    private val notifier: SystemNotifier,
) {
    suspend operator fun invoke() {
        val notification = getStartNotification() ?: return
        val (title, message) = notification.toText()
        if (notifier.show(title, message)) markShown()
    }
}
