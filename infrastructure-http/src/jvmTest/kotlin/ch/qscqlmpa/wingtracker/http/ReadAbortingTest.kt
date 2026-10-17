package ch.qscqlmpa.wingtracker.http

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

// Long enough for a slow build machine, short enough that a test which hangs fails instead.
private const val WAIT_MILLIS = 5_000L
private const val TEST_SECONDS = 20L

// Far longer than any test takes, for the tests that are not about the idle timeout.
private val LONG_IDLE = 1.minutes

class ReadAbortingTest {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = WAIT_MILLIS.toInt() }
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    @AfterEach
    fun closeServer() {
        server.close()
    }

    /** Answers with the start of a large installer, then sends nothing more; returns what the client did then. */
    private fun stallingServer(): CountDownLatch {
        val clientClosed = CountDownLatch(1)
        thread {
            server.accept().use { socket ->
                socket.getInputStream().read(ByteArray(4096))
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 83886080\r\n\r\n!Debian".toByteArray(),
                )
                socket.soTimeout = WAIT_MILLIS.toInt()
                if (socket.getInputStream().read() < 0) clientClosed.countDown()
            }
        }
        return clientClosed
    }

    @Nested
    inner class ReadAborting {
        @Test
        fun `returns what was read and closes the stream`() {
            // given
            var closed = false
            val stream =
                object : ByteArrayInputStream("!Debian package".toByteArray()) {
                    override fun close() {
                        closed = true
                    }
                }

            // when
            val text = runBlocking { stream.readAborting(LONG_IDLE) { it.readBytes().decodeToString() } }

            // then
            assertThat(text).isEqualTo("!Debian package")
            assertThat(closed).isTrue()
        }

        // On its own thread, so a read that is never stopped fails the test instead of hanging the build.
        @Test
        @Timeout(value = TEST_SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        fun `stops a body that stopped arriving as soon as the coroutine is cancelled, and closes the connection`() {
            runBlocking {
                // given
                val clientClosed = stallingServer()
                val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.localPort}/")).build()
                val firstBytes = CountDownLatch(1)
                var outcome: Any? = null
                val reading =
                    launch(Dispatchers.Default) {
                        try {
                            client
                                .sendAborting(
                                    request,
                                    HttpResponse.BodyHandlers.ofInputStream(),
                                ).body()
                                .readAborting(LONG_IDLE) { body ->
                                    body.read(ByteArray(7))
                                    firstBytes.countDown()
                                    body.read(ByteArray(7))
                                }
                        } catch (e: CancellationException) {
                            outcome = "cancelled"
                            throw e
                        } catch (e: IOException) {
                            outcome = e
                        }
                    }
                assertThat(firstBytes.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()

                // when
                withTimeout(WAIT_MILLIS) { reading.cancelAndJoin() }

                // then
                assertThat(outcome).isEqualTo("cancelled")
                assertThat(
                    clientClosed.await(WAIT_MILLIS, TimeUnit.MILLISECONDS),
                ).describedAs("connection closed").isTrue()
            }
        }

        // On its own thread, so a stall that is never noticed fails the test instead of hanging the build.
        @Test
        @Timeout(value = TEST_SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        fun `gives up on a body that stopped arriving for too long, and closes the connection`() {
            // given
            val clientClosed = stallingServer()
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.localPort}/")).build()

            // when / then
            assertThatThrownBy {
                runBlocking {
                    client.sendAborting(request, HttpResponse.BodyHandlers.ofInputStream()).body().readAborting(
                        300.milliseconds,
                    ) { it.readBytes() }
                }
            }.isInstanceOf(HttpTimeoutException::class.java)
            assertThat(clientClosed.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).describedAs("connection closed").isTrue()
        }

        @Test
        fun `keeps reading a body that arrives slowly but steadily`() {
            // given
            val trickle =
                // One byte per read, and none once closed, as a socket would.
                object : InputStream() {
                    private var left = 8

                    @Volatile
                    private var closed = false

                    override fun read(): Int = throw UnsupportedOperationException("only read into a buffer")

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int {
                        if (left == 0) return -1
                        Thread.sleep(100)
                        if (closed) throw IOException("closed")
                        left--
                        b[off] = 1
                        return 1
                    }

                    override fun close() {
                        closed = true
                    }
                }

            // when
            val bytes = runBlocking { trickle.readAborting(300.milliseconds) { it.readBytes() } }

            // then
            assertThat(bytes).hasSize(8)
        }

        @Test
        fun `reports a read that failed by itself`() {
            // given
            val broken =
                object : InputStream() {
                    override fun read(): Int = throw IOException("Connection reset")
                }

            // when / then
            assertThatThrownBy { runBlocking { broken.readAborting(LONG_IDLE) { it.read() } } }
                .isInstanceOf(IOException::class.java)
                .hasMessage("Connection reset")
        }
    }
}
