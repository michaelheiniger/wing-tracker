package ch.qscqlmpa.wingtracker.http

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Reads this stream with [block] on a thread that may block, then closes it. Cancelling the calling coroutine closes
 * the stream at once: a response body that stops arriving has no timeout, so its read would otherwise wait forever.
 */
suspend fun <T> InputStream.readAborting(block: (InputStream) -> T): T =
    use { stream ->
        coroutineScope {
            // Waits beside the read, since the blocked read itself cannot notice the cancellation.
            val closer =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        stream.close()
                    }
                }
            try {
                withContext(Dispatchers.IO) { block(stream) }
            } catch (e: IOException) {
                // Closing the stream fails the read; the pilot cancelled, so that is no failure to report.
                ensureActive()
                throw e
            } finally {
                closer.cancel()
            }
        }
    }
