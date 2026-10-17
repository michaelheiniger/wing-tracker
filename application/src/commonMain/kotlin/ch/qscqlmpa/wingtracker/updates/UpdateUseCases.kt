package ch.qscqlmpa.wingtracker.updates

/** What started a check: the one at startup respects the pilot's choice to be left alone, a manual one does not. */
enum class CheckTrigger {
    Startup,
    Manual,
}

sealed interface UpdateCheckResult {
    /** Development builds have no release version to compare, and the pilot never asked for them to update. */
    data object NotApplicable : UpdateCheckResult

    data object Disabled : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class Available(
        val update: AvailableUpdate,
    ) : UpdateCheckResult

    data class Failed(
        val problem: ReleaseProblem,
        val detail: String?,
    ) : UpdateCheckResult
}

/**
 * Looks for a release newer than this installation that it can install. [installed] is null for a development build;
 * [installerKind] is how this installation was set up, so the update is of the same kind.
 */
class CheckForUpdate(
    private val source: ReleaseSource,
    private val preferences: UpdatePreferencesRepository,
    private val installed: AppVersion?,
    private val installerKind: InstallerKind,
) {
    suspend operator fun invoke(trigger: CheckTrigger): UpdateCheckResult {
        if (installed == null) return UpdateCheckResult.NotApplicable
        val stored = preferences.load()
        if (trigger == CheckTrigger.Startup && !stored.checkAtStartup) return UpdateCheckResult.Disabled
        val published =
            try {
                source.published()
            } catch (e: ReleaseSourceException) {
                return UpdateCheckResult.Failed(e.problem, e.message)
            }
        val update = AvailableUpdate.find(installed, published, installerKind, stored.skippedVersion)
        return update?.let { UpdateCheckResult.Available(it) } ?: UpdateCheckResult.UpToDate
    }
}

class SkipVersion(
    private val preferences: UpdatePreferencesRepository,
) {
    suspend operator fun invoke(version: AppVersion) {
        preferences.save(preferences.load().copy(skippedVersion = version))
    }
}

class SaveCheckAtStartup(
    private val preferences: UpdatePreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferences.save(preferences.load().copy(checkAtStartup = enabled))
    }
}

class GetUpdatePreferences(
    private val preferences: UpdatePreferencesRepository,
) {
    suspend operator fun invoke(): UpdatePreferences = preferences.load()
}
