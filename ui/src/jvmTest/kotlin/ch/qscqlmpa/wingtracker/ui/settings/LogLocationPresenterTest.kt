package ch.qscqlmpa.wingtracker.ui.settings

import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.LogLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

private const val LOGS = "/home/michael/.local/state/wing-tracker/logs"

@OptIn(ExperimentalCoroutinesApi::class)
class LogLocationPresenterTest {
    private val location =
        mockk<LogLocation>(relaxUnitFun = true).also {
            every { it.path } returns LOGS
        }

    private fun TestScope.presenter() =
        LogLocationPresenter(TestScope(UnconfinedTestDispatcher(testScheduler)), location)

    @Nested
    inner class Path {
        @Test
        fun `is the folder the log is written to`() =
            runTest {
                assertThat(presenter().path).isEqualTo(LOGS)
            }
    }

    @Nested
    inner class Open {
        @Test
        fun `opens the folder in the file manager`() =
            runTest {
                // when
                presenter().open()

                // then
                coVerify(exactly = 1) { location.open() }
            }

        @Test
        fun `shows why the folder could not be opened`() =
            runTest {
                // given
                coEvery { location.open() } throws IOException("no file manager")
                val presenter = presenter()

                // when
                presenter.open()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.OpenLogFolder, "no file manager"))
            }

        @Test
        fun `clears an earlier failure once the folder opens`() =
            runTest {
                // given
                coEvery { location.open() } throws IOException("no file manager") andThen Unit
                val presenter = presenter()
                presenter.open()

                // when
                presenter.open()

                // then
                assertThat(presenter.failure).isNull()
            }
    }
}
