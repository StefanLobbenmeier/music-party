package music.spotify

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal val spotifyJson = Json { ignoreUnknownKeys = true }
internal fun invalidResponse(): Nothing = throw SpotifyApiFailure(ApiFailureKind.InvalidResponse, "Invalid Spotify response")
internal inline fun <reified T> decode(body: String): T = try {
    spotifyJson.decodeFromString<T>(body)
} catch (e: Exception) {
    if (e is CancellationException) throw e
    throw SpotifyApiFailure(ApiFailureKind.InvalidResponse, "Invalid Spotify response", cause = e)
}
internal fun httpFailure(response: HttpResponse): SpotifyApiFailure = SpotifyApiFailure(
    when (response.status.value) {
        401 -> ApiFailureKind.LoginRequired
        403 -> ApiFailureKind.Forbidden
        404 -> ApiFailureKind.NotFound
        429 -> ApiFailureKind.Throttled
        in 500..599 -> ApiFailureKind.Server
        else -> ApiFailureKind.InvalidResponse
    }, "Spotify HTTP ${response.status.value}",
    response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.takeIf { it >= 0 },
    httpStatusCode = response.status.value,
)

/** Does not own the injected client. */
internal suspend fun send(client: HttpClient, url: String, configure: HttpRequestBuilder.() -> Unit): HttpResponse = try {
    client.request(url) { expectSuccess = false; configure() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw SpotifyApiFailure(ApiFailureKind.Offline, "Spotify transport failed", cause = e)
}

internal suspend fun readBody(response: HttpResponse): String = try {
    response.bodyAsText()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw SpotifyApiFailure(ApiFailureKind.Offline, "Spotify response transport failed", cause = e)
}
