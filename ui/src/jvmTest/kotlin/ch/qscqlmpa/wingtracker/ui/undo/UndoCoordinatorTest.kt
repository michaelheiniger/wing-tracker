package ch.qscqlmpa.wingtracker.ui.undo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoCoordinatorTest {
    private val undo = UndoCoordinator()
    private val log = mutableListOf<String>()

    private fun kotlinx.coroutines.test.TestScope.offer(name: String) =
        launch(UnconfinedTestDispatcher(testScheduler)) {
            undo.offer(TEXT, onUndo = { log += "undo $name" }, onExpire = { log += "apply $name" })
        }

    @Nested
    inner class Offering {
        @Test
        fun `applies the action when the notification goes away`() =
            runTest {
                // given
                offer("a")

                // when
                undo.expire()

                // then
                assertThat(log).containsExactly("apply a")
            }

        @Test
        fun `takes the action back when the pilot presses undo`() =
            runTest {
                // given
                offer("a")

                // when
                undo.pressUndo()

                // then
                assertThat(log).containsExactly("undo a")
            }

        @Test
        fun `waits until the notification is gone`() =
            runTest {
                // given / when
                offer("a")

                // then
                assertThat(log).isEmpty()
                undo.expire()
            }

        @Test
        fun `applies the earlier action at once when another one starts`() =
            runTest {
                // given
                offer("a")

                // when
                offer("b")

                // then
                assertThat(log).containsExactly("apply a")
                undo.expire()
            }

        @Test
        fun `applies the action when the caller leaves the screen`() =
            runTest {
                // given
                val job = offer("a")

                // when
                job.cancel()

                // then
                assertThat(log).containsExactly("apply a")
            }
    }

    @Nested
    inner class Flushing {
        @Test
        fun `applies the waiting action now, for example when the window closes`() =
            runTest {
                // given
                offer("a")

                // when
                undo.flush()

                // then
                assertThat(log).containsExactly("apply a")
            }

        @Test
        fun `does nothing when nothing waits`() =
            runTest {
                // when
                undo.flush()

                // then
                assertThat(log).isEmpty()
            }
    }
}
