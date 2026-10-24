package ch.qscqlmpa.wingtracker.ui.sync

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettings
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import ch.qscqlmpa.wingtracker.sync.ConflictChoice
import ch.qscqlmpa.wingtracker.sync.ConflictSide
import ch.qscqlmpa.wingtracker.sync.ConnectLogbook
import ch.qscqlmpa.wingtracker.sync.FieldDifference
import ch.qscqlmpa.wingtracker.sync.GetSyncConflicts
import ch.qscqlmpa.wingtracker.sync.GetSyncStatus
import ch.qscqlmpa.wingtracker.sync.JoinLogbook
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.RecordChange
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SaveAutomaticSync
import ch.qscqlmpa.wingtracker.sync.SaveSyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncConflict
import ch.qscqlmpa.wingtracker.sync.SyncLogbook
import ch.qscqlmpa.wingtracker.sync.SyncReport
import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
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
import kotlin.time.Instant

private const val TOKEN = "ghp_exampletoken"

private val SETTINGS =
    SyncSettings(
        repository = "https://codeberg.org/michael/logbook.git",
        username = "michael",
        authorName = "Michael Heiniger",
        authorEmail = "michael@example.org",
    )

private val FLIGHT = SyncedRecord(RecordKind.Flight, "f-2026-09-20")
private val WING = SyncedRecord(RecordKind.Wing, "w-zeno-2")

private fun aConflict(record: SyncedRecord) =
    SyncConflict(
        record = record,
        here = ConflictSide(RecordChange("Michael Heiniger", Instant.parse("2026-10-24T08:15:00Z"), "Update"), false),
        there = ConflictSide(RecordChange("Michael Heiniger", Instant.parse("2026-10-24T07:50:00Z"), "Update"), false),
        differences = listOf(FieldDifference("durationMinutes", "100", "120")),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictPresenterTest {
    private val sync =
        mockk<LogbookSync>(relaxUnitFun = true).also {
            coEvery { it.conflicts() } returns listOf(aConflict(FLIGHT), aConflict(WING))
            coEvery { it.sync(any(), any(), any()) } returns SyncReport(received = 3, sent = 2)
        }
    private var device = DeviceSettings(sync = SETTINGS)
    private val deviceSettings =
        mockk<DeviceSettingsRepository>().also {
            coEvery { it.load() } answers { device }
            coEvery { it.save(any()) } answers { device = firstArg() }
        }
    private val credentials =
        mockk<CredentialStore>(relaxUnitFun = true).also {
            coEvery { it.read(any()) } returns
                TOKEN
        }
    private var resolved: SyncReport? = null

    private fun TestScope.opened(): ConflictPresenter {
        val clock = AppClock()
        return ConflictPresenter(
            TestScope(UnconfinedTestDispatcher(testScheduler)),
            SyncUseCases(
                GetSyncStatus(sync, deviceSettings, clock),
                ConnectLogbook(sync, deviceSettings, clock),
                SaveSyncSettings(sync, deviceSettings),
                SyncLogbook(sync, deviceSettings, clock),
                JoinLogbook(sync, deviceSettings, clock),
                SaveAutomaticSync(deviceSettings),
                GetSyncConflicts(sync),
            ),
            SyncSecret(credentials),
        ).also { presenter -> presenter.open { resolved = it } }
    }

    @Nested
    inner class Open {
        @Test
        fun `lists the conflicts with nothing chosen yet`() =
            runTest {
                // given / when
                val presenter = opened()

                // then
                assertThat(presenter.conflicts).containsExactly(aConflict(FLIGHT), aConflict(WING))
                assertThat(presenter.choices).isEmpty()
                assertThat(presenter.complete).isFalse()
            }
    }

    @Nested
    inner class Apply {
        @Test
        fun `syncs with the version chosen for each record, then closes and tells`() =
            runTest {
                // given
                val presenter = opened()
                presenter.choose(FLIGHT, ConflictChoice.Here)
                presenter.choose(WING, ConflictChoice.There)

                // when
                presenter.apply()

                // then
                coVerify(exactly = 1) {
                    sync.sync(
                        SETTINGS.remote(TOKEN),
                        SETTINGS.author,
                        mapOf(FLIGHT to ConflictChoice.Here, WING to ConflictChoice.There),
                    )
                }
                assertThat(presenter.conflicts).isNull()
                assertThat(resolved).isEqualTo(SyncReport(received = 3, sent = 2))
            }

        @Test
        fun `does nothing until every record has a choice`() =
            runTest {
                // given
                val presenter = opened()
                presenter.choose(FLIGHT, ConflictChoice.Here)

                // when
                presenter.apply()

                // then
                coVerify(exactly = 0) { sync.sync(any(), any(), any()) }
            }

        @Test
        fun `shows what collides anew when the other computer sent more in the meantime`() =
            runTest {
                // given
                val presenter = opened()
                presenter.choose(FLIGHT, ConflictChoice.Here)
                presenter.choose(WING, ConflictChoice.There)
                val reserve = SyncedRecord(RecordKind.Reserve, "r-1")
                coEvery { sync.sync(any(), any(), any()) } returns SyncReport(0, 0, listOf(FLIGHT, WING, reserve))
                coEvery { sync.conflicts() } returns listOf(aConflict(FLIGHT), aConflict(WING), aConflict(reserve))

                // when
                presenter.apply()

                // then
                assertThat(presenter.moreFound).isTrue()
                assertThat(presenter.conflicts?.map { it.record }).containsExactly(FLIGHT, WING, reserve)
                assertThat(presenter.complete).isFalse()
                assertThat(resolved).isNull()
            }

        @Test
        fun `asks for the token again when it is no longer kept`() =
            runTest {
                // given
                coEvery { credentials.read(any()) } returns null
                val presenter = opened()
                presenter.choose(FLIGHT, ConflictChoice.Here)
                presenter.choose(WING, ConflictChoice.Here)

                // when
                presenter.apply()

                // then
                assertThat(presenter.failure).isEqualTo(Failure(FailureKind.SyncTokenMissing))
            }
    }
}
