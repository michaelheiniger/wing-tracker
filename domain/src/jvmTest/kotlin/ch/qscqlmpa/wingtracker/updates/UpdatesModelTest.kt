package ch.qscqlmpa.wingtracker.updates

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UpdatesModelTest {
    private val installed = AppVersion(0, 0, 3)

    @Nested
    inner class ParsingAVersion {
        @Test
        fun `reads a release tag with its leading v`() {
            // when
            val version = AppVersion.parseOrNull("v0.1.12")

            // then
            assertThat(version).isEqualTo(AppVersion(0, 1, 12))
        }

        @Test
        fun `reads a version without the v`() {
            // when
            val version = AppVersion.parseOrNull("1.2.3")

            // then
            assertThat(version).isEqualTo(AppVersion(1, 2, 3))
        }

        @ParameterizedTest
        @ValueSource(strings = ["dev", "1.2", "1.2.3.4", "1.2.3-beta.1", "v", "", "99999999999.0.0"])
        fun `is null for anything that is not a release version`(text: String) {
            // when
            val version = AppVersion.parseOrNull(text)

            // then
            assertThat(version).isNull()
        }

        @Test
        fun `refuses negative parts`() {
            assertThatThrownBy { AppVersion(0, -1, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class ComparingVersions {
        @Test
        fun `compares each part as a number, not as text`() {
            assertThat(AppVersion(0, 10, 0)).isGreaterThan(AppVersion(0, 9, 12))
        }

        @Test
        fun `ranks a higher major above any minor and patch`() {
            assertThat(AppVersion(1, 0, 0)).isGreaterThan(AppVersion(0, 99, 99))
        }

        @Test
        fun `prints as MAJOR MINOR PATCH`() {
            assertThat(AppVersion(0, 1, 12).toString()).isEqualTo("0.1.12")
        }
    }

    @Nested
    inner class FindingAnUpdate {
        @Test
        fun `offers every newer release, newest first, with the installer of the latest`() {
            // given
            val published = listOf(aRelease("0.0.4"), aRelease("0.0.2"), aRelease("0.1.0"), aRelease("0.0.3"))

            // when
            val update = AvailableUpdate.find(installed, published, InstallerKind.Deb, skipped = null)

            // then
            assertThat(update).isNotNull
            assertThat(update!!.releases.map { it.version.toString() }).containsExactly("0.1.0", "0.0.4")
            assertThat(update.installer).isEqualTo(aDebAsset("0.1.0"))
            assertThat(update.skipped).isFalse()
        }

        @Test
        fun `offers nothing when the installed version is the newest`() {
            // given
            val published = listOf(aRelease("0.0.2"), aRelease("0.0.3"))

            // when
            val update = AvailableUpdate.find(installed, published, InstallerKind.Deb, skipped = null)

            // then
            assertThat(update).isNull()
        }

        @Test
        fun `offers nothing when the installation is newer than every release`() {
            // given
            val published = listOf(aRelease("0.0.2"))

            // when
            val update = AvailableUpdate.find(AppVersion(1, 0, 0), published, InstallerKind.Deb, skipped = null)

            // then
            assertThat(update).isNull()
        }

        @Test
        fun `passes over a newer release without an installer of this kind`() {
            // given
            val published = listOf(aRelease("0.1.0", assets = listOf(anMsiAsset("0.1.0"))), aRelease("0.0.4"))

            // when
            val update = AvailableUpdate.find(installed, published, InstallerKind.Deb, skipped = null)

            // then
            assertThat(update!!.releases.map { it.version.toString() }).containsExactly("0.0.4")
            assertThat(update.installer).isEqualTo(aDebAsset("0.0.4"))
        }

        @Test
        fun `picks the installer of the requested kind`() {
            // given
            val published = listOf(aRelease("0.1.0", assets = listOf(aDebAsset("0.1.0"), anMsiAsset("0.1.0"))))

            // when
            val update = AvailableUpdate.find(installed, published, InstallerKind.Msi, skipped = null)

            // then
            assertThat(update!!.installer).isEqualTo(anMsiAsset("0.1.0"))
        }

        @Test
        fun `is marked skipped when the pilot skipped the latest version`() {
            // given
            val published = listOf(aRelease("0.1.0"))

            // when
            val update = AvailableUpdate.find(installed, published, InstallerKind.Deb, skipped = AppVersion(0, 1, 0))

            // then
            assertThat(update!!.skipped).isTrue()
        }

        @Test
        fun `is announced again once a version after the skipped one is out`() {
            // given
            val published = listOf(aRelease("0.1.0"), aRelease("0.1.1"))

            // when
            val update = AvailableUpdate.find(installed, published, InstallerKind.Deb, skipped = AppVersion(0, 1, 0))

            // then
            assertThat(update!!.skipped).isFalse()
            assertThat(update.latest.version).isEqualTo(AppVersion(0, 1, 1))
        }
    }
}
