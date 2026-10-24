@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package music.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import music.bingo.*

private const val GATEWAY = "http://127.0.0.1:8765"
@JsFun("url => { const a = window.__musicPartyBingoAudio || (window.__musicPartyBingoAudio = new Audio()); a.src=url; a.play().catch(()=>{}); }") private external fun playBingoAudio(url: String)
@JsFun("() => { const a=window.__musicPartyBingoAudio; if(a){a.pause(); a.currentTime=0;} }") private external fun stopBingoAudio()
@JsFun("url => window.open(url, '_blank', 'noopener,noreferrer')") private external fun openPlexAuth(url: String)

private data class GatewayServer(val id: String, val name: String)
private data class GatewayLibrary(val id: String, val title: String)
private data class GatewayTrack(val id: String, val title: String, val artist: String, val releaseDate: String?, val partKey: String?) {
    fun songMetadata(): SongMetadata {
        val release = releaseDate?.let { value ->
            val precision = when (value.length) { 4 -> DatePrecision.YEAR; 7 -> DatePrecision.MONTH; 10 -> DatePrecision.DAY; else -> null }
            precision?.let { ReleaseInfo(value, it, "Plex library metadata", "Plex catalog date") }
        }
        return SongMetadata(id, title.ifBlank { null }, artist.split("; ").filter(String::isNotBlank), release)
    }
}

