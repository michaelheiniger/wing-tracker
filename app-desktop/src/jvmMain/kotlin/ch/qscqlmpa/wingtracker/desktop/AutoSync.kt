package ch.qscqlmpa.wingtracker.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import ch.qscqlmpa.wingtracker.settings.GetLogbookSettings
import ch.qscqlmpa.wingtracker.ui.platform.PlatformServices
import ch.qscqlmpa.wingtracker.ui.sync.AutoSyncPresenter
import ch.qscqlmpa.wingtracker.ui.sync.SyncSecret
import ch.qscqlmpa.wingtracker.ui.sync.SyncUseCases
import ch.qscqlmpa.wingtracker.ui.theme.ThemePreference
import kotlinx.coroutines.launch
import org.koin.core.Koin

/**
 * Syncs once at start and on close (ADR 0033). Called outside the rebuilt window, so a window rebuilt after an
 * unexpected error does not sync again.
 */
@Composable
fun rememberAutoSync(
    koin: Koin,
    themePreference: ThemePreference,
): AutoSyncPresenter {
    val scope = rememberCoroutineScope()
    val autoSync =
        remember {
            AutoSyncPresenter(
                scope,
                koin.get<SyncUseCases>(),
                SyncSecret(koin.get<PlatformServices>().credentialStore),
                onStarted = { received ->
                    // Another device may have changed the theme; the rest is read when it is shown.
                    if (received) scope.launch { themePreference.mode = koin.get<GetLogbookSettings>()().themeMode }
                },
            )
        }
    LaunchedEffect(autoSync) { autoSync.startUp() }
    return autoSync
}
