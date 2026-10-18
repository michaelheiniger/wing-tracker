package ch.qscqlmpa.wingtracker.s3

import ch.qscqlmpa.wingtracker.backup.CloudObject
import ch.qscqlmpa.wingtracker.backup.CloudProblem
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudTarget
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.time.Clock
import kotlin.time.Instant

private const val BUCKET = "wing-tracker-backups"
private const val KEY = "wing-tracker/wing-tracker-backup-2026-10-18-1430.zip"
private val NOW = Instant.parse("2026-10-18T12:30:00Z")

/** A request as the fake storage received it. */
private class Received(
    val method: String,
    val path: String,
    val query: String?,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/** Answers each request with the next of [answers]: a status and a body. */
private class FakeStorage(
    private val answers: ArrayDeque<Pair<Int, ByteArray>>,
) : AutoCloseable {
    val received = mutableListOf<Received>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> answer(exchange) }
            start()
        }

    val endpoint: String get() = "http://127.0.0.1:${server.address.port}"

    private fun answer(exchange: HttpExchange) {
        received +=
            Received(
                exchange.requestMethod,
                exchange.requestURI.rawPath,
                exchange.requestURI.rawQuery,
                exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() },
                exchange.requestBody.readBytes(),
            )
        val (status, body) = answers.removeFirst()
        exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() = server.stop(0)
}

private fun listing(
    vararg keys: String,
    nextToken: String? = null,
) = """
    <?xml version="1.0" encoding="UTF-8"?>
    <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      <Name>$BUCKET</Name>
      <IsTruncated>${nextToken != null}</IsTruncated>
      ${nextToken?.let { "<NextContinuationToken>$it</NextContinuationToken>" }.orEmpty()}
      ${keys.joinToString("") {
    "<Contents><Key>$it</Key><Size>18350080</Size><LastModified>2026-10-18T12:30:05.000Z</LastModified></Contents>"
}}
    </ListBucketResult>
    """.trimIndent().encodeToByteArray()

private fun error(code: String) =
    """<?xml version="1.0" encoding="UTF-8"?><Error><Code>$code</Code><Message>No.</Message></Error>"""
        .encodeToByteArray()

class S3CloudStorageTest {
    @TempDir
    lateinit var folder: Path

    private var fake: FakeStorage? = null
    private val storage =
        S3CloudStorage(
            "wing-tracker/0.4.0",
            object : Clock {
                override fun now(): Instant = NOW
            },
        )

    @AfterEach
    fun stop() {
        fake?.close()
    }

    private fun answering(vararg answers: Pair<Int, ByteArray>): CloudTarget {
        val started = FakeStorage(ArrayDeque(answers.toList())).also { fake = it }
        return CloudTarget(started.endpoint, "eu-amsterdam-1", BUCKET, "AKIAMEGAS4EXAMPLE", "s3cr3t/K3y")
    }

    private val received get() = checkNotNull(fake).received

    @Nested
    inner class Check {
        @Test
        fun `lists at most one object of the bucket, signed for the region`() =
            runTest {
                // given
                val target = answering(200 to listing())

                // when
                storage.check(target)

                // then
                val request = received.single()
                assertThat(request.method).isEqualTo("GET")
                assertThat(request.path).isEqualTo("/$BUCKET")
                assertThat(request.query).isEqualTo("list-type=2&max-keys=1&prefix=")
                assertThat(request.headers["authorization"])
                    .startsWith("AWS4-HMAC-SHA256 Credential=AKIAMEGAS4EXAMPLE/20261018/eu-amsterdam-1/s3/aws4_request")
                assertThat(request.headers["x-amz-date"]).isEqualTo("20261018T123000Z")
            }

        @ParameterizedTest
        @CsvSource(
            "403, InvalidAccessKeyId, KeysRefused",
            "403, SignatureDoesNotMatch, KeysRefused",
            "404, NoSuchBucket, NoSuchBucket",
            "403, AccessDenied, AccessDenied",
            "403, RequestTimeTooSkewed, ClockOff",
            "503, SlowDown, Unreachable",
            "400, InvalidArgument, Unexpected",
        )
        fun `tells why the storage refused`(
            status: Int,
            code: String,
            problem: CloudProblem,
        ) = runTest {
            // given
            val target = answering(status to error(code))

            // when
            val failure = runCatching { storage.check(target) }.exceptionOrNull()

            // then
            assertThat((failure as? CloudStorageException)?.problem).isEqualTo(problem)
        }

        @Test
        fun `tells when the storage cannot be reached at all`() =
            runTest {
                // given
                val closedPort = ServerSocket(0).use { it.localPort }
                val target = CloudTarget("http://127.0.0.1:$closedPort", "eu-amsterdam-1", BUCKET, "AKIA", "secret")

                // when
                val failure = runCatching { storage.check(target) }.exceptionOrNull()

                // then
                assertThat((failure as? CloudStorageException)?.problem).isEqualTo(CloudProblem.NoConnection)
            }
    }

