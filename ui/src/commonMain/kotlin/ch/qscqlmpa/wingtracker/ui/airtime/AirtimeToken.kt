package ch.qscqlmpa.wingtracker.ui.airtime

import ch.qscqlmpa.wingtracker.ui.platform.CredentialStore
import ch.qscqlmpa.wingtracker.ui.platform.KeptSecret

private const val KEY = "airtime-token"

/** The pilot's air-time token (ADR 0029). */
class AirtimeToken(
    store: CredentialStore,
) : KeptSecret(store, KEY)
