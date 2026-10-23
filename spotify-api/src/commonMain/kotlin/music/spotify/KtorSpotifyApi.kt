package music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/** Inject a client without automatic retry/auth plugins: this class owns retry policy.
 * At most two transient read retries (250/500 ms); commands and 429 are never backed off.
 */
class KtorSpotifyApi(private val client: HttpClient, private val tokenProvider: AccessTokenProvider,
    private val backoff: suspend (Long) -> Unit = { delay(it) },
) : SpotifyApi {
    private fun id(value: String): String { require(value.isNotBlank() && value.all { it.isLetterOrDigit() }); return value }
    override suspend fun playlist(id: String): PlaylistMetadata {
        val dto = decode<PlaylistDto>(get("playlists/${id(id)}"))
        return PlaylistMetadata(dto.id, dto.name, dto.snapshot)
    }
    override suspend fun playlistItemAt(id: String, offset: Int): PlaylistItem =
        playlistItems(id, offset, 1).items.firstOrNull()
            ?: throw SpotifyApiFailure(ApiFailureKind.NotFound, "Playlist position does not exist")
    override suspend fun playlistItems(id: String, offset: Int, limit: Int): PlaylistPage {
        require(offset >= 0 && limit in 1..50)
        val dto = decode<PageDto>(get("playlists/${id(id)}/items", mapOf("offset" to "$offset", "limit" to "$limit")))
        if (dto.offset != offset || dto.total < 0 || dto.items.size > limit) invalidResponse()
        return PlaylistPage(dto.items.map { it?.model() ?: PlaylistItem(null) }, dto.offset, dto.total, dto.next)
    }
    /** Follows offsets rather than untrusted next URLs; rejects non-progressing/inconsistent pages. */
    fun allPlaylistItems(id: String, pageSize: Int = 50, maxPages: Int = 10_000): Flow<PlaylistItem> = flow {
        require(maxPages > 0)
        var pages = 0
        var offset = 0
        while (true) {
            if (pages++ >= maxPages) invalidResponse()
            val page = playlistItems(id, offset, pageSize)
            page.items.forEach { emit(it) }
            if (page.next == null) break
            val next = offset.toLong() + page.items.size
            if (page.items.isEmpty() || next >= page.total || next > Int.MAX_VALUE) invalidResponse()
            offset = next.toInt()
        }
    }
    override suspend fun track(id: String): SpotifyTrack = decode<ItemDto>(get("tracks/${id(id)}")).track() ?: invalidResponse()
    override suspend fun devices(): List<SpotifyDevice> = decode<DevicesDto>(get("me/player/devices")).devices.map { it.model() }
    override suspend fun playbackState(): SpotifyPlayback? {
        val response = request(HttpMethod.Get, "me/player")
        if (response.status == HttpStatusCode.NoContent) return null
        val dto = decode<PlaybackDto>(response.bodyAsText())
        return SpotifyPlayback(dto.device?.model(), dto.playing, dto.position, dto.item?.track())
    }
    override suspend fun transfer(deviceId: String) {
        require(deviceId.isNotBlank())
        request(HttpMethod.Put, "me/player", body = buildJsonObject { putJsonArray("device_ids") { add(deviceId) } })
    }
    override suspend fun play(uri: String, deviceId: String) {
        require(uri.startsWith("spotify:track:") && uri.removePrefix("spotify:track:").isNotBlank())
        command("play", deviceId, buildJsonObject { putJsonArray("uris") { add(uri) } })
    }
    override suspend fun pause(deviceId: String) { command("pause", deviceId) }
    override suspend fun resume(deviceId: String) { command("play", deviceId) }
    override suspend fun seek(positionMs: Long, deviceId: String) {
        require(positionMs >= 0); command("seek", deviceId, parameters = mapOf("position_ms" to "$positionMs"))
    }
    private suspend fun command(path: String, deviceId: String, body: JsonObject? = null, parameters: Map<String, String> = emptyMap()) {
        require(deviceId.isNotBlank())
        request(HttpMethod.Put, "me/player/$path", parameters + ("device_id" to deviceId), body)
    }
    private suspend fun get(path: String, parameters: Map<String, String> = emptyMap()): String =
        request(HttpMethod.Get, path, parameters).bodyAsText()
    private suspend fun request(method: HttpMethod, path: String, parameters: Map<String, String> = emptyMap(), body: JsonObject? = null): HttpResponse {
        var token = tokenProvider.validAccessToken()
        var refreshed = false
        var retries = 0
        while (true) {
            val response = try {
                send(client, "https://api.spotify.com/v1/$path") {
                    this.method = method
                    bearerAuth(token)
                    url { parameters.forEach { (key, value) -> this.parameters.append(key, value) } }
                    if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
                }.also { readBody(it) }
            } catch (e: SpotifyApiFailure) {
                if (e.kind == ApiFailureKind.Offline && method == HttpMethod.Get && retries < 2) {
                    backoff(250L shl retries++); continue
                }
                throw e
            }
            if (response.status == HttpStatusCode.Unauthorized && !refreshed && tokenProvider is RefreshingAccessTokenProvider) {
                response.bodyAsText()
                refreshed = true; token = tokenProvider.refreshAfterUnauthorized(token); continue
            }
            if (response.status.value in 200..299) return response
            val failure = httpFailure(response)
            response.bodyAsText()
            if (response.status.value in listOf(500, 502, 503, 504) && method == HttpMethod.Get && retries < 2) {
                backoff(250L shl retries++); continue
            }
            throw failure
        }
    }
}
