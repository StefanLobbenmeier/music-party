package music.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable data class EditionMapping(val editionKey: String, val playlist: String, val label: String)

object EditionConfigurations {
    private val key = Regex("de(?:/[a-z0-9]+)?")
    private val id = Regex("[A-Za-z0-9]{22}")
    private fun invalid(message: String): Nothing = throw MusicFailure(FailureKind.InvalidConfiguration, message)

    fun playlistId(input: String): String {
        val text = input.trim()
        if (id.matches(text)) return text
        val path = trustedPath(text, "open.spotify.com") { invalid("Enter a Spotify playlist ID or URL.") }
        return Regex("/playlist/([A-Za-z0-9]{22})/?").matchEntire(path)?.groupValues?.get(1)
            ?: invalid("Enter a Spotify playlist ID or URL.")
    }

    fun fromEntries(entries: List<EditionMapping>): EditionConfiguration {
        val playlists = linkedMapOf<String, String>()
        val labels = linkedMapOf<String, String>()
        for (entry in entries) {
            if (!key.matches(entry.editionKey)) invalid("Invalid edition key: ${entry.editionKey}")
            if (entry.editionKey in playlists) invalid("Duplicate edition key: ${entry.editionKey}")
            if (entry.label.isBlank()) invalid("Edition label must not be blank.")
            playlists[entry.editionKey] = playlistId(entry.playlist)
            labels[entry.editionKey] = entry.label
        }
        return EditionConfiguration(playlists, labels)
    }

    /** Local JSON is an array, preserving duplicate keys until validation. */
    fun decodeLocal(json: String): EditionConfiguration = try {
        fromEntries(Json.decodeFromString<List<EditionMapping>>(json))
    } catch (failure: SerializationException) {
        throw MusicFailure(FailureKind.InvalidConfiguration, "Invalid edition configuration JSON.", failure)
    } catch (failure: IllegalArgumentException) {
        throw MusicFailure(FailureKind.InvalidConfiguration, "Invalid edition configuration JSON.", failure)
    }

    fun encodeLocal(configuration: EditionConfiguration): String {
        val valid = validate(configuration)
        return Json.encodeToString(valid.playlists.map { (key, playlist) -> EditionMapping(key, playlist, valid.labels.getValue(key)) })
    }

    fun validate(configuration: EditionConfiguration): EditionConfiguration {
        if (configuration.labels.keys.any { it !in configuration.playlists }) invalid("Label has no edition mapping.")
        return fromEntries(configuration.playlists.map { (key, playlist) -> EditionMapping(key, playlist, configuration.labels[key] ?: key) })
    }

    /** Reference defaults only: accessibility and physical-card ordering require live verification. */
    fun defaults(): EditionConfiguration = decodeLocal(DEFAULTS_JSON)

    private const val DEFAULTS_JSON = """[
      {"editionKey":"de","playlist":"26zIHVncgI9HmHlgYWwnDi","label":"German base game"},
      {"editionKey":"de/aaaa0012","playlist":"15hZ0ez6sHYhTeCCshxJTN","label":"summer party"},
      {"editionKey":"de/aaaa0015","playlist":"2u0vgWYqU1TWVcDehJnZuN","label":"guilty pleasures"},
      {"editionKey":"de/aaaa0019","playlist":"58y9xPPIRWd8tqlOaKoDOI","label":"bingo"},
      {"editionKey":"de/aaaa0026","playlist":"2jlbmBYM1RLZrsyY67wuDQ","label":"movies/TV"},
      {"editionKey":"de/aaaa0040","playlist":"3QWNtOG8LrhClKhHqFRBHS","label":"celebration"},
      {"editionKey":"de/aaaa0039","playlist":"4oYTRg0JI48jucsJOLily1","label":"rock"}
    ]"""
}
