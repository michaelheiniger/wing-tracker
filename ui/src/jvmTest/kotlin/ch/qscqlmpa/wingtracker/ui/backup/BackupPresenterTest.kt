package ch.qscqlmpa.wingtracker.ui.backup

import ch.qscqlmpa.wingtracker.backup.BackupException
import ch.qscqlmpa.wingtracker.backup.BackupProblem
import ch.qscqlmpa.wingtracker.backup.BackupStatus
import ch.qscqlmpa.wingtracker.backup.BackupSummary
import ch.qscqlmpa.wingtracker.backup.CloudProblem
import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.backup.CreateBackup
import ch.qscqlmpa.wingtracker.backup.GetBackupStatus
import ch.qscqlmpa.wingtracker.backup.UploadBackup
import ch.qscqlmpa.wingtracker.backup.UploadedBackup
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.FileSaver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

private const val FILE_NAME = "wing-tracker-backup-2026-10-18.zip"
private const val DESTINATION = "/media/michael/BACKUP/$FILE_NAME"
private val EARLIER_BACKUP = LocalDateTime(2026, 9, 27, 19, 5)
private val NOW = LocalDateTime(2026, 10, 18, 14, 30)
private val SUMMARY = BackupSummary(files = 412, bytes = 18_350_080)
private val BUCKET =
    CloudStorageSettings(
        provider = CloudProvider.MegaS4,
        region = "eu-amsterdam-1",
        endpoint = null,
        bucket = "wing-tracker-backups",
        accessKeyId = "AKIAMEGAS4EXAMPLE",
        keepLast = 10,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class BackupPresenterTest {
    private val getBackupStatus = mockk<GetBackupStatus>()
    private val createBackup = mockk<CreateBackup>()
    private val uploadBackup = mockk<UploadBackup>()
    private val fileSaver = mockk<FileSaver>()
    private val credentials = mockk<CredentialStore>()
    private var flushed = false
    private var created = 0

    private fun TestScope.presenter() =
        BackupPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            BackupUseCases(getBackupStatus, createBackup, mockk(), mockk(), uploadBackup),
            fileSaver,
            "Save the backup",
            CloudSecret(credentials),
            beforeBackup = { flushed = true },
            onCreated = { created++ },
        )

    private fun givenLastBackup(
        at: LocalDateTime?,
        cloud: CloudStorageSettings? = null,
    ) {
        coEvery { getBackupStatus() } returns BackupStatus(at, FILE_NAME, cloud)
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
                assertThat(presenter.outcome).isEqualTo(BackupOutcome.Saved(DESTINATION, SUMMARY))
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
                assertThat(presenter.outcome).isNull()
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
                assertThat(presenter.outcome).isNull()
                assertThat(presenter.lastMadeAt).isEqualTo(EARLIER_BACKUP)
            }
    }

    @Nested
    inner class Upload {
        @Test
        fun `offers uploading once a bucket is set up`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP, BUCKET)
                val presenter = presenter()

                // when
                presenter.load()

                // then
                assertThat(presenter.cloud).isEqualTo(BUCKET)
            }

        @Test
        fun `uploads with the kept secret key and shows where the backup went`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP, BUCKET)
                coEvery { credentials.read("cloud-storage-secret-key") } returns "s3cr3t/K3y"
                val uploaded = UploadedBackup("wing-tracker-backups", "wing-tracker/$FILE_NAME", SUMMARY, 1, null)
                coEvery { uploadBackup("s3cr3t/K3y") } answers {
                    givenLastBackup(NOW, BUCKET)
                    uploaded
                }
                val presenter = presenter()

                // when
                presenter.upload()

                // then
                assertThat(presenter.outcome).isEqualTo(BackupOutcome.Uploaded(uploaded))
                assertThat(presenter.lastMadeAt).isEqualTo(NOW)
                assertThat(flushed).isTrue()
                assertThat(created).isEqualTo(1)
            }

        @Test
        fun `stops an upload the pilot cancels, without a message, and reads the status again`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP, BUCKET)
                coEvery { credentials.read("cloud-storage-secret-key") } returns "s3cr3t/K3y"
                coEvery { uploadBackup(any()) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.load() }
                presenter.upload()
                assertThat(presenter.uploading).isTrue()

                // when
                presenter.cancelUpload()

                // then
                assertThat(presenter.uploading).isFalse()
                assertThat(presenter.busy).isFalse()
                assertThat(presenter.outcome).isNull()
                assertThat(presenter.failure).isNull()
                assertThat(created).isEqualTo(1)
                coVerify(atLeast = 2) { getBackupStatus() }
            }

        @Test
        fun `asks for the secret key again when none is kept any more`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP, BUCKET)
                coEvery { credentials.read(any()) } returns null
                val presenter = presenter()

                // when
                presenter.upload()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.CloudSecretMissing))
                coVerify(exactly = 0) { uploadBackup(any()) }
            }

        @Test
        fun `tells why the storage refused the upload`() =
            runTest {
                // given
                givenLastBackup(EARLIER_BACKUP, BUCKET)
                coEvery { credentials.read(any()) } returns "s3cr3t/K3y"
                coEvery { uploadBackup(any()) } throws
                    CloudStorageException(CloudProblem.AccessDenied, "403 AccessDenied")
                val presenter = presenter()

                // when
                presenter.upload()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.CloudAccessDenied, "403 AccessDenied"))
                assertThat(created).isZero()
            }
    }
}
