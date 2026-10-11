package ch.qscqlmpa.wingtracker.ui.settings

import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

private const val CURRENT = "/home/michael/.local/share/wing-tracker"
private const val BACKUP = "/media/backup/wing-tracker"

@OptIn(ExperimentalCoroutinesApi::class)
class DataLocationPresenterTest {
    private val location =
        mockk<DataLocation>(relaxUnitFun = true).also {
            every { it.path } returns CURRENT
            every { it.defaultPath } returns CURRENT
            every { it.isDefault } returns true
        }
    private var flushed = false

    private fun TestScope.presenter() =
        DataLocationPresenter(TestScope(UnconfinedTestDispatcher(testScheduler)), location, "Choose") { flushed = true }

    @Nested
    inner class ShowingAndOpening {
        @Test
        fun `shows the folder in use`() =
            runTest {
                assertThat(presenter().path).isEqualTo(CURRENT)
            }

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
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.OpenDataFolder, "no file manager"))
            }
    }

    @Nested
    inner class Choosing {
        @Test
        fun `offers to move the data into an empty folder`() =
            runTest {
                // given
                coEvery { location.chooseFolder("Choose") } returns BACKUP
                coEvery { location.inspect(BACKUP) } returns FolderCheck.Empty
                val presenter = presenter()

                // when
                presenter.choose()

                // then
                assertThat(presenter.proposal).isEqualTo(FolderProposal(BACKUP, canMove = true))
            }

        @Test
        fun `offers only to use a folder that already holds data`() =
            runTest {
                // given
                coEvery { location.chooseFolder("Choose") } returns BACKUP
                coEvery { location.inspect(BACKUP) } returns FolderCheck.HasData
                val presenter = presenter()

                // when
                presenter.choose()

                // then
                assertThat(presenter.proposal).isEqualTo(FolderProposal(BACKUP, canMove = false))
            }

        @Test
        fun `does nothing when the folder dialog is cancelled`() =
            runTest {
                // given
                coEvery { location.chooseFolder("Choose") } returns null
                val presenter = presenter()

                // when
                presenter.choose()

                // then
                assertThat(presenter.proposal).isNull()
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `explains why a folder cannot be used`() =
            runTest {
                // given
                coEvery { location.chooseFolder("Choose") } returns BACKUP
                val presenter = presenter()

                // when / then
                listOf(
                    FolderCheck.Same to FailureKind.DataFolderSame,
                    FolderCheck.Unusable to FailureKind.DataFolderUnusable,
                    FolderCheck.Overlaps to FailureKind.DataFolderOverlaps,
                ).forEach { (check, kind) ->
                    coEvery { location.inspect(BACKUP) } returns check
                    presenter.choose()
                    assertThat(presenter.failure?.kind).isEqualTo(kind)
                    assertThat(presenter.proposal).isNull()
                }
            }

        @Test
        fun `goes back to the default folder through the same question`() =
            runTest {
                // given
                every { location.defaultPath } returns "/home/michael/.local/share/wing-tracker"
                coEvery { location.inspect(any()) } returns FolderCheck.HasData
                val presenter = presenter()

                // when
                presenter.useDefault()

                // then
                assertThat(presenter.proposal?.folder).isEqualTo("/home/michael/.local/share/wing-tracker")
            }
    }

    @Nested
    inner class Confirming {
        private suspend fun TestScope.proposed(): DataLocationPresenter {
            coEvery { location.chooseFolder("Choose") } returns BACKUP
            coEvery { location.inspect(BACKUP) } returns FolderCheck.Empty
            return presenter().also { it.choose() }
        }

        @Test
        fun `moves the data after letting pending actions finish, then asks for a restart`() =
            runTest {
                // given
                val presenter = proposed()

                // when
                presenter.confirm(moveData = true)

                // then
                assertThat(flushed).isTrue()
                coVerify(exactly = 1) { location.change(BACKUP, true) }
                assertThat(presenter.restartRequired).isTrue()
                assertThat(presenter.proposal).isNull()
            }

        @Test
        fun `switches without moving when asked to`() =
            runTest {
                // given
                val presenter = proposed()

                // when
                presenter.confirm(moveData = false)

                // then
                coVerify(exactly = 1) { location.change(BACKUP, false) }
            }

        @Test
        fun `does not ask for a restart when changing failed`() =
            runTest {
                // given
                coEvery { location.change(any(), any()) } throws IOException("Disk full")
                val presenter = proposed()

                // when
                presenter.confirm(moveData = true)

                // then
                assertThat(presenter.restartRequired).isFalse()
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.ChangeDataFolder, "Disk full"))
                assertThat(presenter.busy).isFalse()
            }

        @Test
        fun `changes nothing when the pilot cancels`() =
            runTest {
                // given
                val presenter = proposed()

                // when
                presenter.cancel()
                presenter.confirm(moveData = true)

                // then
                coVerify(exactly = 0) { location.change(any(), any()) }
            }

        @Test
        fun `restarts the application`() =
            runTest {
                // when
                presenter().restart()

                // then
                verify(exactly = 1) { location.restart() }
            }
    }
}
