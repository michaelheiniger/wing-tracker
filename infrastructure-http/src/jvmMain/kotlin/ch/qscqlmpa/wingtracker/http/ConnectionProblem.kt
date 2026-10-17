package ch.qscqlmpa.wingtracker.http

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException

/** Why a request got no answer. */
enum class ConnectionProblem {
    // No connection could be made: most likely this computer is offline.
    NoConnection,

    // Connected, but the answer did not come: a timeout or a connection that broke.
    Broken,
}

/** Tells a connection that could not be made from one that broke. */
fun connectionProblemOf(e: IOException): ConnectionProblem =
    when (e) {
        // The JDK reports an unknown host and a missing network alike, as a connection that could not be made.
        is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ConnectionProblem.NoConnection

        else -> ConnectionProblem.Broken
    }
