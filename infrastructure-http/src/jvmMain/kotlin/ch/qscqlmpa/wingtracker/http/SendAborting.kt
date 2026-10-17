package ch.qscqlmpa.wingtracker.http

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sends [request] without blocking a thread. Cancelling the calling coroutine aborts the exchange, so a pilot who
 * cancels does not leave a connection waiting for its timeout.
 * @throws IOException as the blocking `send` would.
 */
suspend fun <T> HttpClient.sendAborting(
    request: HttpRequest,
    handler: HttpResponse.BodyHandler<T>,
): HttpResponse<T> {
    val answer = sendAsync(request, handler)
    return suspendCancellableCoroutine { continuation ->
        // Only cancel(true) aborts the exchange; the cancel(false) of kotlinx-coroutines' await() leaves it open.
        continuation.invokeOnCancellation { answer.cancel(true) }
        answer.whenComplete { response, error ->
            if (error == null) {
                continuation.resume(response)
            } else {
                continuation.resumeWithException((error as? CompletionException)?.cause ?: error)
            }
        }
    }
}
