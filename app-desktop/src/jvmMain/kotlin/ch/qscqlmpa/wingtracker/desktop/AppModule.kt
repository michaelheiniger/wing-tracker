package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.airtime.AirtimeWebAccount
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.backup.BackupArchive
import ch.qscqlmpa.wingtracker.backup.CheckCloudStorage
import ch.qscqlmpa.wingtracker.backup.CloudStorage
import ch.qscqlmpa.wingtracker.backup.CreateBackup
import ch.qscqlmpa.wingtracker.backup.DiscardTemporaryBackup
import ch.qscqlmpa.wingtracker.backup.DownloadCloudBackup
import ch.qscqlmpa.wingtracker.backup.GetBackupStatus
import ch.qscqlmpa.wingtracker.backup.InspectBackup
import ch.qscqlmpa.wingtracker.backup.ListCloudBackups
import ch.qscqlmpa.wingtracker.backup.RestoreBackup
import ch.qscqlmpa.wingtracker.backup.SaveCloudStorage
import ch.qscqlmpa.wingtracker.backup.UploadBackup
import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightsOfWing
import ch.qscqlmpa.wingtracker.flights.ForgetFlightExport
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeAccount
import ch.qscqlmpa.wingtracker.flights.airtime.ExportFlightsToAirtime
import ch.qscqlmpa.wingtracker.flights.airtime.GetAirtimePages
import ch.qscqlmpa.wingtracker.flights.airtime.PrepareAirtimeExport
import ch.qscqlmpa.wingtracker.flights.igc.AttachIgcTrack
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.flights.igc.ReadFlightTrack
import ch.qscqlmpa.wingtracker.flights.igc.ReadGroundProfile
import ch.qscqlmpa.wingtracker.flights.igc.TrackReader
import ch.qscqlmpa.wingtracker.flights.spreadsheet.ImportSpreadsheetFlights
import ch.qscqlmpa.wingtracker.flights.spreadsheet.OpenSpreadsheet
import ch.qscqlmpa.wingtracker.flights.spreadsheet.PrepareSpreadsheetImport
import ch.qscqlmpa.wingtracker.flights.spreadsheet.SpreadsheetReader
import ch.qscqlmpa.wingtracker.flights.syride.CompleteSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.CountSyrideFlightsWithoutTrack
import ch.qscqlmpa.wingtracker.flights.syride.GetSyrideFlightPage
import ch.qscqlmpa.wingtracker.flights.syride.GetSyrideSitePage
import ch.qscqlmpa.wingtracker.flights.syride.ImportSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.PrepareSyrideImport
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlightSource
import ch.qscqlmpa.wingtracker.flights.syride.UpdateSyrideTakeOffTimes
import ch.qscqlmpa.wingtracker.gear.harness.DeleteHarness
import ch.qscqlmpa.wingtracker.gear.harness.HarnessRepository
import ch.qscqlmpa.wingtracker.gear.harness.ListHarnesses
import ch.qscqlmpa.wingtracker.gear.harness.RegisterHarness
import ch.qscqlmpa.wingtracker.gear.harness.RetireHarness
import ch.qscqlmpa.wingtracker.gear.harness.UpdateHarnessDetails
import ch.qscqlmpa.wingtracker.gear.maintenance.CancelPlannedCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.CompositeMaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.DeleteCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.GetGearMaintenance
import ch.qscqlmpa.wingtracker.gear.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.maintenance.MaintainedGearRepository
import ch.qscqlmpa.wingtracker.gear.maintenance.PlanCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.maintenance.RecordCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.UpdateCheck
import ch.qscqlmpa.wingtracker.gear.maintenance.UpdatePlannedCheck
import ch.qscqlmpa.wingtracker.gear.reserve.DeleteReserve
import ch.qscqlmpa.wingtracker.gear.reserve.ListReserves
import ch.qscqlmpa.wingtracker.gear.reserve.RegisterReserve
import ch.qscqlmpa.wingtracker.gear.reserve.ReserveRepository
import ch.qscqlmpa.wingtracker.gear.reserve.RetireReserve
import ch.qscqlmpa.wingtracker.gear.reserve.UpdateReserveDetails
import ch.qscqlmpa.wingtracker.gear.wing.DeleteWing
import ch.qscqlmpa.wingtracker.gear.wing.ListWings
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.UpdateWingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingFlightLookup
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.github.GitHubReleaseSource
import ch.qscqlmpa.wingtracker.github.HttpReleaseDownloads
import ch.qscqlmpa.wingtracker.igc.Igc4jTrackReader
import ch.qscqlmpa.wingtracker.maps.MapTileSource
import ch.qscqlmpa.wingtracker.maps.ReadMapTile
import ch.qscqlmpa.wingtracker.maps.TerrainElevationSource
import ch.qscqlmpa.wingtracker.maptiles.OpenTopoMapTileSource
import ch.qscqlmpa.wingtracker.maptiles.TerrariumElevationSource
import ch.qscqlmpa.wingtracker.reminders.GetAttention
import ch.qscqlmpa.wingtracker.reminders.GetCalendarEntries
import ch.qscqlmpa.wingtracker.reminders.GetStartNotification
import ch.qscqlmpa.wingtracker.reminders.MarkStartNotificationShown
import ch.qscqlmpa.wingtracker.s3.S3CloudStorage
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveAirtimeNick
import ch.qscqlmpa.wingtracker.settings.SaveBackupReminder
import ch.qscqlmpa.wingtracker.settings.SaveDeviceFolder
import ch.qscqlmpa.wingtracker.settings.SaveInterfaceScale
import ch.qscqlmpa.wingtracker.settings.SaveReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.SaveSyrideUsername
import ch.qscqlmpa.wingtracker.settings.SaveThemeMode
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.spreadsheet.FileSpreadsheetReader
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.storage.json.FileAttachmentStore
import ch.qscqlmpa.wingtracker.storage.json.JsonFlightRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonHarnessRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonReserveRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonSettingsRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonUpdatePreferencesRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonWingRepository
import ch.qscqlmpa.wingtracker.storage.json.ZipBackupArchive
import ch.qscqlmpa.wingtracker.syride.SyrideWebFlightSource
import ch.qscqlmpa.wingtracker.ui.airtime.AirtimeExportUseCases
import ch.qscqlmpa.wingtracker.ui.airtime.AirtimeToken
import ch.qscqlmpa.wingtracker.ui.backup.BackupUseCases
import ch.qscqlmpa.wingtracker.ui.backup.CloudRestoreUseCases
import ch.qscqlmpa.wingtracker.ui.backup.CloudStorageUseCases
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.gear.simple.HarnessOps
import ch.qscqlmpa.wingtracker.ui.gear.simple.ReserveOps
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.reminders.RemindersUseCases
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import ch.qscqlmpa.wingtracker.ui.updates.UpdateUseCases
import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.CheckForUpdate
import ch.qscqlmpa.wingtracker.updates.DownloadUpdate
import ch.qscqlmpa.wingtracker.updates.GetUpdatePreferences
import ch.qscqlmpa.wingtracker.updates.ReleaseDownloads
import ch.qscqlmpa.wingtracker.updates.ReleaseSource
import ch.qscqlmpa.wingtracker.updates.SaveCheckAtStartup
import ch.qscqlmpa.wingtracker.updates.SkipVersion
import ch.qscqlmpa.wingtracker.updates.UpdatePreferencesRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

