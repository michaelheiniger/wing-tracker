package ch.qscqlmpa.wingtracker.updates

/**
 * How this installation deals with updates. It belongs to the installation, not to the logbook, so it is not part of
 * the settings stored in the data folder, which may be shared between computers (ADR 0024).
 */
data class UpdatePreferences(
    val checkAtStartup: Boolean = true,
    // The pilot asked not to be reminded of this version; a later one is announced again.
    val skippedVersion: AppVersion? = null,
)

interface UpdatePreferencesRepository {
    /** Returns defaults when nothing was saved yet, so the first start checks without setup. */
    suspend fun load(): UpdatePreferences

    suspend fun save(preferences: UpdatePreferences)
}
