package ch.qscqlmpa.wingtracker.desktop

import ch.qscqlmpa.wingtracker.flights.FlightRepository
import ch.qscqlmpa.wingtracker.flights.igc.PrepareIgcImport
import ch.qscqlmpa.wingtracker.gear.maintenance.NewAttachment
import ch.qscqlmpa.wingtracker.igc.Igc4jTrackReader
import ch.qscqlmpa.wingtracker.shared.LoadResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.time.Duration.Companion.minutes

// A realistic recording: header, extension records, a logger switched on before take-off and off after landing.
private const val START_LATITUDE = 46.7
private const val START_LONGITUDE = 7.8
private const val METERS_PER_DEGREE = 111_320.0

private fun degreesAndMinutes(
    value: Double,
    degreeDigits: Int,
    positive: Char,
    negative: Char,
): String {
    val hemisphere = if (value >= 0) positive else negative
    val absolute = abs(value)
    val degrees = absolute.toInt()
    val thousandthsOfMinutes = ((absolute - degrees) * 60 * 1000).toInt()
    return String.format(Locale.ROOT, "%0${degreeDigits}d%05d%c", degrees, thousandthsOfMinutes, hemisphere)
}

private fun bRecord(
    secondOfDay: Int,
    longitude: Double,
): String {
    val time = String.format(Locale.ROOT, "%02d%02d%02d", secondOfDay / 3600, secondOfDay / 60 % 60, secondOfDay % 60)
    val latitude = degreesAndMinutes(START_LATITUDE, 2, 'N', 'S')
    val lon = degreesAndMinutes(longitude, 3, 'E', 'W')
    return "B$time$latitude${lon}A0145001520000"
}

private fun realisticIgc(
    standingBefore: Int,
    flying: Int,
    standingAfter: Int,
    startSecondOfDay: Int = 9 * 3600,
): ByteArray {
    val lines =
        mutableListOf(
            "AXCT1234FLIGHT:1",
            "HFDTE200926",
            "HFFXA035",
            "HFPLTPILOTINCHARGE:Test Pilot",
            "HFGTYGLIDERTYPE:Iota 3",
            "HFDTM100GPSDATUM:WGS-1984",
            "I023638FXA3941ENL",
        )
    var longitude = START_LONGITUDE
    var second = startSecondOfDay
    val metersPerFix = 32.0 / 3.6 * 5
    val step = metersPerFix / (METERS_PER_DEGREE * cos(Math.toRadians(START_LATITUDE)))
    repeat(standingBefore / 5) {
        lines += bRecord(second, longitude)
        second += 5
    }
    repeat(flying / 5) {
        longitude += step
        lines += bRecord(second, longitude)
        second += 5
    }
    repeat(standingAfter / 5) {
        lines += bRecord(second, longitude)
        second += 5
    }
    lines += "LXCTthis is a comment record"
    lines += "G1234567890ABCDEF"
    return lines.joinToString("\r\n").encodeToByteArray()
}

class IgcImportEndToEndTest {
    private val flights = mockk<FlightRepository>().also { coEvery { it.findAll() } returns LoadResult(emptyList()) }
    private val prepare = PrepareIgcImport(Igc4jTrackReader(), flights)

    @Test
    fun `reads a realistic recording and finds the flight in it`() =
        runTest {
            // given
            val file =
                NewAttachment("2026-09-20.igc", realisticIgc(standingBefore = 600, flying = 5_400, standingAfter = 480))

            // when
            val draft = prepare(file)

            // then
            assertThat(draft.date).isEqualTo(LocalDate(2026, 9, 20))
            assertThat(draft.duration!!).isBetween(89.minutes, 91.minutes)
            assertThat(draft.takeOff).isNotNull()
            assertThat(draft.checksum).matches("[0-9a-f]{64}")
        }

    @Test
    fun `handles a flight that crosses midnight UTC`() =
        runTest {
            // given
            val file =
                NewAttachment(
                    "late.igc",
                    realisticIgc(
                        standingBefore = 300,
                        flying = 3_600,
                        standingAfter = 300,
                        startSecondOfDay =
                            23 * 3600 + 40 * 60,
                    ),
                )

            // when
            val draft = prepare(file)

            // then
            assertThat(draft.duration!!).isBetween(59.minutes, 61.minutes)
            assertThat(draft.landing!!.toString()).startsWith("2026-09-21T")
        }
}
