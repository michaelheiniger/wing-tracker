package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.statistics.GetStatistics
import ch.qscqlmpa.wingtracker.ui.flights.LogbookUseCases
import ch.qscqlmpa.wingtracker.ui.gear.GearUseCases
import ch.qscqlmpa.wingtracker.ui.gear.wing.maintenance.MaintenanceUseCases
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.koinApplication
import java.nio.file.Path

class AppModuleTest {
    @TempDir
    lateinit var dataDirectory: Path

    // Koin resolves at runtime (ADR 0007), so a missing binding must fail here instead of at app launch.
    @Test
    fun `every binding needed to start the application can be resolved`() {
        // given
        val koin = koinApplication { modules(appModule(dataDirectory)) }.koin

        // when / then
        assertThat(koin.get<GearUseCases>()).isNotNull()
        assertThat(koin.get<LogbookUseCases>()).isNotNull()
        assertThat(koin.get<GetStatistics>()).isNotNull()
        assertThat(koin.get<MaintenanceUseCases>()).isNotNull()
        assertThat(koin.get<PlatformServices>()).isNotNull()
        assertThat(koin.get<ThemePreference>()).isNotNull()
    }
}
