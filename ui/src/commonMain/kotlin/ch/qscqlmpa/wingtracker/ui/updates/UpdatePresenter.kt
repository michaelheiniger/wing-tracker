package ch.qscqlmpa.wingtracker.ui.updates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.updates.AvailableUpdate
import ch.qscqlmpa.wingtracker.updates.CheckForUpdate
import ch.qscqlmpa.wingtracker.updates.CheckTrigger
import ch.qscqlmpa.wingtracker.updates.DownloadUpdate
import ch.qscqlmpa.wingtracker.updates.GetUpdatePreferences
import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import ch.qscqlmpa.wingtracker.updates.SaveCheckAtStartup
import ch.qscqlmpa.wingtracker.updates.SkipVersion
import ch.qscqlmpa.wingtracker.updates.UpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UpdateUseCases(
    val checkForUpdate: CheckForUpdate,
    val getUpdatePreferences: GetUpdatePreferences,
    val saveCheckAtStartup: SaveCheckAtStartup,
    val skipVersion: SkipVersion,
    val downloadUpdate: DownloadUpdate,
)

/** The outcome of a check the pilot asked for; a check at startup only ever shows an update it found. */
sealed interface ManualCheck {
    data object Idle : ManualCheck

    data object Checking : ManualCheck

    data object UpToDate : ManualCheck

    // A development build, which is never updated (ADR 0024).
    data object NotApplicable : ManualCheck

    data object Found : ManualCheck

    data class Failed(
        val failure: Failure,
    ) : ManualCheck
}

/** Drives the update banner, the badge and the update section of the settings (ADR 0024). */
class UpdatePresenter(
    private val scope: CoroutineScope,
    private val useCases: UpdateUseCases,
) {
    var update by mutableStateOf<AvailableUpdate?>(null)
        private set

    // "Later" lasts for this run only; the badge stays so the update can still be found.
    var postponed by mutableStateOf(false)
        private set

    var checkAtStartup by mutableStateOf(true)
        private set

    var manualCheck by mutableStateOf<ManualCheck>(ManualCheck.Idle)
        private set

    var failure by mutableStateOf<Failure?>(null)
        private set

    // One dialog for the banner and the settings, so both show the same notes.
    var showsWhatsNew by mutableStateOf(false)
        private set

    val showsBanner: Boolean get() = update?.skipped == false && !postponed

    val showsBadge: Boolean get() = update?.skipped == false

    fun start() {
        guarded(FailureKind.LoadSettings) {
            checkAtStartup = useCases.getUpdatePreferences().checkAtStartup
            // A failed check at startup is only logged by the adapter: the pilot did not ask for it.
            (useCases.checkForUpdate(CheckTrigger.Startup) as? UpdateCheckResult.Available)?.let { update = it.update }
        }
    }

    fun checkNow() {
        manualCheck = ManualCheck.Checking
        guarded(FailureKind.LoadSettings) {
            manualCheck =
                when (val result = useCases.checkForUpdate(CheckTrigger.Manual)) {
                    is UpdateCheckResult.Available -> {
                        // Asking again brings back an update skipped or postponed earlier.
                        update = result.update.copy(skipped = false)
                        postponed = false
                        ManualCheck.Found
                    }

                    UpdateCheckResult.UpToDate -> {
                        update = null
                        ManualCheck.UpToDate
                    }

                    UpdateCheckResult.NotApplicable, UpdateCheckResult.Disabled -> {
                        ManualCheck.NotApplicable
                    }

                    is UpdateCheckResult.Failed -> {
                        ManualCheck.Failed(Failure(result.problem.failureKind(), result.detail))
                    }
                }
        }
    }

    fun openWhatsNew() {
        if (update != null) showsWhatsNew = true
    }

    fun closeWhatsNew() {
        showsWhatsNew = false
    }

    fun postpone() {
        postponed = true
    }

    fun skip() {
        val current = update ?: return
        guarded(FailureKind.SaveSettings) {
            useCases.skipVersion(current.latest.version)
            update = current.copy(skipped = true)
        }
    }

    fun changeCheckAtStartup(enabled: Boolean) {
        guarded(FailureKind.SaveSettings) {
            useCases.saveCheckAtStartup(enabled)
            checkAtStartup = enabled
        }
    }

    private fun guarded(
        kind: FailureKind,
        action: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                failure = null
                action()
            } catch (e: CancellationException) {
                throw e
                // Storing a preference must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = Failure(kind, e.message)
                if (manualCheck == ManualCheck.Checking) manualCheck = ManualCheck.Idle
            }
        }
    }
}

private fun ReleaseProblem.failureKind(): FailureKind =
    when (this) {
        ReleaseProblem.NoConnection -> FailureKind.NoConnection
        ReleaseProblem.Unreachable -> FailureKind.UpdateUnreachable
        ReleaseProblem.Refused -> FailureKind.UpdateRefused
        ReleaseProblem.Unexpected -> FailureKind.UpdateUnexpected
    }
