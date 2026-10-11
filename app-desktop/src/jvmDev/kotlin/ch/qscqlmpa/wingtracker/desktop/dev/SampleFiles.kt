package ch.qscqlmpa.wingtracker.desktop.dev

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Sample files for the development data: report PDFs and IGC tracks, both generated so no real data is needed. */
private const val LATITUDE_DEGREE_DIGITS = 2
private const val LONGITUDE_DEGREE_DIGITS = 3

object SampleFiles {
    /** A valid one-page PDF with a few lines of text, small enough to keep in the repository's memory. */
    fun pdf(vararg lines: String): ByteArray {
        val text =
            lines
                .mapIndexed {
                    index,
                    line,
                    ->
                    "BT /F1 14 Tf 60 ${780 - index * 22} Td (${escape(line)}) Tj ET"
                }.joinToString("\n")
        val objects =
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R " +
                    "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length ${text.length} >>\nstream\n$text\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            )
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, body ->
            offsets += out.length
            out.append("${index + 1} 0 obj\n$body\nendobj\n")
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().encodeToByteArray()
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    /**
     * An IGC track of a plausible paraglider flight: a logger switched on before take-off, a glide with thermals
     * (circling climbs), a landing, and a few minutes of standing still afterwards.
     */
    fun igc(
        date: java.time.LocalDate,
        startSecondOfDay: Int,
        flightMinutes: Int,
        launch: Pair<Double, Double>,
    ): ByteArray {
        val lines =
            mutableListOf(
                "AXCT1234WINGTRACKER-SAMPLE",
                "HFDTE%02d%02d%02d".format(date.dayOfMonth, date.monthValue, date.year % 100),
                "HFFXA035",
                "HFPLTPILOTINCHARGE:Sample Pilot",
                "HFGTYGLIDERTYPE:Sample wing",
                "HFDTM100GPSDATUM:WGS-1984",
                "I023638FXA3941ENL",
            )
        var second = startSecondOfDay
        var (latitude, longitude) = launch
        var altitude = START_ALTITUDE
        repeat(PRE_FLIGHT_SECONDS / FIX_SECONDS) {
            lines += bRecord(second, latitude, longitude, altitude)
            second +=
                FIX_SECONDS
        }
        val flightFixes = flightMinutes * SECONDS_PER_MINUTE / FIX_SECONDS
        repeat(flightFixes) { index ->
            val thermalling = (index / THERMAL_CYCLE_FIXES) % 2 == 1
            val phase = index * FIX_SECONDS * 2 * Math.PI / CIRCLE_SECONDS
            val metersEast =
                (if (thermalling) sin(phase) * CIRCLE_RADIUS else 0.0) +
                    GLIDE_SPEED * FIX_SECONDS * (if (thermalling) 0.25 else 1.0)
            val metersNorth = (if (thermalling) cos(phase) * CIRCLE_RADIUS else 0.0) + GLIDE_SPEED * FIX_SECONDS * 0.3
            latitude += metersNorth / METERS_PER_DEGREE
            longitude += metersEast / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
            altitude =
                (altitude + (if (thermalling) CLIMB else SINK) * FIX_SECONDS).coerceIn(LANDING_ALTITUDE, MAX_ALTITUDE)
            lines += bRecord(second, latitude, longitude, altitude)
            second += FIX_SECONDS
        }
        repeat(POST_FLIGHT_SECONDS / FIX_SECONDS) {
            lines += bRecord(second, latitude, longitude, LANDING_ALTITUDE)
            second +=
                FIX_SECONDS
        }
        lines += "G0123456789ABCDEF"
        return lines.joinToString("\r\n").encodeToByteArray()
    }

    private fun bRecord(
        secondOfDay: Int,
        latitude: Double,
        longitude: Double,
        altitude: Double,
    ): String {
        val time =
            "%02d%02d%02d".format(
                secondOfDay / SECONDS_PER_HOUR,
                secondOfDay / SECONDS_PER_MINUTE % MINUTES_PER_HOUR,
                secondOfDay % SECONDS_PER_MINUTE,
            )
        val metres = altitude.toInt().coerceAtLeast(0)
        val lat = degreesAndMinutes(latitude, LATITUDE_DEGREE_DIGITS, 'N', 'S')
        val lon = degreesAndMinutes(longitude, LONGITUDE_DEGREE_DIGITS, 'E', 'W')
        return "B$time$lat${lon}A%05d%05d".format(metres, metres + GPS_OFFSET)
    }

    private fun degreesAndMinutes(
        value: Double,
        degreeDigits: Int,
        positive: Char,
        negative: Char,
    ): String {
        val absolute = abs(value)
        val degrees = absolute.toInt()
        val thousandthsOfMinutes = ((absolute - degrees) * MINUTES_PER_HOUR * THOUSAND).toInt()
        return String.format(
            Locale.ROOT,
            "%0${degreeDigits}d%05d%c",
            degrees,
            thousandthsOfMinutes,
            if (value >=
                0
            ) {
                positive
            } else {
                negative
            },
        )
    }

    private const val FIX_SECONDS = 5
    private const val PRE_FLIGHT_SECONDS = 600
    private const val POST_FLIGHT_SECONDS = 420
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val THOUSAND = 1000
    private const val METERS_PER_DEGREE = 111_320.0
    private const val GLIDE_SPEED = 9.5
    private const val CIRCLE_RADIUS = 55.0
    private const val CIRCLE_SECONDS = 24.0
    private const val THERMAL_CYCLE_FIXES = 48
    private const val START_ALTITUDE = 1950.0
    private const val MAX_ALTITUDE = 2450.0
    private const val LANDING_ALTITUDE = 560.0
    private const val CLIMB = 1.8
    private const val SINK = -1.1
    private const val GPS_OFFSET = 48
}
