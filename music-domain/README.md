# Hitster domain

Provider-independent KMP parser, configuration and positional resolver. No network,
Compose or Spotify transport dependency.

`HitsterCardParser.parse` trusts only `www.hitstergame.com` (case insensitive).
HTTP/HTTPS and their explicit default ports are accepted; credentials, other ports,
bare host, escaped paths, backslashes and interior whitespace are rejected.
Schemeless input is normalized to HTTPS; surrounding whitespace is trimmed.
Paths are `/de/<number>` or `/de/<lowercase-alphanumeric-edition>/<number>`.
One trailing slash is permitted; query and fragment are ignored. Numbers are ASCII
positive decimals through Int.MAX_VALUE; leading zeros are accepted.

`EditionConfigurations.defaults()` loads the seven reference mappings and labels
from bundled JSON. Live access and physical card ordering remain unverified.
`decodeLocal`/`encodeLocal` use a JSON array of `{editionKey, playlist, label}` entries
so duplicate edition keys can be rejected before constructing a map. A local saved
configuration is a complete replacement; resetting calls `defaults()`. Storage is
owned by the browser adapter and is independent of OAuth. `validate` also normalizes
the existing `EditionConfiguration` map contract; absent labels default to keys.
Playlist inputs accept exactly 22 ASCII alphanumeric ID characters or trusted
`open.spotify.com/playlist/<id>` HTTP(S) URLs, with the same slash/query/fragment
policy as cards. Configuration failures use `InvalidConfiguration`.

Implement `OrderedPlaylistRepository.itemAt` in the composition root. It receives
exactly `cardNumber - 1`: never filter, sort, shuffle or deduplicate first. A null
return means the position does not exist (`MissingItem`); `OrderedPlaylistItem(null)`
means an unavailable/null/local/episode item at that exact position (`UnavailableTrack`).
Map provider failures to `MusicFailure` at the adapter boundary. Exceptions and
cancellation pass through unchanged. Configuration is validated and copied on
resolver construction; recreate the resolver after settings changes. No cache is used.
