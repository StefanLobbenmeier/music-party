package music.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ContractsTest {
    @Test fun configurationPreservesEditionKeysAndPlaylistOrder() {
        val config = EditionConfiguration(linkedMapOf("de" to "base", "de/aaaa0039" to "rock"))
        val restored = Json.decodeFromString<EditionConfiguration>(Json.encodeToString(config))
        assertEquals(config, restored)
        assertEquals(listOf("de", "de/aaaa0039"), restored.playlists.keys.toList())
    }
}
