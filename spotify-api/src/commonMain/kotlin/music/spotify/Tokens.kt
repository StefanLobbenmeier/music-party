package music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** Optional extension used for the single unauthorized-request retry. */
interface RefreshingAccessTokenProvider : AccessTokenProvider {
    suspend fun refreshAfterUnauthorized(rejectedToken: String): String
}

/** Store in tab-scoped storage in browser adapters; never log this value. */
class SpotifyTokens(val accessToken: String, val refreshToken: String?, val expiresAtMillis: Long)

@Serializable internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** Public-client PKCE transport: no client secret or implicit retries. */
class SpotifyTokenTransport(private val client: HttpClient, private val clientId: String,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    init { require(clientId.isNotBlank()) }
    suspend fun exchange(code: String, redirectUri: String, verifier: String): SpotifyTokens = request(
        parametersOf("grant_type" to listOf("authorization_code"), "client_id" to listOf(clientId),
            "code" to listOf(code), "redirect_uri" to listOf(redirectUri), "code_verifier" to listOf(verifier)), null)
    suspend fun refresh(refreshToken: String): SpotifyTokens = request(
        parametersOf("grant_type" to listOf("refresh_token"), "client_id" to listOf(clientId),
            "refresh_token" to listOf(refreshToken)), refreshToken)
    private suspend fun request(parameters: Parameters, previousRefresh: String?): SpotifyTokens {
        val response = send(client, "https://accounts.spotify.com/api/token") {
            method = HttpMethod.Post; setBody(FormDataContent(parameters))
        }
        if (response.status.value in listOf(400, 401)) throw SpotifyApiFailure(ApiFailureKind.LoginRequired, "Spotify authorization rejected", httpStatusCode = response.status.value)
        if (response.status.value !in 200..299) throw httpFailure(response)
        val dto = decode<TokenResponse>(readBody(response))
        if (dto.accessToken.isBlank() || !dto.tokenType.equals("Bearer", true) || dto.expiresIn !in 1..86400) invalidResponse()
        return SpotifyTokens(dto.accessToken, dto.refreshToken ?: previousRefresh, nowMillis() + dto.expiresIn * 1000)
    }
}

/** One instance per session, shared with SDK callbacks. Storage callback must not reenter this provider. */
class SpotifyTokenProvider(private val transport: SpotifyTokenTransport, initialTokens: SpotifyTokens? = null,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val refreshSkewMillis: Long = 30_000,
    private val onTokensChanged: suspend (SpotifyTokens?) -> Unit = {},
) : RefreshingAccessTokenProvider {
    private val mutex = Mutex()
    private var tokens = initialTokens
    init { require(refreshSkewMillis >= 0) }
    suspend fun setTokens(value: SpotifyTokens?) = mutex.withLock { tokens = value; onTokensChanged(value) }
    override suspend fun validAccessToken(): String = mutex.withLock {
        val current = tokens ?: loginRequired()
        if (current.expiresAtMillis - nowMillis() > refreshSkewMillis) current.accessToken else refresh(current)
    }
    override suspend fun refreshAfterUnauthorized(rejectedToken: String): String = mutex.withLock {
        val current = tokens ?: loginRequired()
        if (current.accessToken != rejectedToken && current.expiresAtMillis > nowMillis()) current.accessToken else refresh(current)
    }
    private suspend fun refresh(current: SpotifyTokens): String {
        val refreshToken = current.refreshToken ?: run { tokens = null; onTokensChanged(null); loginRequired() }
        val replacement = try { transport.refresh(refreshToken) } catch (e: SpotifyApiFailure) {
            if (e.kind == ApiFailureKind.LoginRequired) { tokens = null; onTokensChanged(null) }
            throw e
        }
        tokens = replacement; onTokensChanged(replacement)
        return replacement.accessToken
    }
    private fun loginRequired(): Nothing = throw SpotifyApiFailure(ApiFailureKind.LoginRequired, "Spotify login required")
}
