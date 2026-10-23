# Foundation and feasibility

Current operational instructions and release gates: [release verification](release-verification.md).
Historical step evidence below does not supersede the integrated implementation.

Foundation scope: module scaffold, fixed boundaries, public configuration template and
browser bridge spike. No later work package, React merge or deployment is included.
Reference: `origin/v1` at `23297a71b1471c1870d1c1028384a0a36b7481e7`.

## Toolchain and reproducible commands

Pinned: Kotlin/compiler plugin 2.3.20, Compose Multiplatform 1.10.3, Gradle 9.3.1,
Ktor 3.4.1, coroutines 1.10.2, serialization 1.10.0, qr-scanner 1.4.2.
The wrapper includes the official distribution SHA-256. Gradle supplies Node/Yarn;
there is no system Node/npm prerequisite. The generated Yarn lock must be committed.
Local SDKMAN `current` resolves to JBR OpenJDK 25.0.2. Gradle requires 9.1+ for
Java 25; Kotlin 2.3.20 supports Gradle through 9.3.0 in its published compatibility
range (9.3.1 is the patch release used here). JVM library bytecode targets Java 17;
the build/test runtime is Java 25. CI uses Temurin 25.

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# Separate terminal, after the production build:
python3 -m http.server 8080 --bind 127.0.0.1 --directory composeApp/build/dist/wasmJs/productionExecutable
```

Open `http://127.0.0.1:8080`. Do not use the dev server as evidence that production
assets load. Browser evidence below is recorded against the static production artifact.

