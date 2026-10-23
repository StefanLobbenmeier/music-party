package music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SpotifyApiTest {
    private val track = """{"id":"song","uri":"spotify:track:song","type":"track","external_urls":{"spotify":"https://open.spotify.com/track/song"},"name":"Song","artists":[{"id":"artist","name":"Artist"}],"album":{"id":"album","name":"Album","release_date":"1999","release_date_precision":"year"},"duration_ms":12345,"future":42}"""
    private val token = object : AccessTokenProvider { override suspend fun validAccessToken() = "access" }
    private fun body(request: HttpRequestData) = (request.body as io.ktor.http.content.TextContent).text
    private suspend fun failure(kind: ApiFailureKind, block: suspend () -> Unit): SpotifyApiFailure {
        val failure = assertFailsWith<SpotifyApiFailure> { block() }; assertEquals(kind, failure.kind); return failure
    }
    @Test fun completePlaybackAndMetadata() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertEquals("api.spotify.com", request.url.host)
            val path = request.url.encodedPath
            seen += "${request.method.value} $path"
            val response = when (path) {
                "/v1/playlists/list" -> """{"id":"list","name":"Playlist","snapshot_id":"snapshot"}"""
                "/v1/tracks/song" -> track
                "/v1/me/player/devices" -> """{"devices":[{"id":null,"name":"Restricted","is_active":false,"is_restricted":true}]}"""
                "/v1/me/player" -> if (request.method == HttpMethod.Get) """{"is_playing":true,"progress_ms":123,"device":null,"item":$track}""" else {
                    assertEquals(buildJsonObject { putJsonArray("device_ids") { add("device") } }, Json.parseToJsonElement(body(request)))
                    null
                }
                else -> {
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("device", request.url.parameters["device_id"])
                    if (path.endsWith("seek")) assertEquals("123", request.url.parameters["position_ms"])
                    if (path.endsWith("play") && request.body is io.ktor.http.content.TextContent) {
                        assertEquals("spotify:track:song", Json.parseToJsonElement(body(request)).jsonObject["uris"]!!.jsonArray[0].jsonPrimitive.content)
                    }
                    null
                }
            }
            if (response == null) respond("", HttpStatusCode.NoContent) else respond(response)
        }) { expectSuccess = true }
        val api = KtorSpotifyApi(client, token)
        assertEquals("snapshot", api.playlist("list").snapshotId)
        val song = api.track("song")
        assertEquals("song", song.id); assertEquals("Song", song.name)
        assertEquals("Artist", song.artists.single().name); assertEquals("year", song.album!!.releaseDatePrecision)
        assertEquals(12345, song.durationMs)
        assertTrue(api.devices().single().restricted)
        assertEquals(123, api.playbackState()!!.positionMs)
        api.transfer("device"); api.play("spotify:track:song", "device"); api.pause("device"); api.resume("device"); api.seek(123, "device")
        assertEquals(listOf("GET /v1/playlists/list", "GET /v1/tracks/song", "GET /v1/me/player/devices",
            "GET /v1/me/player", "PUT /v1/me/player", "PUT /v1/me/player/play", "PUT /v1/me/player/pause",
            "PUT /v1/me/player/play", "PUT /v1/me/player/seek"), seen)
        client.close()
    }
    @Test fun controlsStatefulMockPlayer() = runTest {
        var device: String? = null
        var playing = false
        var position = 0L
        var currentTrack = "null"
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond("""{"device":{"id":"$device","name":"Remote","is_active":true},"is_playing":$playing,"progress_ms":$position,"item":$currentTrack}""")
            } else {
                when (request.url.encodedPath) {
                    "/v1/me/player" -> device = Json.parseToJsonElement(body(request)).jsonObject["device_ids"]!!.jsonArray[0].jsonPrimitive.content
                    "/v1/me/player/play" -> { playing = true; if (request.body is io.ktor.http.content.TextContent) currentTrack = track }
                    "/v1/me/player/pause" -> playing = false
                    "/v1/me/player/seek" -> position = request.url.parameters["position_ms"]!!.toLong()
                    else -> error("Unexpected command")
                }
                respond("", HttpStatusCode.NoContent)
            }
        })
        val api = KtorSpotifyApi(client, token)
        api.transfer("remote"); assertEquals("remote", api.playbackState()!!.device!!.id)
        api.play("spotify:track:song", "remote"); assertTrue(api.playbackState()!!.playing)
        assertEquals("song", api.playbackState()!!.item!!.id)
        api.pause("remote"); assertFalse(api.playbackState()!!.playing)
        api.resume("remote"); assertTrue(api.playbackState()!!.playing)
        api.seek(4000, "remote"); assertEquals(4000, api.playbackState()!!.positionMs)
        client.close()
    }
    @Test fun pagingPreservesEveryPosition() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals("/v1/playlists/list/items", request.url.encodedPath)
            assertEquals("2", request.url.parameters["limit"])
            val offset = request.url.parameters["offset"]!!.toInt(); calls++
            respond(when (offset) {
                0 -> """{"offset":0,"total":6,"next":"https://evil.invalid/leak","items":[{"item":$track},{"item":null}]}"""
                2 -> """{"offset":2,"total":6,"next":"next","items":[{"item":{"type":"episode","id":"episode"}},{"is_local":true,"item":$track}]}"""
                4 -> """{"offset":4,"total":6,"next":null,"items":[{"item":{"type":"track","is_playable":false}},{"item":{"type":"track","id":null}}]}"""
                else -> error("Infinite paging")
            })
        })
        val items = KtorSpotifyApi(client, token).allPlaylistItems("list", 2).toList()
        assertEquals(3, calls); assertEquals(6, items.size)
        assertEquals("song", items[0].item!!.id); assertNull(items[1].item)
        assertEquals("episode", items[2].type); assertTrue(items[3].local)
        assertTrue(items.drop(1).all { it.item == null }); client.close()
    }
    @Test fun emptyAndInvalidPagesTerminate() = runTest {
        for (json in listOf(
            """{"offset":0,"total":2,"next":"next","items":[]}""",
            """{"offset":1,"total":2,"items":[]}""")) {
            var calls = 0
            val client = HttpClient(MockEngine { calls++; respond(json) })
            failure(ApiFailureKind.InvalidResponse) { KtorSpotifyApi(client, token).allPlaylistItems("list").toList() }
            assertEquals(1, calls); client.close()
        }
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) })
        assertNull(KtorSpotifyApi(client, token).playbackState())
        failure(ApiFailureKind.InvalidResponse) { KtorSpotifyApi(client, token).devices() }; client.close()
        val empty = HttpClient(MockEngine { respond("""{"offset":10,"total":0,"items":[]}""") })
        failure(ApiFailureKind.NotFound) { KtorSpotifyApi(empty, token).playlistItemAt("list", 10) }; empty.close()
    }
    @Test fun failuresAndRetryBoundaries() = runTest {
        for ((status, kind, expected) in listOf(
            Triple(401, ApiFailureKind.LoginRequired, 1), Triple(403, ApiFailureKind.Forbidden, 1),
            Triple(404, ApiFailureKind.NotFound, 1), Triple(429, ApiFailureKind.Throttled, 1),
            Triple(503, ApiFailureKind.Server, 3), Triple(400, ApiFailureKind.InvalidResponse, 1))) {
            var calls = 0; val waits = mutableListOf<Long>()
            val client = HttpClient(MockEngine { calls++; respond("{}", HttpStatusCode.fromValue(status), headersOf("Retry-After", "17")) })
            val error = failure(kind) { KtorSpotifyApi(client, token, { waits += it }).devices() }
            assertEquals(expected, calls); assertEquals(status, error.httpStatusCode)
            if (status == 429) assertEquals(17, error.retryAfterSeconds)
            assertEquals(if (status == 503) listOf(250L, 500L) else emptyList(), waits)
            calls = 0
            failure(kind) { KtorSpotifyApi(client, token).pause("device") }; assertEquals(1, calls)
            client.close()
        }
        var calls = 0
        val client = HttpClient(MockEngine { calls++; throw javaFreeTransportError() })
        failure(ApiFailureKind.Offline) { KtorSpotifyApi(client, token, {}).devices() }; assertEquals(3, calls)
        calls = 0; failure(ApiFailureKind.Offline) { KtorSpotifyApi(client, token).resume("device") }; assertEquals(1, calls)
        client.close()
    }
    private fun javaFreeTransportError() = IllegalStateException("disconnected")
    @Test fun pagingBudgetStopsExpandingTotals() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            val offset = request.url.parameters["offset"]
            respond("""{"offset":$offset,"total":2147483647,"next":"next","items":[{"item":null}]}""")
        })
        failure(ApiFailureKind.InvalidResponse) { KtorSpotifyApi(client, token).allPlaylistItems("list", 1, 3).toList() }
        assertEquals(3, calls); client.close()
    }
    @Test fun transientReadRecovers() = runTest {
        var calls = 0; val waits = mutableListOf<Long>()
        val client = HttpClient(MockEngine {
            if (++calls == 1) respond("", HttpStatusCode.BadGateway) else respond("""{"devices":[]}""")
        })
        assertTrue(KtorSpotifyApi(client, token, { waits += it }).devices().isEmpty())
        assertEquals(2, calls); assertEquals(listOf(250L), waits); client.close()
    }
    @Test fun cancellationPropagates() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("cancel") })
        assertFailsWith<CancellationException> { KtorSpotifyApi(client, token).devices() }; client.close()
        val retryClient = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
        assertFailsWith<CancellationException> { KtorSpotifyApi(retryClient, token, { throw CancellationException() }).devices() }
        retryClient.close()
    }
}
