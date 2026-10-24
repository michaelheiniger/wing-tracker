package ch.qscqlmpa.wingtracker.sync

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import kotlinx.datetime.LocalDateTime

/** [settings] is null until this device is connected; [lastSyncedAt] is in the pilot's time. */
data class SyncStatus(
    val settings: SyncSettings?,
    val lastSyncedAt: LocalDateTime?,
)

class GetSyncStatus(
    private val sync: LogbookSync,
    private val deviceSettings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): SyncStatus {
        val device = deviceSettings.load()
        // Both are needed: settings without a repository on disk mean the data folder was replaced since.
        val connected = device.sync?.takeIf { sync.isConnected() }
        return SyncStatus(connected, device.lastSyncedAt?.takeIf { connected != null }?.let(clock::local))
    }
}

/** Starts synchronising this logbook to an empty repository (ADR 0033). */
class ConnectLogbook(
    private val sync: LogbookSync,
    private val deviceSettings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        settings: SyncSettings,
        token: String,
    ) {
        sync.connect(settings.remote(token), settings.author)
        // Only once connected: settings for a repository that was refused would look like a working sync.
        deviceSettings.save(deviceSettings.load().copy(sync = settings, lastSyncedAt = clock.now()))
    }
}

/** Changes how this device signs in and signs its changes; the repository itself stays the same. */
class SaveSyncSettings(
    private val sync: LogbookSync,
    private val deviceSettings: DeviceSettingsRepository,
) {
    suspend operator fun invoke(
        username: String?,
        authorName: String,
        authorEmail: String,
    ) {
        val device = deviceSettings.load()
        val current = checkNotNull(device.sync) { "This device is not connected" }
        val changed = current.copy(username = username, authorName = authorName, authorEmail = authorEmail)
        sync.changeAuthor(changed.author)
        deviceSettings.save(device.copy(sync = changed))
    }
}

class SyncLogbook(
    private val sync: LogbookSync,
    private val deviceSettings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(token: String): SyncReport {
        val settings = checkNotNull(deviceSettings.load().sync) { "This device is not connected" }
        val report = sync.sync(settings.remote(token), settings.author)
        // A sync stopped by conflicts exchanged nothing, so the last sync stays the one before.
        if (report.conflicts.isEmpty()) deviceSettings.save(deviceSettings.load().copy(lastSyncedAt = clock.now()))
        return report
    }
}
