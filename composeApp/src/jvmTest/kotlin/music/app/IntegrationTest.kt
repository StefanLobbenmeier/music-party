package music.app

import kotlin.test.*
import kotlinx.coroutines.*
import music.domain.*
import music.spotify.*

class IntegrationTest {
    private val song = ResolvedSong("one", "spotify:track:one", "https://open.spotify.com/track/one")
    @Test fun scanPauseSeekNextAndUsefulErrors() = runBlocking {
        val api = FakeSpotifyApi()
        val coordinator = SpotifyPlaybackCoordinator(api, FakeBrowserPlayer())
        var stops = 0
        val scanner = object : QrScanner {
            override suspend fun start(onDecoded: (String) -> Unit, onFailure: (MusicFailure) -> Unit) {}
            override fun stop() { stops++ }
            override fun dispose() {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = AppModel(scope, object : AppSession {
            override suspend fun initialize() = true
            override suspend fun login() {}
            override suspend fun logout() {}
        }, scanner, coordinator, EditionConfigurations.defaults(), SpotifyPlaylistRepository(api), {})
        model.initialize().join()
        assertEquals(Screen.Scan, model.state.value.screen)
        model.submit("https://www.hitstergame.com/de/00001")
        assertEquals(Screen.Playback, model.state.value.screen)
        assertEquals("fake0", coordinator.state.value.song?.id)
        assertTrue(coordinator.state.value.playing)
        coordinator.pause(); assertFalse(coordinator.state.value.playing)
        coordinator.seek(12000); assertEquals(12000, coordinator.state.value.positionMs)
        coordinator.resume(); assertTrue(coordinator.state.value.playing)
        model.next(); assertEquals(Screen.Scan, model.state.value.screen)
        model.submit("https://evil.example/de/1")
        assertTrue(model.state.value.error!!.contains("valid www.hitstergame.com"))
        assertTrue(stops >= 3)
        model.dispose()
    }
    @Test fun logoutCancelsPendingInitialization() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        var loggedOut = false
        val scanner = object : QrScanner {
            override suspend fun start(onDecoded: (String) -> Unit, onFailure: (MusicFailure) -> Unit) {}
            override fun stop() {}
            override fun dispose() {}
        }
        val api = FakeSpotifyApi()
        val model = AppModel(scope, object : AppSession {
            override suspend fun initialize(): Boolean { started.complete(Unit); awaitCancellation() }
            override suspend fun login() {}
            override suspend fun logout() { loggedOut = true }
        }, scanner, SpotifyPlaybackCoordinator(api, FakeBrowserPlayer()), EditionConfigurations.defaults(),
            SpotifyPlaylistRepository(api), {})
        val initialization = model.initialize()
        started.await()
        assertTrue(model.state.value.busy)
        model.logoutSession()
        initialization.join()
        assertTrue(initialization.isCancelled)
        assertTrue(loggedOut)
        assertEquals(Screen.Login, model.state.value.screen)
        assertFalse(model.state.value.signedIn)
        assertFalse(model.state.value.busy)
        model.dispose()
    }
    @Test fun sdkFailureStillUsesRemoteAndAllCommandsTargetDevice() = runBlocking {
        val fake = FakeSpotifyApi()
        val browser = object : BrowserPlayer by FakeBrowserPlayer() {
            override suspend fun connect() { error("SDK unavailable") }
        }
        val coordinator = SpotifyPlaybackCoordinator(fake, browser)
        coordinator.connectBrowser()
        assertTrue(coordinator.notice.value!!.contains("another device"))
        coordinator.play(song); coordinator.pause(); coordinator.seek(5000); coordinator.resume()
        assertEquals(listOf("play:fake", "pause:fake", "seek:fake", "resume:fake"), fake.commands)
    }
    @Test fun transferWaitsForConfirmationAndTimesOutWithoutPlay() = runBlocking {
        val fake = FakeSpotifyApi()
        var transferred = false
        val api = object : SpotifyApi by fake {
            override suspend fun playbackState(): SpotifyPlayback? = null
            override suspend fun transfer(deviceId: String) { transferred = true }
        }
        val coordinator = SpotifyPlaybackCoordinator(api, FakeBrowserPlayer(), 25, 1)
        val failure = assertFailsWith<MusicFailure> { coordinator.play(song) }
        assertEquals(FailureKind.NoDevice, failure.kind)
        assertTrue(transferred)
        assertTrue(fake.commands.isEmpty())
        assertFalse(coordinator.state.value.playing)
    }
    @Test fun newerCommandCancelsUnconfirmedPlay() = runBlocking {
        val fake = FakeSpotifyApi()
        val started = CompletableDeferred<Unit>()
        val api = object : SpotifyApi by fake {
            override suspend fun play(uri: String, deviceId: String) { started.complete(Unit); awaitCancellation() }
        }
        val coordinator = SpotifyPlaybackCoordinator(api, FakeBrowserPlayer())
        val first = launch { coordinator.play(song) }
        started.await()
        coordinator.pause()
        first.join()
        assertTrue(first.isCancelled)
        assertEquals(listOf("pause:fake"), fake.commands)
    }
    @Test fun restrictedOrDisappearedSelectionNeverReceivesCommands() = runBlocking {
        val fake = FakeSpotifyApi()
        val api = object : SpotifyApi by fake {
            override suspend fun devices() = listOf(SpotifyDevice("blocked", "Restricted", true, true))
        }
        val coordinator = SpotifyPlaybackCoordinator(api, FakeBrowserPlayer())
        assertFailsWith<MusicFailure> { coordinator.selectDevice("blocked") }
        assertTrue(fake.commands.isEmpty())
    }
    @Test fun latestScanWinsAndDuplicatesAreSuppressed() = runBlocking {
        val fake = FakeSpotifyApi()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = CompletableDeferred<Unit>()
        val scanner = object : QrScanner {
            override suspend fun start(onDecoded: (String) -> Unit, onFailure: (MusicFailure) -> Unit) {}
            override fun stop() {}
            override fun dispose() {}
        }
        var resolutions = 0
        val model = AppModel(scope, object : AppSession {
            override suspend fun initialize() = true
            override suspend fun login() {}
            override suspend fun logout() {}
        }, scanner, SpotifyPlaybackCoordinator(fake, FakeBrowserPlayer()), EditionConfigurations.defaults(),
            OrderedPlaylistRepository { _, offset ->
                resolutions++
                if (offset == 0) { first.complete(Unit); awaitCancellation() }
                OrderedPlaylistItem(song)
            }, {})
        model.submit("www.hitstergame.com/de/1", true)
        first.await()
        model.submit("www.hitstergame.com/de/1", true)
        assertEquals(1, resolutions)
        model.submit("www.hitstergame.com/de/2")
        assertEquals(2, resolutions)
        assertEquals(song, model.state.value.resolved)
        assertEquals(listOf("play:fake"), fake.commands)
        model.dispose()
    }
}
