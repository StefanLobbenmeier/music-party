package music.plex.gateway

import io.ktor.client.*
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

private const val PRODUCT = "Music Party"
private val json = Json { ignoreUnknownKeys = true }

private data class PlexServer(val id: String, val name: String, val baseUrl: String, val token: String, val local: Boolean)

/** Local-only Plex gateway. Tokens stay in this process and are cleared when it exits. */
fun main() {
    val port = 8765
    val allowedOrigin = System.getenv("MUSIC_PARTY_APP_ORIGIN") ?: "http://127.0.0.1:8092"
    val (clientId, signingKey) = loadDeviceIdentity()
    val client = HttpClient(ClientCIO) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        install(ClientContentNegotiation) { json(json) }
    }
    var accountToken: String? = null
    var pinId: String? = null
    var pinCode: String? = null
    var servers: List<PlexServer> = emptyList()
    val stateLock = Mutex()

    embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        install(CORS) {
            allowHost(allowedOrigin.removePrefix("http://").removePrefix("https://"), schemes = listOfNotNull(
                allowedOrigin.substringBefore("://").takeIf { "://" in allowedOrigin }
            ))
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Range)
            exposeHeader(HttpHeaders.ContentRange)
            exposeHeader(HttpHeaders.AcceptRanges)
        }
        routing {
            get("/api/health") { call.respond(mapOf("ok" to true)) }
            get("/api/plex/auth/status") { call.respond(mapOf("authenticated" to stateLock.withLock { accountToken != null })) }
            post("/api/plex/auth/start") {
                val publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(signingKey.public.encoded.takeLast(32).toByteArray())
                val pinBody = buildJsonObject {
                    put("strong", true)
                    put("jwk", buildJsonObject {
                        put("kty", "OKP"); put("crv", "Ed25519"); put("x", publicKey)
                        put("kid", clientId); put("alg", "EdDSA")
                    })
                }
                val response = client.post("https://clients.plex.tv/api/v2/pins") {
                    header("Accept", "application/json")
                    header("X-Plex-Product", PRODUCT)
                    header("X-Plex-Client-Identifier", clientId)
                    contentType(ContentType.Application.Json)
                    setBody(pinBody)
                }
                if (!response.status.isSuccess()) return@post call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex PIN request failed (${response.status.value})"))
                val pin = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val id = pin["id"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex did not return a PIN id"))
                val code = pin["code"]?.jsonPrimitive?.content ?: return@post call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex did not return a PIN code"))
                stateLock.withLock { pinId = id; pinCode = code }
                val authUrl = URLBuilder("https://app.plex.tv/auth").apply {
                    fragment = "?clientID=${clientId.encodeURLParameter()}&code=${code.encodeURLParameter()}&context%5Bdevice%5D%5Bproduct%5D=Music%20Party"
                }.buildString()
                call.respond(mapOf("id" to id, "code" to code, "authUrl" to authUrl))
            }
            get("/api/plex/auth/poll") {
                val id = stateLock.withLock { pinId } ?: return@get call.respond(HttpStatusCode.Conflict, mapOf("error" to "No pending Plex sign-in"))
                val now = System.currentTimeMillis() / 1000
                val header = buildJsonObject { put("kid", clientId); put("alg", "EdDSA"); put("typ", "JWT") }
                val payload = buildJsonObject { put("aud", "plex.tv"); put("iss", clientId); put("iat", now); put("exp", now + 300) }
                val encoder = Base64.getUrlEncoder().withoutPadding()
                val unsigned = "${encoder.encodeToString(header.toString().toByteArray())}.${encoder.encodeToString(payload.toString().toByteArray())}"
                val signer = Signature.getInstance("Ed25519").apply { initSign(signingKey.private); update(unsigned.toByteArray()) }
                val deviceJwt = "$unsigned.${encoder.encodeToString(signer.sign())}"
                val response = client.get("https://clients.plex.tv/api/v2/pins/$id") {
                    url { parameters.append("deviceJWT", deviceJwt) }
                    header("Accept", "application/json")
                    header("X-Plex-Product", PRODUCT)
                    header("X-Plex-Client-Identifier", clientId)
                }
                if (!response.status.isSuccess()) return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex PIN check failed (${response.status.value})"))
                val result = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val token = result["authToken"]?.jsonPrimitive?.contentOrNull
                if (token != null) stateLock.withLock { accountToken = token; pinId = null; pinCode = null }
                call.respond(mapOf("authenticated" to (token != null)))
            }
            get("/api/plex/servers") {
                val token = stateLock.withLock { accountToken } ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Sign in to Plex first"))
                val response = client.get("https://clients.plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1") {
                    plexHeaders(clientId, token)
                }
                if (!response.status.isSuccess()) return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex server discovery failed (${response.status.value})"))
                val values = json.parseToJsonElement(response.bodyAsText()).jsonArray
                val found = values.mapNotNull { element ->
                    val item = element.jsonObject
                    val provides = item["provides"]?.jsonPrimitive?.content.orEmpty()
                    if ("server" !in provides) return@mapNotNull null
                    val connections = item["connections"]?.jsonArray.orEmpty().mapNotNull { c ->
                        val co = c.jsonObject
                        val uri = co["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        Triple(co["local"]?.jsonPrimitive?.booleanOrNull == true, co["relay"]?.jsonPrimitive?.booleanOrNull == true, uri)
                    }.sortedWith(compareBy<Triple<Boolean, Boolean, String>> { !it.first }.thenBy { it.second })
                    val connection = connections.firstOrNull { !it.second } ?: connections.firstOrNull() ?: return@mapNotNull null
                    val id = item["clientIdentifier"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val serverToken = item["accessToken"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    PlexServer(id, item["name"]?.jsonPrimitive?.content ?: "Plex server", connection.third.trimEnd('/'), serverToken, connection.first)
                }
                stateLock.withLock { servers = found }
                call.respond(found.map { mapOf("id" to it.id, "name" to it.name, "local" to it.local) })
            }
            get("/api/plex/libraries") {
                val server = stateLock.withLock { servers.firstOrNull { it.id == call.request.queryParameters["serverId"] } }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Choose a discovered Plex server"))
                val response = client.get("${server.baseUrl}/library/sections") { plexHeaders(clientId, server.token) }
                if (!response.status.isSuccess()) return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex library request failed (${response.status.value})"))
                val container = json.parseToJsonElement(response.bodyAsText()).jsonObject["MediaContainer"]?.jsonObject
                val libraries = container?.get("Directory")?.jsonArray.orEmpty().mapNotNull { raw ->
                    val dir = raw.jsonObject
                    if (dir["type"]?.jsonPrimitive?.content != "artist") return@mapNotNull null
                    mapOf("id" to dir["key"]?.jsonPrimitive?.content.orEmpty(), "title" to dir["title"]?.jsonPrimitive?.content.orEmpty())
                }
                call.respond(libraries)
            }
            get("/api/plex/tracks") {
                val server = stateLock.withLock { servers.firstOrNull { it.id == call.request.queryParameters["serverId"] } }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Choose a discovered Plex server"))
                val section = call.request.queryParameters["sectionId"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Select a music library"))
                val tracks = mutableListOf<JsonObject>()
                var start = 0
                while (true) {
                    val response = client.get("${server.baseUrl}/library/sections/${section.encodeURLPathPart()}/all") {
                        url { parameters.append("type", "10"); parameters.append("includeMedia", "1") }
                        header("X-Plex-Container-Start", start.toString())
                        header("X-Plex-Container-Size", "500")
                        plexHeaders(clientId, server.token)
                    }
                    if (!response.status.isSuccess()) return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex track query failed (${response.status.value})"))
                    val container = json.parseToJsonElement(response.bodyAsText()).jsonObject["MediaContainer"]?.jsonObject ?: break
                    val page = container["Metadata"]?.jsonArray.orEmpty().map { it.jsonObject }
                    tracks += page
                    start += page.size
                    val total = container["totalSize"]?.jsonPrimitive?.intOrNull ?: start
                    if (page.isEmpty() || start >= total) break
                }
                call.respond(tracks.mapNotNull { item ->
                    val ratingKey = item["ratingKey"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val media = item["Media"]?.jsonArray?.firstOrNull()?.jsonObject
                    val part = media?.get("Part")?.jsonArray?.firstOrNull()?.jsonObject
                    mapOf(
                        "id" to ratingKey,
                        "title" to item["title"]?.jsonPrimitive?.content.orEmpty(),
                        "artist" to item["grandparentTitle"]?.jsonPrimitive?.content.orEmpty(),
                        "album" to item["parentTitle"]?.jsonPrimitive?.content.orEmpty(),
                        "releaseDate" to item["originallyAvailableAt"]?.jsonPrimitive?.content,
                        "releasePrecision" to "DAY",
                        "partKey" to part?.get("key")?.jsonPrimitive?.content,
                    )
                })
            }
            get("/api/plex/audio/{serverId}/{partKey...}") {
                val server = stateLock.withLock { servers.firstOrNull { it.id == call.parameters["serverId"] } }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown Plex server"))
                val partKey = call.parameters.getAll("partKey")?.joinToString("/")?.removePrefix("/")
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing Plex audio path"))
                if (!partKey.startsWith("library/parts/") && !partKey.startsWith("/library/parts/"))
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid Plex audio path"))
                val range = call.request.headers[HttpHeaders.Range]
                val response = client.get("${server.baseUrl}/${partKey.removePrefix("/")}") {
                    plexHeaders(clientId, server.token)
                    if (range != null) header(HttpHeaders.Range, range)
                }
                if (!response.status.isSuccess() && response.status != HttpStatusCode.PartialContent)
                    return@get call.respond(HttpStatusCode.BadGateway, mapOf("error" to "Plex audio request failed (${response.status.value})"))
                response.headers[HttpHeaders.ContentRange]?.let { call.response.header(HttpHeaders.ContentRange, it) }
                response.headers[HttpHeaders.AcceptRanges]?.let { call.response.header(HttpHeaders.AcceptRanges, it) }
                response.headers[HttpHeaders.ContentLength]?.let { call.response.header(HttpHeaders.ContentLength, it) }
                call.respondOutputStream(ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "audio/mpeg"), response.status) {
                    response.bodyAsChannel().copyTo(this)
                }
            }
            post("/api/plex/logout") {
                stateLock.withLock { accountToken = null; pinId = null; pinCode = null; servers = emptyList() }
                call.respond(mapOf("ok" to true))
            }
        }
    }.start(wait = true)
}

private fun HttpRequestBuilder.plexHeaders(clientId: String, token: String) {
    header("Accept", "application/json")
    header("X-Plex-Product", PRODUCT)
    header("X-Plex-Client-Identifier", clientId)
    header("X-Plex-Token", token)
}

private fun loadDeviceIdentity(): Pair<String, KeyPair> {
    val directory = Path.of(System.getProperty("user.home"), ".music-party-plex")
    val file = directory.resolve("device.properties")
    val properties = Properties()
    val factory = KeyFactory.getInstance("Ed25519")
    if (Files.isRegularFile(file)) {
        Files.newInputStream(file).use(properties::load)
        val decoder = Base64.getDecoder()
        val pair = KeyPair(
            factory.generatePublic(X509EncodedKeySpec(decoder.decode(properties.getProperty("publicKey")))),
            factory.generatePrivate(PKCS8EncodedKeySpec(decoder.decode(properties.getProperty("privateKey"))))
        )
        return properties.getProperty("clientId") to pair
    }
    Files.createDirectories(directory)
    val clientId = UUID.randomUUID().toString()
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    properties.setProperty("clientId", clientId)
    properties.setProperty("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
    properties.setProperty("privateKey", Base64.getEncoder().encodeToString(pair.private.encoded))
    Files.newOutputStream(file).use { properties.store(it, "Music Party Plex device identity") }
    runCatching { Files.setPosixFilePermissions(file, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
    return clientId to pair
}
