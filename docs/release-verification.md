# Release verification and operations (step 6)

**The GitHub Pages build is a technical preview.** Spotify's [Developer Policy, III.2](https://developer.spotify.com/policy)
prohibits games including trivia. Applicability to this companion remains unresolved;
technical verification grants no permission. A provider-neutral Bingo engine and
Plex-only browser/gateway flow are implemented. The local Ktor gateway uses Plex PIN/JWT
authentication, keeps tokens in memory, discovers servers/libraries, lists tracks and
proxies ranged audio. Spotify remains exclusive to QR scan mode. Live Plex account/server
access and phone/browser local-network acceptance are still pending. Review
privacy policy, branding, and streaming metadata/cover-art requirements before public use;
the current track ID/link display is not a completed streaming compliance review.

This document supersedes historical foundation probe descriptions in
[feasibility](feasibility.md); [integration](step-5-integration.md) describes current wiring.

## Reproduce without Spotify credentials

From the repository root, Python 3 and Node 24/npm are required for browser tests.
Gradle supplies its own Node/Yarn for Kotlin builds, but does not put npm on your shell PATH.
Local Java commands must use SDKMAN's current symlink:

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:clean :composeApp:jvmTest :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution
# If no system Node/npm, use the Node installed by the preceding Gradle build on this Mac:
export PATH="/Users/stefan/.gradle/nodejs/node-v24.16.0-darwin-arm64/bin:$PATH"
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install chromium)
# On Linux install OS prerequisites too: npx playwright install --with-deps chromium
npm test --prefix browser-tests
# Separate terminal, from repo root, for interactive production preview:
python3 browser-tests/server.py
```

Open `http://127.0.0.1:8092`. This preview reads the **built** `_redirects`, including
GET/HEAD callback requests. Plain `python3 -m http.server` has no callback rewrite.
For development use `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` with the same Java env;
register the actual loopback port separately. If incremental Wasm compilation hits
`WasmIrFileMetadata` ArrayIndexOutOfBoundsException, rerun the clean command above.

CI `.github/workflows/verify.yml` runs all three JVM suites, production Wasm build,
locked npm install and the complete production Chromium/adapter regression. No secrets
or Spotify credentials are configured or required; tests intercept Spotify API traffic
and fake the SDK. Optional external SDK probes are not release evidence. Build dependency
and browser downloads still require internet access. CI uses Temurin 25, Node 24, Python
on Ubuntu; local Java uses SDKMAN. Version pins are in `gradle/libs.versions.toml`,
wrapper properties, Kotlin Yarn lock and `browser-tests/package-lock.json`.

## Spotify setup and live procedure

1. In the Spotify developer dashboard create/select the intended app, inspect its
   actual quota mode and record owner/test-account Premium status. Development mode
   currently requires a Premium owner and generally permits five allowlisted users;
   inspect the app rather than assume grandfathered limits. Add each test user to
   the allowlist. See [quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).
2. Register the exact callback `http://127.0.0.1:8092/oauth/spotify/callback` locally;
   use `https://YOUR-ORIGIN/oauth/spotify/callback` for any non-loopback origin.
   `localhost` is not accepted. The app enforces same origin and this exact pathname,
   without query/fragment. See [redirect requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri).
3. Enter the **public** 32-hex-character client ID and callback in on-screen setup.
   `config.example.json` is reference documentation, not runtime provisioning.
   Setup and playlist overrides persist in localStorage. OAuth tokens and one-time
   PKCE state/verifier use tab sessionStorage; logout clears credentials, device and
   resolved-song state, but retains setup/playlist configuration. Site data removal
   clears configuration; revoke the application in Spotify to revoke consent.
4. Sign in and consent. The actual bridge requests `streaming`,
   `user-read-playback-state`, `user-modify-playback-state`, `playlist-read-private`,
   `playlist-read-collaborative`, `user-read-email`, `user-read-private`.
   These cover SDK/profile, device/control and private/collaborative playlist reads.
   No playlist-write scope or client secret is used. Never paste tokens, authorization
   codes, secrets or storage dumps into issues/logs. Host access logs must redact/drop
   callback query strings; the local preview logs no requests.
5. Verify callback cleanup, clean reload, expiry/refresh and logout with the intended
   account. Connect browser player, wait for readiness, click **Enable audio**, select
   its device, and confirm audible playback plus reconciled controls. Test a remote
   Spotify Connect device separately, including local SDK failure and device loss.
   SDK streaming requires Premium; account, DRM and autoplay errors are independent
   of Wasm support. A 204 command response alone is not playback acceptance.

