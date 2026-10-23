package music.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable data class HitsterCard(val editionKey: String, val cardNumber: Int)
@Serializable data class ResolvedSong(val id: String, val uri: String, val url: String)
@Serializable data class EditionConfiguration(val playlists: Map<String, String>)
interface SongResolver { suspend fun resolve(card: HitsterCard): ResolvedSong }
enum class FailureKind {
    InvalidQr, UnknownEdition, InaccessiblePlaylist, MissingItem, UnavailableTrack,
    LoginRequired, NoDevice, PlaybackRestricted, Throttled, Offline
}
class MusicFailure(val kind: FailureKind, message: String, cause: Throwable? = null) : Exception(message, cause)
data class PlaybackState(val deviceId: String? = null, val song: ResolvedSong? = null,
    val playing: Boolean = false, val positionMs: Long = 0, val failure: FailureKind? = null)
interface PlaybackCoordinator {
    val state: StateFlow<PlaybackState>
    suspend fun selectDevice(deviceId: String)
    suspend fun play(song: ResolvedSong)
    suspend fun pause()
    suspend fun resume()
    suspend fun seek(positionMs: Long)
}
interface QrScanner {
    suspend fun start(onDecoded: (String) -> Unit, onFailure: (MusicFailure) -> Unit)
    fun stop()
    fun dispose()
}
data class BrowserDeviceState(val connected: Boolean = false, val deviceId: String? = null,
    val active: Boolean = false, val playing: Boolean = false)
interface BrowserPlayer {
    val deviceState: StateFlow<BrowserDeviceState>
    suspend fun connect()
    suspend fun activateFromUserGesture()
    fun disconnect()
}
