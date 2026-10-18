package ch.qscqlmpa.wingtracker.ui.platform

/** Where a secret ended up. */
enum class SecretKeeping {
    // In the operating system's credential store, so it is still there after a restart.
    System,

    // In memory only: the system has no store or refused, and a secret is never written in plain text (ADR 0029).
    Session,
}

/** One secret of the pilot under [key], kept by the system when it can and for the session otherwise. */
open class KeptSecret(
    private val store: CredentialStore,
    private val key: String,
) {
    private var forSession: String? = null

    suspend fun current(): String? =
        forSession ?: try {
            store.read(key)
        } catch (_: CredentialStoreException) {
            null
        }

    suspend fun keep(secret: String): SecretKeeping {
        val trimmed = secret.trim()
        require(trimmed.isNotEmpty()) { "A secret cannot be empty" }
        forSession = trimmed
        return try {
            store.write(key, trimmed)
            SecretKeeping.System
        } catch (_: CredentialStoreException) {
            SecretKeeping.Session
        }
    }

    /** Forgets the secret everywhere, e.g. after the service refused it. */
    suspend fun forget() {
        forSession = null
        try {
            store.delete(key)
        } catch (_: CredentialStoreException) {
            // Nothing was kept there that could be forgotten.
        }
    }
}
