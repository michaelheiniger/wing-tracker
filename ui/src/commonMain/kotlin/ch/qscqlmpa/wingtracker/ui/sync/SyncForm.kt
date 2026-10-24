package ch.qscqlmpa.wingtracker.ui.sync

import ch.qscqlmpa.wingtracker.sync.SyncSettings
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError

/** The sync form as typed; [token] stays empty to keep the one already stored. */
data class SyncInput(
    val repository: String = "",
    val username: String = "",
    val token: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
) {
    companion object {
        fun from(settings: SyncSettings) =
            SyncInput(
                repository = settings.repository,
                username = settings.username.orEmpty(),
                authorName = settings.authorName,
                authorEmail = settings.authorEmail,
            )
    }
}

enum class SyncField { Repository, Username, Token, AuthorName, AuthorEmail }

sealed interface SyncFormResult {
    /** [token] is null when the stored one is kept. */
    data class Valid(
        val settings: SyncSettings,
        val token: String?,
    ) : SyncFormResult

    data class Invalid(
        val errors: Map<SyncField, FormError>,
    ) : SyncFormResult
}

/** [tokenStored] tells whether a token is already kept, so the field may stay empty. */
fun SyncInput.validate(tokenStored: Boolean): SyncFormResult {
    val errors = linkedMapOf<SyncField, FormError>()
    val repository = repository.trim()
    when {
        repository.isEmpty() -> errors[SyncField.Repository] = FormError.Required
        !repository.startsWith("https://") -> errors[SyncField.Repository] = FormError.InvalidEndpoint
    }
    if (token.isBlank() && !tokenStored) errors[SyncField.Token] = FormError.Required
    if (authorName.isBlank()) errors[SyncField.AuthorName] = FormError.Required
    val email = authorEmail.trim()
    when {
        email.isEmpty() -> errors[SyncField.AuthorEmail] = FormError.Required

        // Only what Git needs; whether the address works is the host's business.
        !EMAIL.matches(email) -> errors[SyncField.AuthorEmail] = FormError.InvalidEmail
    }
    if (errors.isNotEmpty()) return SyncFormResult.Invalid(errors)
    return SyncFormResult.Valid(
        SyncSettings(repository, username.trim().ifEmpty { null }, authorName.trim(), email),
        token.trim().ifEmpty { null },
    )
}

private val EMAIL = Regex("""[^\s@]+@[^\s@]+""")
