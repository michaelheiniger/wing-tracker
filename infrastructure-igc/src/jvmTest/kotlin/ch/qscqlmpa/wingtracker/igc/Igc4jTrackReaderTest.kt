package ch.qscqlmpa.wingtracker.igc

import ch.qscqlmpa.wingtracker.flights.igc.InvalidTrackException
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

// Minimal but realistic IGC text: an A record, the date header and B records
// (time, latitude, longitude, validity, altitudes).
private fun bRecord(
    time: String,
    latitude: String = "4642345N",
    longitude: String = "00748210E",
    gpsAltitude: String = "01234",
) = "B$time$latitude${longitude}A00000$gpsAltitude"

private fun igc(
    dateHeader: String? = "HFDTE200926",
    vararg fixes: String,
) = (
    listOfNotNull(
        "AXCT1234FLIGHT:1",
        dateHeader,
        "HFPLTPILOT:Test Pilot",
    ) + fixes
).joinToString("\r\n").encodeToByteArray()

class Igc4jTrackReaderTest {
    private val reader = Igc4jTrackReader()

    @Nested
    inner class Reading {
        @Test
        fun `reads the date and the fixes with their moments in UTC`() {
            // given
            val bytes = igc(fixes = arrayOf(bRecord("094512"), bRecord("094517", latitude = "4642400N")))

            // when
            val result = reader.read(bytes)

            // then
            assertThat(result.track.date).isEqualTo(LocalDate(2026, 9, 20))
            assertThat(result.track.fixes.map { it.time }).containsExactly(
                Instant.parse("2026-09-20T09:45:12Z"),
                Instant.parse("2026-09-20T09:45:17Z"),
            )
        }

        @Test
        fun `converts coordinates to decimal degrees`() {
            // given
            val bytes = igc(fixes = arrayOf(bRecord("094512", latitude = "4630000N", longitude = "00730000E")))

            // when
            val fix =
                reader
                    .read(bytes)
                    .track.fixes
                    .single()

            // then
            assertThat(fix.latitude).isEqualTo(46.5)
            assertThat(fix.longitude).isEqualTo(7.5)
        }

        @Test
        fun `reads the date from the longer header variant`() {
            // given
            val bytes = igc(dateHeader = "HFDTEDATE:200926,01", fixes = arrayOf(bRecord("094512")))

            // when
            val track = reader.read(bytes).track

            // then
            assertThat(track.date).isEqualTo(LocalDate(2026, 9, 20))
        }

        @Test
        fun `carries on into the next day when a flight crosses midnight UTC`() {
            // given
            val bytes = igc(fixes = arrayOf(bRecord("235955"), bRecord("000005"), bRecord("000010")))

            // when
            val fixes = reader.read(bytes).track.fixes

            // then
            assertThat(fixes.map { it.time }).containsExactly(
                Instant.parse("2026-09-20T23:59:55Z"),
                Instant.parse("2026-09-21T00:00:05Z"),
                Instant.parse("2026-09-21T00:00:10Z"),
            )
        }
    }

    @Nested
    inner class Checksum {
        @Test
        fun `is the same for the same content and different for other content`() {
            // given
            val one = igc(fixes = arrayOf(bRecord("094512")))
            val other = igc(fixes = arrayOf(bRecord("094513")))

            // when / then
            assertThat(reader.read(one).checksum).isEqualTo(reader.read(one.copyOf()).checksum)
            assertThat(reader.read(one).checksum).isNotEqualTo(reader.read(other).checksum)
        }

        @Test
        fun `is a SHA-256 hash in hex`() {
            // given / when
            val checksum = reader.read(igc(fixes = arrayOf(bRecord("094512")))).checksum

            // then
            assertThat(checksum).matches("[0-9a-f]{64}")
        }
    }

    @Nested
    inner class Invalid {
        @Test
        fun `rejects a file without a date`() {
            assertThatThrownBy { reader.read(igc(dateHeader = null, fixes = arrayOf(bRecord("094512")))) }
                .isInstanceOf(InvalidTrackException::class.java)
                .hasMessageContaining("date")
        }

        @Test
        fun `rejects a file without position fixes`() {
            assertThatThrownBy { reader.read(igc()) }
                .isInstanceOf(InvalidTrackException::class.java)
                .hasMessageContaining("fixes")
        }

        @Test
        fun `rejects a malformed position record`() {
            assertThatThrownBy { reader.read(igc(fixes = arrayOf("B0945"))) }
                .isInstanceOf(InvalidTrackException::class.java)
        }

        @Test
        fun `rejects a date that does not exist`() {
            assertThatThrownBy { reader.read(igc(dateHeader = "HFDTE310226", fixes = arrayOf(bRecord("094512")))) }
                .isInstanceOf(InvalidTrackException::class.java)
        }

        @Test
        fun `rejects text that is not an IGC file at all`() {
            assertThatThrownBy { reader.read("This is not a flight.".encodeToByteArray()) }
                .isInstanceOf(InvalidTrackException::class.java)
        }
    }
}
