package music.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import music.domain.*
import music.spotify.*

fun SpotifyTrack.song() = ResolvedSong(id, uri, url)
fun Throwable.actionable(): MusicFailure = when (this) {
    is MusicFailure -> this
    is SpotifyApiFailure -> when (kind) {
        ApiFailureKind.LoginRequired -> MusicFailure(FailureKind.LoginRequired, "Session expired. Sign in again.")
        ApiFailureKind.Forbidden -> MusicFailure(FailureKind.PlaybackRestricted, "Spotify denied access. Check Premium, app allowlisting and device restrictions.")
        ApiFailureKind.NotFound -> MusicFailure(FailureKind.NoDevice, "Device unavailable. Open Spotify and refresh devices.")
        ApiFailureKind.Throttled -> MusicFailure(FailureKind.Throttled, "Spotify rate limit. Wait ${retryAfterSeconds ?: 30} seconds before retrying.")
        else -> MusicFailure(FailureKind.Offline, "Spotify unavailable. Check your connection and retry.")
    }
    else -> MusicFailure(FailureKind.Offline, "Operation failed. Check your connection and retry.")
}

class SpotifyPlaylistRepository(private val api: SpotifyApi) : OrderedPlaylistRepository {
    override suspend fun itemAt(playlistId: String, offset: Int): OrderedPlaylistItem? = try {
        OrderedPlaylistItem(api.playlistItemAt(playlistId, offset).item?.song())
    } catch (e: SpotifyApiFailure) {
        when (e.kind) {
            ApiFailureKind.NotFound -> null
            ApiFailureKind.Forbidden -> throw MusicFailure(FailureKind.InaccessiblePlaylist,
                "Playlist inaccessible. Configure an owned or collaborated playlist with verified card order.")
            else -> throw e.actionable()
        }
    }
}

/** One command at a time. A newer intent cancels the previous wait/request before taking the lock.
 * Cancellation cannot undo a command already accepted by Spotify; every next intent reconciles REST.
 */
class SpotifyPlaybackCoordinator(private val api: SpotifyApi, private val browser: BrowserPlayer,
    private val timeoutMs: Long = 10_000, private val pollMs: Long = 350,
) : PlaybackCoordinator {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var current: Job? = null
    private var selected: String? = null
    private val available = MutableStateFlow<List<SpotifyDevice>>(emptyList())
    val devices = available.asStateFlow()
    val notice = MutableStateFlow<String?>(null)
    private var desiredSong: ResolvedSong? = null

    private suspend fun command(block: suspend () -> Unit) = coroutineScope {
        val job = currentCoroutineContext().job
        val previous = current
        current = job
        if (previous !== job) previous?.cancel()
        try { mutex.withLock { block() } }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) {
            val failure = e.actionable()
            mutable.value = mutable.value.copy(failure = failure.kind)
            notice.value = failure.message
            throw failure
        } finally { if (current === job) current = null }
    }
    suspend fun connectBrowser() {
        try { browser.connect() }
        catch (e: CancellationException) { throw e }
        catch (_: Throwable) { notice.value = "Browser player unavailable. Open Spotify on another device, then refresh devices." }
    }
    suspend fun refreshDevices() { available.value = api.devices() }
    private suspend fun usable(): String = withTimeoutOrNull(timeoutMs) { findUsable() }
        ?: throw MusicFailure(FailureKind.NoDevice, "Device readiness timed out. Open Spotify, refresh devices and retry.")
    private suspend fun findUsable(): String {
        refreshDevices()
        while (selected == null && available.value.none { it.id != null && !it.restricted } && browser.deviceState.value.connected) {
            delay(pollMs); refreshDevices()
        }
        val candidates = available.value.filter { it.id != null && !it.restricted }
        val id = selected ?: candidates.firstOrNull { it.active }?.id
            ?: browser.deviceState.value.deviceId?.takeIf { local -> candidates.any { it.id == local } }
            ?: candidates.firstOrNull()?.id
            ?: throw MusicFailure(FailureKind.NoDevice, "No usable device. Enable this browser or open Spotify on another device and refresh.")
        if (candidates.none { it.id == id }) throw MusicFailure(FailureKind.NoDevice,
            "Selected device disappeared or is restricted. Select another device and retry.")
        selected = id
        if (api.playbackState()?.device?.let { it.id == id && it.active } != true) {
            api.transfer(id)
            confirm { it.device?.id == id && it.device?.active == true }
        }
        return id
    }
    private suspend fun confirm(predicate: (SpotifyPlayback) -> Boolean) {
        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val actual = api.playbackState()
                if (actual != null) {
                    mutable.value = PlaybackState(actual.device?.id, actual.item?.song(), actual.playing,
                        actual.positionMs ?: 0)
                    if (predicate(actual)) return@withTimeoutOrNull true
                }
                delay(pollMs)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (result != true) throw MusicFailure(FailureKind.NoDevice,
            "Spotify did not confirm playback in time. Refresh devices, select a device and retry.")
    }
    override suspend fun selectDevice(deviceId: String) = command {
        selected = deviceId; usable(); notice.value = null
    }
    override suspend fun play(song: ResolvedSong) = command {
        desiredSong = song
        val id = usable()
        api.play(song.uri, id)
        confirm { it.device?.id == id && it.item?.uri == song.uri && it.playing }
        notice.value = null
    }
    suspend fun retry() { desiredSong?.let { play(it) } }
    override suspend fun pause() = command {
        val id = usable(); api.pause(id); confirm { it.device?.id == id && !it.playing }; notice.value = null
    }
    override suspend fun resume() = command {
        val id = usable(); api.resume(id); confirm { it.device?.id == id && it.playing }; notice.value = null
    }
    override suspend fun seek(positionMs: Long) = command {
        val target = positionMs.coerceAtLeast(0)
        val id = usable(); api.seek(target, id)
        confirm { it.device?.id == id && kotlin.math.abs((it.positionMs ?: -10_000) - target) < 2500 }
        notice.value = null
    }
    suspend fun reconcile() = mutex.withLock {
        val actual = api.playbackState()
        mutable.value = if (actual == null) PlaybackState(failure = mutable.value.failure)
        else PlaybackState(actual.device?.id, actual.item?.song(), actual.playing, actual.positionMs ?: 0)
    }
    fun cancel() { current?.cancel() }
    fun reset() { cancel(); selected = null; desiredSong = null; mutable.value = PlaybackState(); available.value = emptyList(); notice.value = null }
}
