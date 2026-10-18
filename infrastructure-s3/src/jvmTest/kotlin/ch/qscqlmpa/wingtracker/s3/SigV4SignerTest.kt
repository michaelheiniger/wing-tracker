package ch.qscqlmpa.wingtracker.s3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

// The credentials, bucket and moment of AWS's published Signature Version 4 examples for S3.
private const val ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE"
private const val SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
private const val HOST = "examplebucket.s3.amazonaws.com"
private val AT = Instant.parse("2013-05-24T00:00:00Z")

class SigV4SignerTest {
    private val signer = SigV4Signer("us-east-1", ACCESS_KEY_ID, SECRET_KEY)

    @Nested
    inner class Sign {
        @Test
        fun `signs AWS's example of reading part of an object`() {
            // given
            val request = UnsignedRequest("GET", HOST, "/test.txt", headers = mapOf("range" to "bytes=0-9"))

            // when
            val signed = signer.sign(request, AT)

            // then
            assertThat(signed.headers["authorization"]).isEqualTo(
                "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                    "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                    "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            )
        }

        @Test
        fun `signs AWS's example of listing objects`() {
            // given
            val request = UnsignedRequest("GET", HOST, "/", query = listOf("max-keys" to "2", "prefix" to "J"))

            // when
            val signed = signer.sign(request, AT)

            // then
            assertThat(signed.headers["authorization"])
                .endsWith("Signature=34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7")
            assertThat(signed.pathAndQuery).isEqualTo("/?max-keys=2&prefix=J")
        }

        @Test
        fun `sends the date and payload hash it signed, and leaves the host to the HTTP client`() {
            // given
            val request = UnsignedRequest("GET", HOST, "/test.txt")

            // when
            val signed = signer.sign(request, AT)

            // then
            assertThat(signed.headers)
                .containsEntry("x-amz-date", "20130524T000000Z")
                .containsEntry("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
                .doesNotContainKey("host")
        }

        @Test
        fun `encodes each path segment and the query, but keeps the slashes`() {
            // given
            val request =
                UnsignedRequest(
                    "GET",
                    HOST,
                    "/wing-tracker-backups/wing tracker/vol+Moléson.zip",
                    query = listOf("prefix" to "wing-tracker/", "list-type" to "2"),
                )

            // when
            val signed = signer.sign(request, AT)

            // then
            assertThat(signed.pathAndQuery).isEqualTo(
                "/wing-tracker-backups/wing%20tracker/vol%2BMol%C3%A9son.zip?list-type=2&prefix=wing-tracker%2F",
            )
        }
    }
}
