package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.sync.SyncProblem
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val REPOSITORY = "https://codeberg.org/michael/logbook.git"

class GitProblemsTest {
    @Nested
    inner class SyncProblemOf {
        @Test
        fun `tells a missing network from the host name it could not find`() {
            // given
            val e = TransportException("$REPOSITORY: cannot open git-upload-pack", UnknownHostException("codeberg.org"))

            // when
            val problem = syncProblemOf(e)

            // then
            assertThat(problem).isEqualTo(SyncProblem.NoConnection)
        }

        @Test
        fun `tells a connection that broke`() {
            // given
            val e = TransportException("$REPOSITORY: read timed out", SocketTimeoutException("Read timed out"))

            // when
            val problem = syncProblemOf(e)

            // then
            assertThat(problem).isEqualTo(SyncProblem.Unreachable)
        }

        @Test
        fun `tells refused credentials`() {
            // given
            val e = TransportException("$REPOSITORY: not authorized")

            // when
            val problem = syncProblemOf(e)

            // then
            assertThat(problem).isEqualTo(SyncProblem.CredentialsRefused)
        }

        @Test
        fun `tells a repository that does not exist`() {
            // given
            val e = TransportException("cannot open", NoRemoteRepositoryException(URIish(REPOSITORY), "not found"))

            // when
            val problem = syncProblemOf(e)

            // then
            assertThat(problem).isEqualTo(SyncProblem.NoSuchRepository)
        }

        @Test
        fun `does not take a failing disk for the network`() {
            // given
            val e = TransportException("cannot write", IOException("No space left on device"))

            // when
            val problem = syncProblemOf(e)

            // then
            assertThat(problem).isEqualTo(SyncProblem.Unexpected)
        }
    }
}
