package ch.qscqlmpa.wingtracker.git

import ch.qscqlmpa.wingtracker.http.ConnectionProblem
import ch.qscqlmpa.wingtracker.http.connectionProblemOf
import ch.qscqlmpa.wingtracker.sync.SyncProblem
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import java.io.IOException
import javax.net.ssl.SSLException

/**
 * JGit reports most transport failures as one exception type and tells them apart only in its message, so the
 * message is read; the cause tells a missing network from a broken one.
 */
internal fun syncProblemOf(e: Throwable): SyncProblem {
    val causes = generateSequence(e) { it.cause }.toList()
    val network = causes.filterIsInstance<IOException>().firstOrNull { it.isNetworkFailure() }
    if (network != null) {
        return when (connectionProblemOf(network)) {
            ConnectionProblem.NoConnection -> SyncProblem.NoConnection
            ConnectionProblem.Broken -> SyncProblem.Unreachable
        }
    }
    if (causes.any { it is NoRemoteRepositoryException }) return SyncProblem.NoSuchRepository
    val messages = causes.mapNotNull { it.message?.lowercase() }
    return when {
        messages.any { REFUSED.any(it::contains) } -> SyncProblem.CredentialsRefused
        messages.any { NOT_FOUND.any(it::contains) } -> SyncProblem.NoSuchRepository
        else -> SyncProblem.Unexpected
    }
}

// A failure of the file system is no network problem, so only what the JDK's network classes throw counts.
private fun IOException.isNetworkFailure() = javaClass.name.startsWith("java.net.") || this is SSLException

private val REFUSED = listOf("not authorized", "authentication is required", "401", "403")
private val NOT_FOUND = listOf("not found", "404")
