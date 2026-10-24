package ch.qscqlmpa.wingtracker.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.sync.ConnectLogbook
import ch.qscqlmpa.wingtracker.sync.GetSyncStatus
import ch.qscqlmpa.wingtracker.sync.SaveSyncSettings
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncLogbook
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncStatus
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.KeptSecret
import ch.qscqlmpa.wingtracker.ui.platform.SecretKeeping
import ch.qscqlmpa.wingtracker.ui.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Use cases the sync settings need; passed in so this module never sees the DI framework. */
class SyncUseCases(
    val getSyncStatus: GetSyncStatus,
    val connectLogbook: ConnectLogbook,
    val saveSyncSettings: SaveSyncSettings,
    val syncLogbook: SyncLogbook,
)

private const val TOKEN_KEY = "sync-token"

/** The access token of the repository the logbook is synchronised to (ADR 0033). */
class SyncSecret(
    store: CredentialStore,
) : KeptSecret(store, TOKEN_KEY)

/** What the last action of the section achieved, for the line under its buttons. */
sealed interface SyncOutcome {
    data class Connected(
        val keeping: SecretKeeping,
    ) : SyncOutcome

    data class Saved(
        val keeping: SecretKeeping?,
    ) : SyncOutcome

    data class Synced(
        val received: Int,
        val sent: Int,
    ) : SyncOutcome

    /** Nothing was exchanged, since these records changed here and on another device. */
    data class Conflicts(
        val records: List<SyncedRecord>,
    ) : SyncOutcome
}

/** Drives the sync settings: connect this logbook to a repository, change how it signs in, and sync by hand. */
class SyncPresenter(
    private val scope: CoroutineScope,
    private val useCases: SyncUseCases,
    private val secret: SyncSecret,
    // A deletion that can still be undone is written first, so it is part of what is sent.
    private val beforeSync: suspend () -> Unit = {},
    // Lets the rest of the app show what other devices sent.
    private val onSynced: () -> Unit = {},
) {
    /** Null until loaded. */
    var status by mutableStateOf<SyncStatus?>(null)
        private set
    val connected: Boolean get() = status?.settings != null

    var input by mutableStateOf(SyncInput())
        private set
    var errors by mutableStateOf<Map<SyncField, FormError>>(emptyMap())
        private set

    /** Whether the form is open: to connect, or to change the settings of a connected logbook. */
    var editing by mutableStateOf(false)
        private set
    var tokenStored by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<SyncOutcome?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    private var running: Job? = null

    fun load() =
        attempt(FailureKind.LoadSettings) {
            status = useCases.getSyncStatus()
            input = storedForm
            tokenStored = secret.current() != null
        }

    fun edit() {
        editing = true
        outcome = null
        failure = null
    }

    /** Drops what was typed, and stops a connect still running, whose answer would no longer matter. */
    fun cancelEdit() {
        running?.cancel()
        editing = false
        input = storedForm
        errors = emptyMap()
        failure = null
    }

    fun change(update: (SyncInput) -> SyncInput) {
        input = update(input)
        outcome = null
    }

    /** Connects with what is typed, or saves it for a logbook already connected. */
    fun save() {
        // A token is needed to connect, even when one is still kept from before.
        val valid = validated(tokenStored = tokenStored && connected) ?: return
        if (connected) saveSettings(valid) else connect(valid)
    }

    fun syncNow() {
        running =
            attempt(FailureKind.Sync) {
                val token = secret.current()
                if (token == null) {
                    failure = Failure(FailureKind.SyncTokenMissing)
                    return@attempt
                }
                busy = true
                try {
                    beforeSync()
                    val report = useCases.syncLogbook(token)
                    outcome =
                        if (report.conflicts.isEmpty()) {
                            SyncOutcome.Synced(report.received, report.sent)
                        } else {
                            SyncOutcome.Conflicts(report.conflicts)
                        }
                    status = useCases.getSyncStatus()
                    if (report.received > 0) onSynced()
                } finally {
                    busy = false
                }
            }
    }

    /** Stops a running sync or connect; what was exchanged so far stays consistent (ADR 0033). */
    fun cancel() {
        running?.cancel()
    }

    private fun connect(valid: SyncFormResult.Valid) {
        running =
            attempt(FailureKind.ConnectSync) {
                busy = true
                try {
                    val token = checkNotNull(valid.token) { "Connecting needs a token" }
                    beforeSync()
                    useCases.connectLogbook(valid.settings, token)
                    val keeping = secret.keep(token)
                    status = useCases.getSyncStatus()
                    tokenStored = true
                    editing = false
                    input = storedForm
                    outcome = SyncOutcome.Connected(keeping)
                } finally {
                    busy = false
                }
            }
    }

    private fun saveSettings(valid: SyncFormResult.Valid) =
        attempt(FailureKind.SaveSettings) {
            val settings = valid.settings
            useCases.saveSyncSettings(settings.username, settings.authorName, settings.authorEmail)
            val keeping = valid.token?.let { secret.keep(it) }
            status = useCases.getSyncStatus()
            tokenStored = true
            editing = false
            input = storedForm
            outcome = SyncOutcome.Saved(keeping)
        }

    private val storedForm: SyncInput get() = status?.settings?.let(SyncInput::from) ?: SyncInput()

    private fun validated(tokenStored: Boolean): SyncFormResult.Valid? {
        outcome = null
        return when (val result = input.validate(tokenStored)) {
            is SyncFormResult.Invalid -> {
                errors = result.errors
                null
            }

            is SyncFormResult.Valid -> {
                errors = emptyMap()
                result
            }
        }
    }

    private fun attempt(
        kind: FailureKind,
        action: suspend () -> Unit,
    ): Job =
        scope.launch {
            try {
                failure = null
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncException) {
                failure = Failure(e.problem.failureKind(), e.message)
                // A storage or credential store failure must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(kind)
            }
        }
}

internal fun SyncProblem.failureKind(): FailureKind =
    when (this) {
        SyncProblem.NoConnection -> FailureKind.NoConnection
        SyncProblem.Unreachable -> FailureKind.SyncUnreachable
        SyncProblem.CredentialsRefused -> FailureKind.SyncCredentialsRefused
        SyncProblem.NoSuchRepository -> FailureKind.SyncNoSuchRepository
        SyncProblem.RemoteNotEmpty -> FailureKind.SyncRemoteNotEmpty
        SyncProblem.Unexpected -> FailureKind.SyncUnexpected
    }