    @Nested
    inner class Upload {
        @Test
        fun `puts the file under its key with the hash of what it sends`() =
            runTest {
                // given
                val target = answering(200 to ByteArray(0))
                val bytes = "PK\u0003\u0004 a backup".encodeToByteArray()
                val file = folder.resolve("backup.zip").also { it.writeBytes(bytes) }

                // when
                storage.upload(target, KEY, file.toString())

                // then
                val request = received.single()
                assertThat(request.method).isEqualTo("PUT")
                assertThat(request.path).isEqualTo("/$BUCKET/$KEY")
                assertThat(request.body).isEqualTo(bytes)
                assertThat(request.headers["x-amz-content-sha256"]).isEqualTo(sha256(bytes))
                assertThat(request.headers.keys.filter { it.startsWith("x-amz-checksum") }).isEmpty()
            }
    }

    @Nested
    inner class ListObjects {
        @Test
        fun `follows every page of the listing`() =
            runTest {
                // given
                val first = "wing-tracker/wing-tracker-backup-2026-09-27-1905.zip"
                val target = answering(200 to listing(first, nextToken = "page 2"), 200 to listing(KEY))

                // when
                val objects = storage.list(target, "wing-tracker/")

                // then
                val listed = CloudObject(KEY, 18_350_080, Instant.parse("2026-10-18T12:30:05Z"))
                assertThat(objects).containsExactly(listed.copy(key = first), listed)
                assertThat(received[1].query).contains("continuation-token=page%202")
            }

        @Test
        fun `reports an answer that is no listing`() =
            runTest {
                // given
                val target = answering(200 to "<html>Login</html>".encodeToByteArray())

                // when
                val failure = runCatching { storage.list(target, "wing-tracker/") }.exceptionOrNull()

                // then
                assertThat((failure as? CloudStorageException)?.problem).isEqualTo(CloudProblem.Unexpected)
            }
    }

    @Nested
    inner class Download {
        @Test
        fun `saves the object to the file`() =
            runTest {
                // given
                val bytes = "PK\u0003\u0004 a backup".encodeToByteArray()
                val target = answering(200 to bytes)
                val file = folder.resolve("downloaded.zip")

                // when
                storage.download(target, KEY, file.toString())

                // then
                assertThat(file.readBytes()).isEqualTo(bytes)
                assertThat(received.single().path).isEqualTo("/$BUCKET/$KEY")
            }

        @Test
        fun `writes nothing when the object is refused`() =
            runTest {
                // given
                val target = answering(403 to error("AccessDenied"))
                val file = folder.resolve("downloaded.zip")

                // when
                val failure = runCatching { storage.download(target, KEY, file.toString()) }.exceptionOrNull()

                // then
                assertThat((failure as? CloudStorageException)?.problem).isEqualTo(CloudProblem.AccessDenied)
                assertThat(folder.toFile().list()).isEmpty()
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun `deletes the object`() =
            runTest {
                // given
                val target = answering(204 to ByteArray(0))

                // when
                storage.delete(target, KEY)

                // then
                assertThat(received.single().method).isEqualTo("DELETE")
                assertThat(received.single().path).isEqualTo("/$BUCKET/$KEY")
            }

        @Test
        fun `accepts an object that is already gone`() =
            runTest {
                // given
                val target = answering(404 to error("NoSuchKey"))

                // when
                val failure = runCatching { storage.delete(target, KEY) }.exceptionOrNull()

                // then
                assertThat(failure).isNull()
            }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
