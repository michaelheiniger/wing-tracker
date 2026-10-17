package ch.qscqlmpa.wingtracker.syride

import ch.qscqlmpa.wingtracker.flights.syride.SyrideException
import ch.qscqlmpa.wingtracker.flights.syride.SyrideProblem
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException

private val SAMPLE = SyrideWebFlightSourceTest::class.java.getResource("/activities-sample.html")!!.readText()

class SyrideWebFlightSourceTest {
    private val requested = mutableListOf<String>()

    private fun source(
        profile: String,
        pages: List<String>,
    ) = SyrideWebFlightSource(
        fetchPage = { url, _ ->
            requested += url
            if ("activites.php" !in
                url
            ) {
                profile
            } else {
                pages.getOrElse(requested.count { "activites.php" in it } - 1) { "" }
            }
        },
        pause = {},
    )

    @Nested
    inner class Fetching {
        @Test
        fun `follows the feed page by page until it is empty`() =
            runTest {
                // given
                val source =
                    source("<a href='x?idRider=28153'>", listOf(SAMPLE, SAMPLE.replace("activite", "activite9")))

                // when
                val flights = source.fetch("QSCQLMPA")

                // then
                assertThat(flights).hasSize(4)
                assertThat(requested.filter { "activites.php" in it }).hasSize(3)
                assertThat(requested.last()).contains("idRider=28153").contains("append=1&beforeId=")
            }

        @Test
        fun `asks for the profile of the given pilot`() =
            runTest {
                // when
                source("idRider=1", emptyList()).fetch("QSCQLMPA")

                // then
                assertThat(requested.first()).isEqualTo("https://syride.com/en/pilots/QSCQLMPA")
            }
    }

    @Nested
    inner class FlightPage {
        @Test
        fun `points to the flight on the pilot's Syride profile`() {
            // given
            val source = source(profile = "", pages = emptyList())

            // when
            val page = source.flightPage("michael", "3720061")

            // then
            assertThat(page).isEqualTo("https://syride.com/en/pilots/michael/3720061")
        }

        @Test
        fun `writes spaces in the account name as Syride does`() {
            // given
            val source = source(profile = "", pages = emptyList())

            // when
            val page = source.flightPage("Jean Müller", "3720061")

            // then
            assertThat(page).isEqualTo("https://syride.com/en/pilots/Jean_M%C3%BCller/3720061")
        }
    }

    @Nested
    inner class Problems {
        @Test
        fun `reports an unknown pilot, which Syride answers with pilot id 0`() =
            runTest {
                assertThatThrownBy {
                    kotlinx.coroutines.runBlocking {
                        source(
                            "idRider=0",
                            emptyList(),
                        ).fetch("nobody")
                    }
                }.isInstanceOfSatisfying(SyrideException::class.java) {
                    assertThat(it.problem).isEqualTo(SyrideProblem.UserNotFound)
                }
            }

        @Test
        fun `reports a profile page without a pilot id as changed pages`() =
            runTest {
                assertThatThrownBy { kotlinx.coroutines.runBlocking { source("<html>", emptyList()).fetch("x") } }
                    .isInstanceOfSatisfying(SyrideException::class.java) {
                        assertThat(it.problem).isEqualTo(SyrideProblem.PagesChanged)
                    }
            }

        @Test
        fun `reports changed pages when most flight cards cannot be read`() =
            runTest {
                // given
                val broken = "<div id='activite1'><ul>Flight name : <font>X</font></ul></div>"

                // when / then
                assertThatThrownBy { kotlinx.coroutines.runBlocking { source("idRider=5", listOf(broken)).fetch("x") } }
                    .isInstanceOfSatisfying(SyrideException::class.java) {
                        assertThat(it.problem).isEqualTo(SyrideProblem.PagesChanged)
                    }
            }
    }

    @Nested
    inner class ProblemOf {
        @Test
        fun `reports a connection that could not be made as no connection`() {
            // given
            val failures =
                listOf(ConnectException(), UnknownHostException("syride.com"), HttpConnectTimeoutException("timed out"))

            // when
            val problems = failures.map(::problemOf)

            // then
            assertThat(problems).containsOnly(SyrideProblem.NoConnection)
        }

        @Test
        fun `reports a connection that broke or an answer that did not come as unreachable`() {
            // given
            val failures = listOf(IOException("Connection reset"), HttpTimeoutException("request timed out"))

            // when
            val problems = failures.map(::problemOf)

            // then
            assertThat(problems).containsOnly(SyrideProblem.Unreachable)
        }
    }
}
