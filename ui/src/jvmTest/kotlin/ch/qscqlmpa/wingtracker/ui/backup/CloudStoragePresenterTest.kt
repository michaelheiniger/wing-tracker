package ch.qscqlmpa.wingtracker.ui.backup

import ch.qscqlmpa.wingtracker.backup.CheckCloudStorage
import ch.qscqlmpa.wingtracker.backup.CloudProblem
import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorage
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.backup.CloudTarget
import ch.qscqlmpa.wingtracker.backup.SaveCloudStorage
import ch.qscqlmpa.wingtracker.settings.GetLogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettings
import ch.qscqlmpa.wingtracker.settings.LogbookSettingsRepository
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.SecretKeeping
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val SECRET = "s3cr3t/K3y"
private const val STORE_KEY = "cloud-storage-secret-key"

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
class CloudStoragePresenterTest {
    private val settings = mockk<LogbookSettingsRepository>(relaxUnitFun = true)
    private val storage = mockk<CloudStorage>(relaxUnitFun = true)
    private val credentials = mockk<CredentialStore>(relaxUnitFun = true)
    private var changed = 0

    private fun TestScope.presenter() =
        CloudStoragePresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            CloudStorageUseCases(GetLogbookSettings(settings), SaveCloudStorage(settings), CheckCloudStorage(storage)),
            CloudSecret(credentials),
        ) { changed++ }

    private fun givenStored(
        cloud: CloudStorageSettings?,
        secret: String?,
    ) {
        coEvery { settings.load() } returns LogbookSettings(cloudStorage = cloud)
        coEvery { credentials.read(STORE_KEY) } returns secret
    }

    @Nested
    inner class Load {
        @Test
        fun `fills the form from the stored bucket, never with the secret key`() =
            runTest {
                // given
                givenStored(BUCKET, SECRET)
                val presenter = presenter()

                // when
                presenter.load()

                // then
                assertThat(presenter.input).isEqualTo(CloudStorageInput.from(BUCKET))
                assertThat(presenter.input.secretKey).isEmpty()
                assertThat(presenter.stored).isEqualTo(BUCKET)
                assertThat(presenter.secretStored).isTrue()
                assertThat(presenter.editing).isFalse()
            }
    }

    @Nested
    inner class CancelEdit {
        @Test
        fun `puts back what is saved and closes the form`() =
            runTest {
                // given
                givenStored(BUCKET, SECRET)
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { it.copy(bucket = "", secretKey = "an3w/S3cr3t") }
                presenter.save()

                // when
                presenter.cancelEdit()

                // then
                assertThat(presenter.input).isEqualTo(CloudStorageInput.from(BUCKET))
                assertThat(presenter.errors).isEmpty()
                assertThat(presenter.editing).isFalse()
                coVerify(exactly = 0) { settings.save(any()) }
            }

        @Test
        fun `stops a running test`() =
            runTest {
                // given
                givenStored(BUCKET, SECRET)
                coEvery { storage.check(any()) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.test()

                // when
                presenter.cancelEdit()

                // then
                assertThat(presenter.busy).isFalse()
                assertThat(presenter.outcome).isNull()
                assertThat(presenter.failure).isNull()
            }
    }

    @Nested
    inner class Save {
        @Test
        fun `keeps the secret key in the system and the rest in the settings`() =
            runTest {
                // given
                givenStored(null, null)
                val presenter = presenter().also { it.load() }
                presenter.change { CloudStorageInput.from(BUCKET).copy(secretKey = SECRET) }

                // when
                presenter.save()

                // then
                coVerify(exactly = 1) { credentials.write(STORE_KEY, SECRET) }
                coVerify(exactly = 1) { settings.save(LogbookSettings(cloudStorage = BUCKET)) }
                assertThat(presenter.outcome).isEqualTo(CloudStorageOutcome.Saved(SecretKeeping.System))
                assertThat(presenter.input.secretKey).isEmpty()
                assertThat(presenter.stored).isEqualTo(BUCKET)
                assertThat(presenter.editing).isFalse()
                assertThat(changed).isEqualTo(1)
            }

        @Test
        fun `saves nothing and keeps the form open while it is incomplete`() =
            runTest {
                // given
                givenStored(null, null)
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { CloudStorageInput.from(BUCKET) }

                // when
                presenter.save()

                // then
                assertThat(presenter.errors).containsOnlyKeys(CloudStorageField.SecretKey)
                assertThat(presenter.editing).isTrue()
                coVerify(exactly = 0) { settings.save(any()) }
            }
    }

    @Nested
    inner class TestConnection {
        @Test
        fun `tries what is typed with the stored secret key`() =
            runTest {
                // given
                givenStored(BUCKET, SECRET)
                val presenter = presenter().also { it.load() }
                presenter.change { it.copy(bucket = "wing-tracker-archive") }

                // when
                presenter.test()

                // then
                coVerify(exactly = 1) {
                    storage.check(
                        CloudTarget(
                            "https://s3.eu-amsterdam-1.megas4.com",
                            "eu-amsterdam-1",
                            "wing-tracker-archive",
                            "AKIAMEGAS4EXAMPLE",
                            SECRET,
                        ),
                    )
                }
                assertThat(presenter.outcome).isEqualTo(CloudStorageOutcome.Connected)
                coVerify(exactly = 0) { settings.save(any()) }
            }

        @Test
        fun `tells why the storage refused`() =
            runTest {
                // given
                givenStored(BUCKET, SECRET)
                coEvery { storage.check(any()) } throws
                    CloudStorageException(CloudProblem.KeysRefused, "403 SignatureDoesNotMatch")
                val presenter = presenter().also { it.load() }

                // when
                presenter.test()

                // then
                assertThat(presenter.failure)
                    .isEqualTo(Failure(FailureKind.CloudKeysRefused, "403 SignatureDoesNotMatch"))
                assertThat(presenter.busy).isFalse()
            }
    }

    @Nested
    inner class Remove {
        @Test
        fun `forgets the bucket and the secret key`() =
            runTest {
                // given
                givenStored(BUCKET, SECRET)
                val presenter = presenter().also { it.load() }

                // when
                presenter.remove()

                // then
                coVerify(exactly = 1) { settings.save(LogbookSettings(cloudStorage = null)) }
                coVerify(exactly = 1) { credentials.delete(STORE_KEY) }
                assertThat(presenter.stored).isNull()
                assertThat(presenter.input).isEqualTo(CloudStorageInput())
            }
    }
}
