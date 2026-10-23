package music.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal data class ArtistDto(val id: String? = null, val name: String)
@Serializable internal data class AlbumDto(val id: String? = null, val name: String,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("release_date_precision") val precision: String? = null)
@Serializable internal data class ItemDto(
    val id: String? = null, val uri: String? = null, val type: String? = null,
    @SerialName("external_urls") val urls: Map<String, String> = emptyMap(),
    @SerialName("is_local") val local: Boolean = false,
    @SerialName("is_playable") val playable: Boolean? = null,
    val restrictions: Map<String, String>? = null,
    val name: String? = null, val artists: List<ArtistDto> = emptyList(), val album: AlbumDto? = null,
    @SerialName("duration_ms") val duration: Long? = null,
) {
    fun track(): SpotifyTrack? = if (type == "track" && !local && playable != false && restrictions.isNullOrEmpty() &&
        !id.isNullOrBlank() && !uri.isNullOrBlank() && !urls["spotify"].isNullOrBlank())
        SpotifyTrack(id, uri, urls.getValue("spotify"), name, artists.map { SpotifyArtist(it.id, it.name) },
            album?.let { SpotifyAlbum(it.id, it.name, it.releaseDate, it.precision) }, duration) else null
}
@Serializable internal data class PlaylistItemDto(val item: ItemDto? = null,
    @SerialName("is_local") val local: Boolean = false) {
    fun model() = PlaylistItem(if (local) null else item?.track(), local || item?.local == true, item?.type)
}
@Serializable internal data class PageDto(val items: List<PlaylistItemDto?>, val offset: Int, val total: Int, val next: String? = null)
@Serializable internal data class PlaylistDto(val id: String, val name: String, @SerialName("snapshot_id") val snapshot: String)
@Serializable internal data class DeviceDto(val id: String? = null, val name: String,
    @SerialName("is_active") val active: Boolean = false, @SerialName("is_restricted") val restricted: Boolean = false) {
    fun model() = SpotifyDevice(id, name, active, restricted)
}
@Serializable internal data class DevicesDto(val devices: List<DeviceDto>)
@Serializable internal data class PlaybackDto(val device: DeviceDto? = null,
    @SerialName("is_playing") val playing: Boolean,
    @SerialName("progress_ms") val position: Long? = null, val item: ItemDto? = null)
