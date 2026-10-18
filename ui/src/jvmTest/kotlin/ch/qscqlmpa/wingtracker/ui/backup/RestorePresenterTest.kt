package ch.qscqlmpa.wingtracker.ui.backup

import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupPreview
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import ch.qscqlmpa.wingtracker.backup.CloudBackup
import ch.qscqlmpa.wingtracker.backup.DiscardTemporaryBackup
import ch.qscqlmpa.wingtracker.backup.DownloadCloudBackup
import ch.qscqlmpa.wingtracker.backup.InspectBackup
import ch.qscqlmpa.wingtracker.backup.ListCloudBackups
import ch.qscqlmpa.wingtracker.backup.RestoreBackup
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.DataLocation
import ch.qscqlmpa.wingtracker.ui.platform.FilePicker
import ch.qscqlmpa.wingtracker.ui.platform.FolderCheck
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

private const val CURRENT = "/home/michael/.local/share/wing-tracker"
private const val ARCHIVE = "/media/michael/BACKUP/wing-tracker-backup-2026-10-18.zip"
private const val TARGET = "/home/michael/Documents/Parapente/WingTracker"

private val PREVIEW =
    BackupPreview(
        madeAt = LocalDateTime(2026, 10, 18, 14, 30),
        appVersion = "0.4.0",
        summary = BackupSummary(files = 412, bytes = 18_350_080),
        madeByNewerVersion = false,
    )
private val CONFIRM = RestoreStep.Confirm(ARCHIVE, PREVIEW)
private const val CLOUD_KEY = "wing-tracker/wing-tracker-backup-2026-10-18-1430.zip"
private const val DOWNLOADED = "/tmp/wing-tracker-backup-81273/wing-tracker-backup-2026-10-18-1430.zip"

@OptIn(ExperimentalCoroutinesApi::class)
class RestorePresenterTest {
    private val inspectBackup = mockk<InspectBackup>()
    private val restoreBackup = mockk<RestoreBackup>(relaxed = true)
    private val filePicker = mockk<FilePicker>()
    private val location =
        mockk<DataLocation>(relaxUnitFun = true).also {
            every { it.path } returns CURRENT
        }
    private val listCloudBackups = mockk<ListCloudBackups>()
    private val downloadCloudBackup = mockk<DownloadCloudBackup>()
    private val discardTemporaryBackup = mockk<DiscardTemporaryBackup>(relaxed = true)
    private val credentials = mockk<CredentialStore>().also { coEvery { it.read(any()) } returns "s3cr3t/K3y" }
    private var flushed = false

