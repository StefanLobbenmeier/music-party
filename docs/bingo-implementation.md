# Bingo implementation boundary

The first Bingo implementation adds `:bingo-domain`, a provider-neutral round engine.
The requested catalog and player are Plex-only; Spotify stays in the separate QR mode.
At app startup a neutral mode chooser is shown without restoring or constructing the
Spotify session. Entering or leaving a mode navigates to a fresh page context, so the
Spotify Web Playback SDK (loaded dynamically on demand) cannot stay loaded after switching
to Bingo. The Bingo screen signs into Plex, lists reachable servers and music libraries,
loads eligible tracks and plays audio through the gateway.
The engine draws distinct eligible songs without replacement, selects one
eligible category per round, keeps the solution out of the unrevealed state, and exposes
explicit reveal/next-round transitions. It supports artist, title, release year ±3, and
release decade. It retains date precision and provider provenance; a year is described as
the metadata provider describes it and is never relabelled as the song's original release
year. Lyrics require an attributed source plus an explicit format, and remain disabled by
default.

Plex supplies metadata and streams the selected local music item. The implementation includes
Plex PIN/JWT authentication, server discovery, library selection and a Plex playback adapter.
It uses a local Ktor gateway, which keeps Plex account and server tokens
out of browser storage and proxies audio from a local PMS. The Plex API remains exclusive
to that gateway and the Bingo UI. A hosted HTTPS page cannot safely assume direct browser
access to an arbitrary local HTTP Plex server; gateway access from phones and hosted origins
still needs live browser acceptance.

Spotify's current Developer Policy says not to create games, including trivia quizzes.
Accordingly, no Spotify catalog/player is connected to Bingo. Spotify remains isolated to
QR-card playback; Bingo will use Plex only.

Official reference: [Spotify Developer Policy, section III](https://developer.spotify.com/policy).
