package music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.request.forms.FormDataContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TokensTest {
    private val response = """{"access_token":"fresh","token_type":"Bearer","expires_in":3600,"refresh_token":"rotated","future":true}"""
    @Test fun pkceExchangeAndRefreshForms() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://accounts.spotify.com/api/token", request.url.toString())
            assertNull(request.headers[HttpHeaders.Authorization])
            val form = (request.body as FormDataContent).formData
            assertEquals("client", form["client_id"]); assertNull(form["client_secret"])
            if (calls++ == 0) {
                assertEquals("authorization_code", form["grant_type"])
                assertEquals("code +", form["code"]); assertEquals("verifier", form["code_verifier"])
                assertEquals("http://127.0.0.1/callback", form["redirect_uri"])
                respond(response)
            } else {
                assertEquals("refresh_token", form["grant_type"]); assertEquals("rotated", form["refresh_token"])
                respond("""{"access_token":"second","token_type":"Bearer","expires_in":3600}""")
            }
        })
        val transport = SpotifyTokenTransport(client, "client", { 1000 })
        val tokens = transport.exchange("code +", "http://127.0.0.1/callback", "verifier")
        assertEquals(3601000, tokens.expiresAtMillis)
        assertEquals("rotated", transport.refresh(tokens.refreshToken!!).refreshToken)
        client.close()
    }
    @Test fun concurrentExpiryRefreshAndRotation() = runTest {
        var refreshes = 0; var stored: SpotifyTokens? = null
        val client = HttpClient(MockEngine { refreshes++; delay(10); respond(response) })
        val provider = SpotifyTokenProvider(SpotifyTokenTransport(client, "client", { 1000 }),
            SpotifyTokens("expired", "refresh", 0), { 1000 }, onTokensChanged = { stored = it })
        val results = coroutineScope { List(20) { async { provider.validAccessToken() } }.awaitAll() }
        assertTrue(results.all { it == "fresh" }); assertEquals(1, refreshes); assertEquals("rotated", stored!!.refreshToken)
        assertEquals("fresh", provider.refreshAfterUnauthorized("expired")); assertEquals(1, refreshes)
        provider.setTokens(null)
        assertFailsWith<SpotifyApiFailure> { provider.validAccessToken() }; assertNull(stored)
        client.close()
    }
    @Test fun concurrentUnauthorizedRequestsRefreshOnce() = runTest {
        var refreshes = 0; var rejected = 0; var successes = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "accounts.spotify.com") { refreshes++; delay(10); respond(response) }
            else if (request.headers[HttpHeaders.Authorization] == "Bearer old") {
                rejected++; delay(5); respond("{}", HttpStatusCode.Unauthorized)
            } else { successes++; respond("""{"devices":[]}""") }
        })
        val provider = SpotifyTokenProvider(SpotifyTokenTransport(client, "client", { 0 }), SpotifyTokens("old", "refresh", 999999), { 0 })
        val api = KtorSpotifyApi(client, provider)
        coroutineScope { List(10) { async { api.devices() } }.awaitAll() }
        assertEquals(1, refreshes); assertEquals(10, rejected); assertEquals(10, successes)
        client.close()
    }
    @Test fun rejectedRefreshClearsSessionAndDoesNotLoop() = runTest {
        var calls = 0; var cleared = false
        val client = HttpClient(MockEngine { calls++; respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) })
        val provider = SpotifyTokenProvider(SpotifyTokenTransport(client, "client"), SpotifyTokens("old", "refresh", 0), onTokensChanged = { cleared = it == null })
        repeat(5) { assertEquals(ApiFailureKind.LoginRequired, assertFailsWith<SpotifyApiFailure> { provider.validAccessToken() }.kind) }
        assertEquals(1, calls); assertTrue(cleared); client.close()
    }
    @Test fun repeatedUnauthorizedStopsAfterOneRetry() = runTest {
        var calls = 0; var refreshes = 0
        val client = HttpClient(MockEngine { calls++; respond("", HttpStatusCode.Unauthorized) })
        val provider = object : RefreshingAccessTokenProvider {
            override suspend fun validAccessToken() = "old"
            override suspend fun refreshAfterUnauthorized(rejectedToken: String): String { refreshes++; return "new" }
        }
        assertEquals(ApiFailureKind.LoginRequired, assertFailsWith<SpotifyApiFailure> { KtorSpotifyApi(client, provider).pause("device") }.kind)
        assertEquals(2, calls); assertEquals(1, refreshes); client.close()
    }
    @Test fun transientRefreshAndCancellationDoNotEraseSession() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            when (++calls) {
                1 -> respond("", HttpStatusCode.ServiceUnavailable)
                2 -> throw CancellationException()
                else -> respond(response)
            }
        })
        val provider = SpotifyTokenProvider(SpotifyTokenTransport(client, "client", { 0 }), SpotifyTokens("old", "refresh", 0), { 0 })
        assertEquals(ApiFailureKind.Server, assertFailsWith<SpotifyApiFailure> { provider.validAccessToken() }.kind)
        assertFailsWith<CancellationException> { provider.validAccessToken() }
        assertEquals("fresh", provider.validAccessToken()); assertEquals(3, calls); client.close()
    }
}
