# Local Plex gateway

The Bingo browser client talks only to `127.0.0.1:8765`. This small Ktor process
performs Plex PIN/JWT authentication, discovers servers, lists music libraries and
tracks, and proxies ranged audio requests. Plex account and per-server tokens remain
in process memory and are discarded when the gateway exits. The Ed25519 device identity
is persisted under `~/.music-party-plex/device.properties` so restarting does not register
a new Plex device each time. The gateway binds to loopback and allows only the configured
app origin.

From the repository root, with SDKMAN's active Java:

```sh
export JAVA_HOME=/Users/stefan/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :plex-gateway:run
```

The default app origin is `http://127.0.0.1:8092`, matching
`python3 browser-tests/server.py`. When the app uses another origin, set its exact
origin before starting the gateway, for example:

```sh
MUSIC_PARTY_APP_ORIGIN=https://music.example.test ./gradlew :plex-gateway:run
```

The frontend must be able to reach loopback from the browser. This has not yet been
verified on phones or across browsers' local-network access prompts. The gateway
does not expose a route that accepts Plex tokens from the browser, and it does not
persist authentication across restarts. Keep the process on the trusted device and
stop it after the session.
