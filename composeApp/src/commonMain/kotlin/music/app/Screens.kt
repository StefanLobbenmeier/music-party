package music.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import music.domain.*

@Composable
fun MusicParty(model: AppModel?, setupError: String?, configure: (String, String) -> Unit,
    redirectDefault: String, enableAudio: () -> Unit, connectBrowser: () -> Unit,
    openLink: (String) -> Unit, demo: () -> Unit, changeSetup: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Music Party", style = MaterialTheme.typography.headlineMedium)
                Text("Spotify scan and playback companion")
                Text("Spotify game-use permission is unresolved. No game rounds are implemented. Live account, playlist order and device verification are pending.",
                    style = MaterialTheme.typography.bodySmall)
                if (model == null) {
                    var clientId by remember { mutableStateOf("") }
                    var redirect by remember { mutableStateOf(redirectDefault) }
                    Text("Set up Spotify", style = MaterialTheme.typography.titleLarge)
                    Text("Enter your public app client ID and exact registered callback URI. No client secret. Premium and app allowlisting may be required.")
                    OutlinedTextField(clientId, { clientId = it }, label = { Text("Public client ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(redirect, { redirect = it }, label = { Text("Registered redirect URI") }, modifier = Modifier.fillMaxWidth())
                    setupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { configure(clientId, redirect) }) { Text("Save setup") }
                    OutlinedButton(onClick = demo) { Text("Try fake providers (no Spotify)") }
                } else {
                    val state by model.state.collectAsState()
                    val actual by model.playback.state.collectAsState()
                    val devices by model.playback.devices.collectAsState()
                    val notice by model.playback.notice.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.signedIn) TextButton(onClick = { model.next() }) { Text("Scan") }
                        TextButton(onClick = { model.navigate(Screen.Settings) }) { Text("Settings") }
                        if (state.signedIn) TextButton(onClick = { model.logout() }) { Text("Log out") }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    (state.error ?: notice)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { model.state.value = state.copy(error = null); model.playback.notice.value = null }) { Text("Dismiss") }
                    }
                    when (state.screen) {
                        Screen.Setup, Screen.Login -> {
                            Text("Sign in to Spotify", style = MaterialTheme.typography.titleLarge)
                            Text("Use the account allowed for your configured app. Session credentials stay in this tab.")
                            Button(onClick = { model.login() }, enabled = !state.busy) { Text("Sign in") }
                        }
                        Screen.Scan -> {
                            var input by remember { mutableStateOf("") }
                            Text("Scan a card", style = MaterialTheme.typography.titleLarge)
                            Text("Use the camera below or paste the QR text. Only trusted Hitster card URLs are accepted.")
                            OutlinedTextField(input, { input = it }, label = { Text("QR text") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { model.submit(input) }, enabled = !state.busy) { Text("Resolve and play") }
                            OutlinedButton(onClick = { model.startCamera() }, enabled = !state.busy) { Text("Start camera") }
                            TextButton(onClick = { model.scanner.stop() }) { Text("Stop camera") }
                        }
                        Screen.Playback -> {
                            Text(if (actual.playing) "Playing on Spotify" else "Playback", style = MaterialTheme.typography.titleLarge)
                            Text("Position: ${actual.positionMs / 1000} seconds")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { model.run { if (actual.playing) model.playback.pause() else model.playback.resume() } }, enabled = !state.busy) {
                                    Text(if (actual.playing) "Pause" else "Resume")
                                }
                                OutlinedButton(onClick = { model.next() }) { Text("Next scan") }
                            }
                            var seconds by remember { mutableStateOf("") }
                            OutlinedTextField(seconds, { seconds = it.filter(Char::isDigit).take(7) }, label = { Text("Seek to seconds") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { model.run { model.playback.seek((seconds.toLongOrNull() ?: 0) * 1000) } }, enabled = !state.busy) { Text("Seek") }
                            TextButton(onClick = { model.run { state.resolved?.let { model.playback.play(it) } } }) { Text("Retry resolved track") }
                            state.resolved?.let { song -> TextButton(onClick = { openLink(song.url) }) { Text("Open track on Spotify") } }
                        }
                        Screen.Settings -> {
                            var json by remember(model.configuration) { mutableStateOf(EditionConfigurations.encodeLocal(model.configuration)) }
                            TextButton(onClick = changeSetup) { Text("Change Spotify app setup") }
                            Text("Playlist configuration", style = MaterialTheme.typography.titleLarge)
                            Text("Edit editionKey, playlist ID/URL and label. Ordering must match physical cards exactly. Following a public playlist may not grant access.")
                            OutlinedTextField(json, { json = it }, label = { Text("Edition mappings JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 8)
                            Button(onClick = { model.save(json) }) { Text("Save playlists") }
                            OutlinedButton(onClick = { json = EditionConfigurations.encodeLocal(EditionConfigurations.defaults()) }) { Text("Reset to reference defaults") }
                        }
                    }
                    if (state.signedIn) {
                        HorizontalDivider()
                        Text("Playback device", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = enableAudio) { Text("Enable audio in this browser") }
                        TextButton(onClick = connectBrowser) { Text("Connect / retry browser player") }
                        TextButton(onClick = { model.run { model.playback.refreshDevices() } }) { Text("Refresh devices") }
                        if (devices.isEmpty()) Text("Open Spotify on a device, then refresh.")
                        devices.forEach { device ->
                            OutlinedButton(onClick = { model.run { model.playback.selectDevice(device.id!!) } },
                                enabled = device.id != null && !device.restricted) {
                                Text("${if (actual.deviceId == device.id) "✓ " else ""}${device.name}${if (device.restricted) " (restricted)" else ""}")
                            }
                        }
                    }
                }
            }
        }
    }
}
