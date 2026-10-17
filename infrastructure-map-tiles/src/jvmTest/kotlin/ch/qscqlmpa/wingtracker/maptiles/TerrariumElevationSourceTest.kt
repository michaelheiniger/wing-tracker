package ch.qscqlmpa.wingtracker.maptiles

import ch.qscqlmpa.wingtracker.maps.GeoPoint
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.time.Instant

private const val USER_AGENT = "wing-tracker/0.1.0"
private val NOW = Instant.parse("2026-10-17T18:00:00Z")
private val NIEDERHORN = GeoPoint(46.7104, 7.7714)
private val BEATENBUCHT = GeoPoint(46.6918, 7.7736)

/** A terrain tile whose height grows from west to east by [metersPerPixel], in the encoding the source reads. */
private fun terrainTile(
    westMeters: Double,
    metersPerPixel: Double = 0.0,
): ByteArray {
    val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until 256) {
        val value = westMeters + x * metersPerPixel + 32768
        val red = (value / 256).toInt()
        val green = (value - red * 256).toInt()
        val blue = ((value - red * 256 - green) * 256).toInt()
        for (y in 0 until 256) image.setRGB(x, y, (red shl 16) or (green shl 8) or blue)
    }
    return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}

class TerrariumElevationSourceTest {
    @TempDir
    lateinit var directory: Path

    private val requested = mutableListOf<String>()

    private fun source(answer: (String) -> TileAnswer) =
        TerrariumElevationSource(directory, USER_AGENT, get = {
            url,
            _,
            ->
            answer(url).also { requested += url }
        }, now = { NOW })

    @Nested
    inner class Elevations {
        @Test
        fun `reads the height encoded in the tile under each point`() =
            runTest {
                // given
                val source = source { TileAnswer(200, terrainTile(westMeters = 1950.0)) }

                // when
                val heights = source.elevations(listOf(NIEDERHORN, BEATENBUCHT))

                // then
                assertThat(heights).containsExactly(1950, 1950)
            }

        @Test
        fun `interpolates between pixels`() =
            runTest {
                // given
                val source = source { TileAnswer(200, terrainTile(westMeters = 1000.0, metersPerPixel = 2.0)) }

                // when
                val height = checkNotNull(source.elevations(listOf(NIEDERHORN)).single())

                // then: Niederhorn lies 107.8 pixels into its tile, the height of pixel centre 107.3
                assertThat(height).isEqualTo(1215)
            }

        @Test
        fun `fetches each tile once, saying who asks`() =
            runTest {
                // given
                val source = source { TileAnswer(200, terrainTile(westMeters = 1950.0)) }

                // when
                source.elevations(listOf(NIEDERHORN, BEATENBUCHT, NIEDERHORN))

                // then
                assertThat(requested).hasSize(1)
                assertThat(requested.single()).startsWith("https://").contains("/12/2136/")
            }

        @Test
        fun `has no height where the tile cannot be had`() =
            runTest {
                // given
                val source = source { throw ConnectException("Network is unreachable") }

                // when
                val heights = source.elevations(listOf(NIEDERHORN))

                // then
                assertThat(heights).containsExactly(null)
            }

        @Test
        fun `has no height where the tile is not an image`() =
            runTest {
                // given
                val source = source { TileAnswer(200, "<html>Service unavailable</html>".toByteArray()) }

                // when
                val heights = source.elevations(listOf(NIEDERHORN))

                // then
                assertThat(heights).containsExactly(null)
            }
    }
}
