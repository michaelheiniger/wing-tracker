package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.flights.FlightId
import ch.qscqlmpa.wingtracker.sync.LogbookChange
import ch.qscqlmpa.wingtracker.sync.RecordKind
import ch.qscqlmpa.wingtracker.sync.SyncException
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import ch.qscqlmpa.wingtracker.sync.SyncRemote
import ch.qscqlmpa.wingtracker.sync.SyncReport
import ch.qscqlmpa.wingtracker.sync.SyncedRecord
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

private val FLIGHT = FlightId("f-2026-09-20")

class GitLogbookTest {
    @TempDir
    lateinit var root: Path

    private lateinit var dataFolder: Path
    private lateinit var remote: SyncRemote
    private lateinit var logbook: GitLogbook

    @BeforeEach
    fun setUp() {
        dataFolder = Files.createDirectories(root.resolve("data"))
        remote = aRemote(root.resolve("remote.git"))
        logbook = GitLogbook(dataFolder, TestLayout(dataFolder))
    }

    private fun remoteContent(name: String = "check") = RemoteContent(remote, root.resolve(name))

    private suspend fun connected() {
        dataFolder.store("wings/w-1.json", """{"schemaVersion": 4, "name": "Zeno 2"}""")
        logbook.connect(remote, PILOT)
    }

    private suspend fun logFlight(content: String = aFlightDocument()) =
        logbook.run(LogbookChange.FlightLogged(FLIGHT)) { dataFolder.store("flights/${FLIGHT.value}.json", content) }

    @Nested
    inner class Connecting {
        @Test
        fun `sends everything stored as the first change`() =
            runTest {
                // given
                dataFolder.store("wings/w-1.json", """{"schemaVersion": 4, "name": "Zeno 2"}""")
                dataFolder.store("attachments/a-1", "%PDF-1.7 check report")

                // when
                logbook.connect(remote, PILOT)

                // then
                val content = remoteContent()
                assertThat(content.files()).containsExactlyInAnyOrder(".gitignore", "attachments/a-1", "wings/w-1.json")
                assertThat(content.commits().single().fullMessage).isEqualTo(FIRST_COMMIT_MESSAGE)
                assertThat(
                    content
                        .commits()
                        .single()
                        .authorIdent.name,
                ).isEqualTo(PILOT.name)
                assertThat(logbook.isConnected()).isTrue()
            }

        @Test
        fun `keeps what belongs to this device out of the repository`() =
            runTest {
                // given
                dataFolder.store("wings/w-1.json", """{"schemaVersion": 4, "name": "Zeno 2"}""")
                dataFolder.store("device-settings.json", """{"schemaVersion": 1, "interfaceScalePercent": 125}""")
                dataFolder.store("quarantine/flights/f-1.json", "not json at all")
                dataFolder.store("flights/f-2-81273.tmp", "half written")

                // when
                logbook.connect(remote, PILOT)

                // then
                assertThat(remoteContent().files()).containsExactlyInAnyOrder(".gitignore", "wings/w-1.json")
            }

        @Test
        fun `refuses a repository that already holds something`() =
            runTest {
                // given
                OtherDevice(remote, root.resolve("other")).commitAndPush("flights/f-9.json", aFlightDocument())

                // when
                val failure = runCatching { logbook.connect(remote, PILOT) }.exceptionOrNull()

                // then
                assertThat((failure as? SyncException)?.problem).isEqualTo(SyncProblem.RemoteNotEmpty)
                assertThat(logbook.isConnected()).isFalse()
            }

        @Test
        fun `tells when the repository does not exist`() =
            runTest {
                // given
                val missing = remote.copy(url = root.resolve("missing.git").toUri().toString())

                // when
                val failure = runCatching { logbook.connect(missing, PILOT) }.exceptionOrNull()

                // then
                assertThat((failure as? SyncException)?.problem).isEqualTo(SyncProblem.NoSuchRepository)
            }
    }

