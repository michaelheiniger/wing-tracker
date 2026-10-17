package ch.qscqlmpa.wingtracker.desktop

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class DeviceTracksTest {
    @TempDir
    lateinit var device: Path

    private fun givenFile(relative: String) {
        device.resolve(relative).also { it.parent.createDirectories() }.writeText("AXSY")
    }

    @Nested
    inner class ReadTracks {
        @Test
        fun `finds the tracks in every folder, whatever the case of their extension`() {
            // given
            givenFile("IGC/20260926/114344.IGC")
            givenFile("IGC/20260929/093012.igc")
            givenFile("IGC/20260929/notes.txt")

            // when
            val tracks = readTracks(device, "igc")

            // then
            assertThat(tracks.map { it.fileName }).containsExactly("20260926/114344.IGC", "20260929/093012.igc")
        }

        @Test
        fun `keeps the plain name of a track directly in the chosen folder`() {
            // given
            givenFile("3271596.igc")

            // when
            val tracks = readTracks(device, "igc")

            // then
            assertThat(tracks.map { it.fileName }).containsExactly("3271596.igc")
        }

        @Test
        fun `leaves out tracks in hidden folders, such as the trash of a USB stick`() {
            // given
            givenFile(".Trash-1000/files/114344.IGC")
            givenFile("IGC/20260926/114344.IGC")

            // when
            val tracks = readTracks(device, "igc")

            // then
            assertThat(tracks.map { it.fileName }).containsExactly("20260926/114344.IGC")
        }
    }
}
