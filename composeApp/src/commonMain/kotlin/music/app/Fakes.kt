package music.app

import kotlinx.coroutines.flow.MutableStateFlow
import music.domain.*
import music.spotify.*

/** Explicit, credential-free demo; never used as a fallback for a failed live request. */
class FakeSpotifyApi : SpotifyApi {
    private val device = SpotifyDevice("fake", "Fake Spotify device (silent demo)", true, false)
    var actual = SpotifyPlayback(device, false, 0, null)
    val commands = mutableListOf<String>()
    override suspend fun playlist(id: String) = PlaylistMetadata(id, "Fake playlist", "fake")
    override suspend fun playlistItemAt(id: String, offset: Int) = PlaylistItem(SpotifyTrack("fake$offset", "spotify:track:fake$offset", "https://open.spotify.com/track/fake$offset"))
    override suspend fun playlistItems(id: String, offset: Int, limit: Int) = PlaylistPage(listOf(playlistItemAt(id, offset)), offset, offset + 1, null)
    override suspend fun track(id: String) = SpotifyTrack(id, "spotify:track:$id", "https://open.spotify.com/track/$id")
    override suspend fun devices() = listOf(device)
    override suspend fun playbackState() = actual
    override suspend fun transfer(deviceId: String) { commands += "transfer:$deviceId"; actual = actual.copy(device = device) }
    override suspend fun play(uri: String, deviceId: String) { commands += "play:$deviceId"; actual = actual.copy(item = track(uri.substringAfterLast(':')), playing = true, positionMs = 0) }
    override suspend fun pause(deviceId: String) { commands += "pause:$deviceId"; actual = actual.copy(playing = false) }
    override suspend fun resume(deviceId: String) { commands += "resume:$deviceId"; actual = actual.copy(playing = true) }
    override suspend fun seek(positionMs: Long, deviceId: String) { commands += "seek:$deviceId"; actual = actual.copy(positionMs = positionMs) }
}
class FakeBrowserPlayer : BrowserPlayer {
    override val deviceState = MutableStateFlow(BrowserDeviceState())
    override suspend fun connect() {}
    override suspend fun activateFromUserGesture() {}
    override fun disconnect() {}
}