Never silently substitute a playlist. All seven defaults are reference IDs, including
one labelled bingo; that label is not a bingo mode. Current REST uses
`/playlists/{id}/items` and `item`, without legacy `/tracks` fallback. The current
[playlist-items reference](https://developer.spotify.com/documentation/web-api/reference/get-playlists-items)
restricts reads to owned/collaborated playlists; following a public playlist does not
establish access. For **each** configured edition record HTTP status/schema and compare
physical first/middle/last cards, then verify the complete mapping before claiming
support. An explicitly configured accessible replacement must retain verified full
card order. Card N means offset N−1: no sorting, shuffling, filtering or deduplication;
null/local/episode/unavailable positions fail without shifting. Playlist edits invalidate
alignment. Recheck settings/cards after any edit; no lookup cache is used.

## Static hosting

The GitHub Pages workflow publishes this directory after pushes to `main`:
`composeApp/build/dist/wasmJs/productionExecutable`. Preserve every JS chunk/QR worker,
Wasm binary, `index.html`, `_redirects`, `_headers`, and reference assets. No server,
secret, Node runtime or Spotify token belongs in the artifact. The workflow sets the
document base to `/music-party/` and supplies `404.html` as the Pages fallback, allowing
the OAuth callback route to load the app while keeping its query string.

Cloudflare Pages or Netlify static hosting can consume the shipped rules:

```text
/oauth/spotify/callback /index.html 200
```

This is an internal rewrite preserving the callback query, not a 301/302 redirect.
For another host configure the equivalent exact path GET/HEAD rewrite; missing assets
must stay 404 instead of returning HTML. The Pages deployment needs no provider token.
The checked `_headers` requests revalidation for all assets (including index and chunks),
no MIME sniffing, no referrer, and same-origin camera permission. On other hosts apply
equivalent headers. Serve Wasm as `application/wasm`, JS as JavaScript, enable gzip/Brotli,
and use trusted HTTPS. No strict CSP is claimed: SDK/DRM and Wasm policy need host testing.

Local tests check shipped rules, callback body/status/content type (GET and HEAD with
synthetic query), clean callback reload, missing asset 404, every JS/Wasm asset MIME
and nonempty body, real QR worker load, and actual Compose rendering. This verifies
artifact behavior via an equivalent local server, **not** an unprovisioned host/CDN.
After future provisioning repeat these checks at the real origin before live OAuth.

Foundation recorded `Artifact storage quota has been hit.` Optional upload remains
non-blocking; required compilation/tests/browser checks cannot be skipped. Upload now
uses three-day retention. A successful upload step does not prove quota recovery
because `continue-on-error` masks archival failures; inspect logs/artifact listing.
Freeing old artifacts or changing quota is repository-owner work, not a release test
waiver. Locally rebuilding the directory above is the reproducible fallback.

## Browser/device acceptance record — 2026-10-23

Execution results are recorded after the final verification run below. No live app ID,
account credentials, trusted phone HTTPS origin or physical test phone/camera were
provided. Do not interpret a responsive viewport or synthetic stream as a phone test.

- Desktop macOS arm64 Chromium: automated production UI, real Wasm/Ktor composition,
  intercepted remote commands, synthetic camera/real worker decode, lifecycle suite,
  invalid callback/replay and reload. Silent fake provider produces no real audio.
- 390×844 Chromium viewport: scan/controls and no horizontal overflow only.
- Physical camera, inverted physical cards, phone Safari/iOS Chrome/Android Chrome,
  desktop Safari/Firefox/Edge, trusted HTTPS mobile session: **NOT RUN**.
- Live OAuth/refresh, actual quota/allowlist, all seven playlists/access/schema/order,
  SDK authenticated readiness/DRM/audible local playback and real remote controls:
  **NOT RUN**, requiring app/account/device interaction.

Use current WasmGC-capable browsers; SDK browser support alone does not guarantee
Compose/Wasm support. Web Crypto, session/local storage, camera secure context and
permissions, QR worker fetches, DRM and user activation are separate requirements.
The [SDK documentation](https://developer.spotify.com/documentation/web-playback-sdk)
notes iOS needs interaction after transfer and extensions can block the SDK. Click
Enable audio directly; QR callbacks do not preserve user activation. Remote playback
can remain usable when local SDK fails; manual QR input is the camera fallback.
Titles can appear in Spotify/OS/external devices; the app cannot guarantee secrecy.

For eventual phone testing, provision a trusted HTTPS origin without publishing this
release, register its exact callback, and use an allowlisted Premium account. Record
phone/OS/browser versions, consent/callback/refresh, normal/inverted QR, camera release
on navigation/logout, SDK activation after transfer/backgrounding and remote playback.
A self-signed/untrusted certificate, desktop WebKit emulator or loopback on a different
machine does not establish real phone acceptance. Product policy and all live gates
remain outstanding even when automated CI passes.

## Executed final checks

2026-10-23, macOS arm64, SDKMAN current Java:

- Full clean Gradle command above: **PASS**, 26 tests (7 Compose, 5 domain, 14 API).
  Production distribution **PASS**; rebuilt after host resource changes, also **PASS**.
  Existing three webpack size/dynamic-import warnings remain. Final sizes: Skiko
  8.24 MiB Wasm, application 3.59 MiB Wasm, main JS 609 KiB, worker chunk 43.2 KiB.
- `npm ci --prefix browser-tests`: **PASS**, 0 audit vulnerabilities.
- `(cd browser-tests && npx playwright install chromium)`: **PASS**.
- `npm test --prefix browser-tests`: **PASS**, Chromium **149.0.7827.55**,
  all production asset/callback, UI and adapter checks described above; no uncaught
  page errors. Test HTTP data and SDK lifecycle fakes are not live Spotify evidence.
- `git diff --check`: **PASS**.
- Quota recheck: integration CI run `40022336901` actually uploaded artifact
  `12530414105`, 4,943,607 bytes, confirmed by successful upload log and artifact API.
  Thus quota was not blocking that run; future storage availability is not guaranteed.

No phone/HTTPS/live Spotify result is claimed. See the explicit NOT RUN list above.
