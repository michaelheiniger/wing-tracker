package ch.qscqlmpa.wingtracker.flights

import ch.qscqlmpa.wingtracker.attachments.Attachment
import ch.qscqlmpa.wingtracker.attachments.AttachmentId
import ch.qscqlmpa.wingtracker.gear.wing.WingId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private fun aSyrideFlight() =
    Flight(
        id = FlightId("9d41c6a2-5b7e-4f08-8c13-2a6e0f9b7d35"),
        details =
            FlightDetails(
                date = LocalDate(2026, 9, 26),
                duration = 98.minutes,
                wingId = WingId("3f2b8a1e-7c4d-4e59-9a61-0d2c5b8e1f47"),
                takeOffSite = "Dent De Jaman",
                notes = "Course Tour du Bocal vol 3",
                takeOffTime = LocalTime(13, 43),
                distanceKm = 15.0,
                topAltitudeMeters = 2464,
                instrument = "Nav XL",
            ),
        loggedAt = Instant.parse("2026-10-15T19:31:53Z"),
        importedFrom = ExternalReference("syride", "flight:3720061"),
    )

private fun aTrack(checksum: String = "3b7e72f0c1d9") =
    IgcFile(Attachment(AttachmentId("igc-1"), "114344.IGC"), checksum)

class FlightTest {
    @Nested
    inner class AttachTrack {
        @Test
        fun `adds the track and keeps everything else`() {
            // given
            val flight = aSyrideFlight()

            // when
            val linked = flight.attachTrack(aTrack())

            // then
            assertThat(linked).usingRecursiveComparison().isEqualTo(flight.copy(igcFile = aTrack()))
        }

        @Test
        fun `refuses to replace a track the flight already has`() {
            // given
            val flight = aSyrideFlight().attachTrack(aTrack())

            // when / then
            assertThatThrownBy { flight.attachTrack(aTrack(checksum = "9f0c41aa77e2")) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    inner class MarkExported {
        @Test
        fun `remembers the system and when the flight was sent`() {
            // given
            val flight = aSyrideFlight()
            val sentAt = Instant.parse("2026-10-17T20:12:04Z")

            // when
            val exported = flight.markExported("airtime", sentAt)

            // then
            assertThat(exported.exports).containsExactly(FlightExport("airtime", sentAt))
            assertThat(exported.isExportedTo("airtime")).isTrue()
        }

        @Test
        fun `keeps the flight's id in the other system`() {
            // when
            val exported = aSyrideFlight().markExported("airtime", Instant.parse("2026-10-17T20:12:04Z"), "986")

            // then
            assertThat(exported.exports.single().externalId).isEqualTo("986")
        }

        @Test
        fun `refuses to mark a flight exported twice to the same system`() {
            // given
            val flight = aSyrideFlight().markExported("airtime", Instant.parse("2026-10-17T20:12:04Z"))

            // when / then
            assertThatThrownBy { flight.markExported("airtime", Instant.parse("2026-10-18T08:00:00Z")) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    @Nested
    inner class ForgetAndRestoreExport {
        private val sent = FlightExport("airtime", Instant.parse("2026-10-17T20:12:04Z"), "986")

        @Test
        fun `forgets the record of one system only`() {
            // given
            val flight = aSyrideFlight().copy(exports = listOf(sent, FlightExport("xcontest", sent.at)))

            // when
            val forgotten = flight.forgetExport("airtime")

            // then
            assertThat(forgotten.exports).containsExactly(FlightExport("xcontest", sent.at))
        }

        @Test
        fun `puts a forgotten record back`() {
            // given
            val flight = aSyrideFlight().copy(exports = listOf(sent)).forgetExport("airtime")

            // when / then
            assertThat(flight.restoreExport(sent).exports).containsExactly(sent)
        }

        @Test
        fun `keeps a newer export instead of the record put back`() {
            // given
            val newer = FlightExport("airtime", Instant.parse("2026-10-18T09:00:00Z"), "1560")
            val flight = aSyrideFlight().copy(exports = listOf(newer))

            // when / then
            assertThat(flight.restoreExport(sent).exports).containsExactly(newer)
        }
    }
}
