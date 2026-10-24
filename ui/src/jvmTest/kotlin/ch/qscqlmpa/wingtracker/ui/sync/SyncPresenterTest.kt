package ch.qscqlmpa.wingtracker.ui.sync

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.sync.ConnectLogbook
import ch.qscqlmpa.wingtracker.sync.GetSyncStatus
import ch.qscqlmpa.wingtracker.sync.JoinLogbook
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SaveSyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncLogbook
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncReport
import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
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
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

private const val TOKEN = "ghp_exampletoken"
private const val STORE_KEY = "sync-token"

private val SETTINGS =
    SyncSettings(
        repository = "https://codeberg.org/michael/logbook.git",
        username = "michael",
        authorName = "Michael Heiniger",
        authorEmail = "michael@example.org",
    )

private val FILLED_IN =
    SyncInput(
        repository = SETTINGS.repository,
        username = "michael",
        token = TOKEN,
        authorName = SETTINGS.authorName,
        authorEmail = SETTINGS.authorEmail,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPresenterTest {
    private val sync = mockk<LogbookSync>(relaxUnitFun = true)
    private val credentials = mockk<CredentialStore>(relaxUnitFun = true)
    private val clock =
        AppClock(
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-10-24T16:10:00Z")
                },
            zone = TimeZone.UTC,
        )
    private var device = DeviceSettings(interfaceScalePercent = 125)
    private val deviceSettings =
        mockk<DeviceSettingsRepository>().also {
            coEvery { it.load() } answers { device }
            coEvery { it.save(any()) } answers { device = firstArg() }
        }
    private var flushed = 0
    private var synced = 0
    private var offered: Pair<SyncSettings, String>? = null

    private fun TestScope.presenter() =
        SyncPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            SyncUseCases(
                GetSyncStatus(sync, deviceSettings, clock),
                ConnectLogbook(sync, deviceSettings, clock),
                SaveSyncSettings(sync, deviceSettings),
                SyncLogbook(sync, deviceSettings, clock),
                JoinLogbook(sync, deviceSettings, clock),
            ),
            SyncSecret(credentials),
            beforeSync = { flushed++ },
            onSynced = { synced++ },
            onRemoteHoldsLogbook = { settings, token -> offered = settings to token },
        )

    private fun givenConnected(token: String? = TOKEN) {
        device = device.copy(sync = SETTINGS, lastSyncedAt = Instant.parse("2026-10-20T17:45:00Z"))
        coEvery { sync.isConnected() } returns true
        coEvery { credentials.read(STORE_KEY) } returns token
    }

    private fun givenNotConnected() {
        coEvery { sync.isConnected() } returns false
        coEvery { credentials.read(STORE_KEY) } returns null
    }

    @Nested
    inner class Connecting {
        @Test
        fun `connects with what is typed, keeps the token and closes the form`() =
            runTest {
                // given
                givenNotConnected()
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { FILLED_IN }
                coEvery { sync.isConnected() } returns true

                // when
                presenter.save()

                // then
                coVerify(exactly = 1) { sync.connect(SETTINGS.remote(TOKEN), SETTINGS.author) }
                coVerify(exactly = 1) { credentials.write(STORE_KEY, TOKEN) }
                assertThat(presenter.connected).isTrue()
                assertThat(presenter.editing).isFalse()
                assertThat(presenter.input.token).isEmpty()
                assertThat(presenter.outcome).isEqualTo(SyncOutcome.Connected(SecretKeeping.System))
                assertThat(flushed).isEqualTo(1)
            }

        @Test
        fun `offers to join the logbook a repository already holds, instead of failing`() =
            runTest {
                // given
                givenNotConnected()
                coEvery { sync.connect(any(), any()) } throws SyncException(SyncProblem.RemoteNotEmpty)
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { FILLED_IN }

                // when
                presenter.save()

                // then
                assertThat(offered).isEqualTo(SETTINGS to TOKEN)
                assertThat(presenter.failure).isNull()
                assertThat(presenter.editing).isFalse()
                assertThat(presenter.connected).isFalse()
                coVerify(exactly = 0) { credentials.write(any(), any()) }
            }

        @Test
        fun `keeps the form open and says why when the token is refused`() =
            runTest {
                // given
                givenNotConnected()
                coEvery { sync.connect(any(), any()) } throws SyncException(SyncProblem.CredentialsRefused)
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { FILLED_IN }

                // when
                presenter.save()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SyncCredentialsRefused)
                assertThat(presenter.editing).isTrue()
                assertThat(offered).isNull()
            }

        @Test
        fun `asks for a token even when one is still kept from before`() =
            runTest {
                // given
                givenNotConnected()
                coEvery { credentials.read(STORE_KEY) } returns TOKEN
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { FILLED_IN.copy(token = "") }

                // when
                presenter.save()

                // then
                assertThat(presenter.errors).containsEntry(SyncField.Token, FormError.Required)
                coVerify(exactly = 0) { sync.connect(any(), any()) }
            }

        @Test
        fun `stops a connect when the form is cancelled`() =
            runTest {
                // given
                givenNotConnected()
                coEvery { sync.connect(any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { FILLED_IN }
                presenter.save()

                // when
                presenter.cancelEdit()

                // then
                assertThat(presenter.busy).isFalse()
                assertThat(presenter.editing).isFalse()
                assertThat(presenter.connected).isFalse()
            }
    }

    @Nested
    inner class SyncingNow {
        @Test
        fun `writes pending deletions first, then tells what was exchanged`() =
            runTest {
                // given
                givenConnected()
                coEvery { sync.sync(SETTINGS.remote(TOKEN), SETTINGS.author) } returns
                    SyncReport(received = 3, sent = 1)
                val presenter = presenter().also { it.load() }

                // when
                presenter.syncNow()

                // then
                assertThat(presenter.outcome).isEqualTo(SyncOutcome.Synced(received = 3, sent = 1))
                assertThat(presenter.status?.lastSyncedAt?.toString()).isEqualTo("2026-10-24T16:10")
                assertThat(flushed).isEqualTo(1)
                assertThat(synced).isEqualTo(1)
            }

        @Test
        fun `names the records changed on both sides`() =
            runTest {
                // given
                givenConnected()
                val conflicts = listOf(SyncedRecord(RecordKind.Flight, "f-2026-09-20"))
                coEvery { sync.sync(any(), any()) } returns SyncReport(0, 0, conflicts)
                val presenter = presenter().also { it.load() }

                // when
                presenter.syncNow()

                // then
                assertThat(presenter.outcome).isEqualTo(SyncOutcome.Conflicts(conflicts))
                assertThat(synced).isZero()
            }

        @Test
        fun `asks for the token again when it is no longer kept`() =
            runTest {
                // given
                givenConnected(token = null)
                val presenter = presenter().also { it.load() }

                // when
                presenter.syncNow()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.SyncTokenMissing))
                coVerify(exactly = 0) { sync.sync(any(), any()) }
            }

        @Test
        fun `says when the host refused the token`() =
            runTest {
                // given
                givenConnected()
                coEvery { sync.sync(any(), any()) } throws SyncException(SyncProblem.CredentialsRefused)
                val presenter = presenter().also { it.load() }

                // when
                presenter.syncNow()

                // then
                assertThat(presenter.failure?.kind).isEqualTo(FailureKind.SyncCredentialsRefused)
                assertThat(presenter.busy).isFalse()
            }

        @Test
        fun `stops a sync when cancelled`() =
            runTest {
                // given
                givenConnected()
                coEvery { sync.sync(any(), any()) } coAnswers { awaitCancellation() }
                val presenter = presenter().also { it.load() }
                presenter.syncNow()

                // when
                presenter.cancel()

                // then
                assertThat(presenter.busy).isFalse()
                assertThat(presenter.outcome).isNull()
            }
    }

    @Nested
    inner class SavingTheSettings {
        @Test
        fun `keeps the kept token when the field stays empty and the repository as connected`() =
            runTest {
                // given
                givenConnected()
                val presenter = presenter().also { it.load() }
                presenter.edit()
                presenter.change { it.copy(authorName = "Michael H.") }

                // when
                presenter.save()

                // then
                assertThat(device.sync).isEqualTo(SETTINGS.copy(authorName = "Michael H."))
                assertThat(presenter.outcome).isEqualTo(SyncOutcome.Saved(keeping = null))
                coVerify(exactly = 0) { credentials.write(any(), any()) }
            }
    }
}
