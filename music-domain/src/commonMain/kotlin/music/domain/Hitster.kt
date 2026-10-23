package music.domain

/** Strict ASCII URL subset: no decoding, user info, escaped host/path or arbitrary URL fetching. */
internal fun trustedPath(input: String, host: String, invalid: () -> Nothing): String {
    val text = input.trim()
    if (text.any { it.isWhitespace() || it.code < 32 || it == '\\' }) invalid()
    val normalized = if ("://" in text) text else "https://$text"
    val match = Regex("^(https?)://([^/?#]+)(/[^?#]*)?(?:\\?[^#]*)?(?:#.*)?$", RegexOption.IGNORE_CASE)
        .matchEntire(normalized) ?: invalid()
    val scheme = match.groupValues[1].lowercase()
    val authority = match.groupValues[2].lowercase()
    if (authority != host && authority != "$host:${if (scheme == "https") 443 else 80}") invalid()
    return match.groupValues[3]
}

object HitsterCardParser {
    fun parse(input: String): HitsterCard {
        fun invalid(): Nothing = throw MusicFailure(FailureKind.InvalidQr, "Enter a valid www.hitstergame.com card URL.")
        val path = trustedPath(input, "www.hitstergame.com", ::invalid)
        val match = Regex("^/de/(?:([a-z0-9]+)/)?([0-9]+)/?$").matchEntire(path) ?: invalid()
        val number = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: invalid()
        val edition = match.groupValues[1]
        return HitsterCard(if (edition.isEmpty()) "de" else "de/$edition", number)
    }
}

/** Null return means no such position; an unavailable entry is still a real position. */
fun interface OrderedPlaylistRepository {
    suspend fun itemAt(playlistId: String, offset: Int): OrderedPlaylistItem?
}

data class OrderedPlaylistItem(val song: ResolvedSong?)

/** Adapters map provider failures to MusicFailure; cancellation passes through unchanged. */
class HitsterSongResolver(configuration: EditionConfiguration, private val repository: OrderedPlaylistRepository) : SongResolver {
    private val configuration = EditionConfigurations.validate(configuration)
    override suspend fun resolve(card: HitsterCard): ResolvedSong {
        if (card.cardNumber <= 0) throw MusicFailure(FailureKind.InvalidQr, "Card number must be positive.")
        val playlist = configuration.playlists[card.editionKey]
            ?: throw MusicFailure(FailureKind.UnknownEdition, "Configure playlist for edition ${card.editionKey}.")
        val item = repository.itemAt(playlist, card.cardNumber - 1)
            ?: throw MusicFailure(FailureKind.MissingItem, "Playlist has no item at card ${card.cardNumber}.")
        return item.song ?: throw MusicFailure(FailureKind.UnavailableTrack, "The track at card ${card.cardNumber} is unavailable.")
    }
}
