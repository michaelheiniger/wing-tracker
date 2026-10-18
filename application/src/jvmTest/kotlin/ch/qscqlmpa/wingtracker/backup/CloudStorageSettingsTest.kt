package ch.qscqlmpa.wingtracker.backup

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun aMegaBucket() =
    CloudStorageSettings(
        provider = CloudProvider.MegaS4,
        region = "eu-amsterdam-1",
        endpoint = null,
        bucket = "wing-tracker-backups",
        accessKeyId = "AKIAMEGAS4EXAMPLE",
        keepLast = 10,
    )

class CloudStorageSettingsTest {
    @Nested
    inner class EndpointUrl {
        @Test
        fun `builds MEGA's endpoint from the region`() {
            // given
            val settings = aMegaBucket()

            // when
            val url = settings.endpointUrl

            // then
            assertThat(url).isEqualTo("https://s3.eu-amsterdam-1.megas4.com")
        }

        @Test
        fun `uses another provider's endpoint as typed, without a trailing slash`() {
            // given
            val settings =
                aMegaBucket().copy(
                    provider = CloudProvider.Other,
                    region = "eu-central-003",
                    endpoint = "https://s3.eu-central-003.backblazeb2.com/",
                )

            // when
            val url = settings.endpointUrl

            // then
            assertThat(url).isEqualTo("https://s3.eu-central-003.backblazeb2.com")
        }
    }

    @Nested
    inner class Rules {
        @Test
        fun `refuses a MEGA region that would change the host`() {
            assertThatThrownBy { aMegaBucket().copy(region = "evil.example.com/x") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `refuses an endpoint over plain HTTP`() {
            assertThatThrownBy {
                aMegaBucket().copy(provider = CloudProvider.Other, endpoint = "http://s3.example.com")
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `refuses to keep no backup at all`() {
            assertThatThrownBy { aMegaBucket().copy(keepLast = 0) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class Target {
        @Test
        fun `never shows the secret key in its text`() {
            // given
            val target = aMegaBucket().target("s3cr3t/K3y")

            // when
            val text = target.toString()

            // then
            assertThat(text).doesNotContain("s3cr3t")
        }
    }
}
