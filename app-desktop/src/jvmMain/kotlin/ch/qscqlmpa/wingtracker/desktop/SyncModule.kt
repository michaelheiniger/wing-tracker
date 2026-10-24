package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.git.GitLogbook
import ch.qscqlmpa.wingtracker.git.SyncedFolderLayout
import ch.qscqlmpa.wingtracker.storage.json.JsonDataFolder
import ch.qscqlmpa.wingtracker.sync.ConnectLogbook
import ch.qscqlmpa.wingtracker.sync.GetSyncStatus
import ch.qscqlmpa.wingtracker.sync.JoinLogbook
import ch.qscqlmpa.wingtracker.sync.LogbookSync
import ch.qscqlmpa.wingtracker.sync.LogbookTransactions
import ch.qscqlmpa.wingtracker.sync.SaveSyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncLogbook
import ch.qscqlmpa.wingtracker.ui.sync.SyncUseCases
import org.koin.dsl.binds
import org.koin.dsl.module
import java.nio.file.Path

// One adapter serves both ports, since transactions and syncs share the lock that keeps them apart (ADR 0033).
internal fun syncModule(dataDirectory: Path) =
    module {
        single { GitLogbook(dataDirectory, syncedFolderLayout(dataDirectory)) } binds
            arrayOf(LogbookTransactions::class, LogbookSync::class)
        factory { GetSyncStatus(get(), get(), get()) }
        factory { ConnectLogbook(get(), get(), get()) }
        factory { SaveSyncSettings(get(), get()) }
        factory { SyncLogbook(get(), get(), get()) }
        factory { JoinLogbook(get(), get(), get()) }
        single { SyncUseCases(get(), get(), get(), get(), get()) }
    }

// Hands the storage adapter's layout to the sync adapter, so neither depends on the other.
private fun syncedFolderLayout(dataDirectory: Path) =
    object : SyncedFolderLayout {
        override val deviceOnly = JsonDataFolder.deviceOnly

        override fun isQuarantined(path: String) = JsonDataFolder.isQuarantined(dataDirectory, path)

        override fun recordAt(path: String) = JsonDataFolder.recordAt(path)
    }
