package ch.qscqlmpa.wingtracker.http

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.http.HttpTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Reads this stream with [block] on a thread that may block, then closes it. A response body that stops arriving has
 * no timeout of its own, so the stream is closed as soon as the calling coroutine is cancelled, or once nothing has
 * arrived for [idleTimeout]; the read then fails with an [HttpTimeoutException].
 */
suspend fun <T> InputStream.readAborting(
    idleTimeout: Duration,
    block: (InputStream) -> T,
): T =
    WatchedStream(this).use { stream ->
        coroutineScope {
            // Waits beside the read, since the blocked read itself can notice neither a cancellation nor a stall.
            // Real time, not the caller's clock: the stall is measured in real time.
            val watchdog =
                launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    try {
                        while (true) {
                            val left = idleTimeout - stream.idleFor()
                            if (!left.isPositive()) break
                            delay(left)
                        }
                        stream.stalled = true
                    } finally {
                        stream.close()
                    }
                }
            try {
                withContext(Dispatchers.IO) { block(stream) }
            } catch (e: IOException) {
                // Closing the stream fails the read; the pilot cancelled, so that is no failure to report.
                ensureActive()
                if (stream.stalled) {
                    throw HttpTimeoutException(
                        "Nothing arrived for $idleTimeout",
                    ).apply { initCause(e) }
                }
                throw e
            } finally {
                watchdog.cancel()
            }
        }
    }

/** Remembers when data last arrived; written by the reading thread, read by the watchdog. */
private class WatchedStream(
    stream: InputStream,
) : FilterInputStream(stream) {
    @Volatile
    private var lastArrival = System.nanoTime()

    @Volatile
    var stalled = false

    fun idleFor(): Duration = (System.nanoTime() - lastArrival).nanoseconds

    override fun read(): Int = super.read().also { if (it >= 0) lastArrival = System.nanoTime() }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int = super.read(b, off, len).also { if (it > 0) lastArrival = System.nanoTime() }
}
