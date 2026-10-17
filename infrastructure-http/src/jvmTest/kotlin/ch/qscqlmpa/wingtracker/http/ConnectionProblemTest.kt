package ch.qscqlmpa.wingtracker.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException

class ConnectionProblemTest {
    @Nested
    inner class ConnectionProblemOf {
        @Test
        fun `reports a connection that could not be made as no connection`() {
            // given
            val failures =
                listOf(ConnectException(), UnknownHostException("syride.com"), HttpConnectTimeoutException("timed out"))

            // when
            val problems = failures.map(::connectionProblemOf)

            // then
            assertThat(problems).containsOnly(ConnectionProblem.NoConnection)
        }

        @Test
        fun `reports a connection that broke or an answer that did not come as broken`() {
            // given
            val failures = listOf(IOException("Connection reset"), HttpTimeoutException("request timed out"))

            // when
            val problems = failures.map(::connectionProblemOf)

            // then
            assertThat(problems).containsOnly(ConnectionProblem.Broken)
        }
    }
}