    private fun TestScope.presenter() =
        RestorePresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            BackupUseCases(mockk(), mockk(), inspectBackup, restoreBackup, mockk()),
            filePicker,
            location,
            RestoreTitles(pickBackup = "Choose the backup", chooseFolder = "Choose a folder"),
            CloudRestoreUseCases(listCloudBackups, downloadCloudBackup, discardTemporaryBackup),
            CloudSecret(credentials),
        ) { flushed = true }

    private fun givenPickedBackup() {
        coEvery { filePicker.pickPath("Choose the backup", setOf("zip")) } returns ARCHIVE
        coEvery { inspectBackup(ARCHIVE) } returns PREVIEW
    }

    private fun givenChosenFolder(check: FolderCheck) {
        coEvery { location.chooseFolder("Choose a folder") } returns TARGET
        coEvery { location.inspect(TARGET) } returns check
    }

    @Nested
    inner class Start {
        @Test
        fun `shows the checked backup for confirmation`() =
            runTest {
                // given
                givenPickedBackup()
                val presenter = presenter()

                // when
                presenter.start()

                // then
                assertThat(presenter.step).isEqualTo(CONFIRM)
            }

        @Test
        fun `does nothing when the pilot cancels the file dialog`() =
            runTest {
                // given
                coEvery { filePicker.pickPath(any(), any()) } returns null
                val presenter = presenter()

                // when
                presenter.start()

                // then
                assertThat(presenter.step).isNull()
                coVerify(exactly = 0) { inspectBackup(any()) }
            }

        @Test
        fun `explains why a damaged backup cannot be restored`() =
            runTest {
                // given
                coEvery { filePicker.pickPath(any(), any()) } returns ARCHIVE
                coEvery { inspectBackup(ARCHIVE) } throws BackupException(BackupProblem.Damaged, "flights differ")
                val presenter = presenter()

                // when
                presenter.start()

                // then
                assertThat(presenter.step).isNull()
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.BackupDamaged, "flights differ"))
            }
    }

    @Nested
    inner class ChooseFolder {
        @Test
        fun `restores into the chosen empty folder and switches to it on restart`() =
            runTest {
                // given
                givenPickedBackup()
                givenChosenFolder(FolderCheck.Empty)
                val presenter = presenter().also { it.start() }

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.step).isEqualTo(RestoreStep.Restored(TARGET))
                assertThat(flushed).isTrue()
                coVerifyOrder {
                    restoreBackup(ARCHIVE, TARGET)
                    location.change(TARGET, moveData = false)
                }
            }

        @Test
        fun `refuses a folder that already holds data`() =
            runTest {
                // given
                givenPickedBackup()
                givenChosenFolder(FolderCheck.HasData)
                val presenter = presenter().also { it.start() }

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.step).isEqualTo(CONFIRM)
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.RestoreFolderNotEmpty, TARGET))
                coVerify(exactly = 0) { restoreBackup(any(), any()) }
            }

        @Test
        fun `refuses the folder in use`() =
            runTest {
                // given
                givenPickedBackup()
                givenChosenFolder(FolderCheck.Same)
                val presenter = presenter().also { it.start() }

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.DataFolderSame)
                coVerify(exactly = 0) { restoreBackup(any(), any()) }
            }

        @Test
        fun `goes back to the confirmation and keeps the current folder when restoring fails`() =
            runTest {
                // given
                givenPickedBackup()
                givenChosenFolder(FolderCheck.Empty)
                coEvery { restoreBackup(ARCHIVE, TARGET) } throws IOException("No space left on device")
                val presenter = presenter().also { it.start() }

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.step).isEqualTo(CONFIRM)
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.RestoreBackup, "No space left on device"))
                coVerify(exactly = 0) { location.change(any(), any()) }
            }
    }

    @Nested
    inner class CancelAndRestart {
        @Test
        fun `cancelling closes the restore without touching anything`() =
            runTest {
                // given
                givenPickedBackup()
                val presenter = presenter().also { it.start() }

                // when
                presenter.cancel()

                // then
                assertThat(presenter.step).isNull()
                coVerify(exactly = 0) { restoreBackup(any(), any()) }
            }

        @Test
        fun `restarting hands over to the platform`() =
            runTest {
                // given
                val presenter = presenter()

                // when
                presenter.restart()

                // then
                verify(exactly = 1) { location.restart() }
            }
    }

    @Nested
    inner class FromCloud {
        private val newest = CloudBackup(CLOUD_KEY, LocalDateTime(2026, 10, 18, 14, 30, 20), 18_350_080)

        private fun givenCloudBackup() {
            coEvery { listCloudBackups("s3cr3t/K3y") } returns listOf(newest)
            coEvery { downloadCloudBackup("s3cr3t/K3y", CLOUD_KEY) } returns DOWNLOADED
            coEvery { inspectBackup(DOWNLOADED) } returns PREVIEW
        }

        @Test
        fun `lists the backups in the bucket`() =
            runTest {
                // given
                givenCloudBackup()
                val presenter = presenter()

                // when
                presenter.startFromCloud()

                // then
                assertThat(presenter.step).isEqualTo(RestoreStep.ChooseCloud(listOf(newest)))
            }

        @Test
        fun `downloads and checks the chosen backup, then confirms it like a file`() =
            runTest {
                // given
                givenCloudBackup()
                val presenter = presenter().also { it.startFromCloud() }

                // when
                presenter.chooseCloudBackup(newest)

                // then
                assertThat(presenter.step).isEqualTo(RestoreStep.Confirm(DOWNLOADED, PREVIEW, downloaded = true))
            }

        @Test
        fun `goes back to the list and removes the download when it turns out damaged`() =
            runTest {
                // given
                givenCloudBackup()
                coEvery { inspectBackup(DOWNLOADED) } throws BackupException(BackupProblem.Damaged, "flights differ")
                val presenter = presenter().also { it.startFromCloud() }

                // when
                presenter.chooseCloudBackup(newest)

                // then
                assertThat(presenter.step).isEqualTo(RestoreStep.ChooseCloud(listOf(newest)))
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.BackupDamaged)
                coVerify(exactly = 1) { discardTemporaryBackup(DOWNLOADED) }
            }

        @Test
        fun `goes back to the list without a message when the pilot cancels the download`() =
            runTest {
                // given
                givenCloudBackup()
                coEvery { downloadCloudBackup(any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.startFromCloud() }
                presenter.chooseCloudBackup(newest)
                assertThat(presenter.step).isEqualTo(RestoreStep.Downloading(newest))

                // when
                presenter.cancelDownload()

                // then
                assertThat(presenter.step).isEqualTo(RestoreStep.ChooseCloud(listOf(newest)))
                assertThat(presenter.failure).isNull()
            }

        @Test
        fun `removes the download once restored`() =
            runTest {
                // given
                givenCloudBackup()
                givenChosenFolder(FolderCheck.Empty)
                val presenter = presenter().also { it.startFromCloud() }
                presenter.chooseCloudBackup(newest)

                // when
                presenter.chooseFolder()

                // then
                assertThat(presenter.step).isEqualTo(RestoreStep.Restored(TARGET))
                coVerify(exactly = 1) { restoreBackup(DOWNLOADED, TARGET) }
                coVerify(exactly = 1) { discardTemporaryBackup(DOWNLOADED) }
            }

        @Test
        fun `removes the download when the pilot cancels`() =
            runTest {
                // given
                givenCloudBackup()
                val presenter = presenter().also { it.startFromCloud() }
                presenter.chooseCloudBackup(newest)

                // when
                presenter.cancel()

                // then
                assertThat(presenter.step).isNull()
                coVerify(exactly = 1) { discardTemporaryBackup(DOWNLOADED) }
            }

        @Test
        fun `asks for the secret key again when none is kept any more`() =
            runTest {
                // given
                coEvery { credentials.read(any()) } returns null
                val presenter = presenter()

                // when
                presenter.startFromCloud()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.CloudSecretMissing))
                assertThat(presenter.step).isNull()
            }
    }
}
