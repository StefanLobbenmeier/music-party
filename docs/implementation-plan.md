# Music Party: Compose web implementation plan

Status: implementation packages 1–5 merged; step 6 verification recorded in
[release verification](release-verification.md). Live acceptance and policy gates remain open.
The architecture and work packages below preserve the original planning brief.
Prepared: 2026-10-23.

## Goal and scope

Build a Kotlin Compose web app that signs in with Spotify, scans a Hitster QR code, resolves its card number through a configured playlist, and plays the corresponding track. Load a Spotify Web Playback SDK player in the browser and support controlling another available Spotify Connect device through a separate Ktor API module.

The first release includes login/logout, playlist configuration, camera scanning, manual QR-text entry as a fallback, track resolution, device selection, playback/pause/resume/seek, recoverable errors, and responsive UI. Assume “Spotify web player” means an embedded SDK-backed browser player, matching v1; it does not mean framing open.spotify.com.

Bingo is a later phase: select random songs and guessing categories, let people write answers on paper, and reveal solutions on demand. No answer forms, scoring, multiplayer synchronization, board export, or backend are required for the first release. Do not port v1's printable bingo board as the requested new bingo mode.

## Repository evidence

The working branch `feat/compose-web-spotify-app` starts with an empty tracked tree. The reference is fetched `origin/v1` at commit `23297a71b1471c1870d1c1028384a0a36b7481e7` (“Add hitster rock”). Read reference files with `git show origin/v1:<path>`; do not merge the React application into the new project.

Useful reference files:

- `src/lib/resolve-song-url.ts`: Hitster path parsing, seven edition mappings, one-based card number converted to zero-based playlist offset.
- `src/lib/spotify-client.ts`: existing app ID, callback route, SDK-managed authorization. Configure the new app explicitly; do not silently reuse its client ID or copy its scope list unchanged.
- `src/qr-scanner/scanner.tsx`: npm `qr-scanner`, rear camera preference, inline muted video, inverted QR recognition, cleanup.
- `src/context-provider.tsx`: Spotify Web Playback SDK, device name `music-party`.
- `src/spotify/spotify-url-player.tsx`: start playback by Spotify URI and expandable track information.
- `src/spotify/player-controls.tsx`: playback state, seek, progress.
- `src/routes/BingoRoute.tsx`: seeded printable board, a different feature from the requested future guessing rounds.
- `.github/workflows/main.yml`: previous Cloudflare Pages deployment. Preserve this as a hosting option, not an instruction to deploy during implementation.

Seed edition configuration from v1, retaining its exact playlist order assumptions:

```json
{
  "de": "26zIHVncgI9HmHlgYWwnDi",
  "de/aaaa0012": "15hZ0ez6sHYhTeCCshxJTN",
  "de/aaaa0015": "2u0vgWYqU1TWVcDehJnZuN",
  "de/aaaa0019": "58y9xPPIRWd8tqlOaKoDOI",
  "de/aaaa0026": "2jlbmBYM1RLZrsyY67wuDQ",
  "de/aaaa0040": "3QWNtOG8LrhClKhHqFRBHS",
  "de/aaaa0039": "4oYTRg0JI48jucsJOLily1"
}
```

Labels, respectively: German base game, summer party, guilty pleasures, bingo, movies/TV, celebration, rock. These are reference mappings, not verified accessible datasets or guarantees that current playlist ordering still matches physical cards.

## Feasibility checks to perform first

