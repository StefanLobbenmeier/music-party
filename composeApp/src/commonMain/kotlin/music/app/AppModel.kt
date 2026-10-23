package music.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import music.domain.*

enum class Screen { Setup, Login, Scan, Playback, Settings }
interface AppSession {
    suspend fun initialize(): Boolean
    suspend fun login()
    suspend fun logout()
}
data class AppState(val screen: Screen = Screen.Setup, val busy: Boolean = false,
    val error: String? = null, val signedIn: Boolean = false, val resolved: ResolvedSong? = null)

/** Screen intents are shared with tests; platform dependencies are constructor-injected. */
class AppModel(val scope: CoroutineScope, private val session: AppSession,
    val scanner: QrScanner, val playback: SpotifyPlaybackCoordinator,
    configuration: EditionConfiguration, private val repository: OrderedPlaylistRepository,
    private val saveConfiguration: (EditionConfiguration) -> Unit,
) {
    val state = MutableStateFlow(AppState())
    var configuration = configuration; private set
    private var initialization: Job? = null
    private var scan: Job? = null
    private var revision = 0
    private var acceptingScan = true
    fun report(error: Throwable) {
        val failure = error.actionable()
        if (failure.kind == FailureKind.LoginRequired) {
            scanner.stop(); playback.cancel()
            state.value = state.value.copy(screen = Screen.Login, signedIn = false)
        }
        state.value = state.value.copy(error = failure.message, busy = false)
    }
    fun run(action: suspend () -> Unit): Job = scope.launch {
        try { action() } catch (e: CancellationException) { throw e } catch (e: Throwable) { report(e) }
    }
    fun initialize(): Job {
        initialization?.cancel()
        return run {
        state.value = state.value.copy(screen = Screen.Login, busy = true)
        val signedIn = session.initialize()
        state.value = AppState(if (signedIn) Screen.Scan else Screen.Login, signedIn = signedIn)
        if (signedIn) {
            playback.connectBrowser()
            playback.refreshDevices()
        }
        }.also { initialization = it }
    }
    fun login() = run { session.login() }
    fun navigate(screen: Screen) {
        if (screen != Screen.Scan) { scanner.stop(); scan?.cancel(); revision++; state.value = state.value.copy(busy = false) }
        state.value = state.value.copy(screen = screen, error = null)
    }
    fun next() {
        scan?.cancel(); playback.cancel(); scanner.stop(); revision++
        acceptingScan = true
        state.value = state.value.copy(screen = Screen.Scan, busy = false, error = null)
    }
    fun submit(text: String, fromCamera: Boolean = false) {
        if (fromCamera && !acceptingScan) return
        acceptingScan = false
        scanner.stop(); scan?.cancel(); playback.cancel()
        val epoch = ++revision
        scan = run {
            state.value = state.value.copy(busy = true, error = null)
            try {
                val song = HitsterSongResolver(configuration, repository).resolve(HitsterCardParser.parse(text))
                currentCoroutineContext().ensureActive()
                if (epoch != revision) return@run
                state.value = state.value.copy(resolved = song, screen = Screen.Playback)
                playback.play(song)
            } finally { if (epoch == revision) state.value = state.value.copy(busy = false) }
        }
    }
    // UNDISTPATCHED enters the browser camera/activation bridge inside the actual click.
    fun startCamera() = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        acceptingScan = true
        try { scanner.start({ submit(it, true) }, { report(it) }) }
        catch (e: CancellationException) { throw e } catch (e: Throwable) { report(e) }
    }
    fun enableAudio(player: BrowserPlayer) = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try { player.activateFromUserGesture() }
        catch (e: CancellationException) { throw e }
        catch (_: Throwable) { state.value = state.value.copy(error = "Audio activation failed. Connect this browser and click Enable audio again, or choose a remote device.") }
    }
    fun save(json: String) {
        try {
            val value = EditionConfigurations.decodeLocal(json)
            saveConfiguration(value); configuration = value
            if (state.value.signedIn) next() else navigate(Screen.Login)
        } catch (e: Throwable) { report(e) }
    }
    fun logout() {
        next()
        run { logoutSession() }
    }
    suspend fun logoutSession() {
        initialization?.cancel()
        scanner.stop(); scan?.cancel(); playback.reset()
        state.value = AppState(Screen.Login)
        session.logout()
        playback.reset()
    }
    fun dispose() { scanner.dispose(); playback.cancel(); scope.cancel() }
}
