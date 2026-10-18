package ch.qscqlmpa.wingtracker.s3

import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private const val ALGORITHM = "AWS4-HMAC-SHA256"
private const val SERVICE = "s3"
private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** The SHA-256 of an empty body, which every request without one signs. */
internal const val EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

/** A request before signing; [path] and [query] are not encoded yet, [headers] have lower-case names. */
internal class UnsignedRequest(
    val method: String,
    val host: String,
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val payloadHash: String = EMPTY_PAYLOAD_HASH,
)

/** What goes on the wire: the encoded path and query, and every header including the signature. */
internal class SignedRequest(
    val pathAndQuery: String,
    val headers: Map<String, String>,
)

/**
 * Signs S3 requests with Signature Version 4, the only one S4 accepts (ADR 0032). It follows AWS's description of
 * the algorithm and is tested against AWS's own examples.
 */
internal class SigV4Signer(
    private val region: String,
    private val accessKeyId: String,
    private val secretKey: String,
) {
    fun sign(
        request: UnsignedRequest,
        at: Instant,
    ): SignedRequest {
        val amzDate = AMZ_DATE.format(at.toJavaInstant())
        val day = amzDate.substring(0, DAY_LENGTH)
        val signedByEveryRequest =
            mapOf("host" to request.host, "x-amz-content-sha256" to request.payloadHash, "x-amz-date" to amzDate)
        val headers = (request.headers + signedByEveryRequest).mapKeys { it.key.lowercase() }.toSortedMap()
        val signedHeaders = headers.keys.joinToString(";")
        val path = encodePath(request.path)
        val query =
            request.query
                .map { (name, value) -> uriEncode(name) to uriEncode(value) }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString("&") { (name, value) -> "$name=$value" }
        val canonicalRequest =
            listOf(
                request.method,
                path,
                query,
                headers.entries.joinToString("") { (name, value) -> "$name:${value.trim()}\n" },
                signedHeaders,
                request.payloadHash,
            ).joinToString("\n")
        val scope = "$day/$region/$SERVICE/aws4_request"
        val stringToSign =
            listOf(ALGORITHM, amzDate, scope, sha256Hex(canonicalRequest.encodeToByteArray())).joinToString("\n")
        val signature = hmac(signingKey(day), stringToSign).toHex()
        val authorization =
            "$ALGORITHM Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        // Host is set by the HTTP client itself from the address.
        return SignedRequest(
            pathAndQuery = if (query.isEmpty()) path else "$path?$query",
            headers = headers.filterKeys { it != "host" } + ("authorization" to authorization),
        )
    }

    private fun signingKey(day: String): ByteArray {
        val dated = hmac("AWS4$secretKey".encodeToByteArray(), day)
        return hmac(hmac(hmac(dated, region), SERVICE), "aws4_request")
    }

    private fun hmac(
        key: ByteArray,
        data: String,
    ): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data.encodeToByteArray())
        }

    private companion object {
        const val DAY_LENGTH = 8
    }
}

/** S3 keeps the slashes of a path and encodes each segment once. */
private fun encodePath(path: String): String = path.split('/').joinToString("/") { uriEncode(it) }

// Everything but the unreserved characters of RFC 3986 is percent-encoded, as SigV4 requires.
internal fun uriEncode(text: String): String =
    buildString {
        text.encodeToByteArray().forEach { byte ->
            val c = byte.toInt().toChar()
            if ((c.isLetterOrDigit() && c.code < ASCII_LIMIT) || c in "-_.~") {
                append(c)
            } else {
                append('%').append("%02X".format(byte.toInt() and BYTE_MASK))
            }
        }
    }

private const val ASCII_LIMIT = 128
private const val BYTE_MASK = 0xFF

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
