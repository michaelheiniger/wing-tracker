package ch.qscqlmpa.wingtracker.s3

import ch.qscqlmpa.wingtracker.backup.CloudProblem
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudTarget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.time.Clock

// An S3 server that checks Signature Version 4 like S4 does; MinIO no longer publishes images.
private const val IMAGE = "chrislusf/seaweedfs:3.97"
private const val S3_PORT = 8333
private const val ACCESS_KEY = "AKIAWINGTRACKERTEST"
private const val SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/wingTrackerTestKey"
private const val BUCKET = "wing-tracker-backups"
private const val KEY = "wing-tracker/wing-tracker-backup-2026-10-18-1430.zip"
private const val STARTUP_SECONDS = 120L

/** The adapter against a real S3 server, where a mistake in signing or addressing shows (ADR 0032). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3CloudStorageServerTest {
    @TempDir
    lateinit var folder: Path

    private var server: GenericContainer<*>? = null
    private val storage = S3CloudStorage("wing-tracker/test")

    private val target: CloudTarget
        get() {
            val started = checkNotNull(server)
            val endpoint = "http://${started.host}:${started.getMappedPort(S3_PORT)}"
            return CloudTarget(endpoint, "us-east-1", BUCKET, ACCESS_KEY, SECRET_KEY)
        }

    @BeforeAll
    fun start() {
        // Windows runners have no Linux containers; the fake-server tests still cover the adapter there.
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker is not available")
        server =
            GenericContainer(IMAGE)
                .withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs-s3.json"), "/etc/s3.json")
                .withCommand("server", "-s3", "-s3.config=/etc/s3.json", "-dir=/data")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forListeningPorts(S3_PORT).withStartupTimeout(Duration.ofSeconds(STARTUP_SECONDS)))
                .also { it.start() }
        runBlocking { createBucket() }
    }

    @AfterAll
    fun stop() {
        server?.stop()
    }

    // The port has no call for this: the pilot creates the bucket at the provider.
    private suspend fun createBucket() {
        val unsigned = UnsignedRequest("PUT", target.endpoint.removePrefix("http://"), "/$BUCKET")
        val signed = SigV4Signer(target.region, ACCESS_KEY, SECRET_KEY).sign(unsigned, Clock.System.now())
        val request =
            HttpRequest
                .newBuilder(URI.create(target.endpoint + signed.pathAndQuery))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .apply { signed.headers.forEach { (name, value) -> header(name, value) } }
                .build()
        val status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        check(status in 200..299) { "The bucket could not be created: $status" }
    }

    @Nested
    inner class RoundTrip {
        @Test
        fun `uploads, lists, downloads and deletes a backup`() =
            runTest {
                // given
                val bytes = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
                val upload = folder.resolve("upload.zip").also { it.writeBytes(bytes) }
                val download = folder.resolve("download.zip")

                // when
                storage.check(target)
                storage.upload(target, KEY, upload.toString())
                val listed = storage.list(target, "wing-tracker/")
                storage.download(target, KEY, download.toString())
                storage.delete(target, KEY)
                val afterDelete = storage.list(target, "wing-tracker/")

                // then
                assertThat(listed.map { it.key to it.size }).containsExactly(KEY to bytes.size.toLong())
                assertThat(download.readBytes()).isEqualTo(bytes)
                assertThat(afterDelete).isEmpty()
            }
    }

    @Nested
    inner class Refusals {
        @Test
        fun `tells a wrong secret key`() =
            runTest {
                // given
                val wrong = target.copy(secretKey = "not the secret key")

                // when
                val failure = runCatching { storage.check(wrong) }.exceptionOrNull()

                // then
                assertThat((failure as? CloudStorageException)?.problem).isEqualTo(CloudProblem.KeysRefused)
            }

        @Test
        fun `tells a bucket that does not exist`() =
            runTest {
                // given
                val missing = target.copy(bucket = "no-such-bucket")

                // when
                val failure = runCatching { storage.check(missing) }.exceptionOrNull()

                // then
                assertThat((failure as? CloudStorageException)?.problem).isEqualTo(CloudProblem.NoSuchBucket)
            }
    }
}
