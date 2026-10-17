package ch.qscqlmpa.wingtracker.ui.airtime

import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.CredentialStoreException

private const val KEY = "airtime-token"

/** Where a token ended up. */
enum class TokenKeeping {
    // In the operating system's credential store, so it is still there after a restart.
    System,

    // In memory only: the system has no store or refused, and the token is never written in plain text (ADR 0029).
    Session,
}

/** The pilot's air-time token, kept by the system when it can and for the session otherwise. */
class AirtimeToken(
    private val store: CredentialStore,
) {
    private var forSession: String? = null

    suspend fun current(): String? =
        forSession ?: try {
            store.read(KEY)
        } catch (_: CredentialStoreException) {
            null
        }

    suspend fun keep(token: String): TokenKeeping {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "A token cannot be empty" }
        forSession = trimmed
        return try {
            store.write(KEY, trimmed)
            TokenKeeping.System
        } catch (_: CredentialStoreException) {
            TokenKeeping.Session
        }
    }

    /** Forgets the token everywhere, e.g. after air-time refused it. */
    suspend fun forget() {
        forSession = null
        try {
            store.delete(KEY)
        } catch (_: CredentialStoreException) {
            // Nothing was kept there that could be forgotten.
        }
    }
}
