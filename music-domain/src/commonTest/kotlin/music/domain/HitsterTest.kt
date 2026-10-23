package music.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class HitsterTest {
    private inline fun failure(kind: FailureKind, block: () -> Unit) {
        assertEquals(kind, assertFailsWith<MusicFailure> { block() }.kind)
    }

    @Test fun acceptedUrls() {
        for (prefix in listOf("https://", "http://", "")) {
            for (suffix in listOf("", "/", "?utm=x", "#fragment", "/?x=1#f")) {
                assertEquals(HitsterCard("de", 1), HitsterCardParser.parse("${prefix}www.hitstergame.com/de/00001$suffix"))
                assertEquals(HitsterCard("de/aaaa0039", 123), HitsterCardParser.parse("${prefix}www.hitstergame.com/de/aaaa0039/123$suffix"))
            }
        }
        assertEquals(Int.MAX_VALUE, HitsterCardParser.parse("HTTPS://WWW.HITSTERGAME.COM:443/de/2147483647").cardNumber)
        assertEquals(1, HitsterCardParser.parse("http://www.hitstergame.com:80/de/1").cardNumber)
    }

    @Test fun rejectedUrlsAndNumbers() {
        val hostile = listOf("ftp://www.hitstergame.com/de/1", "https://evil.com/de/1",
            "https://www.hitstergame.com.evil.com/de/1", "https://evil.com@www.hitstergame.com/de/1",
            "https://www.hitstergame.com@evil.com/de/1", "https://www.hitstergame.com:444/de/1",
            "http://www.hitstergame.com:443/de/1", "https://hitstergame.com/de/1",
            "https://www%2ehitstergame.com/de/1", "https://www.hitstergame.com\\@evil.com/de/1",
            "//www.hitstergame.com/de/1", "https://www.hitstergame.com/de/1\n/2")
        val paths = listOf("0", "000", "-1", "+1", "1.0", "1e2", "2147483648", "99999999999999999999", "%31", "١", "", "1//", "1/extra", "a/b/1", "../1")
        for (input in hostile + paths.map { "https://www.hitstergame.com/de/$it" }) {
            failure(FailureKind.InvalidQr) { HitsterCardParser.parse(input) }
        }
    }

    @Test fun exactDefaultsAndLocalValidation() {
        val keys = listOf("de", "de/aaaa0012", "de/aaaa0015", "de/aaaa0019", "de/aaaa0026", "de/aaaa0040", "de/aaaa0039")
        val ids = listOf("26zIHVncgI9HmHlgYWwnDi", "15hZ0ez6sHYhTeCCshxJTN", "2u0vgWYqU1TWVcDehJnZuN", "58y9xPPIRWd8tqlOaKoDOI", "2jlbmBYM1RLZrsyY67wuDQ", "3QWNtOG8LrhClKhHqFRBHS", "4oYTRg0JI48jucsJOLily1")
        val config = EditionConfigurations.defaults()
        assertEquals(keys, config.playlists.keys.toList())
        assertEquals(ids, config.playlists.values.toList())
        assertEquals(listOf("German base game", "summer party", "guilty pleasures", "bingo", "movies/TV", "celebration", "rock"), config.labels.values.toList())
        assertEquals(config, EditionConfigurations.decodeLocal(EditionConfigurations.encodeLocal(config)))
        for (suffix in listOf("", "/", "?si=abc#x")) assertEquals(ids[0], EditionConfigurations.playlistId("https://open.spotify.com/playlist/${ids[0]}$suffix"))
        for (bad in listOf("short", "spotify:playlist:${ids[0]}", "https://evil.com/playlist/${ids[0]}", "https://user@open.spotify.com/playlist/${ids[0]}", "https://open.spotify.com:123/playlist/${ids[0]}", "https://open.spotify.com/track/${ids[0]}")) {
            failure(FailureKind.InvalidConfiguration) { EditionConfigurations.playlistId(bad) }
        }
        val entry = EditionMapping("de", ids[0], "Base")
        failure(FailureKind.InvalidConfiguration) { EditionConfigurations.fromEntries(listOf(entry, entry)) }
        failure(FailureKind.InvalidConfiguration) { EditionConfigurations.decodeLocal("[{}]") }
        failure(FailureKind.InvalidConfiguration) { EditionConfigurations.decodeLocal("not json") }
        failure(FailureKind.InvalidConfiguration) { EditionConfigurations.fromEntries(listOf(entry.copy(editionKey = "../de"))) }
        failure(FailureKind.InvalidConfiguration) { EditionConfigurations.fromEntries(listOf(entry.copy(label = " "))) }
    }

    @Test fun resolverPreservesEveryPositionAndFailure() = runBlocking {
        val songs = (0..4).map { ResolvedSong("song$it", "provider:song:$it", "https://provider.test/$it") }
        val items = listOf(OrderedPlaylistItem(songs[0]), OrderedPlaylistItem(null), OrderedPlaylistItem(songs[2]), OrderedPlaylistItem(null), OrderedPlaylistItem(songs[4]))
        val calls = mutableListOf<Int>()
        val config = EditionConfigurations.defaults()
        val resolver = HitsterSongResolver(config, OrderedPlaylistRepository { id, offset ->
            assertEquals(config.playlists.getValue("de"), id)
            calls += offset
            items.getOrNull(offset)
        })
        for (card in listOf(1, 3, 5)) assertEquals(songs[card - 1], resolver.resolve(HitsterCard("de", card)))
        for (card in listOf(2, 4)) failure(FailureKind.UnavailableTrack) { resolver.resolve(HitsterCard("de", card)) }
        for (card in listOf(6, Int.MAX_VALUE)) failure(FailureKind.MissingItem) { resolver.resolve(HitsterCard("de", card)) }
        assertEquals(listOf(0, 2, 4, 1, 3, 5, Int.MAX_VALUE - 1), calls)
        failure(FailureKind.UnknownEdition) { resolver.resolve(HitsterCard("de/unknown", 1)) }
        for (card in listOf(0, -1)) failure(FailureKind.InvalidQr) { resolver.resolve(HitsterCard("de", card)) }
        assertEquals(7, calls.size)
        val cancelled = CancellationException("cancelled")
        assertSame(cancelled, assertFailsWith<CancellationException> {
            HitsterSongResolver(config, OrderedPlaylistRepository { _, _ -> throw cancelled }).resolve(HitsterCard("de", 1))
        })
        for (kind in listOf(FailureKind.InaccessiblePlaylist, FailureKind.LoginRequired, FailureKind.Throttled, FailureKind.Offline)) {
            val expected = MusicFailure(kind, "repository failure")
            assertSame(expected, assertFailsWith<MusicFailure> { HitsterSongResolver(config, OrderedPlaylistRepository { _, _ -> throw expected }).resolve(HitsterCard("de", 1)) })
        }
    }
}
