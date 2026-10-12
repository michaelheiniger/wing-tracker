package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightsOfWing
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.flights.igc.ImportIgcFlight
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.flights.igc.TrackReader
import ch.qscqlmpa.wingtracker.flights.syride.ImportSyrideFlights
import ch.qscqlmpa.wingtracker.flights.syride.PrepareSyrideImport
import ch.qscqlmpa.wingtracker.flights.syride.SyrideFlightSource
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
import ch.qscqlmpa.wingtracker.igc.Igc4jTrackReader
import ch.qscqlmpa.wingtracker.reminders.GetAttention
import ch.qscqlmpa.wingtracker.reminders.GetCalendarEntries
import ch.qscqlmpa.wingtracker.reminders.GetStartNotification
import ch.qscqlmpa.wingtracker.reminders.MarkStartNotificationShown
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
import ch.qscqlmpa.wingtracker.settings.SaveInterfaceScale
import ch.qscqlmpa.wingtracker.settings.SaveReminderLeadTimes
import ch.qscqlmpa.wingtracker.settings.SaveSyrideUsername
import ch.qscqlmpa.wingtracker.settings.SaveThemeMode
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.storage.json.FileAttachmentStore
import ch.qscqlmpa.wingtracker.storage.json.JsonFlightRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonHarnessRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonReserveRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonSettingsRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonWingRepository
import ch.qscqlmpa.wingtracker.syride.SyrideWebFlightSource
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.gear.simple.HarnessOps
import ch.qscqlmpa.wingtracker.ui.gear.simple.ReserveOps
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.reminders.RemindersUseCases
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import ch.qscqlmpa.wingtracker.ui.undo.UndoCoordinator
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

/**
 * Composition root: the only place where layers get wired together (ADR 0007). Split by feature so each module
 * stays small enough to read at a glance.
 */
fun appModules(
    dataDirectory: Path,
    devMode: Boolean = false,
): List<Module> =
    listOf(
        storageModule(dataDirectory),
        gearModule(),
        flightsModule(),
        maintenanceModule(),
        remindersModule(),
        uiModule(dataDirectory, devMode),
    )

private fun storageModule(dataDirectory: Path) =
    module {
        single { AppClock() }
        single<TrackReader> { Igc4jTrackReader() }
        single<SyrideFlightSource> { SyrideWebFlightSource() }
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
        factory { PrepareIgcImport(get(), get()) }
        factory { ImportIgcFlight(get(), get(), get(), get()) }
        factory { GetLogbook(get(), get()) }
        factory { PrepareSyrideImport(get(), get(), get()) }
        factory { ImportSyrideFlights(get(), get(), get(), RegisterWing(get())) }
        single { LogbookUseCases(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
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
        single { RemindersUseCases(get(), get(), get(), get()) }
        single { StartupReminder(get(), get(), TrayNotifier()) }
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
        )
    }
    single { ThemePreference() }
    single { UndoCoordinator() }
}
