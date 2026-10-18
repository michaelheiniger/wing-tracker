package ch.qscqlmpa.wingtracker.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.backup.CheckCloudStorage
import ch.qscqlmpa.wingtracker.backup.CloudProblem
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.backup.SaveCloudStorage
import ch.qscqlmpa.wingtracker.settings.GetAppSettings
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

/** Use cases the cloud storage settings need; passed in so this module never sees the DI framework. */
class CloudStorageUseCases(
    val getAppSettings: GetAppSettings,
    val saveCloudStorage: SaveCloudStorage,
    val checkCloudStorage: CheckCloudStorage,
)

private const val SECRET_KEY = "cloud-storage-secret-key"

/** The secret key of the bucket backups are uploaded to (ADR 0032). */
class CloudSecret(
    store: CredentialStore,
) : KeptSecret(store, SECRET_KEY)

/** What the last action of the section achieved, for the line under its buttons. */
sealed interface CloudStorageOutcome {
    data class Saved(
        val keeping: SecretKeeping?,
    ) : CloudStorageOutcome

    data object Connected : CloudStorageOutcome

    data object Removed : CloudStorageOutcome
}

/** Drives the cloud storage settings: set up the bucket backups go to, test it, or remove it (ADR 0032). */
class CloudStoragePresenter(
    private val scope: CoroutineScope,
    private val useCases: CloudStorageUseCases,
    private val secret: CloudSecret,
    // Lets the backup section offer uploading as soon as a bucket is set up.
    private val onChanged: () -> Unit = {},
) {
    var input by mutableStateOf(CloudStorageInput())
        private set
    var errors by mutableStateOf<Map<CloudStorageField, FormError>>(emptyMap())
        private set

    /** What is saved, for the summary; the form may hold changes not saved yet. */
    var stored by mutableStateOf<CloudStorageSettings?>(null)
        private set
    val configured: Boolean get() = stored != null

    /** Whether the form is open; it is closed once saved, and reset to what is saved when cancelled. */
    var editing by mutableStateOf(false)
        private set
    var secretStored by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<CloudStorageOutcome?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    private var check: Job? = null

    fun load() =
        attempt(FailureKind.LoadSettings) {
            stored = useCases.getAppSettings().cloudStorage
            input = formOfStored()
            secretStored = secret.current() != null
        }

    fun edit() {
        editing = true
        outcome = null
        failure = null
    }

    /** Drops what was typed, and stops a running test whose answer would no longer match the form. */
    fun cancelEdit() {
        check?.cancel()
        editing = false
        input = formOfStored()
        errors = emptyMap()
        outcome = null
        failure = null
    }

    fun change(update: (CloudStorageInput) -> CloudStorageInput) {
        input = update(input)
        outcome = null
    }

    fun save() {
        val valid = validated() ?: return
        attempt(FailureKind.SaveSettings) {
            val keeping = valid.secretKey?.let { secret.keep(it) }
            useCases.saveCloudStorage(valid.settings)
            stored = valid.settings
            editing = false
            secretStored = true
            input = input.copy(secretKey = "")
            outcome = CloudStorageOutcome.Saved(keeping)
            onChanged()
        }
    }

    /** Tries what is typed, saved or not, so a mistake shows before the first upload. */
    fun test() {
        val valid = validated() ?: return
        check =
            attempt(FailureKind.CloudCheck) {
                busy = true
                try {
                    val key = valid.secretKey ?: checkNotNull(secret.current()) { "No secret key is kept" }
                    useCases.checkCloudStorage(valid.settings.target(key))
                    outcome = CloudStorageOutcome.Connected
                } finally {
                    busy = false
                }
            }
    }

    fun remove() =
        attempt(FailureKind.SaveSettings) {
            useCases.saveCloudStorage(null)
            secret.forget()
            stored = null
            secretStored = false
            input = CloudStorageInput()
            errors = emptyMap()
            outcome = CloudStorageOutcome.Removed
            onChanged()
        }

    private fun formOfStored() = stored?.let(CloudStorageInput::from) ?: CloudStorageInput()

    private fun validated(): CloudStorageFormResult.Valid? {
        outcome = null
        return when (val result = input.validate(secretStored)) {
            is CloudStorageFormResult.Invalid -> {
                errors = result.errors
                null
            }

            is CloudStorageFormResult.Valid -> {
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
            } catch (e: CloudStorageException) {
                failure = Failure(e.problem.failureKind(), e.message)
                // A storage or credential store failure must show a message, never crash the window.
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                failure = e.toFailure(kind)
            }
        }
}

internal fun CloudProblem.failureKind(): FailureKind =
    when (this) {
        CloudProblem.NoConnection -> FailureKind.NoConnection
        CloudProblem.Unreachable -> FailureKind.CloudUnreachable
        CloudProblem.KeysRefused -> FailureKind.CloudKeysRefused
        CloudProblem.NoSuchBucket -> FailureKind.CloudNoSuchBucket
        CloudProblem.AccessDenied -> FailureKind.CloudAccessDenied
        CloudProblem.ClockOff -> FailureKind.CloudClockOff
        CloudProblem.Unexpected -> FailureKind.CloudUnexpected
    }
