@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package music.app.browser

import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import music.domain.*
import music.spotify.*
import org.w3c.dom.HTMLVideoElement
import kotlin.js.Promise

private external interface AuthBridge : JsAny {
    fun begin(): Promise<JsAny?>
    fun callback(): String
    fun clear()
}
@JsModule("./browser-adapters.js")
private external object BrowserBridge {
    fun createAuth(clientId: String, redirectUri: String): AuthBridge
    fun createCamera(video: HTMLVideoElement, decoded: (String) -> Unit, failed: (String) -> Unit): CameraBridge
    fun createPlayer(token: ((String) -> Unit, () -> Unit) -> Unit, changed: (String) -> Unit): PlayerBridge
    fun read(): String?
    fun write(value: String?)
}
private external interface CameraBridge : JsAny {
    fun start(): Promise<JsAny?>
    fun stop()
    fun pause()
    fun dispose()
}
private external interface PlayerBridge : JsAny {
    fun connect(): Promise<JsAny?>
    fun activate(): Promise<JsAny?>
    fun disconnect()
    fun dispose()
}

/** Tab-scoped credentials. Inject storage for tests; never stringify this object for diagnostics. */
interface SessionStorage {
    fun read(): String?
    fun write(value: String?)
}
class TabSessionStorage : SessionStorage {
    override fun read() = BrowserBridge.read()
    override fun write(value: String?) = BrowserBridge.write(value)
}
private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun restore(storage: SessionStorage): SpotifyTokens? = try {
    storage.read()?.let { raw ->
        val value = Json.parseToJsonElement(raw).jsonObject
        val access = value.text("access")?.takeIf { it.isNotBlank() } ?: error("Invalid session")
        SpotifyTokens(access, value.text("refresh"), value["expires"]!!.jsonPrimitive.long)
    }
} catch (_: Exception) { storage.write(null); null }
private fun persist(storage: SessionStorage, tokens: SpotifyTokens?) {
    storage.write(tokens?.let { buildJsonObject {
        put("access", it.accessToken); put("refresh", it.refreshToken?.let(::JsonPrimitive) ?: JsonNull)
        put("expires", it.expiresAtMillis)
    }.toString() })
}

enum class OAuthCallback { None, Authorized, Denied, Invalid }
/** Own one per tab session. Logout hooks release camera/player and clear account caches. */
class BrowserSession(client: HttpClient, clientId: String, private val redirectUri: String,
    private val storage: SessionStorage = TabSessionStorage(), private val onLogout: () -> Unit = {},
) : RefreshingAccessTokenProvider {
    private val auth = BrowserBridge.createAuth(clientId, redirectUri)
    private val transport = SpotifyTokenTransport(client, clientId)
    private val provider = SpotifyTokenProvider(transport, restore(storage), onTokensChanged = { persist(storage, it) })
    private val lifecycle = Mutex()
    private var generation = 0
    suspend fun login() { auth.begin().await<JsAny?>() }
    suspend fun handleCallback(): OAuthCallback {
        val epoch = generation
        val result = Json.parseToJsonElement(auth.callback()).jsonObject
        return when (result.text("kind")) {
            "none" -> OAuthCallback.None
            "denied" -> OAuthCallback.Denied
            "code" -> {
                val tokens = transport.exchange(result.text("code")!!, redirectUri, result.text("verifier")!!)
                lifecycle.withLock {
                    if (epoch != generation) return@withLock OAuthCallback.Invalid
                    provider.setTokens(tokens); OAuthCallback.Authorized
                }
            }
            else -> OAuthCallback.Invalid
        }
    }
    suspend fun logout() {
        // Invalidate callbacks before waiting for an in-flight refresh to finish.
        generation++
        try { onLogout() } finally {
            lifecycle.withLock { provider.setTokens(null); auth.clear(); storage.write(null) }
        }
    }
    override suspend fun validAccessToken() = provider.validAccessToken()
    override suspend fun refreshAfterUnauthorized(rejectedToken: String) = provider.refreshAfterUnauthorized(rejectedToken)
}

/** Call start directly from the user action. A decode pauses until the next explicit start. */
class BrowserQrScanner(video: HTMLVideoElement) : QrScanner {
    private var decoded: (String) -> Unit = {}
    private var failed: (MusicFailure) -> Unit = {}
    private val camera = BrowserBridge.createCamera(video, { decoded(it) }, {
        failed(MusicFailure(FailureKind.InvalidQr, when (it) {
            "permission-denied" -> "Camera permission denied. Allow camera access or enter the QR text."
            "no-camera" -> "No camera found. Enter the QR text instead."
            else -> "Camera unavailable. Retry or enter the QR text."
        }))
    })
    override suspend fun start(onDecoded: (String) -> Unit, onFailure: (MusicFailure) -> Unit) {
        decoded = onDecoded; failed = onFailure
        try { camera.start().await<JsAny?>() } catch (cancelled: CancellationException) { stop(); throw cancelled }
    }
    fun pause() = camera.pause()
    override fun stop() = camera.stop()
    override fun dispose() { camera.dispose(); decoded = {}; failed = {} }
}

data class BrowserSdkState(val loaded: Boolean = false, val connected: Boolean = false,
    val ready: Boolean = false, val active: Boolean = false, val playing: Boolean = false,
    val activated: Boolean = false, val failure: String? = null)

/** Local SDK failure leaves the independent SpotifyApi remote-device path usable. */
class SpotifyBrowserPlayer(tokens: AccessTokenProvider) : BrowserPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val device = MutableStateFlow(BrowserDeviceState())
    override val deviceState = device.asStateFlow()
    private val sdk = MutableStateFlow(BrowserSdkState())
    val sdkState = sdk.asStateFlow()
    private val player = BrowserBridge.createPlayer({ success, failure ->
        scope.launch {
            try { success(tokens.validAccessToken()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure() }
        }
    }, { raw ->
        val state = Json.parseToJsonElement(raw).jsonObject
        fun flag(key: String) = state[key]?.jsonPrimitive?.booleanOrNull == true
        device.value = BrowserDeviceState(flag("connected"), state.text("deviceId"), flag("active"), flag("playing"))
        sdk.value = BrowserSdkState(flag("loaded"), flag("connected"), flag("ready"), flag("active"),
            flag("playing"), flag("activated"), state.text("failure"))
    })
    override suspend fun connect() {
        try { player.connect().await<JsAny?>() } catch (cancelled: CancellationException) { disconnect(); throw cancelled }
    }
    // No coroutine launch or token/network work before activateElement: preserve browser user activation.
    override suspend fun activateFromUserGesture() { player.activate().await<JsAny?>() }
    override fun disconnect() { player.disconnect(); scope.coroutineContext.cancelChildren() }
    fun dispose() { player.dispose(); scope.cancel() }
}
