package ch.qscqlmpa.wingtracker.sync

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.settings.DeviceSettingsRepository
import kotlinx.datetime.LocalDateTime

/**
 * [settings] is null until this device is connected; [lastSyncedAt] is in the pilot's time. [automatic] tells
 * whether the app syncs when it starts and closes, which only a connected device does.
 */
data class SyncStatus(
    val settings: SyncSettings?,
    val lastSyncedAt: LocalDateTime?,
    val automatic: Boolean = false,
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
        return SyncStatus(
            connected,
            device.lastSyncedAt?.takeIf { connected != null }?.let(clock::local),
            automatic = connected != null && device.syncAtStartAndClose,
        )
    }
}

class SaveAutomaticSync(
    private val deviceSettings: DeviceSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        deviceSettings.save(deviceSettings.load().copy(syncAtStartAndClose = enabled))
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
    /** [resolutions] says which version to keep of each record changed on both sides. */
    suspend operator fun invoke(
        token: String,
        resolutions: Map<SyncedRecord, ConflictChoice> = emptyMap(),
    ): SyncReport {
        val settings = checkNotNull(deviceSettings.load().sync) { "This device is not connected" }
        val report = sync.sync(settings.remote(token), settings.author, resolutions)
        // A sync stopped by conflicts exchanged nothing, so the last sync stays the one before.
        if (report.conflicts.isEmpty()) deviceSettings.save(deviceSettings.load().copy(lastSyncedAt = clock.now()))
        return report
    }
}

/**
 * Uses on this device the logbook a repository already holds: it is copied into the empty [folder] given to
 * [invoke], which becomes the data folder after a restart (ADR 0020). This device's settings go along, so it looks
 * the same afterwards.
 */
class JoinLogbook(
    private val sync: LogbookSync,
    private val deviceSettings: DeviceSettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        settings: SyncSettings,
        token: String,
        folder: String,
    ) {
        sync.join(settings.remote(token), settings.author, folder)
        val device = deviceSettings.load()
        // The last backup was of the logbook left behind, so the joined one has none yet.
        val joined = device.copy(sync = settings, lastSyncedAt = clock.now(), lastBackupAt = null)
        deviceSettings.saveFor(folder, joined)
    }
}

class GetSyncConflicts(
    private val sync: LogbookSync,
) {
    suspend operator fun invoke(): List<SyncConflict> = sync.conflicts()
}