    @Nested
    inner class Transactions {
        @Test
        fun `commits the writes of a transaction as one commit named after its change`() =
            runTest {
                // given
                connected()

                // when
                logbook.run(LogbookChange.CheckRecorded(aWingRef(), aCheckId(), completes = null)) {
                    dataFolder.store("wings/w-1.json", """{"schemaVersion": 4, "name": "Zeno 2", "checks": 1}""")
                    dataFolder.store("attachments/a-2", "%PDF-1.7 report")
                }

                // then
                Git.open(dataFolder.toFile()).use { git ->
                    val last = git.log().call().first()
                    assertThat(last.fullMessage).isEqualTo("Record check c-1 on wing w-1")
                    assertThat(git.status().call().isClean).isTrue()
                }
            }

        @Test
        fun `joins a nested transaction to the outer one`() =
            runTest {
                // given
                connected()

                // when
                logbook.run(LogbookChange.FlightsImportedFromSyride) {
                    logbook.run(LogbookChange.GearRegistered(aWingRef("w-2"))) {
                        dataFolder.store("wings/w-2.json", """{"schemaVersion": 4, "name": "Iota 2"}""")
                    }
                    dataFolder.store("flights/f-1.json", aFlightDocument())
                }

                // then
                Git.open(dataFolder.toFile()).use { git ->
                    val messages = git.log().call().map { it.fullMessage }
                    assertThat(messages).containsExactly("Import flights from Syride", FIRST_COMMIT_MESSAGE)
                }
            }

        @Test
        fun `commits what an interrupted transaction wrote, marked as interrupted`() =
            runTest {
                // given
                connected()

                // when
                runCatching {
                    logbook.run(LogbookChange.FlightsExportedToAirtime) {
                        dataFolder.store("flights/f-1.json", aFlightDocument())
                        throw IOException("air-time stopped answering")
                    }
                }

                // then
                Git.open(dataFolder.toFile()).use { git ->
                    assertThat(
                        git
                            .log()
                            .call()
                            .first()
                            .fullMessage,
                    ).isEqualTo("Export flights to air-time (interrupted)")
                }
            }

        @Test
        fun `makes no commit for a transaction that changed nothing`() =
            runTest {
                // given
                connected()

                // when
                logbook.run(LogbookChange.ThemeModeChanged) { }

                // then
                Git.open(dataFolder.toFile()).use { git -> assertThat(git.log().call()).hasSize(1) }
            }

        @Test
        fun `only runs the block while the logbook is not synchronised`() =
            runTest {
                // given / when
                logFlight()

                // then
                assertThat(dataFolder.resolve("flights/${FLIGHT.value}.json").exists()).isTrue()
                assertThat(logbook.isConnected()).isFalse()
            }

        @Test
        fun `never records a quarantined record as deleted`() =
            runTest {
                // given
                dataFolder.store("flights/f-1.json", aFlightDocument())
                logbook.connect(remote, PILOT)
                Files.createDirectories(dataFolder.resolve("quarantine/flights"))
                Files.move(dataFolder.resolve("flights/f-1.json"), dataFolder.resolve("quarantine/flights/f-1.json"))

                // when
                logFlight()

                // then
                Git.open(dataFolder.toFile()).use { git ->
                    assertThat(git.repository.filesAt("HEAD")).contains("flights/f-1.json")
                }
            }
    }

    @Nested
    inner class Synchronising {
        @Test
        fun `sends this device's changes`() =
            runTest {
                // given
                connected()
                logFlight()

                // when
                val report = logbook.sync(remote, PILOT)

                // then
                assertThat(report).isEqualTo(SyncReport(received = 0, sent = 1))
                assertThat(remoteContent().read("flights/${FLIGHT.value}.json")).isEqualTo(aFlightDocument())
            }

        @Test
        fun `receives the changes another device sent`() =
            runTest {
                // given
                connected()
                OtherDevice(
                    remote,
                    root.resolve("other"),
                ).commitAndPush("flights/f-9.json", aFlightDocument(site = "Fiesch"))

                // when
                val report = logbook.sync(remote, PILOT)

                // then
                assertThat(report).isEqualTo(SyncReport(received = 1, sent = 0))
                assertThat(
                    dataFolder.resolve("flights/f-9.json").readText(),
                ).isEqualTo(aFlightDocument(site = "Fiesch"))
            }

        @Test
        fun `replays this device's changes on top of the other device's, keeping the history a straight line`() =
            runTest {
                // given
                connected()
                OtherDevice(
                    remote,
                    root.resolve("other"),
                ).commitAndPush("flights/f-9.json", aFlightDocument(site = "Fiesch"))
                logFlight()

                // when
                val report = logbook.sync(remote, PILOT)

                // then
                assertThat(report).isEqualTo(SyncReport(received = 1, sent = 1))
                val content = remoteContent()
                assertThat(content.files()).contains("flights/f-9.json", "flights/${FLIGHT.value}.json")
                assertThat(content.commits().map { it.parentCount }).containsExactly(1, 1, 0)
                assertThat(content.commits().first().fullMessage).isEqualTo("Log flight ${FLIGHT.value}")
            }

        @Test
        fun `exchanges nothing and names the records changed on both sides`() =
            runTest {
                // given
                dataFolder.store("flights/${FLIGHT.value}.json", aFlightDocument())
                logbook.connect(remote, PILOT)
                OtherDevice(remote, root.resolve("other"))
                    .commitAndPush("flights/${FLIGHT.value}.json", aFlightDocument(duration = 120))
                logbook.run(LogbookChange.FlightUpdated(FLIGHT)) {
                    dataFolder.store("flights/${FLIGHT.value}.json", aFlightDocument(duration = 100))
                }

                // when
                val report = logbook.sync(remote, PILOT)

                // then
                assertThat(report.conflicts).containsExactly(SyncedRecord(RecordKind.Flight, FLIGHT.value))
                assertThat(dataFolder.resolve("flights/${FLIGHT.value}.json").readText())
                    .isEqualTo(aFlightDocument(duration = 100))
                assertThat(
                    remoteContent().read("flights/${FLIGHT.value}.json"),
                ).isEqualTo(aFlightDocument(duration = 120))
            }

        @Test
        fun `commits changes made outside the app before exchanging`() =
            runTest {
                // given
                connected()
                dataFolder.store("flights/f-3.json", aFlightDocument(site = "Grindelwald First"))

                // when
                logbook.sync(remote, PILOT)

                // then
                val content = remoteContent()
                assertThat(content.files()).contains("flights/f-3.json")
                assertThat(content.commits().first().fullMessage).isEqualTo(STRAY_CHANGES_MESSAGE)
            }

        @Test
        fun `signs the next changes with a new author`() =
            runTest {
                // given
                connected()

                // when
                logbook.changeAuthor(OTHER_DEVICE)
                logFlight()

                // then
                Git.open(dataFolder.toFile()).use { git ->
                    assertThat(
                        git
                            .log()
                            .call()
                            .first()
                            .authorIdent.name,
                    ).isEqualTo(OTHER_DEVICE.name)
                }
            }
    }
}
