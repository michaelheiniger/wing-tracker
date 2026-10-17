package ch.qscqlmpa.wingtracker.maps

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// The tile with Niederhorn in it at a typical zoom level for a flight.
private val NIEDERHORN = TileCoordinates(zoom = 13, x = 4272, y = 2890)
private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

class ReadMapTileTest {
    private val source = mockk<MapTileSource>()
    private val readMapTile = ReadMapTile(source)

    init {
        every { source.maxZoom } returns 17
    }

    @Nested
    inner class Invoke {
        @Test
        fun `reads a tile of the grid from the source`() =
            runTest {
                // given
                coEvery { source.tile(NIEDERHORN) } returns PNG

                // when
                val tile = readMapTile(NIEDERHORN)

                // then
                assertThat(tile).isEqualTo(PNG)
            }

        @Test
        fun `has nothing when the source cannot get the tile now`() =
            runTest {
                // given
                coEvery { source.tile(NIEDERHORN) } returns null

                // when
                val tile = readMapTile(NIEDERHORN)

                // then
                assertThat(tile).isNull()
            }

        @ParameterizedTest(name = "zoom {0}, x {1}, y {2}")
        @CsvSource("-1, 0, 0", "18, 0, 0", "2, 4, 0", "2, 0, 4", "2, -1, 0")
        fun `never asks the source for a tile outside the grid`(
            zoom: Int,
            x: Int,
            y: Int,
        ) = runTest {
            // given
            val outside = TileCoordinates(zoom, x, y)

            // when
            val tile = readMapTile(outside)

            // then
            assertThat(tile).isNull()
            coVerify(exactly = 0) { source.tile(any()) }
        }
    }
}
