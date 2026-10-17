package ch.qscqlmpa.wingtracker.maptiles

import ch.qscqlmpa.wingtracker.http.sendAborting
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val TIMEOUT_SECONDS = 20L

/** What a tile server answered; [body] only matters when [status] is OK. */
class TileAnswer(
    val status: Int,
    val body: ByteArray,
)

private val client: HttpClient by lazy {
    HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build()
}

/** @throws java.io.IOException when no answer came. */
internal suspend fun httpGetTile(
    url: String,
    userAgent: String,
): TileAnswer {
    val request =
        HttpRequest
            .newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            // Tile services refuse clients that do not say who they are.
            .header("User-Agent", userAgent)
            .GET()
            .build()
    val response = client.sendAborting(request, HttpResponse.BodyHandlers.ofByteArray())
    return TileAnswer(response.statusCode(), response.body())
}
