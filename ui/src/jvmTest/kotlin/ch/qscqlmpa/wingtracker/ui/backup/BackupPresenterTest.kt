package ch.qscqlmpa.wingtracker.ui.backup

import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.BackupStatus
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import ch.qscqlmpa.wingtracker.backup.CreateBackup
import ch.qscqlmpa.wingtracker.backup.GetBackupStatus
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

private const val FILE_NAME = "wing-tracker-backup-2026-10-18.zip"
private const val DESTINATION = "/media/michael/BACKUP/$FILE_NAME"
private val EARLIER_BACKUP = LocalDateTime(2026, 9, 27, 19, 5)
private val NOW = LocalDateTime(2026, 10, 18, 14, 30)
private val SUMMARY = BackupSummary(files = 412, bytes = 18_350_080)

@OptIn(ExperimentalCoroutinesApi::class)
class BackupPresenterTest {
    private val getBackupStatus = mockk<GetBackupStatus>()
    private val createBackup = mockk<CreateBackup>()
    private val fileSaver = mockk<FileSaver>()
    private var flushed = false

    private fun TestScope.presenter() =
        BackupPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            BackupUseCases(getBackupStatus, createBackup),
            fileSaver,
            "Save the backup",
        ) { flushed = true }

    private fun givenLastBackup(at: LocalDateTime?) {
        coEvery { getBackupStatus() } returns BackupStatus(at, FILE_NAME)
    }

    @Nested
    inner class Load {
        @Test
        fun `shows when the last backup was made`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP)
                val presenter = presenter()

                // when
                presenter.load()

                // then
                assertThat(presenter.lastMadeAt).isEqualTo(EARLIER_BACKUP)
            }
    }

    @Nested
    inner class Create {
        @Test
        fun `saves the backup where the pilot chose and shows what went into it`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP)
                coEvery { fileSaver.choose("Save the backup", FILE_NAME) } returns DESTINATION
                coEvery { createBackup(DESTINATION) } answers {
                    givenLastBackup(NOW)
                    SUMMARY
                }
                val presenter = presenter()

                // when
                presenter.create()

                // then
                assertThat(presenter.saved).isEqualTo(SavedBackup(DESTINATION, SUMMARY))
                assertThat(presenter.lastMadeAt).isEqualTo(NOW)
                assertThat(presenter.busy).isFalse()
                assertThat(flushed).isTrue()
            }

        @Test
        fun `does nothing when the pilot cancels the save dialog`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP)
                coEvery { fileSaver.choose(any(), any()) } returns null
                val presenter = presenter()

                // when
                presenter.create()

                // then
                coVerify(exactly = 0) { createBackup(any()) }
                assertThat(presenter.saved).isNull()
                assertThat(presenter.failure).isNull()
                assertThat(flushed).isFalse()
            }

        @Test
        fun `explains that a backup cannot go inside the data folder`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP)
                coEvery { fileSaver.choose(any(), any()) } returns DESTINATION
                coEvery { createBackup(DESTINATION) } throws
                    BackupException(BackupProblem.InsideDataFolder, DESTINATION)
                val presenter = presenter()

                // when
                presenter.create()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.BackupInsideDataFolder, DESTINATION))
                assertThat(presenter.busy).isFalse()
            }

        @Test
        fun `reports a backup that could not be written`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP)
                coEvery { fileSaver.choose(any(), any()) } returns DESTINATION
                coEvery { createBackup(DESTINATION) } throws IOException("No space left on device")
                val presenter = presenter()
                presenter.load()

                // when
                presenter.create()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.CreateBackup, "No space left on device"))
                assertThat(presenter.saved).isNull()
                assertThat(presenter.lastMadeAt).isEqualTo(EARLIER_BACKUP)
            }
    }
}
