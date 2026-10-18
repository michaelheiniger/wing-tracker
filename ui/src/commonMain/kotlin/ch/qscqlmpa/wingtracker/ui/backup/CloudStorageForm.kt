package ch.qscqlmpa.wingtracker.ui.backup

import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError

/** The cloud storage form as typed; [secretKey] stays empty to keep the one already stored. */
data class CloudStorageInput(
    val provider: CloudProvider = CloudProvider.MegaS4,
    val region: String = "",
    val endpoint: String = "",
    val bucket: String = "",
    val accessKeyId: String = "",
    val secretKey: String = "",
    val keepLast: String = "",
) {
    companion object {
        fun from(settings: CloudStorageSettings) =
            CloudStorageInput(
                provider = settings.provider,
                region = settings.region,
                endpoint = settings.endpoint.orEmpty(),
                bucket = settings.bucket,
                accessKeyId = settings.accessKeyId,
                keepLast = settings.keepLast?.toString().orEmpty(),
            )
    }
}

enum class CloudStorageField { Region, Endpoint, Bucket, AccessKeyId, SecretKey, KeepLast }

sealed interface CloudStorageFormResult {
    /** [secretKey] is null when the stored one is kept. */
    data class Valid(
        val settings: CloudStorageSettings,
        val secretKey: String?,
    ) : CloudStorageFormResult

    data class Invalid(
        val errors: Map<CloudStorageField, FormError>,
    ) : CloudStorageFormResult
}

private val COUNT = Regex("""\d{1,4}""")

/** [secretStored] tells whether a secret key is already kept, so the field may stay empty. */
fun CloudStorageInput.validate(secretStored: Boolean): CloudStorageFormResult {
    val errors = linkedMapOf<CloudStorageField, FormError>()
    val region = region.trim()
    when {
        region.isEmpty() -> {
            errors[CloudStorageField.Region] = FormError.Required
        }

        provider == CloudProvider.MegaS4 && !CloudStorageSettings.isRegionCode(region) -> {
            errors[CloudStorageField.Region] = FormError.InvalidRegion
        }
    }
    val endpoint = endpoint.trim().takeIf { provider == CloudProvider.Other }
    if (endpoint != null &&
        !endpoint.startsWith("https://")
    ) {
        errors[CloudStorageField.Endpoint] = FormError.InvalidEndpoint
    }
    if (bucket.isBlank()) errors[CloudStorageField.Bucket] = FormError.Required
    if (accessKeyId.isBlank()) errors[CloudStorageField.AccessKeyId] = FormError.Required
    if (secretKey.isBlank() && !secretStored) errors[CloudStorageField.SecretKey] = FormError.Required
    val keep = keepLast.trim()
    val keepCount = keep.takeIf { COUNT.matches(it) }?.toInt()?.takeIf { it > 0 }
    if (keep.isNotEmpty() && keepCount == null) errors[CloudStorageField.KeepLast] = FormError.InvalidBackupCount
    if (errors.isNotEmpty()) return CloudStorageFormResult.Invalid(errors)
    return CloudStorageFormResult.Valid(
        CloudStorageSettings(provider, region, endpoint, bucket.trim(), accessKeyId.trim(), keepCount),
        secretKey.trim().ifEmpty { null },
    )
}
