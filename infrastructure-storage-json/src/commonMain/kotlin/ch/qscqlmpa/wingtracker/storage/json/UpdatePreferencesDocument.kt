package ch.qscqlmpa.wingtracker.storage.json

import ch.qscqlmpa.wingtracker.updates.AppVersion
import ch.qscqlmpa.wingtracker.updates.UpdatePreferences
import kotlinx.serialization.Serializable

@Serializable
internal data class UpdatePreferencesDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val checkAtStartup: Boolean = true,
    val skippedVersion: String? = null,
) {
    fun toDomain(): UpdatePreferences =
        UpdatePreferences(
            checkAtStartup = checkAtStartup,
            skippedVersion =
                skippedVersion?.let {
                    requireNotNull(AppVersion.parseOrNull(it)) { "The skipped version '$it' is not a version" }
                },
        )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun from(preferences: UpdatePreferences) =
            UpdatePreferencesDocument(
                checkAtStartup = preferences.checkAtStartup,
                skippedVersion = preferences.skippedVersion?.toString(),
            )
    }
}
