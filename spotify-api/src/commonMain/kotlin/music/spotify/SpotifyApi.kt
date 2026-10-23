package music.spotify

/** Implementations must propagate cancellation and never log tokens. */
interface AccessTokenProvider { suspend fun validAccessToken(): String }
data class SpotifyArtist(val id: String?, val name: String)
data class SpotifyAlbum(val id: String?, val name: String, val releaseDate: String?, val releaseDatePrecision: String?)
data class SpotifyTrack(val id: String, val uri: String, val url: String,
    val name: String? = null, val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null, val durationMs: Long? = null)
/** Null/episode/local items retain their exact playlist position. */
data class PlaylistItem(val item: SpotifyTrack?, val local: Boolean = false, val type: String? = null)
data class PlaylistPage(val items: List<PlaylistItem>, val offset: Int, val total: Int, val next: String?)
data class PlaylistMetadata(val id: String, val name: String, val snapshotId: String)
data class SpotifyDevice(val id: String?, val name: String, val active: Boolean, val restricted: Boolean)
data class SpotifyPlayback(val device: SpotifyDevice?, val playing: Boolean, val positionMs: Long?, val item: SpotifyTrack?)
interface SpotifyApi {
    suspend fun playlist(id: String): PlaylistMetadata
    suspend fun playlistItemAt(id: String, offset: Int): PlaylistItem
    suspend fun playlistItems(id: String, offset: Int = 0, limit: Int = 50): PlaylistPage
    suspend fun track(id: String): SpotifyTrack
    suspend fun devices(): List<SpotifyDevice>
    suspend fun playbackState(): SpotifyPlayback?
    suspend fun transfer(deviceId: String)
    suspend fun play(uri: String, deviceId: String)
    suspend fun pause(deviceId: String)
    suspend fun resume(deviceId: String)
    suspend fun seek(positionMs: Long, deviceId: String)
}
enum class ApiFailureKind { LoginRequired, Forbidden, NotFound, Throttled, Offline, Server, InvalidResponse }
class SpotifyApiFailure(val kind: ApiFailureKind, message: String, val retryAfterSeconds: Long? = null,
    cause: Throwable? = null, val httpStatusCode: Int? = null) : Exception(message, cause)
