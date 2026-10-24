@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package music.app

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import music.app.browser.*
import music.domain.*
import music.spotify.*
import org.w3c.dom.HTMLVideoElement

@JsFun("(key,tab) => {try {return (tab ? sessionStorage : localStorage).getItem(key)} catch {return '__storage_denied__'}}")
private external fun storageRead(key: String, tab: Boolean): String?
@JsFun("(key,value,tab) => {try { const store = tab ? sessionStorage : localStorage; if(value === null) store.removeItem(key); else store.setItem(key,value); return true; } catch {return false}}")
private external fun storageWrite(key: String, value: String?, tab: Boolean): Boolean
private fun readLocal(key: String): String? = storageRead(key, false).also { check(it != "__storage_denied__") { "Browser storage unavailable" } }
private fun writeLocal(key: String, value: String) { check(storageWrite(key, value, false)) { "Browser storage unavailable" } }
private fun removeLocal(key: String) { check(storageWrite(key, null, false)) { "Browser storage unavailable" } }
@JsFun("() => { if(!location.pathname.endsWith('/oauth/spotify/callback') || !location.search) return false; history.replaceState(null,'',location.pathname); return true; }") private external fun clearOrphanCallback(): Boolean
@JsFun("() => new URL('oauth/spotify/callback', document.baseURI).href") private external fun defaultRedirect(): String
@JsFun("url => window.open(url, '_blank', 'noopener,noreferrer')") private external fun openSpotify(url: String)
@JsFun("() => document.visibilityState === 'visible'") private external fun visible(): Boolean
@JsFun("(id,redirect) => { try { const u=new URL(redirect); return /^[a-fA-F0-9]{32}$/.test(id) && u.origin === location.origin && u.pathname.endsWith('/oauth/spotify/callback') && !u.search && !u.hash && !u.username && !u.password && (u.protocol === 'https:' || (u.protocol === 'http:' && ['127.0.0.1','[::1]'].includes(u.hostname))); } catch {return false} }")
private external fun validSetup(id: String, redirect: String): Boolean
private fun validateSetup(id: String, redirect: String) { require(validSetup(id, redirect)) }
@JsFun("show => {document.getElementById('camera-panel').hidden = !show;}") private external fun showCamera(show: Boolean)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    val client = HttpClient(Js) { install(HttpTimeout) { requestTimeoutMillis = 15_000 } }
    var model by mutableStateOf<AppModel?>(null)
    var player: BrowserPlayer? = null
    var setupError by mutableStateOf<String?>(null)
    var poll: Job? = null
    fun install(id: String?, redirect: String?) {
        poll?.cancel()
        model?.dispose()
        (player as? SpotifyBrowserPlayer)?.dispose()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            var configurationWarning: String? = null
            var configuration = EditionConfigurations.defaults()
            try { readLocal("music-party.editions")?.let { configuration = EditionConfigurations.decodeLocal(it) } }
            catch (_: Throwable) { configurationWarning = "Saved playlist settings invalid or unavailable. Reference defaults loaded; check settings before scanning." }
            val api: SpotifyApi
            val session: AppSession
            val activeScanner = BrowserQrScanner(document.getElementById("camera") as HTMLVideoElement)
            if (id == null) {
                api = FakeSpotifyApi(); player = FakeBrowserPlayer()
                session = object : AppSession {
                    override suspend fun initialize() = true
                    override suspend fun login() {}
                    override suspend fun logout() { player?.disconnect() }
                }
            } else {
                validateSetup(id, redirect!!)
                val browserSession = BrowserSession(client, id, redirect, storage = object : SessionStorage {
                    override fun read(): String? = storageRead("music-party.oauth.tokens", true).also { check(it != "__storage_denied__") }
                    override fun write(value: String?) { check(storageWrite("music-party.oauth.tokens", value, true)) }
                }, onLogout = {
                    activeScanner.stop(); player?.disconnect()
                })
                player = SpotifyBrowserPlayer(browserSession)
                api = KtorSpotifyApi(client, browserSession)
                session = object : AppSession {
                    override suspend fun initialize(): Boolean {
                        when (browserSession.handleCallback()) {
                            OAuthCallback.Denied -> throw MusicFailure(FailureKind.LoginRequired, "Spotify sign-in denied. Sign in again when ready.")
                            OAuthCallback.Invalid -> throw MusicFailure(FailureKind.LoginRequired, "Invalid or expired callback. Start a new sign-in.")
                            else -> Unit
                        }
                        return try { browserSession.validAccessToken(); true }
                        catch (e: SpotifyApiFailure) { if (e.kind == ApiFailureKind.LoginRequired) false else throw e }
                    }
                    override suspend fun login() = browserSession.login()
                    override suspend fun logout() = browserSession.logout()
                }
            }
            val coordinator = SpotifyPlaybackCoordinator(api, player!!)
            val app = AppModel(appScope, session, activeScanner, coordinator, configuration,
                SpotifyPlaylistRepository(api), { writeLocal("music-party.editions", EditionConfigurations.encodeLocal(it)) })
            model = app
            app.initialize().invokeOnCompletion {
                if (configurationWarning != null && app.state.value.error == null)
                    app.state.value = app.state.value.copy(error = configurationWarning)
            }
            poll = appScope.launch {
                app.state.collectLatest { state ->
                    showCamera(state.screen == Screen.Scan && state.signedIn)
                    if (state.signedIn) while (isActive) {
                        delay(4000)
                        if (visible()) try { coordinator.reconcile() }
                        catch (e: CancellationException) { throw e } catch (e: Throwable) { app.report(e) }
                    }
                }
            }
        } catch (_: Throwable) { setupError = "Invalid setup or browser storage unavailable. Check client ID, callback URI and storage permissions."; appScope.cancel() }
    }
    try {
        readLocal("music-party.setup")?.let {
            val config = Json.parseToJsonElement(it).jsonObject
            install(config.getValue("clientId").jsonPrimitive.content, config.getValue("redirectUri").jsonPrimitive.content)
        } ?: run {
            if (clearOrphanCallback()) setupError = "Callback has no saved app setup. Configure the app and sign in again."
        }
    } catch (_: Throwable) { clearOrphanCallback(); setupError = "Saved setup could not be loaded. Enter the public app configuration again." }
    ComposeViewport(document.getElementById("compose-root")!!) {
        MusicParty(model, setupError, { id, redirect ->
            try {
                validateSetup(id.trim(), redirect.trim())
                writeLocal("music-party.setup", buildJsonObject { put("clientId", id.trim()); put("redirectUri", redirect.trim()) }.toString())
                install(id.trim(), redirect.trim())
            } catch (_: Throwable) { setupError = "Enter a 32-character public client ID and this origin's /oauth/spotify/callback URI (HTTPS or loopback IP). Allow browser storage." }
        }, defaultRedirect(), { player?.let { model?.enableAudio(it) } },
            { model?.run { model?.playback?.connectBrowser(); model?.playback?.refreshDevices() } }, ::openSpotify,
            { install(null, null) }, {
                model?.run {
                    model?.logoutSession()
                    removeLocal("music-party.setup")
                    model?.dispose(); (player as? SpotifyBrowserPlayer)?.dispose()
                    model = null
                }
            })
    }
}
