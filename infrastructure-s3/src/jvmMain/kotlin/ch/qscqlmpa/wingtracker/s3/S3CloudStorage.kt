package ch.qscqlmpa.wingtracker.s3

import ch.qscqlmpa.wingtracker.backup.CloudObject
import ch.qscqlmpa.wingtracker.backup.CloudProblem
import ch.qscqlmpa.wingtracker.backup.CloudStorage
import ch.qscqlmpa.wingtracker.backup.CloudStorageException
import ch.qscqlmpa.wingtracker.backup.CloudTarget
import ch.qscqlmpa.wingtracker.http.ConnectionProblem
import ch.qscqlmpa.wingtracker.http.connectionProblemOf
import ch.qscqlmpa.wingtracker.http.readAborting
import ch.qscqlmpa.wingtracker.http.sendAborting
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private const val CONNECT_TIMEOUT_SECONDS = 20L

// Short answers only; uploads and downloads take as long as the connection needs and are cancelled by the pilot.
private const val ANSWER_TIMEOUT_SECONDS = 30L
private val DOWNLOAD_IDLE_TIMEOUT = 60.seconds
private const val BUFFER_BYTES = 64 * 1024
private const val STATUS_NOT_FOUND = 404
private val STATUS_SUCCESS = 200..299
private const val STATUS_SERVER_ERROR = 500

/**
 * Talks to any S3-compatible storage with path-style addresses and Signature Version 4, sending only headers that
 * every such service accepts (ADR 0032).
 */
class S3CloudStorage(
    private val userAgent: String,
    private val clock: Clock = Clock.System,
) : CloudStorage {
    private val client: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build()
    }

    override suspend fun check(target: CloudTarget) {
        list(target, prefix = "", maxKeys = 1)
    }

    override suspend fun upload(
        target: CloudTarget,
        key: String,
        file: String,
    ) {
        val path = Path(file)
        // Hashed first, since the signature covers the body and S4 takes no unsigned or checksummed payloads.
        val hash = withContext(Dispatchers.IO) { path.inputStream().use { sha256Hex(it) } }
        val request =
            UnsignedRequest(
                "PUT",
                target.host,
                "/${target.bucket}/$key",
                headers = mapOf("content-type" to "application/zip"),
                payloadHash = hash,
            )
        val response = send(target, request, HttpRequest.BodyPublishers.ofFile(path), timeout = null)
        if (!response.statusCode().isSuccess()) throw failure(response.statusCode(), response.body())
        logger.info { "Uploaded $key to ${target.bucket}" }
    }

    override suspend fun list(
        target: CloudTarget,
        prefix: String,
    ): List<CloudObject> = list(target, prefix, maxKeys = null)

    private suspend fun list(
        target: CloudTarget,
        prefix: String,
        maxKeys: Int?,
    ): List<CloudObject> {
        val objects = mutableListOf<CloudObject>()
        var token: String? = null
        do {
            val query =
                listOfNotNull(
                    "list-type" to "2",
                    "prefix" to prefix,
                    maxKeys?.let { "max-keys" to it.toString() },
                    token?.let { "continuation-token" to it },
                )
            val response = send(target, UnsignedRequest("GET", target.host, "/${target.bucket}", query))
            if (!response.statusCode().isSuccess()) throw failure(response.statusCode(), response.body())
            val page =
                try {
                    parseListing(response.body())
                } catch (e: IllegalArgumentException) {
                    throw CloudStorageException(CloudProblem.Unexpected, "The listing cannot be read: ${e.message}", e)
                }
            objects += page.objects
            token = page.nextToken.takeIf { maxKeys == null }
        } while (token != null)
        return objects
    }

    override suspend fun download(
        target: CloudTarget,
        key: String,
        file: String,
    ) {
        val request =
            HttpRequest
                .newBuilder()
                .GET()
                .signed(target, UnsignedRequest("GET", target.host, "/${target.bucket}/$key"), timeout = null)
        val response = exchange(target) { client.sendAborting(request, HttpResponse.BodyHandlers.ofInputStream()) }
        val body = response.body()
        if (!response.statusCode().isSuccess()) {
            throw failure(response.statusCode(), body.use { it.readBytes().decodeToString() })
        }
        val destination = Path(file)
        // Written under a temporary name, so a cut-off download never looks like a complete backup.
        val partial = destination.resolveSibling("${destination.fileName}.part")
        exchange(target) {
            body.readAborting(DOWNLOAD_IDLE_TIMEOUT) { input ->
                Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        withContext(Dispatchers.IO) { Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING) }
    }

    override suspend fun delete(
        target: CloudTarget,
        key: String,
    ) {
        val response = send(target, UnsignedRequest("DELETE", target.host, "/${target.bucket}/$key"))
        val status = response.statusCode()
        if (!status.isSuccess() && status != STATUS_NOT_FOUND) throw failure(status, response.body())
    }

    private suspend fun send(
        target: CloudTarget,
        unsigned: UnsignedRequest,
        body: HttpRequest.BodyPublisher = HttpRequest.BodyPublishers.noBody(),
        timeout: Duration? = Duration.ofSeconds(ANSWER_TIMEOUT_SECONDS),
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder().method(unsigned.method, body).signed(target, unsigned, timeout)
        return exchange(target) { client.sendAborting(request, HttpResponse.BodyHandlers.ofString()) }
    }

    private fun HttpRequest.Builder.signed(
        target: CloudTarget,
        unsigned: UnsignedRequest,
        timeout: Duration?,
    ): HttpRequest {
        val signed = SigV4Signer(target.region, target.accessKeyId, target.secretKey).sign(unsigned, clock.now())
        uri(URI.create(target.endpoint.trimEnd('/') + signed.pathAndQuery))
        header("User-Agent", userAgent)
        signed.headers.forEach { (name, value) -> header(name, value) }
        timeout?.let(::timeout)
        return build()
    }

    private suspend fun <T> exchange(
        target: CloudTarget,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: IOException) {
            logger.warn(e) { "The storage at ${target.endpoint} could not be reached" }
            val problem =
                when (connectionProblemOf(e)) {
                    ConnectionProblem.NoConnection -> CloudProblem.NoConnection
                    ConnectionProblem.Broken -> CloudProblem.Unreachable
                }
            throw CloudStorageException(problem, e.message, e)
        }

    private fun failure(
        status: Int,
        body: String,
    ): CloudStorageException {
        val code = errorCodeOf(body)
        val problem =
            when (code) {
                "InvalidAccessKeyId", "SignatureDoesNotMatch" -> CloudProblem.KeysRefused
                "NoSuchBucket" -> CloudProblem.NoSuchBucket
                "AccessDenied" -> CloudProblem.AccessDenied
                "RequestTimeTooSkewed" -> CloudProblem.ClockOff
                else -> if (status >= STATUS_SERVER_ERROR) CloudProblem.Unreachable else CloudProblem.Unexpected
            }
        logger.warn { "The storage answered $status ${code.orEmpty()}" }
        return CloudStorageException(problem, listOfNotNull(status.toString(), code).joinToString(" "))
    }
}

private fun Int.isSuccess() = this in STATUS_SUCCESS

/** Host and port as the HTTP client sends them, which the signature must cover exactly. */
private val CloudTarget.host: String get() = URI.create(endpoint).rawAuthority

private fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_BYTES)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return digest.digest().toHex()
}