@Composable
fun PlexBingoScreen(client: HttpClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var authenticated by remember { mutableStateOf(false) }
    var signInPending by remember { mutableStateOf(false) }
    var authUrl by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf(emptyList<GatewayServer>()) }
    var selectedServer by remember { mutableStateOf<GatewayServer?>(null) }
    var libraries by remember { mutableStateOf(emptyList<GatewayLibrary>()) }
    var selectedLibrary by remember { mutableStateOf<GatewayLibrary?>(null) }
    var tracks by remember { mutableStateOf(emptyList<GatewayTrack>()) }
    var roundSession by remember { mutableStateOf<BingoSession?>(null) }
    var round by remember { mutableStateOf(BingoRoundState(RoundPhase.PREPARING)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parse(body: String) = Json.parseToJsonElement(body)
    suspend fun requestJson(response: HttpResponse): JsonElement {
        val body = response.bodyAsText()
        val value = parse(body)
        if (response.status.value !in 200..299) {
            val message = value.jsonObject["error"]?.jsonPrimitive?.contentOrNull
            throw IllegalStateException(message ?: "Plex gateway request failed (${response.status.value})")
        }
        return value
    }
    suspend fun loadServers() {
        val result = requestJson(client.get("$GATEWAY/api/plex/servers")).jsonArray
        servers = result.map { item -> GatewayServer(item.jsonObject.getValue("id").jsonPrimitive.content, item.jsonObject.getValue("name").jsonPrimitive.content) }
        authenticated = true
        if (servers.isEmpty()) error = "Plex account signed in, but no reachable Plex Media Server was found."
    }
    fun launchRequest(block: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null
            try { block() } catch (e: Throwable) { error = e.message ?: "Could not reach the local Plex gateway. Is it running on this device?" }
            finally { busy = false }
        }
    }
    fun discoverLibraries(server: GatewayServer) = launchRequest {
        selectedServer = server; selectedLibrary = null; libraries = emptyList(); tracks = emptyList(); roundSession = null
        val result = requestJson(client.get("$GATEWAY/api/plex/libraries") { url { parameters.append("serverId", server.id) } }).jsonArray
        libraries = result.map { item -> GatewayLibrary(item.jsonObject.getValue("id").jsonPrimitive.content, item.jsonObject.getValue("title").jsonPrimitive.content) }
        if (libraries.isEmpty()) error = "No music libraries were found on ${server.name}."
    }
    fun loadTracks(library: GatewayLibrary) = launchRequest {
        selectedLibrary = library; roundSession = null; tracks = emptyList()
        val result = requestJson(client.get("$GATEWAY/api/plex/tracks") {
            url { parameters.append("serverId", selectedServer!!.id); parameters.append("sectionId", library.id) }
        }).jsonArray
        tracks = result.map { item -> item.jsonObject.let { obj ->
            GatewayTrack(obj.getValue("id").jsonPrimitive.content, obj["title"]?.jsonPrimitive?.content.orEmpty(),
                obj["artist"]?.jsonPrimitive?.content.orEmpty(), obj["releaseDate"]?.jsonPrimitive?.contentOrNull,
                obj["partKey"]?.jsonPrimitive?.contentOrNull)
        } }
        val byId = tracks.associateBy { it.id }
        roundSession = BingoSession(
            catalog = object : SongCatalog { override suspend fun allEligibleSongs() = tracks.map { CatalogSong(it.id) } },
            metadataProvider = object : SongMetadataProvider { override suspend fun metadata(songId: String) = byId[songId]?.songMetadata() },
            promptPlayer = object : BingoPromptPlayer {
                override suspend fun play(songId: String) {
                    val part = byId[songId]?.partKey ?: error("Plex did not return an audio part for this song")
                    playBingoAudio("$GATEWAY/api/plex/audio/${selectedServer!!.id}/${part.removePrefix("/")}")
                }
                override suspend fun stop() = stopBingoAudio()
            }
        )
        if (tracks.isEmpty()) error = "This library has no available tracks."
    }
    fun beginSignIn() = launchRequest {
        val response = requestJson(client.post("$GATEWAY/api/plex/auth/start")).jsonObject
        authUrl = response.getValue("authUrl").jsonPrimitive.content
        signInPending = true
    }

    LaunchedEffect(Unit) {
        val status = runCatching { requestJson(client.get("$GATEWAY/api/plex/auth/status")).jsonObject }.getOrNull()
        if (status?.get("authenticated")?.jsonPrimitive?.booleanOrNull == true) {
            runCatching { loadServers() }.onFailure { error = it.message ?: "Could not list Plex servers" }
        }
    }

    LaunchedEffect(signInPending) {
        if (signInPending) {
            repeat(180) {
                delay(2000)
                val result = runCatching { requestJson(client.get("$GATEWAY/api/plex/auth/poll")).jsonObject }.getOrNull()
                if (result?.get("authenticated")?.jsonPrimitive?.booleanOrNull == true) {
                    signInPending = false
                    runCatching { loadServers() }.onFailure { error = it.message ?: "Could not list Plex servers" }
                    return@LaunchedEffect
                }
            }
            if (signInPending) { signInPending = false; error = "Plex sign-in timed out. Start again and approve the request." }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack, enabled = !busy) { Text("← Modes") }
                Text("Bingo · Plex", style = MaterialTheme.typography.headlineMedium)
                Text("Plex is the only account, catalog, and audio provider in Bingo mode.")
                if (!authenticated) {
                    Text("Sign in to your Plex account. The local gateway keeps Plex tokens in memory and streams from your selected server.")
                    Button(onClick = ::beginSignIn, enabled = !busy && !signInPending) { Text(if (signInPending) "Waiting for Plex sign-in…" else "Sign in to Plex") }
                    if (signInPending) {
                        authUrl?.let { Button(onClick = { openPlexAuth(it) }) { Text("Open Plex sign-in") } }
                        Text("Approve Music Party in the Plex tab. This page checks sign-in for up to six minutes.")
                    }
                    Text("Start the local Plex gateway first: ./gradlew :plex-gateway:run", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Choose a Plex Media Server", style = MaterialTheme.typography.titleMedium)
                    servers.forEach { server -> OutlinedButton(onClick = { discoverLibraries(server) }, enabled = !busy) { Text(if (selectedServer?.id == server.id) "✓ ${server.name}" else server.name) } }
                    if (selectedServer != null) {
                        Text("Choose a music library", style = MaterialTheme.typography.titleMedium)
                        libraries.forEach { library -> OutlinedButton(onClick = { loadTracks(library) }, enabled = !busy) { Text(if (selectedLibrary?.id == library.id) "✓ ${library.title}" else library.title) } }
                    }
                    if (selectedLibrary != null) {
                        Text("${tracks.size} tracks available in ${selectedLibrary!!.title}")
                        if (tracks.isNotEmpty()) {
                            Button(onClick = { launchRequest { round = roundSession!!.start() } }, enabled = !busy) { Text("Start Bingo") }
                            if (round.phase != RoundPhase.PREPARING) {
                                HorizontalDivider()
                                Text("Round ${round.roundNumber} · ${round.category?.label.orEmpty()}", style = MaterialTheme.typography.titleLarge)
                                round.question?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
                                if (round.phase == RoundPhase.QUESTION) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { launchRequest { roundSession!!.playPrompt() } }, enabled = !busy) { Text("Play song") }
                                        OutlinedButton(onClick = { roundSession!!.reveal(); round = roundSession!!.state.value }, enabled = !busy) { Text("Reveal answer") }
                                    }
                                }
                                if (round.phase == RoundPhase.REVEALED) {
                                    Text(round.solution?.answer.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                    round.solution?.acceptedAnswer?.let { Text("Accepted: $it") }
                                }
                                if (round.phase == RoundPhase.REVEALED) Button(onClick = { launchRequest { round = roundSession!!.nextRound() } }, enabled = !busy) { Text("Next round") }
                                if (round.phase == RoundPhase.EXHAUSTED) Text("All eligible songs have been played.")
                            }
                        }
                    }
                    TextButton(onClick = { launchRequest { stopBingoAudio(); client.post("$GATEWAY/api/plex/logout"); authenticated = false; servers = emptyList(); selectedServer = null; libraries = emptyList(); tracks = emptyList(); roundSession = null } }, enabled = !busy) { Text("Sign out of Plex") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Gateway: $GATEWAY", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