1. **Playlist access is a critical dependency.** The current playlist-items reference restricts reads to playlists owned by or collaborated on by the authenticated user. The 2026 development-mode migration also changes `/tracks` to `/items` and response fields from `track` to `item`. Probe the intended application/account against the configured editions before claiming Hitster support works. Following a public playlist is not ownership. If inaccessible, use an explicitly configured, accessible playlist whose full ordering has been verified against the cards; do not silently substitute a playlist or assume a server proxy fixes authorization. Keep the original mappings as reference defaults and display an actionable access error. Record actual app quota mode and response schema. [Playlist items reference](https://developer.spotify.com/documentation/web-api/reference/get-playlists-items), [migration guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide).
2. **Browser playback and account setup.** Validate the intended Premium account and app allowlist, a registered callback, SDK device readiness, and one real playback command. Current development mode generally permits five allowlisted users and requires the app owner to have Premium; existing apps can have grandfathered limits. Do not assume public rollout is available. [Quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes), [SDK setup](https://developer.spotify.com/documentation/web-playback-sdk/tutorials/getting-started).
3. **Game usage constraint.** Spotify's policy explicitly states “Do not create a game, including trivia quizzes.” This directly affects the proposed bingo integration and may affect the Hitster companion usage. Record this as an unresolved product/platform constraint; do not present technical success as establishing permission for that use. Keep later game logic provider-independent so its design does not require Spotify. Resolve the permitted integration approach before shipping the game experience. [Spotify Developer Policy, section III](https://developer.spotify.com/policy).
4. **Browser bridge spike.** Compile a minimal Compose/Wasm page with a real video element, the QR scanner worker, one SDK event callback, and a Ktor browser request. Test mobile Safari and Chrome early. Produce exact compatible dependency versions and browser requirements before distributing implementation.

These checks should yield concrete results or documented external blockers. Work using fakes can continue without credentials; live acceptance must remain explicitly pending until tested.

## Architecture decisions

Use Compose Multiplatform with a `wasmJs` browser target: this is the Compose UI route for web, as distinct from Android-only Jetpack Compose or Kotlin/JS Compose HTML. Web support is currently Beta. Keep browser APIs behind interfaces and avoid adding Android/iOS applications in this phase. Add JVM targets to non-UI modules for fast tests where practical. [Kotlin web overview](https://kotlinlang.org/docs/web-overview.html), [Wasm project setup](https://kotlinlang.org/docs/wasm-get-started.html).

Use Gradle Kotlin DSL, a checked-in Gradle wrapper and version catalog, Kotlin serialization, coroutines/StateFlow, and explicit constructor injection. The foundation task selects and pins a compatible Kotlin/Compose/Gradle/Ktor set verified by a production build; do not let each task choose versions independently. Ktor supports browser/Wasm clients; verify the engine for the pinned release in the initial spike. [Ktor engines](https://ktor.io/docs/client-engines.html).

Proposed module ownership and dependencies:

- `:music-domain`: platform-independent card parser, edition configuration models, resolved song model, repository/player interfaces, playback coordination logic. No Compose, DOM, or Spotify SDK dependency.
- `:spotify-api`: standalone KMP library using Ktor for Spotify REST and token endpoints. Owns API DTOs, pagination, typed failures, OAuth token exchange/refresh, and remote-control operations. No dependency on Compose or the browser SDK; accept an `HttpClient` and token provider. Expose a small Kotlin API usable independently of this application.
- `:composeApp`: Compose UI, composition root, mapping Spotify DTOs into domain models, browser auth/session adapters, `QrScanner` adapter, Spotify Web Playback SDK adapter, navigation and configuration persistence. Browser implementations live in `wasmJsMain`; shared screen logic belongs in `commonMain` where possible.
- Later `:bingo-domain`: only add when implementing bingo. Depends on provider-neutral song catalog/metadata/playback contracts, not on Spotify DTOs.

Small JavaScript bridge files are acceptable for browser SDKs and npm interop. Keep React and the TypeScript Spotify API SDK out of the new application; all Spotify REST calls go through Ktor. Prefer one repository with these three initial modules over a large module hierarchy.

Agree on these contracts before parallel implementation (names can be adjusted once by the coordinator):

```kotlin
data class HitsterCard(val editionKey: String, val cardNumber: Int)
data class ResolvedSong(val id: String, val uri: String, val url: String)
interface SongResolver { suspend fun resolve(card: HitsterCard): ResolvedSong }
interface AccessTokenProvider { suspend fun validAccessToken(): String }
// SpotifyApi: playlistItemAt(id, offset), playlistItems(page), track(id),
// devices(), playbackState(), transfer(deviceId), play(uri, deviceId),
// pause(deviceId), resume(deviceId), seek(positionMs, deviceId).
// QrScanner: start(onDecoded, onFailure), stop(), dispose().
// BrowserPlayer: connect(), activateFromUserGesture(), deviceState, disconnect().
// PlaybackCoordinator: state, selectDevice(), play(song), pause(), resume(), seek().
```

Use typed errors (invalid QR, unknown edition, inaccessible playlist, missing item, unavailable track, login required, no device, playback restricted, throttled, offline). Cancellation must propagate rather than become a user-facing failure. Inject clocks and randomness only where behavior requires them.

## Authentication and session behavior

- Authorization Code with PKCE, using browser cryptographic randomness and S256. No client secret belongs in a browser bundle. Validate OAuth state, use a one-time verifier, handle denial and malformed callbacks, and clear callback query parameters after handling. [PKCE flow](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow).
- Configure public client ID and exact redirect URI. Retain `/oauth/spotify/callback` unless the foundation task has a concrete reason to change it. Host rewrites must serve the app on a direct callback request.
- Use HTTPS outside loopback. Local redirect configuration should use an explicit loopback IP such as `http://127.0.0.1:<port>/oauth/spotify/callback`; Spotify disallows `localhost` redirects. Phone testing needs a trusted HTTPS origin. [Redirect requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri).
- Start with `streaming`, `user-read-playback-state`, `user-modify-playback-state`, and the playlist-read scopes needed by supported configurations. Include SDK-required profile scopes according to the pinned SDK documentation. Do not request playlist-write access for a read/play app.
- Default to tab-scoped session storage for redirect continuity and session tokens; keep storage behind an interface. Clear tokens, pending auth, user-specific caches and SDK connection on logout. No token logging or tokens in application URLs.
- Serialize concurrent refreshes, refresh before expiry, store a replacement refresh token when returned, retry an unauthorized request at most once, and return to login when refresh is rejected. The browser SDK token callback must request a fresh token from the same provider, not capture the initial token.

## QR scanning and Hitster resolution

- Use `qr-scanner` through a thin browser adapter, starting from v1's working integration. Verify its current package/worker setup with Wasm bundling. Support normal and inverted codes, rear camera preference, inline video, permission denied/no camera errors, and camera release on navigation, logout and disposal.
- Camera access begins with a user action. Decode misses during normal scanning are not error banners. Pause scanning after accepting a valid result; duplicate camera frames must cause only one resolution/play request. Resume explicitly for the next card or after recoverable failure. Include manual text entry for camera-unavailable environments and testing.
- Parse exact trusted Hitster hosts (initially `www.hitstergame.com`; add the bare host only with fixtures). Permit schemeless input by normalizing to HTTPS; accept only HTTP(S), reject credentials/unexpected ports and unrelated hosts. Parse `/de/<card>` and `/de/<edition>/<card>` with a strictly positive decimal card number. Define handling for trailing slash, query and fragment and test it consistently. Never fetch an arbitrary scanned URL.
- Configuration is a bundled JSON default plus validated, browser-local overrides in a small settings screen. Users can change edition-to-playlist mappings and reset defaults. Accept Spotify playlist ID/URL input and normalize it; reject duplicate edition keys and invalid IDs. Store configuration independently of OAuth tokens.
- Convert card `N` to offset `N - 1`; request exactly the playlist position without sorting or filtering beforehand. Null/local/unavailable tracks and episodes at that position are failures, not reasons to shift to another song. Return Spotify ID, URI and URL; use URI for playback and URL for linking.
- Never re-sort, shuffle or de-duplicate a Hitster mapping. Cache successful lookups only briefly in memory, keyed by edition/playlist/position and current account; invalidate on configuration changes. Playlist edits can invalidate physical-card alignment, so document that assumption and provide a manual recheck path.

## Spotify REST and playback behavior

- Implement only needed endpoints: playlist metadata/items, individual track metadata, devices, playback state, transfer, start/resume, pause and seek. Add pagination now for catalog reuse in later bingo; do not rely on removed recommendations or bulk endpoints.
- Base implementation on current `/playlists/{id}/items` and current DTOs. Add a legacy adapter only if the actual target application's verified API behavior requires it; no blind fallback on authorization errors.
- Ktor serialization tolerates unknown fields and nullable/unavailable content. Correctly handle empty `204` responses, `401`, `403`, `404`, `429` with `Retry-After`, and transient transport/server failures. Apply bounded backoff for reads. Avoid automatically replaying stale playback commands after long delays.
- Load `https://sdk.scdn.co/spotify-player.js` once, register readiness before initialization, instantiate one named browser device per session, and clean up listeners/device on logout. Distinguish SDK loaded, connected, device ready, active and actually playing states. [SDK overview](https://developer.spotify.com/documentation/web-playback-sdk).
- Offer “This browser” and available remote devices. Remote control uses the Ktor module. A browser SDK failure must not prevent use of an available remote player. Disable unsupported controls on restricted devices.
- Use a user-gesture “Enable audio” action and the SDK activation API; expose a retry when autoplay is blocked. Do not assume a camera callback retains browser user activation.
- Playback coordinator waits for a usable device, transfers when needed, waits for confirmation with a bounded timeout, and starts the resolved URI on that explicit device. Serialize commands and cancel/ignore superseded scan results. Do not report success from a `204` alone; reconcile against SDK events or playback state.
- SDK events provide local device state; bounded, visibility-aware polling provides remote state. Keep state fresh without aggressive polling. If a device disappears, retain the resolved song and offer another device/retry.
- Show progress, pause/resume, seek and scan-next controls. Song metadata reveal can follow v1, subject to the unresolved usage/design requirements; the app cannot guarantee hiding titles from Spotify itself, OS media controls, or external devices. Include Spotify attribution and links wherever metadata is displayed.

## Work packages for the next coordinator

The coordinator should distribute these tasks after completing the initial spike and committing the shared scaffold/contracts. Each worker receives this plan, the pinned v1 commit, owned paths, agreed interfaces, and acceptance criteria. Keep build/version/shared-contract changes with one owner to reduce conflicts.

1. **Foundation and feasibility — first, sequential.** Own root Gradle files, version catalog, module skeletons, configuration template, browser bridge spike and `docs/feasibility.md`. Record playlist/account test results, compatible dependency versions, exact dev/test/build commands, production asset behavior, and remaining external prerequisites. Accept when all modules compile, the production page loads, camera/SDK interop is proven or a concrete blocker is recorded, and contracts are fixed.
2. **Spotify API library — after foundation.** Own `spotify-api/**` and its tests. Implement DTOs, token transport/refresh integration, pagination and remote commands. Use Ktor MockEngine to verify method/path/body/auth, empty responses, refresh coordination and bounded errors. Accept when the library builds without Compose and can control a mocked player completely.
3. **Hitster domain/configuration — parallel with API library.** Own `music-domain/**`, mapping defaults and fixtures. Implement parser, resolver against a fake repository, ordered-item handling, validated configuration and domain error types. Accept exact first/middle/last-position behavior, all seven defaults, unknown-edition behavior, and rejection of malformed/unrelated URLs.
4. **Browser adapters — parallel after contracts.** Own auth/session, camera and Web Playback SDK adapter files in `composeApp/wasmJsMain`. Use fakes for pending API work. Accept callback reload handling, real camera cleanup, duplicate-scan suppression, SDK lifecycle/activation behavior and refreshed tokens. Coordinate any npm/Gradle changes with foundation owner.
5. **Compose screens and integration — UI can start with fakes after foundation; final wiring waits for 2–4.** Own screens, navigation, composition root and playback coordinator. Implement login/setup, scan, playback/device selection, settings and actionable errors. Accept the full scan-to-play flow with fake and live providers, plus direct callback navigation and mobile layout.
6. **Release verification/documentation — after integration.** Own CI and operational docs with coordinator oversight. Run relevant unit/browser tests and production build; test an HTTPS phone session and at least one desktop browser. Document setup, scopes, allowlisting, playlist ordering/access, browser limitations and manual live-test results. Prepare static deployment configuration and callback rewrites; deployment is a separate action.

Merge order: foundation → API/domain/adapters (independently as ready) → final UI integration → verification. Workers should return changed paths, test results, interface changes and blockers. The coordinator must reconcile integration behavior, not simply concatenate completed tasks. No implementation or delegated work is requested as part of this planning turn.

## Verification and completion criteria

Automate the boundaries most likely to fail:

- Domain fixtures: both Hitster formats, leading-zero card numbers, zero/negative/overflow/malformed numbers, unknown edition, hostile hostname, first and out-of-range positions, and exact index preservation for null items.
- API fixtures: current response field names, paging, null/episode/local items, unavailable markets, `204`, one refresh for concurrent requests, rejected refresh, rate limiting and no infinite retries.
- Auth/browser adapter tests: state mismatch, denied login, callback replay, expired session, logout cleanup, worker loading, repeated QR callbacks, SDK ready/not-ready and autoplay failure.
- Coordinator tests: scan before player readiness, remote selection, transfer completion, superseded scan, disconnect mid-play and commands targeting the selected device.
- Browser integration with fake Spotify/camera providers: login → configure → scan → resolve → select → play → pause/seek → next card; test production paths as well as dev server.

Live acceptance requires the actual configured app/account: successful login/refresh, a real accessible Hitster playlist mapping verified against cards, camera scan and audible playback in the browser, plus remote-device control. Test supported mobile Safari/Chrome and desktop Chrome/Firefox as available, recording untested combinations honestly. A mocked end-to-end test does not establish Spotify access or DRM/autoplay support.

The repository must include reproducible Gradle commands, configuration examples with no secrets, a production static artifact build, and CI running tests/build without Spotify credentials. Use SDKMAN's active Java for every Java/Gradle command:

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
```

Keep the `current` symlink. Foundation must verify the active JDK's compatibility before pinning the wrapper; record any mismatch instead of silently switching Java installations. CI may use a corresponding supported JDK version without the local macOS path.

## Later: bingo guessing rounds

Preserve this feature in the backlog; do not implement it in the initial scan/play release. The Spotify game-use constraint above remains unresolved.

- Define a provider-neutral `SongCatalog`, `SongMetadataProvider`, `LyricsProvider` and `RoundGenerator` when needed. Reuse playback and metadata boundaries without coupling round logic to Spotify transport.
- Select randomly from an explicitly configured, fully paginated eligible song pool. De-duplicate for bingo only, exclude unavailable songs, draw without replacement until exhausted, and inject seeded randomness for tests. Do not change Hitster playlist indexing to support this.
- Default to one randomly selected enabled category per round, with a later setting for multiple categories if desired. Categories: artist, song title, lyrics, release year ±3 years (inclusive), release decade. This is a proposed default because category count per round was not specified.
- Round states: preparing → playing/question → solution revealed → next round. Show category and a neutral round number before reveal. Fetch metadata before the round and keep answer text/artwork out of the unrevealed UI and accessibility tree. Reveal button displays the answer; no player answer input or automatic scorekeeping.
- Spotify album release date is not reliably the original song release year. Preserve date precision and provenance; use curated/original-release data when available or label the answer as the returned album release year. Show the accepted ±3-year range and decade interval explicitly. Skip categories lacking reliable data.
- Treat lyrics as an unresolved data-source dependency: do not assume the Spotify Web API supplies lyrics. A later authorized provider or curated material must supply the prompt/solution excerpt; disable this category until that exists. Define whether players identify missing words or recall a line before implementing it. Do not scrape Spotify or add a secret-bearing third-party key to the browser bundle.
- Future tests: deterministic draws, no repeats until exhaustion, category eligibility, reveal transitions, inclusive year boundaries, decade boundaries and unavailable metadata.

## Handoff decisions still requiring real-world evidence

Proceed with the architecture and defaults above. The next coordinator should resolve these through the feasibility task, asking the user only for information that cannot be obtained from the repository or authorized environment:

- Which Spotify application/client ID and registered origins will be used, its quota mode and available test accounts/devices.
- Whether those accounts can read the intended edition playlists, and which accessible mappings are acceptable if they cannot.
- The permitted product approach for Spotify-backed game features.
- For the later phase only: lyrics source/prompt semantics and preferred release-year provenance.

Do not mark these questions answered merely because v1 previously worked.
