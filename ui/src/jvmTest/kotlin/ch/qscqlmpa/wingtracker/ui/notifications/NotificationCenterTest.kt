package ch.qscqlmpa.wingtracker.ui.notifications

import ch.qscqlmpa.wingtracker.ui.components.Severity
import ch.qscqlmpa.wingtracker.ui.platform.SystemNotifier
import ch.qscqlmpa.wingtracker.ui.resources.Res
import ch.qscqlmpa.wingtracker.ui.resources.notification_title
import ch.qscqlmpa.wingtracker.ui.resources.sync_start_offline
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

private val OFFLINE = AppNotification(Res.string.sync_start_offline, Severity.Warning)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCenterTest {
    private val system = mockk<SystemNotifier>().also { every { it.show(any(), any()) } returns true }

    // Resources cannot be read in unit tests, so their key stands in for the text.
    private fun TestScope.center() = NotificationCenter(backgroundScope, system, testScheduler.timeSource) { it.key }

    @Nested
    inner class Post {
        @Test
        fun `shows a notification in the window and dismisses it after its duration`() =
            runTest {
                // given
                val center = center()

                // when
                center.post(OFFLINE)
                advanceTimeBy(NOTIFICATION_DURATION - 1.seconds)
                val stillShown = center.shown.map { it.notification }
                advanceTimeBy(2.seconds)

                // then
                assertThat(stillShown).containsExactly(OFFLINE)
                assertThat(center.shown).isEmpty()
            }

        @Test
        fun `keeps a notification without duration until it is closed`() =
            runTest {
                // given
                val center = center()
                center.post(OFFLINE.copy(duration = null))

                // when
                advanceTimeBy(NOTIFICATION_DURATION * 10)

                // then
                assertThat(center.shown).hasSize(1)
            }

        @Test
        fun `shows several at once, newest last`() =
            runTest {
                // given
                val center = center()
                val second = OFFLINE.copy(severity = Severity.Critical)

                // when
                center.post(OFFLINE)
                center.post(second)

                // then
                assertThat(center.shown.map { it.notification }).containsExactly(OFFLINE, second)
            }

        @Test
        fun `hands a system notification to the desktop instead of the window`() =
            runTest {
                // given
                val center = center()

                // when
                center.post(OFFLINE.copy(channel = NotificationChannel.System))
                runCurrent()

                // then
                verify(
                    exactly = 1,
                ) { system.show(Res.string.notification_title.key, Res.string.sync_start_offline.key) }
                assertThat(center.shown).isEmpty()
            }

        @Test
        fun `shows a system notification in the window when the desktop has no notification area`() =
            runTest {
                // given
                every { system.show(any(), any()) } returns false
                val center = center()

                // when
                center.post(OFFLINE.copy(channel = NotificationChannel.System))
                runCurrent()

                // then
                assertThat(center.shown).hasSize(1)
            }
    }

    @Nested
    inner class Dismiss {
        @Test
        fun `closes a notification at once`() =
            runTest {
                // given
                val center = center()
                center.post(OFFLINE)

                // when
                center.dismiss(center.shown.single().id)

                // then
                assertThat(center.shown).isEmpty()
            }
    }

    @Nested
    inner class HoldAndRelease {
        @Test
        fun `pauses the countdown while held, then goes on with the time that was left`() =
            runTest {
                // given
                val center = center()
                center.post(OFFLINE)
                val id = center.shown.single().id
                advanceTimeBy(4.seconds)

                // when
                center.hold(id)
                advanceTimeBy(NOTIFICATION_DURATION * 3)
                val heldShown = center.shown.size
                center.release(id)
                advanceTimeBy(NOTIFICATION_DURATION - 4.seconds - 1.seconds)
                val beforeEnd = center.shown.size
                advanceTimeBy(2.seconds)

                // then
                assertThat(heldShown).isEqualTo(1)
                assertThat(beforeEnd).isEqualTo(1)
                assertThat(center.shown).isEmpty()
            }
    }
}
