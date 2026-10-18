package ch.qscqlmpa.wingtracker.ui.airtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeException
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportDraft
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeExportResult
import ch.qscqlmpa.wingtracker.flights.airtime.AirtimeProblem
import ch.qscqlmpa.wingtracker.flights.airtime.ExportFlightsToAirtime
import ch.qscqlmpa.wingtracker.flights.airtime.PrepareAirtimeExport
import ch.qscqlmpa.wingtracker.settings.SaveAirtimeNick
import ch.qscqlmpa.wingtracker.ui.Failure
import ch.qscqlmpa.wingtracker.ui.FailureKind
import ch.qscqlmpa.wingtracker.ui.platform.SecretKeeping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the export is: ask for the account, read it, review what would be sent, send, or show what happened. */
enum class AirtimeStage { Account, Reading, Review, Sending, Done }

/** How many of the chosen flights are done while sending. */
data class SendProgress(
    val done: Int,
    val total: Int,
)

/**
 * Drives the export to air-time (ADR 0029): the pilot gives the nick and, once, a token; the preview shows what would
 * be sent and asks the colour of each glider to create; nothing is sent before the pilot confirms.
 */
class AirtimeExportPresenter(
    private val scope: CoroutineScope,
    private val prepare: PrepareAirtimeExport,
    private val export: ExportFlightsToAirtime,
    private val saveNick: SaveAirtimeNick,
    private val token: AirtimeToken,
    initialNick: String,
) {
    var nick by mutableStateOf(initialNick)
        private set

    /** A token typed in this dialog; empty when the kept one is used. */
    var tokenInput by mutableStateOf("")
        private set

    /** Whether a token is kept from earlier, so the pilot need not paste it again. */
    var hasToken by mutableStateOf(false)
        private set

    /** Where the token typed in this dialog ended up; null until one was kept. */
    var keeping by mutableStateOf<SecretKeeping?>(null)
        private set
    var stage by mutableStateOf(AirtimeStage.Account)
        private set
    var draft by mutableStateOf<AirtimeExportDraft?>(null)
        private set
    val selection = AirtimeSelection()
    var progress by mutableStateOf<SendProgress?>(null)
        private set
    var result by mutableStateOf<AirtimeExportResult?>(null)
        private set

    /** True when the pilot stopped the sending; the flights sent until then stay sent. */
    var stopped by mutableStateOf(false)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    private var job: Job? = null

    // The token the preview was read with, so sending acts as the same account.
    private var tokenInUse: String? = null

    val canRead: Boolean
        get() = stage == AirtimeStage.Account && nick.isNotBlank() && (hasToken || tokenInput.isNotBlank())

    val canSend: Boolean get() = stage == AirtimeStage.Review && selection.isComplete

    /** Finds out whether a token is kept; the account step asks for one only when there is none. */
    fun start() {
        scope.launch { hasToken = token.current() != null }
    }

    fun changeNick(value: String) {
        nick = value
    }

    fun changeToken(value: String) {
        tokenInput = value
    }

    /** Fills in the nick used before, unless the pilot already started typing another one. */
    fun suggestNick(remembered: String?) {
        if (nick.isEmpty() && !remembered.isNullOrBlank()) nick = remembered
    }

    /** Forgets the kept token, e.g. to paste a new one after generating it again on air-time. */
    fun forgetToken() {
        scope.launch {
            token.forget()
            hasToken = false
            keeping = null
        }
    }

    fun read() {
        if (!canRead) return
        val typed = tokenInput.trim()
        val name = nick.trim()
        stage = AirtimeStage.Reading
        failure = null
        job =
            scope.launch {
                try {
                    val used = typed.ifEmpty { token.current() }
                    if (used == null) {
                        hasToken = false
                        stage = AirtimeStage.Account
                        return@launch
                    }
                    val prepared = prepare(name, used)
                    // Kept only once air-time accepted it, so a mistyped token is never remembered.
                    if (typed.isNotEmpty()) {
                        keeping = token.keep(typed)
                        hasToken = true
                        tokenInput = ""
                    }
                    saveNick(name)
                    tokenInUse = used
                    draft = prepared
                    selection.start(prepared)
                    stage = AirtimeStage.Review
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AirtimeException) {
                    refused(e.problem, typed.isEmpty())
                    failure = Failure(e.problem.failureKind(), e.message.takeIf { e.problem.hasDetail() })
                    stage = AirtimeStage.Account
                    // Any failure must reach the user as a message; it must never crash the window.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    failure = Failure(FailureKind.ExportAirtime, e.message)
                    stage = AirtimeStage.Account
                }
            }
    }

    /** Stops a read that keeps waiting, e.g. on a connection that hangs; the pilot is back at the account step. */
    fun cancelReading() {
        if (stage != AirtimeStage.Reading) return
        job?.cancel()
        stage = AirtimeStage.Account
    }

    fun back() {
        stage = AirtimeStage.Account
        failure = null
    }

    fun send() {
        val current = draft ?: return
        val used = tokenInUse ?: return
        if (!canSend) return
        stage = AirtimeStage.Sending
        stopped = false
        failure = null
        val chosen = selection.chosen
        val colours = selection.trimmedColours
        progress = SendProgress(0, chosen.size)
        job =
            scope.launch {
                try {
                    val outcome =
                        export(used, current, chosen, colours) { done, total ->
                            progress =
                                SendProgress(done, total)
                        }
                    outcome.failure?.let {
                        refused(it, keptToken = true)
                        failure = Failure(it.failureKind())
                    }
                    result = outcome
                } catch (e: CancellationException) {
                    throw e
                    // Any failure must reach the user as a message; it must never crash the window.
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    failure = Failure(FailureKind.ExportAirtime, e.message)
                }
                stage = AirtimeStage.Done
            }
    }

    /** Stops sending after the flight on its way; the flights sent until then are marked and stay on air-time. */
    fun stopSending() {
        if (stage != AirtimeStage.Sending) return
        job?.cancel()
        stopped = true
        stage = AirtimeStage.Done
    }

    // A refused token is forgotten, so the next export asks for a new one instead of failing the same way.
    private suspend fun refused(
        problem: AirtimeProblem,
        keptToken: Boolean,
    ) {
        if (problem != AirtimeProblem.TokenRefused || !keptToken) return
        token.forget()
        hasToken = false
        keeping = null
    }
}

/** The problems whose message depends on what air-time answered, worth showing to help a bug report. */
private fun AirtimeProblem.hasDetail() = this == AirtimeProblem.Unreachable || this == AirtimeProblem.UnexpectedAnswer

internal fun AirtimeProblem.failureKind() =
    when (this) {
        AirtimeProblem.NoConnection -> FailureKind.NoConnection
        AirtimeProblem.Unreachable -> FailureKind.AirtimeUnreachable
        AirtimeProblem.TokenRefused -> FailureKind.AirtimeTokenRefused
        AirtimeProblem.PilotNotFound -> FailureKind.AirtimePilotNotFound
        AirtimeProblem.UnexpectedAnswer -> FailureKind.AirtimeUnexpected
    }
