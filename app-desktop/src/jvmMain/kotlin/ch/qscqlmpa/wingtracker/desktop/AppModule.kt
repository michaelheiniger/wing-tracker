package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.AppClock
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
import ch.qscqlmpa.wingtracker.settings.SettingsRepository
import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.storage.json.JsonFlightRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonSettingsRepository
import ch.qscqlmpa.wingtracker.storage.json.JsonWingRepository
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
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
        single { GearUseCases(get(), get(), get(), get(), get()) }

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
