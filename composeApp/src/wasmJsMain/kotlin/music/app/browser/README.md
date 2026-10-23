# Browser adapters (step 4)

Current operational instructions and release gates: [release verification](../../../../../../../docs/release-verification.md).
Historical step evidence below does not supersede the integrated implementation.

`BrowserAdapters.kt` implements the existing `QrScanner`, `BrowserPlayer`, and
`RefreshingAccessTokenProvider` boundaries. Shared domain/API contracts are unchanged.
Screens, composition root and playback coordination are wired by step 5; this document
describes the adapter package boundary.

- Create one `BrowserSession` per tab with the configured public client ID and exact
  registered `/oauth/spotify/callback` URI. `login()` redirects using Web Crypto
  randomness and S256. Call `handleCallback()` on startup before navigation; its
  result distinguishes ordinary navigation, authorization, denial and invalid state.
  Pending auth expires after ten minutes and is consumed before token exchange.
  Reload/replay cannot exchange it again; a failed/cancelled exchange needs a new login.
  Callback query and fragment are removed before processing, including on failures.
  HTTPS is required except explicit loopback IPs. Host callback rewrites remain an
  integration/hosting prerequisite; the development Python server has none.
- Tokens and pending verifier/state use sessionStorage, not localStorage. Token
  refresh and replacement refresh-token persistence use the shared API provider;
  pass the **same session** to the Ktor API and `SpotifyBrowserPlayer`. Refresh
  rejection clears tokens. Storage denial propagates; no insecure storage fallback.
  `SessionStorage` allows an injected token store. `logout()` invalidates in-flight
  callback exchanges and clears storage after any in-flight refresh finishes. Supply
  `onLogout` to dispose camera/player and invalidate account caches (those objects
  are owned by the composition root). SDK disconnect alone permits reconnect;
  dispose permanently cancels the adapter scope and removes its pagehide handler.
- `BrowserQrScanner(video)` requires a real video element. Start directly from a
  user action; when using coroutines, enter the call before dispatching (for example
  `CoroutineStart.UNDISPATCHED`). Video is muted/inline; rear camera and both inversion
  modes are requested using the existing qr-scanner/worker dependency. The first
  decode stops/relinquishes the stream before invoking the consumer. Further frames
  are suppressed until explicit start. Stop/pause release all tracks and clear
  srcObject; dispose also destroys the scanner/worker. Pending starts are invalidated
  on stop/dispose. Permission/no-camera failures have actionable messages; ordinary
  decode misses are ignored. The existing domain error vocabulary has no camera
  category, so scanner failures use InvalidQr with camera-specific messages.
- `SpotifyBrowserPlayer` loads the SDK on connect and shares one script-loading
  promise. One live local player is allowed per browser realm. `deviceState` retains
  the existing contract; additional `sdkState` exposes loaded, connected, ready,
  active, playing, activated and sanitized failure separately. Ready does not imply
  transfer/active/playing. SDK `activateElement()` runs synchronously on entry to
  `activateFromUserGesture()`; invoke it directly from Enable audio, before any
  coroutine dispatch/network/token work. The SDK must already have connected.
  Autoplay failure permits another explicit activation. Each token request invokes
  the shared provider anew. Disconnect removes listeners and invalidates late SDK,
  loader and token callbacks. `dispose()` additionally releases pagehide listener.
  A local SDK failure only changes local state/throws from connect; independent
  remote-device discovery/control through `SpotifyApi` remains available.

Implementation follows the [SDK reference](https://developer.spotify.com/documentation/web-playback-sdk/reference)
and [PKCE flow](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow).
No client secret, token logging, playlist-write scope or initial-token capture is used.
The only build change declares the existing pinned JSON serialization library in
composeApp; no npm version, worker setup, toolchain or shared contract changed.

## Verification (2026-10-23)

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:clean :music-domain:jvmTest :spotify-api:jvmTest :composeApp:compileKotlinWasmJs :composeApp:wasmJsBrowserDistribution
# Node is available from Gradle on this machine (system npm was absent):
export PATH="/Users/stefan/.gradle/nodejs/node-v24.16.0-darwin-arm64/bin:$PATH"
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install chromium)
npm test --prefix browser-tests
```

Gradle: **PASS**, including existing API token-refresh/concurrency/rejected-refresh
and domain tests. Production webpack retains the existing size/dynamic-import
warnings. An incremental compiler run after bridge declaration changes hit Kotlin
`WasmIrFileMetadata` ArrayIndexOutOfBoundsException; the clean build above passed.
Initial browser attempts failed due to absent system npm/Playwright Chromium; the
commands above install/use the prerequisites.

Browser regression: **PASS**, existing production Compose/Ktor/real QR worker and
synthetic camera smoke, plus `browser-tests/adapters.mjs`. New bridge tests run in
Chromium against the source JS module with only qr-scanner replaced by an injected
fake for deterministic duplicate/error callbacks; existing smoke separately exercises
real worker decoding. They verify real Web Crypto challenge generation, callback
adapter recreation with persisted pending state, state mismatch/denial/replay,
duplicate query/malformed/expired pending state, URL and logout storage cleanup,
actual synthetic-stream track release, repeated scans, pause/resume, camera errors,
start/disposal race, SDK states/activation/autoplay, token callback renewal, listener
cleanup, unavailable local SDK and shared script loading. They test JS bridge behavior;
Kotlin adapters are compilation-checked, while API provider refresh behavior is covered
by the API module tests. No claim of full UI or Wasm-to-SDK end-to-end integration.

Real physical camera/inverted physical cards, mobile Safari/Chrome, live OAuth and
refresh with the intended account, DRM/autoplay, local audible playback and remote
control remain **NOT RUN**. Credentials, registered callback/HTTPS phone origin and
real devices are absent. Existing playlist-access/account and product-policy gates in
`docs/feasibility.md` remain unresolved. Do not treat fakes as live acceptance.
