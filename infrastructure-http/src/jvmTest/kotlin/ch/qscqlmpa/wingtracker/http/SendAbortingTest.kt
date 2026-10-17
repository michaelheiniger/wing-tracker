package ch.qscqlmpa.wingtracker.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.concurrent.thread

// Long enough for a slow build machine, short enough that a test which hangs fails instead.
private const val WAIT_MILLIS = 5_000

class SendAbortingTest {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = WAIT_MILLIS }
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    private fun request(port: Int = server.localPort) =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/releases")).GET().build()

    @AfterEach
    fun closeServer() {
        server.close()
    }

    @Nested
    inner class SendAborting {
        @Test
        fun `returns the answer of the server`() {
            // given
            thread {
                server.accept().use { socket ->
                    socket.getInputStream().read(ByteArray(4096))
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n[]".toByteArray())
                }
            }

            // when
            val response = runBlocking { client.sendAborting(request(), HttpResponse.BodyHandlers.ofString()) }

            // then
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.body()).isEqualTo("[]")
        }

        @Test
        fun `aborts the exchange when the waiting coroutine is cancelled`() {
            runBlocking {
                // given
                val waiting =
                    async(Dispatchers.IO) { client.sendAborting(request(), HttpResponse.BodyHandlers.ofString()) }
                val socket = server.accept().apply { soTimeout = WAIT_MILLIS }
                val input = socket.getInputStream()
                input.read(ByteArray(4096))

                // when
                waiting.cancel()

                // then
                assertThat(input.read()).describedAs("bytes after the connection is closed").isEqualTo(-1)
                socket.close()
            }
        }

        @Test
        fun `throws what went wrong itself, not wrapped, when no connection could be made`() {
            // given
            val closedPort = ServerSocket(0).use { it.localPort }

            // when / then
            assertThatThrownBy {
                runBlocking { client.sendAborting(request(closedPort), HttpResponse.BodyHandlers.ofString()) }
            }.isInstanceOf(ConnectException::class.java)
        }
    }
}
