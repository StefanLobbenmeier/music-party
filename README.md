# Music Party

Compose/Wasm Spotify scan and playback companion. See [implementation plan](docs/implementation-plan.md) and
[feasibility evidence and external prerequisites](docs/feasibility.md).

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
./gradlew :composeApp:jvmTest :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution
python3 -m http.server 8080 --bind 127.0.0.1 --directory composeApp/build/dist/wasmJs/productionExecutable
```

Open `http://127.0.0.1:8080`. Gradle downloads pinned Node/Yarn; system npm is unnecessary.
Use on-screen setup for a public client ID and registered callback URI, or choose
explicit fake providers for a silent credential-free demo. See
[Compose integration](docs/step-5-integration.md) for configuration and contract decisions.
Live acceptance and Spotify game-use permission remain pending. No game rounds are implemented.

Optional production browser regression (Node 24+, Python 3):

```sh
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install --with-deps chromium)
npm test --prefix browser-tests
```