// Where releases are published (ADR 0009, ADR 0024).
private const val RELEASE_REPOSITORY = "michaelheiniger/wing-tracker"

// Where flights are exported to (ADR 0029).
private const val AIRTIME_URL = "https://air-time.net"

/**
 * Composition root: the only place where layers get wired together (ADR 0007). Split by feature so each module
 * stays small enough to read at a glance.
 */
fun appModules(
    dataDirectory: Path,
    devMode: Boolean = false,
    installationDirectory: Path = InstallationDirectory.resolve(devMode),
    downloadDirectory: Path = UpdateDownloadDirectory.resolve(devMode),
    mapTileDirectory: Path = MapTileDirectory.resolve(devMode),
): List<Module> =
    listOf(
        storageModule(dataDirectory),
        gearModule(),
        flightsModule(),
        maintenanceModule(),
        remindersModule(),
        backupModule(dataDirectory),
        updatesModule(installationDirectory, downloadDirectory, devMode),
        mapsModule(mapTileDirectory),
        uiModule(dataDirectory, devMode),
    )

private fun storageModule(dataDirectory: Path) =
    module {
        single { AppClock() }
        single<TrackReader> { Igc4jTrackReader() }
        single<SyrideFlightSource> { SyrideWebFlightSource() }
        single<AirtimeAccount> {
            AirtimeWebAccount(
                AIRTIME_URL,
                "wing-tracker/${BuildInfo.VERSION} (+https://github.com/$RELEASE_REPOSITORY)",
            )
        }
        single<SpreadsheetReader> { FileSpreadsheetReader() }
        single<WingRepository> { JsonWingRepository(dataDirectory) }
        single<HarnessRepository> { JsonHarnessRepository(dataDirectory) }
        single<ReserveRepository> { JsonReserveRepository(dataDirectory) }
        single<FlightRepository> { JsonFlightRepository(dataDirectory) }
        single<SettingsRepository> { JsonSettingsRepository(dataDirectory) }
        single<AttachmentStore> { FileAttachmentStore(dataDirectory) }
        single<MaintainedGearRepository> { CompositeMaintainedGearRepository(get(), get(), get()) }
    }

