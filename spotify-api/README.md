# Spotify REST library

Kotlin Multiplatform (JVM + browser Wasm), Ktor, serialization and coroutines only.
No Compose, music-domain, browser SDK or storage dependency. The caller owns the
injected `HttpClient` and closes it. Do not install automatic retry or auth plugins:
this library controls retries and bearer headers. `expectSuccess = true` is supported.

```kotlin
val transport = SpotifyTokenTransport(httpClient, clientId)
val provider = SpotifyTokenProvider(transport, restoredTokens,
    onTokensChanged = { tokens -> sessionStore.save(tokens) })
// Browser adapter validates state and consumes the one-time PKCE verifier first:
provider.setTokens(transport.exchange(code, redirectUri, verifier))
val api = KtorSpotifyApi(httpClient, provider)
api.transfer(deviceId)
api.play("spotify:track:someId", deviceId)
api.pause(deviceId)
api.resume(deviceId)
api.seek(12_000, deviceId)
val state = api.playbackState() // null for 204; commands alone do not confirm playback
```

Use one provider per session for both REST and SDK token callbacks. The provider
refreshes 30 seconds before expiry, serializes refreshes, rotates refresh tokens,
and retains the previous refresh token when omitted by Spotify. Inject a clock,
initial session and storage callback for browser integration/tests. `setTokens(null)`
clears a session. Rejected refresh (400/401) clears stored tokens; transient refresh
failure retains them. Storage callbacks run under the mutex and must not reenter it.
A plain `AccessTokenProvider` also works, but cannot refresh on 401; implementing
`RefreshingAccessTokenProvider` opts into one refresh/retry per REST operation.
No client secret, automatic token-endpoint retry, token logging or URL tokens.

Current `/playlists/{id}/items` and `item` schema only: no legacy `/tracks` fallback.
`playlistItems` requests one page (limit 1–50); `playlistItemAt` requests exactly
one zero-based position and throws NotFound only if no position exists. Null,
episode, local, restricted and explicitly unplayable items remain in place with a
null `PlaylistItem.item`; type/local flags remain available. Missing stable track
identity/link also produces a null item. Playback episodes remain representable as
playing/progress/device with a null track. Optional metadata preserves album release
date precision; this is not a claim about original song release year.

`allPlaylistItems` is a cold Flow on `KtorSpotifyApi`; it never follows server-provided
URLs with credentials. It advances by actual item count while preserving order,
rejects offset mismatches and stalled/inconsistent pages, and caps traversal at
10,000 pages (caller configurable). Cancellation stops requests/backoff/iteration.
`next`, `offset`, and `total` remain available on individual pages.

Typed failures include LoginRequired, Forbidden, NotFound, Throttled, Offline,
Server and InvalidResponse. HTTP failures carry `httpStatusCode` and Spotify's
numeric `Retry-After` in `retryAfterSeconds`. 429 is surfaced immediately for the UI
to handle. Reads retry transport failures and HTTP 500/502/503/504 at most twice
(250 ms then 500 ms). Commands never retry transient failures or rate limits;
only a 401 can trigger their one immediate token refresh/retry. No long-delayed
playback replay. Malformed JSON is InvalidResponse; unknown fields are ignored.

Verified against MockEngine only; real account, playlist access/schema, Premium,
SDK device and policy gates remain pending as recorded in `docs/feasibility.md`.
Current reference sources:
- https://developer.spotify.com/documentation/web-api/reference/get-playlists-items
- https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback
- https://developer.spotify.com/documentation/web-api/tutorials/refreshing-tokens

Checks (SDKMAN active Java):

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :spotify-api:jvmTest :spotify-api:compileKotlinWasmJs :spotify-api:compileTestKotlinWasmJs :music-domain:jvmTest :composeApp:wasmJsBrowserDistribution
```
