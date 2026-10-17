package ch.qscqlmpa.wingtracker.maptiles

import ch.qscqlmpa.wingtracker.maps.TileCoordinates
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.ConnectException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private const val USER_AGENT = "wing-tracker/0.1.0"
private val NOW = Instant.parse("2026-10-17T18:00:00Z")

// The tile with Niederhorn in it at a typical zoom level for a flight.
private val NIEDERHORN = TileCoordinates(zoom = 13, x = 4272, y = 2890)
private val FRESH_PNG = "fresh tile".toByteArray()
private val OLD_PNG = "old tile".toByteArray()

class OpenTopoMapTileSourceTest {
    @TempDir
    lateinit var directory: Path

    private val requested = mutableListOf<Pair<String, String>>()

    private fun answering(answer: () -> TileAnswer): suspend (String, String) -> TileAnswer =
        { url, userAgent ->
            requested += url to userAgent
            answer()
        }

    private fun source(
        get: suspend (String, String) -> TileAnswer = answering { TileAnswer(200, FRESH_PNG) },
        cacheLimitBytes: Long = Long.MAX_VALUE,
    ) = OpenTopoMapTileSource(directory, USER_AGENT, get, now = { NOW }, cacheLimitBytes = cacheLimitBytes)

    private fun givenCached(
        coordinates: TileCoordinates,
        bytes: ByteArray,
        age: Duration,
    ): Path {
        val file = directory.resolve("${coordinates.zoom}/${coordinates.x}/${coordinates.y}.png")
        file.parent.createDirectories()
        file.writeBytes(bytes)
        file.setLastModifiedTime(FileTime.from((NOW - age).toJavaInstant()))
        return file
    }

    @Nested
    inner class Tile {
        @Test
        fun `fetches a tile it does not have, saying who asks, and keeps it`() =
            runTest {
                // given
                val source = source()

                // when
                val tile = source.tile(NIEDERHORN)

                // then
                assertThat(tile).isEqualTo(FRESH_PNG)
                assertThat(requested.single().first).endsWith("/13/4272/2890.png").startsWith("https://")
                assertThat(requested.single().second).isEqualTo(USER_AGENT)
                assertThat(directory.resolve("13/4272/2890.png").readBytes()).isEqualTo(FRESH_PNG)
            }

        @Test
        fun `serves a recent tile from the cache without asking the server`() =
            runTest {
                // given
                givenCached(NIEDERHORN, OLD_PNG, age = 2.days)
                val source = source()

                // when
                val tile = source.tile(NIEDERHORN)

                // then
                assertThat(tile).isEqualTo(OLD_PNG)
                assertThat(requested).isEmpty()
            }

        @Test
        fun `fetches an outdated tile again`() =
            runTest {
                // given
                givenCached(NIEDERHORN, OLD_PNG, age = 60.days)
                val source = source()

                // when
                val tile = source.tile(NIEDERHORN)

                // then
                assertThat(tile).isEqualTo(FRESH_PNG)
                assertThat(directory.resolve("13/4272/2890.png").readBytes()).isEqualTo(FRESH_PNG)
            }

        @Test
        fun `still serves an outdated tile when offline`() =
            runTest {
                // given
                givenCached(NIEDERHORN, OLD_PNG, age = 60.days)
                val source = source(get = { _, _ -> throw ConnectException("Network is unreachable") })

                // when
                val tile = source.tile(NIEDERHORN)

                // then
                assertThat(tile).isEqualTo(OLD_PNG)
            }

        @Test
        fun `has nothing when offline and the tile was never fetched`() =
            runTest {
                // given
                val source = source(get = { _, _ -> throw IOException("Connection reset") })

                // when
                val tile = source.tile(NIEDERHORN)

                // then
                assertThat(tile).isNull()
            }

        @Test
        fun `keeps nothing when the server refuses the tile`() =
            runTest {
                // given
                val source = source(get = answering { TileAnswer(429, "Too many requests".toByteArray()) })

                // when
                val tile = source.tile(NIEDERHORN)

                // then
                assertThat(tile).isNull()
                assertThat(directory.resolve("13/4272/2890.png")).doesNotExist()
            }
    }

    @Nested
    inner class Trimming {
        @Test
        fun `deletes the oldest tiles once the cache is larger than its limit`() =
            runTest {
                // given
                val oldest = givenCached(TileCoordinates(13, 4271, 2890), ByteArray(400), age = 20.days)
                val older = givenCached(TileCoordinates(13, 4273, 2890), ByteArray(400), age = 10.days)
                val recent = givenCached(TileCoordinates(13, 4272, 2889), ByteArray(400), age = 1.days)
                val source = source(cacheLimitBytes = 900)

                // when
                source.tile(NIEDERHORN)

                // then
                assertThat(oldest.exists()).isFalse()
                assertThat(older.exists()).isTrue()
                assertThat(recent.exists()).isTrue()
            }
    }
}
