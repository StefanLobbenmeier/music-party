# Music Party

Compose/Wasm Spotify scan and playback companion. **Release is gated and nothing is
deployed.** Spotify's [game prohibition](https://developer.spotify.com/policy) remains
unresolved for this use. No bingo/game rounds are implemented. Live account, playlist
and physical phone acceptance remain pending.

See [release verification and operations](docs/release-verification.md) for setup,
scopes/allowlisting, exact commands, static hosting rules, browser limitations and
live-test prerequisites. [Implementation plan](docs/implementation-plan.md),
[foundation feasibility](docs/feasibility.md) and [integration](docs/step-5-integration.md)
preserve design and earlier evidence.

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:clean :composeApp:jvmTest :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution
# Node 24/npm and Python 3 required for browser verification:
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install chromium)
npm test --prefix browser-tests
# Interactive production preview with the shipped OAuth callback rewrite:
python3 browser-tests/server.py
```

Open `http://127.0.0.1:8092`. Enter a public client ID and exact registered callback
`http://127.0.0.1:8092/oauth/spotify/callback`, or select **Try fake providers** for a
silent credential-free demo. No client secret is accepted. Gradle supplies its own
Node/Yarn for builds; the browser commands require npm on PATH (see operations doc
for the Gradle-installed Node path). CI requires no Spotify credentials. Deployment
is a separate action; this repository has no publishing workflow.
