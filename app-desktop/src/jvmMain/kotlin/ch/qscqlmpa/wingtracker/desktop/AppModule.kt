package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.AppClock
import ch.qscqlmpa.wingtracker.attachments.AttachmentStore
import ch.qscqlmpa.wingtracker.flights.DeleteFlight
import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.FlightsOfWing
import ch.qscqlmpa.wingtracker.flights.GetLogbook
import ch.qscqlmpa.wingtracker.flights.LogFlight
import ch.qscqlmpa.wingtracker.flights.UpdateFlight
import ch.qscqlmpa.wingtracker.gear.wing.DeleteWing
import ch.qscqlmpa.wingtracker.gear.wing.ListWings
import ch.qscqlmpa.wingtracker.gear.wing.RegisterWing
import ch.qscqlmpa.wingtracker.gear.wing.RetireWing
import ch.qscqlmpa.wingtracker.gear.wing.UpdateWingDetails
import ch.qscqlmpa.wingtracker.gear.wing.WingFlightLookup
import ch.qscqlmpa.wingtracker.gear.wing.WingRepository
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.CancelPlannedCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.DeleteCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.GetMaintenanceStatuses
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.GetWingMaintenance
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.PlanCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.ReadAttachment
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.RecordCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.UpdateCheck
import ch.qscqlmpa.wingtracker.gear.wing.maintenance.UpdatePlannedCheck
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.storage.json.FileAttachmentStore
import ch.qscqlmpa.wingtracker.storage.json.JsonFlightRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonSettingsRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonWingRepository
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.wing.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import org.koin.dsl.module
import java.nio.file.Path

/** Composition root: the only place where layers get wired together (ADR 0007). */
fun appModule(dataDirectory: Path) =
    module {
        single<WingRepository> { JsonWingRepository(dataDirectory) }
        single<SettingsRepository> { JsonSettingsRepository(dataDirectory) }

        factory { ListWings(get()) }
        factory { RegisterWing(get()) }
        factory { UpdateWingDetails(get()) }
        factory { RetireWing(get()) }
        single<WingFlightLookup> { FlightsOfWing(get()) }
        factory { DeleteWing(get(), get()) }
        single { GearUseCases(get(), get(), get(), get(), get(), get()) }

        single<AttachmentStore> { FileAttachmentStore(dataDirectory) }
        factory { GetMaintenanceStatuses(get(), get(), get()) }
        factory { GetWingMaintenance(get(), get(), get()) }
        factory { RecordCheck(get(), get(), get()) }
        factory { UpdateCheck(get(), get(), get()) }
        factory { DeleteCheck(get(), get()) }
        factory { PlanCheck(get(), get()) }
        factory { UpdatePlannedCheck(get(), get()) }
        factory { CancelPlannedCheck(get()) }
        factory { ReadAttachment(get()) }
        single { MaintenanceUseCases(get(), get(), get(), get(), get(), get(), get(), get()) }
        single { PlatformServices(DesktopFilePicker(), DesktopFileOpener()) }

        single<FlightRepository> { JsonFlightRepository(dataDirectory) }
        single { AppClock() }
        factory { LogFlight(get(), get(), get()) }
        factory { UpdateFlight(get(), get(), get()) }
        factory { DeleteFlight(get()) }
        factory { GetLogbook(get(), get()) }
        single { LogbookUseCases(get(), get(), get(), get()) }
        factory { GetStatistics(get(), get(), get()) }

        single { ThemePreference() }
    }
