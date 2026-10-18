package ch.qscqlmpa.wingtracker.backup

import kotlin.time.Instant

private val REGION_CODE = Regex("[a-z0-9]+(-[a-z0-9]+)*")

enum class CloudProvider {
    MegaS4,

    // Any other service that speaks the S3 protocol, reached by its endpoint.
    Other,
}

/**
 * The S3-compatible bucket backups are uploaded to (ADR 0032). The secret key is kept by the operating system,
 * never here. [endpoint] is only used for [CloudProvider.Other]; MEGA's follows from the region.
 */
data class CloudStorageSettings(
    val provider: CloudProvider,
    val region: String,
    val endpoint: String?,
    val bucket: String,
    val accessKeyId: String,
    // Null keeps every backup.
    val keepLast: Int?,
) {
    init {
        require(region.isNotBlank()) { "A region is needed" }
        // MEGA's region becomes part of a host name, so it may hold nothing that changes which host is reached.
        require(provider != CloudProvider.MegaS4 || isRegionCode(region)) { "Not a region code: $region" }
        require(bucket.isNotBlank()) { "A bucket is needed" }
        require(accessKeyId.isNotBlank()) { "An access key id is needed" }
        require(keepLast == null || keepLast > 0) { "At least one backup must be kept" }
        // The keys sign every request and the backup holds the logbook, so nothing goes over plain HTTP.
        require(provider == CloudProvider.MegaS4 || endpoint?.startsWith("https://") == true) {
            "The endpoint must be an HTTPS address"
        }
    }

    val endpointUrl: String
        get() =
            when (provider) {
                CloudProvider.MegaS4 -> "https://s3.$region.megas4.com"
                CloudProvider.Other -> checkNotNull(endpoint).trimEnd('/')
            }

    fun target(secretKey: String) = CloudTarget(endpointUrl, region, bucket, accessKeyId, secretKey)

    companion object {
        fun isRegionCode(text: String): Boolean = REGION_CODE.matches(text)
    }
}

/** Everything a request to the bucket needs. */
data class CloudTarget(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKeyId: String,
    val secretKey: String,
) {
    // Never shows the secret, e.g. in a log line or a failed test's message.
    override fun toString() = "CloudTarget(endpoint=$endpoint, region=$region, bucket=$bucket)"
}

/** One stored object, e.g. a backup. */
data class CloudObject(
    val key: String,
    val size: Long,
    val lastModified: Instant,
)

enum class CloudProblem {
    // No connection could be made: most likely this computer is offline, or the endpoint is mistyped.
    NoConnection,

    // Connected, but the answer did not come, or the service failed: worth trying again later.
    Unreachable,

    // The access key id is unknown or the secret key does not match it.
    KeysRefused,
    NoSuchBucket,

    // The keys are valid but may not do this, e.g. a read-only key asked to upload.
    AccessDenied,

    // The service refuses requests signed at a time too far from its own: this computer's clock is off.
    ClockOff,

    // The answer could not be understood.
    Unexpected,
}

class CloudStorageException(
    val problem: CloudProblem,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: problem.name, cause)

/** An S3-compatible object storage (ADR 0032). Every call throws [CloudStorageException] when it fails. */
interface CloudStorage {
    /** Checks that the bucket can be listed with these keys. */
    suspend fun check(target: CloudTarget)

    /** Uploads the local [file] as [key], replacing an object of that name. */
    suspend fun upload(
        target: CloudTarget,
        key: String,
        file: String,
    )

    /** The objects whose key starts with [prefix], in no particular order. */
    suspend fun list(
        target: CloudTarget,
        prefix: String,
    ): List<CloudObject>

    /** Saves the object [key] as the local [file]. */
    suspend fun download(
        target: CloudTarget,
        key: String,
        file: String,
    )

    /** Deleting an object that is not there is not an error. */
    suspend fun delete(
        target: CloudTarget,
        key: String,
    )
}
