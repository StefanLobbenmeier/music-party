# Compose integration (step 5)

The composition root is `composeApp/src/wasmJsMain/kotlin/music/app/Main.kt`.
Shared Compose screens, screen intents, API-to-domain mapping and playback coordination
are in `composeApp/src/commonMain/kotlin/music/app`. The added JVM target tests shared
logic without a browser; all foundation dependency versions remain pinned unchanged.

## Setup and behavior

Start with the on-screen setup. Supply a **public** Spotify client ID and exact
registered callback URI on this origin at `/oauth/spotify/callback`. No client ID is
bundled, and no secret is accepted. Setup and validated edition mappings use separate
localStorage keys; tokens/PKCE remain in the existing tab-scoped session adapter.
The example configuration remains a reference template, not implicit credentials.
Storage failures are visible. Settings can change the app setup after logging out.

The explicit “Try fake providers” button uses a silent fake device and synthetic
playlist entries. It never silently replaces a failed Spotify request. Paste
`https://www.hitstergame.com/de/00001` to exercise scan/play, pause, resume, seek and
next scan without credentials. Camera scanning still uses the real browser adapter.

Settings accept a JSON array of `editionKey`, playlist ID or URL, and label, using
the domain's existing validation. Save replaces the configuration; reset prepares
the reference defaults for saving. No cache, sorting or fallback playlists are used.
Every resolution uses the exact card position. Playlist access failures instruct
users to configure an owned/collaborated playlist with verified physical-card order.

Callback startup runs before session restoration/device startup. Denial, invalid
state and expired/replayed callbacks return to a recoverable sign-in screen. A
Cloudflare/Netlify-style `_redirects` entry serves the direct callback route. Other
hosts must configure the equivalent rewrite; Python's plain static server does not.
The browser-test server implements this same callback rewrite for production assets.

## Integration decisions

- Shared `SpotifyPlaybackCoordinator` implements the existing domain contract and
  uses the API for **all** playback commands, including the SDK device. SDK remains
  responsible for browser audio/device lifecycle and explicit user-gesture activation.
  The same BrowserSession supplies fresh tokens to SDK and REST.
- Browser SDK connection failure is a visible notice; remote devices remain usable.
  Connect/retry and Enable audio are separate actions. Enable audio enters the
  adapter synchronously with `CoroutineStart.UNDISPATCHED` to preserve activation.
- A usable device has a non-null ID and is unrestricted. Selection is explicit and
  retained; an unavailable selected device never silently redirects to another device.
  Initial default favors the active device, then the ready browser, then a remote.
- Readiness/transfer confirmation is bounded to ten seconds. Commands target the
  selected ID. Transfer and play/pause/resume/seek are reconciled against actual REST
  state; a 204 alone never reports success. The injected API/poll interval supports
  deterministic fakes. The browser Ktor client also has a 15-second request timeout.
- New command intent cancels the older request/wait and uses a mutex to prevent
  concurrent command issuance. Cancellation cannot undo an already accepted Spotify
  side effect. Each subsequent intent rechecks device/actual state. Scans also have
  generation protection; duplicate camera results are ignored until explicit restart.
- Background REST reconciliation runs every four seconds only while signed in and
  the document is visible. It shares the command mutex. Track/device loss reports
  actual state while the screen retains the resolved song for retry/another device.
- Navigation, next scan, logout and disposal stop the camera. Logout clears resolved
  state, coordinator devices/selection, token storage and SDK connection. Configuration
  remains independent. API failures are mapped to actionable domain/UI messages;
  cancellation is never converted into a banner.

No existing domain/API/browser adapter contract changed. One minimal browser-adapter
fix treats a clean callback path (no query/fragment) as ordinary navigation, allowing
session restoration on reload after a successful callback. Its regression is included
in the adapter browser tests. The composition root injects checked session storage
and translates JavaScript failures at the Wasm boundary into recoverable errors.
The foundation probe UI and unused `bridge.js` were replaced. CI's existing build
command now includes the new Compose JVM tests. This is implementation verification,
not step 6 release verification, deployment, phone/live acceptance or a release claim.

## Verification

Use SDKMAN's active Java for every Gradle command:

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:jvmTest :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution
export PATH="/Users/stefan/.gradle/nodejs/node-v24.16.0-darwin-arm64/bin:$PATH"
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install chromium)
npm test --prefix browser-tests
```

Results are recorded below after execution. Live OAuth, account/quota/allowlist,
playlist `/items` access and ordering, audible SDK/remote playback, physical cameras
and mobile Safari/Chrome remain blocked by absent app/account/device access and a
trusted HTTPS phone origin. Spotify's unresolved game-use restriction in
`docs/feasibility.md` remains a product gate. No bingo or game rounds are included.

### Executed results (2026-10-23)

- `./gradlew :composeApp:clean :composeApp:jvmTest :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution`: **PASS**. A prior incremental interop edit hit the already documented Kotlin `WasmIrFileMetadata` ArrayIndexOutOfBoundsException; clean compilation passed. No version changes were used.
- Final `./gradlew :composeApp:jvmTest :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution`: **PASS**, 26 JVM tests (7 integration, 5 domain, 14 API/token). Production webpack retains its existing three dynamic-import/bundle-size warnings.
- `npm ci --prefix browser-tests`: **PASS**, zero audit vulnerabilities. Existing installed Playwright Chromium was available; no browser installation was needed this run.
- `npm test --prefix browser-tests`: **PASS** against the production distribution. Covers pending setup and invalid config, manual QR errors and scan/play/pause/resume/seek/next, settings validation/reset, real QR worker decoding through a synthetic camera stream and release of all tracks, 390px viewport controls, logout, direct callback route, invalid callback cleanup and clean reload. Also exercises the actual Wasm BrowserSession/Ktor/domain/coordinator path with intercepted HTTP providers and deliberately blocked SDK, confirming remote commands target the remote device and logout removes credentials. Existing exhaustive adapter lifecycle tests also pass. No uncaught page errors.
- `git diff --check`: **PASS**.

The browser test uses accessibility locators to find canvas control bounds and sends
real pointer/keyboard/wheel events. It accounts for clipped semantics and Compose
render frames. It does not substitute DOM buttons for the shared Compose screens.
T3 preview snapshots failed twice with `Preview automation snapshot timed out after
15000ms.` The repository Playwright workflow was used for this verification.
