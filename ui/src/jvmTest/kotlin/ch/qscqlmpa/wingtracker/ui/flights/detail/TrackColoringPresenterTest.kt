package ch.qscqlmpa.wingtracker.ui.flights.detail

import ch.qscqlmpa.wingtracker.settings.AppSettings
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveTrackColoring
import ch.qscqlmpa.wingtracker.settings.TrackColoring
import ch.qscqlmpa.wingtracker.ui.FailureKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TrackColoringPresenterTest {
    private val getAppSettings = mockk<GetAppSettings>()
    private val saveTrackColoring = mockk<SaveTrackColoring>(relaxUnitFun = true)

    private fun TestScope.presenter() =
        TrackColoringPresenter(TestScope(UnconfinedTestDispatcher(testScheduler)), getAppSettings, saveTrackColoring)

    @Nested
    inner class Load {
        @Test
        fun `starts with the colouring the pilot chose last time`() =
            runTest {
                // given
                coEvery { getAppSettings() } returns AppSettings(trackColoring = TrackColoring.ClimbSink)
                val presenter = presenter()

                // when
                presenter.load()

                // then
                assertThat(presenter.coloring).isEqualTo(TrackColoring.ClimbSink)
            }
    }

    @Nested
    inner class Choose {
        @Test
        fun `shows the chosen colouring and remembers it`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.choose(TrackColoring.ClimbSink)

                // then
                assertThat(presenter.coloring).isEqualTo(TrackColoring.ClimbSink)
                coVerify(exactly = 1) { saveTrackColoring(TrackColoring.ClimbSink) }
            }

        @Test
        fun `keeps showing the choice and reports that it could not be remembered`() =
            runTest {
                // given
                coEvery { saveTrackColoring(any()) } throws IOException("No space left on device")
                val presenter = presenter()

                // when
                presenter.choose(TrackColoring.ClimbSink)

                // then
                assertThat(presenter.coloring).isEqualTo(TrackColoring.ClimbSink)
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SaveSettings)
            }
    }
}
