@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package music.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsModule("./bridge.js")
external object Bridge {
    fun install(callback: (String) -> Unit)
}

@JsFun("(name, value) => { document.documentElement.dataset[name] = value; }")
external fun recordProbe(name: String, value: String)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    var bridgeEvent by mutableStateOf("No SDK event yet")
    Bridge.install { bridgeEvent = it; recordProbe("kotlinBridgeEvent", it) }
    ComposeViewport(document.getElementById("compose-root")!!) {
        var request by remember { mutableStateOf("Ktor request pending") }
        LaunchedEffect(Unit) {
            val client = HttpClient(Js)
            try {
                val response = client.get("/config.example.json")
                request = "Ktor browser GET: ${response.status.value}; ${response.bodyAsText().length} bytes"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { request = "Ktor probe failed: ${failure.message}" }
            finally { client.close(); recordProbe("ktorProbe", request) }
        }
        MaterialTheme { Text("Music Party — foundation spike\n$request\n$bridgeEvent") }
    }
}