References: [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html),
[Compose compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html),
[Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html),
[Ktor browser engines](https://ktor.io/docs/client-engines.html).

## Fixed boundaries for following packages

- `music-domain/Contracts.kt`: provider-neutral card/song/configuration, resolver,
  typed domain failures, scanner, browser device lifecycle and playback coordinator.
  No DOM, Compose or Spotify transport dependency. `PlaybackState` reports reconciled
  state, never success merely because a command returned 204.
- `spotify-api/SpotifyApi.kt`: independent KMP REST boundary, token provider and API
  failure vocabulary. The implementation accepts an injected Ktor `HttpClient` and
  `AccessTokenProvider`; it has no domain/UI dependency. Tokens/refresh transport,
  serialization DTO annotations and endpoint implementation belong to package 2.
  `playlistItemAt` uses zero-based offsets and preserves null/local/episode positions.
  Paging retains `next`, `offset` and `total`; no sorting/filtering or blind legacy fallback.
  `playbackState()` returns null for an empty 204; restricted/null-ID devices remain
  representable. `SpotifyApiFailure` maps to domain failures in the composition root.
- `composeApp`: owns the JS bridge and composition root. Scanner callbacks are
  recoverable failures, cancellation always propagates from suspending operations.
  Device IDs are explicit on every playback command. The browser player requests a
  fresh token from the shared provider when the SDK asks; never capture an initial token.
- `config.example.json` retains all seven exact edition mappings with a blank client
  ID. This template is fetched only to prove Ktor/browser networking; runtime validation,
  configuration overrides and auth belong to later packages. No old application ID is reused.

The bridge is a spike, not the completed lifecycle adapter: no OAuth, SDK Player
instance, session storage, full error mapping, navigation or scan-to-play coordination
is implemented. The fake event exercises JS-to-Kotlin callback delivery only.

## External acceptance gates

No application client ID, account, access token, registered callback, quota-mode
record or test-device details were supplied. Therefore all seven default playlist
access tests are **NOT RUN** (same credential prerequisite for each); physical-card
ordering is unverified. No account compatibility or playlist accessibility is claimed.
Actual quota mode and `/items` response schema remain unknown. Do not infer access
from following public playlists or v1's previous behavior.

Live checklist for the actual intended application:

1. Record client ID (public), quota mode, allowlisted test account, owner Premium
   status, test account Premium status, and exact registered redirect URI. Never record tokens.
2. Authorize with PKCE; verify state, refresh and callback reload, then probe each of
   the seven configured playlist IDs using current `/playlists/{id}/items`. Record
   status/schema and first/middle/last card ordering. A 403 requires an actionable
   inaccessible-playlist result, not a proxy or automatic replacement. Only explicitly
   configured owned/collaborated playlists with verified full order are acceptable alternatives.
3. Register SDK readiness before loading the script, create `music-party`, obtain a
   real device ID, activate audio from a user gesture, and confirm audible playback
   plus state reconciliation. Repeat remote-device control separately.
4. Test camera start/decode (including inverted QR), release and worker loading on
   real mobile Safari/Chrome over trusted HTTPS, plus desktop Chrome/Firefox.
   Require WasmGC-capable browsers; DRM/autoplay/camera support is independent of
   Wasm support. No supported-version claim is established by this desktop spike.

Keep callback `/oauth/spotify/callback`; local redirects use `127.0.0.1`, not
`localhost`. Static hosting must rewrite direct callback requests to `index.html`.
The Python static server above does not provide that rewrite (callback 404 is expected).
Serve `.wasm` as `application/wasm`, JavaScript with a JS MIME type, and deploy all
worker/chunk assets with the bundle. Host at origin root for this spike; base-path
hosting is not verified. Do not cache `index.html` as immutable across deployments.
No hosting/deployment configuration is activated in this work package.

Start later auth scope review with `streaming`, `user-read-playback-state`,
`user-modify-playback-state`, `playlist-read-private`, `playlist-read-collaborative`,
and SDK-required `user-read-email`/`user-read-private`; confirm against current SDK
requirements for the actual app. No playlist-write scope or client secret is appropriate.

[Playlist access reference](https://developer.spotify.com/documentation/web-api/reference/get-playlists-items),
[2026 migration](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide),
[quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes),
[SDK setup](https://developer.spotify.com/documentation/web-playback-sdk/tutorials/getting-started),
[redirect requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri).

Spotify's [Developer Policy section III](https://developer.spotify.com/policy) prohibits
creating games, including trivia quizzes. This is an unresolved product/platform gate
for bingo and potentially this Hitster companion. Technical success establishes no
permission. Resolve the permitted integration before shipping a game; future game
logic stays provider-independent.

## Execution evidence

Executed 2026-10-23 locally on macOS arm64:

- `:music-domain:jvmTest`: PASS (configuration serialization contract test).
- `:spotify-api:jvmTest`: NO-SOURCE, expected for an interface-only scaffold;
  `compileKotlinJvm` and `compileKotlinWasmJs` both passed.
- `:composeApp:wasmJsBrowserDistribution`: PASS, compiling all three Wasm modules.
- Static production page in headless Chromium: Compose canvas rendered, Ktor GET
  `/config.example.json` returned 200 and 420 bytes. Both hashed Wasm assets loaded.
- Real QR worker chunk (`69.js`) fetched successfully and decoded the bundled fixture
  to `https://www.hitstergame.com/de/00001`. The callback updated Compose text.
- Fake SDK callback reached Kotlin; the actual Spotify script also loaded and invoked
  readiness, exposing `Spotify.Player` as a function. No authenticated Player instance,
  SDK device readiness, DRM, autoplay or audible playback has been proven.
- Real-camera attempt: `Camera not found.` This environment has no exposed camera.
  Physical camera scanning, mobile Safari/Chrome and desktop Firefox remain NOT RUN.
- T3 collaborative snapshot failed twice with
  `Preview automation snapshot timed out after 15000ms.` Used local Playwright/Chromium
  after those failures. Its screenshot confirmed the rendered Compose/Ktor status.

The credential-free production regression is `browser-tests/smoke.mjs`. It verifies
Wasm canvas rendering, the Ktor response, QR decoding through an actual worker,
JS-to-Kotlin events, and synthetic camera start/release. Synthetic video does not
establish physical-camera support. CI runs this check without Spotify/network SDK
access. To reproduce (Node 24+ and Python 3 required for this optional browser check):

```sh
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install --with-deps chromium)
npm test --prefix browser-tests
# Optional external SDK-script probe, still without account/device authentication:
LIVE_SDK_PROBE=1 npm test --prefix browser-tests
```

`qr-fixture.png` encodes the URL above; generated once using Python qrcode 8.2 and
Pillow 11.3.0. These Python packages are not build/runtime requirements.

Production output is `composeApp/build/dist/wasmJs/productionExecutable`:
`index.html`, configuration/fixture, bundled JS, dynamically imported QR worker JS,
and two hashed Wasm binaries. The raw `bridge.js` copy is an input artifact and is not
loaded as an extra script. Current uncompressed output includes about 8.24 MiB Skiko
Wasm, 1.65 MiB app Wasm and 597 KiB main JS; configure compression before release.
Webpack reports bundle-size warnings and a dynamic dependency-expression warning
from generated Wasm loading. The actual production paths were exercised successfully.
Node tooling emits legacy URL/punycode and peer-dependency warnings; these did not
prevent the pinned build. Compose Gradle dependency accessors are deprecated but work.

Live acceptance remains **PENDING**: actual app/account quota and allowlisting,
playlist ownership/access/schema/order, trusted HTTPS phone camera testing,
real SDK device/audible playback, remote controls and permitted product usage.

Final automated production smoke result: **PASS** with `LIVE_SDK_PROBE=1`, including
synthetic camera stream acquisition and release (video `srcObject` cleared), worker
fixture decode, Kotlin callback observations and no uncaught page errors. The CI variant
omits the live SDK probe so it requires no Spotify service or credentials.

GitHub Linux/Temurin 25 CI also passed the full Gradle build and credential-free
production browser smoke. Its initial run failed only while archiving the bundle:
`Artifact storage quota has been hit.` Optional artifact upload is now non-blocking;
compilation/tests/browser checks remain required. Build output is reproducible locally,
but downloading an archived CI bundle depends on the repository's storage quota.
