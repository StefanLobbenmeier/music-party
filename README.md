# Music Party

Compose/Wasm foundation only. See [implementation plan](docs/implementation-plan.md) and
[feasibility evidence and external prerequisites](docs/feasibility.md).

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
./gradlew :music-domain:jvmTest :spotify-api:jvmTest :composeApp:wasmJsBrowserDistribution
python3 -m http.server 8080 --bind 127.0.0.1 --directory composeApp/build/dist/wasmJs/productionExecutable
```

Open `http://127.0.0.1:8080`. Gradle downloads pinned Node/Yarn; system npm is unnecessary.
The camera and SDK buttons are feasibility probes. No Spotify login, resolver or player
implementation exists yet. Configuration is a public template, never a place for secrets.

Optional production browser regression (Node 24+, Python 3):

```sh
npm ci --prefix browser-tests
(cd browser-tests && npx playwright install --with-deps chromium)
npm test --prefix browser-tests
```
