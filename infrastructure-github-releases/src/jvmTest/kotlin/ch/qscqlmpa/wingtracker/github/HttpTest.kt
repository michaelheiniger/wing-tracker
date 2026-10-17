package ch.qscqlmpa.wingtracker.github

import ch.qscqlmpa.wingtracker.updates.ReleaseProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException

class HttpTest {
    @Nested
    inner class ProblemOf {
        @Test
        fun `reports a connection that could not be made as no connection`() {
            // given
            val failures =
                listOf(
                    ConnectException(),
                    UnknownHostException("api.github.com"),
                    HttpConnectTimeoutException("timed out"),
                )

            // when
            val problems = failures.map(::problemOf)

            // then
            assertThat(problems).containsOnly(ReleaseProblem.NoConnection)
        }

        @Test
        fun `reports a connection that broke or an answer that did not come as unreachable`() {
            // given
            val failures = listOf(IOException("Connection reset"), HttpTimeoutException("request timed out"))

            // when
            val problems = failures.map(::problemOf)

            // then
            assertThat(problems).containsOnly(ReleaseProblem.Unreachable)
        }
    }
}