private fun gearModule() =
    module {
        factory { ListWings(get()) }
        factory { RegisterWing(get()) }
        factory { UpdateWingDetails(get()) }
        factory { RetireWing(get()) }
        single<WingFlightLookup> { FlightsOfWing(get()) }
        factory { DeleteWing(get(), get()) }
        factory {
            HarnessOps(
                ListHarnesses(get()),
                RegisterHarness(get()),
                UpdateHarnessDetails(get()),
                RetireHarness(get()),
                DeleteHarness(get()),
            )
        }
        factory {
            ReserveOps(
                ListReserves(get()),
                RegisterReserve(get()),
                UpdateReserveDetails(get()),
                RetireReserve(get()),
                DeleteReserve(get()),
            )
        }
        single { GearUseCases(get(), get(), get(), get(), get(), get(), get<HarnessOps>(), get<ReserveOps>()) }
    }

private fun flightsModule() =
    module {
        factory { LogFlight(get(), get(), get()) }
        factory { UpdateFlight(get(), get(), get()) }
        factory { DeleteFlight(get(), get()) }
        factory { PrepareIgcImport(get(), get(), get()) }
        factory { AttachIgcTrack(get(), get()) }
        factory { ReadFlightTrack(get(), get(), get()) }
        factory { ImportIgcFlight(get(), get(), get(), get()) }
        factory { GetLogbook(get(), get()) }
        factory { PrepareSyrideImport(get(), get(), get()) }
        factory { ImportSyrideFlights(get(), get(), get(), RegisterWing(get())) }
        factory { UpdateSyrideTakeOffTimes(get()) }
        factory { CountSyrideFlightsWithoutTrack(get()) }
        factory { GetSyrideFlightPage(get(), get()) }
        factory { GetSyrideSitePage(get()) }
        factory { CompleteSyrideFlights(get()) }
        factory { PrepareAirtimeExport(get(), get(), get(), get(), get(), get()) }
        factory { ExportFlightsToAirtime(get(), get(), get(), get()) }
        factory { GetAirtimePages(get()) }
        factory { SaveAirtimeNick(get()) }
        factory { ForgetFlightExport(get()) }
        factory { OpenSpreadsheet(get()) }
        factory { PrepareSpreadsheetImport(get(), get()) }
        factory { ImportSpreadsheetFlights(get(), get(), get(), RegisterWing(get())) }
        single {
            LogbookUseCases(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        factory { GetStatistics(get(), get(), get()) }
    }

private fun maintenanceModule() =
    module {
        factory { GetMaintenanceStatuses(get(), get(), get(), get()) }
        factory { GetGearMaintenance(get(), get(), get(), get()) }
        factory { RecordCheck(get(), get(), get()) }
        factory { UpdateCheck(get(), get(), get()) }
        factory { DeleteCheck(get(), get()) }
        factory { PlanCheck(get(), get()) }
        factory { UpdatePlannedCheck(get(), get()) }
        factory { CancelPlannedCheck(get()) }
        factory { ReadAttachment(get()) }
        single { MaintenanceUseCases(get(), get(), get(), get(), get(), get(), get(), get()) }
    }

private fun remindersModule() =
    module {
        factory { GetAttention(get(), get(), get(), get()) }
        factory { GetStartNotification(get(), get(), get()) }
        factory { MarkStartNotificationShown(get(), get()) }
        factory { GetCalendarEntries(get(), get(), get()) }
        factory { GetAppSettings(get()) }
        factory { SaveReminderLeadTimes(get()) }
        factory { SaveThemeMode(get()) }
        factory { SaveInterfaceScale(get()) }
        factory { SaveSyrideUsername(get()) }
        factory { SaveDeviceFolder(get()) }
        factory { SaveBackupReminder(get()) }
        single { RemindersUseCases(get(), get(), get(), get(), get()) }
        single { StartupReminder(get(), get(), TrayNotifier()) }
    }

private fun backupModule(dataDirectory: Path) =
    module {
        single<BackupArchive> { ZipBackupArchive(dataDirectory) }
        factory { GetBackupStatus(get(), get()) }
        factory { CreateBackup(get(), get(), get(), BuildInfo.VERSION) }
        factory { InspectBackup(get(), get(), BuildInfo.VERSION) }
        factory { RestoreBackup(get()) }
        single<CloudStorage> { S3CloudStorage("wing-tracker/${BuildInfo.VERSION}") }
        factory { SaveCloudStorage(get()) }
        factory { CheckCloudStorage(get()) }
        factory { UploadBackup(get(), get(), get(), get(), BuildInfo.VERSION) }
        single { BackupUseCases(get(), get(), get(), get(), get()) }
        single { CloudStorageUseCases(get(), get(), get()) }
        factory { ListCloudBackups(get(), get(), get()) }
        factory { DownloadCloudBackup(get(), get(), get()) }
        factory { DiscardTemporaryBackup(get()) }
        single { CloudRestoreUseCases(get(), get(), get()) }
    }

private fun mapsModule(tileDirectory: Path) =
    module {
        // Tile services ask to be told who fetches and how to reach them (ADR 0027).
        val userAgent = "wing-tracker/${BuildInfo.VERSION} (+https://github.com/$RELEASE_REPOSITORY)"
        // Each source keeps its own tiles, so each is trimmed within its own limit.
        single<MapTileSource> { OpenTopoMapTileSource(tileDirectory.resolve("opentopomap"), userAgent) }
        single<TerrainElevationSource> { TerrariumElevationSource(tileDirectory.resolve("terrain"), userAgent) }
        factory { ReadMapTile(get()) }
        factory { ReadGroundProfile(get()) }
    }

private fun updatesModule(
    installationDirectory: Path,
    downloadDirectory: Path,
    devMode: Boolean,
) = module {
    val userAgent = "wing-tracker/${BuildInfo.VERSION}"
    single<ReleaseSource> { GitHubReleaseSource(RELEASE_REPOSITORY, userAgent) }
    single<ReleaseDownloads> { HttpReleaseDownloads(downloadDirectory, userAgent) }
    single<UpdatePreferencesRepository> { JsonUpdatePreferencesRepository(installationDirectory) }
    factory {
        // A development build has no release version and is never updated (ADR 0024).
        val installed = if (devMode) null else AppVersion.parseOrNull(BuildInfo.VERSION)
        CheckForUpdate(get(), get(), installed, InstallationDirectory.installerKind())
    }
    factory { GetUpdatePreferences(get()) }
    factory { SaveCheckAtStartup(get()) }
    factory { SkipVersion(get()) }
    factory { DownloadUpdate(get()) }
    single { UpdateUseCases(get(), get(), get(), get(), get()) }
}

private fun uiModule(
    dataDirectory: Path,
    devMode: Boolean,
) = module {
    single {
        PlatformServices(
            DesktopFilePicker(),
            DesktopFileOpener(),
            DesktopFileSaver(),
            DesktopFileDrop(),
            dataLocation(dataDirectory, devMode),
            DesktopLogLocation(DesktopLogging.directory(devMode)),
            DesktopUpdateInstaller(InstallationDirectory.installerKind(), DesktopLogging.directory(devMode)),
            credentialStore(devMode),
            supportsAllFileNames(),
        )
    }
    single { ThemePreference() }
    single { UndoCoordinator() }
    // One for the whole run, so a token kept for the session outlives the dialog (ADR 0029).
    single { AirtimeToken(get<PlatformServices>().credentialStore) }
    factory { AirtimeExportUseCases(get(), get(), get(), get(), get()) }
}

// Linux has the Secret Service; macOS has neither, so it keeps secrets for the session only (ADR 0029).
// A development build keeps its own, so trying out a form never replaces the pilot's real keys.
private fun credentialStore(
    devMode: Boolean,
    osName: String = System.getProperty("os.name"),
): CredentialStore {
    val application = if (devMode) "wing-tracker-dev" else "wing-tracker"
    return if ("win" in osName.lowercase()) {
        WindowsCredentialStore(application)
    } else {
        SecretToolCredentialStore(application)
    }
}
