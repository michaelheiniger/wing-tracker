package ch.qscqlmpa.wingtracker.ui.backup

import ch.qscqlmpa.wingtracker.backup.CloudProvider
import ch.qscqlmpa.wingtracker.backup.CloudStorageSettings
import ch.qscqlmpa.wingtracker.ui.gear.wing.FormError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aFilledForm() =
    CloudStorageInput(
        provider = CloudProvider.MegaS4,
        region = " eu-amsterdam-1 ",
        bucket = "wing-tracker-backups",
        accessKeyId = "AKIAMEGAS4EXAMPLE",
        secretKey = "s3cr3t/K3y",
        keepLast = "10",
    )

class CloudStorageFormTest {
    @Nested
    inner class Validate {
        @Test
        fun `reads a MEGA bucket and the typed secret key`() {
            // given
            val input = aFilledForm()

            // when
            val result = input.validate(secretStored = false)

            // then
            assertThat(result).isEqualTo(
                CloudStorageFormResult.Valid(
                    CloudStorageSettings(
                        CloudProvider.MegaS4,
                        "eu-amsterdam-1",
                        null,
                        "wing-tracker-backups",
                        "AKIAMEGAS4EXAMPLE",
                        10,
                    ),
                    "s3cr3t/K3y",
                ),
            )
        }

        @Test
        fun `keeps every backup when the number is left empty`() {
            // given
            val input = aFilledForm().copy(keepLast = "")

            // when
            val result = input.validate(secretStored = false)

            // then
            assertThat((result as CloudStorageFormResult.Valid).settings.keepLast).isNull()
        }

        @Test
        fun `keeps the stored secret key when the field is left empty`() {
            // given
            val input = aFilledForm().copy(secretKey = "")

            // when
            val result = input.validate(secretStored = true)

            // then
            assertThat((result as CloudStorageFormResult.Valid).secretKey).isNull()
        }

        @Test
        fun `asks for a secret key when none is stored`() {
            // given
            val input = aFilledForm().copy(secretKey = "")

            // when
            val result = input.validate(secretStored = false)

            // then
            assertThat((result as CloudStorageFormResult.Invalid).errors)
                .containsEntry(CloudStorageField.SecretKey, FormError.Required)
        }

        @Test
        fun `refuses a MEGA region that is no region code`() {
            // given
            val input = aFilledForm().copy(region = "s3.eu-amsterdam-1.megas4.com/")

            // when
            val result = input.validate(secretStored = true)

            // then
            assertThat((result as CloudStorageFormResult.Invalid).errors)
                .containsEntry(CloudStorageField.Region, FormError.InvalidRegion)
        }

        @Test
        fun `asks another provider for an HTTPS endpoint`() {
            // given
            val input = aFilledForm().copy(provider = CloudProvider.Other, endpoint = "s3.example.com")

            // when
            val result = input.validate(secretStored = true)

            // then
            assertThat((result as CloudStorageFormResult.Invalid).errors)
                .containsEntry(CloudStorageField.Endpoint, FormError.InvalidEndpoint)
        }

        @Test
        fun `reports every missing field and a wrong number at once`() {
            // given
            val input = CloudStorageInput(keepLast = "ten")

            // when
            val result = input.validate(secretStored = false)

            // then
            assertThat((result as CloudStorageFormResult.Invalid).errors.keys).containsExactly(
                CloudStorageField.Region,
                CloudStorageField.Bucket,
                CloudStorageField.AccessKeyId,
                CloudStorageField.SecretKey,
                CloudStorageField.KeepLast,
            )
        }
    }
}
