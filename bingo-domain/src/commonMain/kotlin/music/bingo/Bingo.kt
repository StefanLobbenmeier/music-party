package music.bingo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Implementations must follow all pages and return the explicitly eligible pool. */
interface SongCatalog { suspend fun allEligibleSongs(): List<CatalogSong> }
data class CatalogSong(val id: String, val available: Boolean = true)
interface SongMetadataProvider { suspend fun metadata(songId: String): SongMetadata? }
data class SongMetadata(
    val id: String,
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val release: ReleaseInfo? = null,
)

enum class DatePrecision { YEAR, MONTH, DAY }

/** Original provider value and semantics are retained; album date must not imply song origin. */
data class ReleaseInfo(val value: String, val precision: DatePrecision, val provenance: String, val description: String) {
    fun yearOrNull(): Int? {
        val size = when (precision) { DatePrecision.YEAR -> 4; DatePrecision.MONTH -> 7; DatePrecision.DAY -> 10 }
        if (value.length != size || value.take(4).any { !it.isDigit() }) return null
        if (precision != DatePrecision.YEAR && value.getOrNull(4) != '-') return null
        if (precision == DatePrecision.DAY && value.getOrNull(7) != '-') return null
        val year = value.take(4).toIntOrNull()?.takeIf { it in 1..9999 } ?: return null
        if (precision != DatePrecision.YEAR) {
            val month = value.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            if (precision == DatePrecision.DAY) {
                val day = value.substring(8, 10).toIntOrNull() ?: return null
                val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
                val lastDay = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
                if (day !in 1..lastDay) return null
            }
        }
        return year
    }
}

interface LyricsProvider { suspend fun prompt(songId: String): LyricsPrompt? }
enum class LyricsFormat { MISSING_WORDS, RECALL_A_LINE }
data class LyricsPrompt(val prompt: String, val answer: String, val format: LyricsFormat, val attribution: String)

/** Provider-neutral audio boundary. The domain has no Spotify player implementation. */
interface BingoPromptPlayer { suspend fun play(songId: String); suspend fun stop() }

enum class BingoCategory(val label: String) {
    ARTIST("Artist"), SONG_TITLE("Song title"), LYRICS("Lyrics"),
    RELEASE_YEAR("Release year ±3"), RELEASE_DECADE("Release decade"),
}
enum class RoundPhase { PREPARING, QUESTION, REVEALED, EXHAUSTED }

/** solution is null until reveal(), so unrevealed answer text is not exposed to UI semantics. */
data class BingoRoundState(
    val phase: RoundPhase,
    val roundNumber: Int = 0,
    val category: BingoCategory? = null,
    val question: String? = null,
    val solution: BingoSolution? = null,
)
data class BingoSolution(val answer: String, val acceptedAnswer: String? = null, val attribution: String? = null)
private data class Song(val metadata: SongMetadata, val lyrics: LyricsPrompt?)
private data class Round(val songId: String, val category: BingoCategory, val question: String, val solution: BingoSolution)

/**
 * Selects one category at random for each song and draws without replacement until the
 * configured catalog is exhausted. Inject Random to make draws reproducible. Lyrics are
 * disabled unless an authorized provider and an explicit prompt-format choice are supplied.
 */
class BingoSession(
    private val catalog: SongCatalog,
    private val metadataProvider: SongMetadataProvider,
    private val random: Random = Random.Default,
    private val lyricsProvider: LyricsProvider? = null,
    private val promptPlayer: BingoPromptPlayer? = null,
    private val lyricsEnabled: Boolean = false,
) {
    private val mutableState = MutableStateFlow(BingoRoundState(RoundPhase.PREPARING))
    val state: StateFlow<BingoRoundState> = mutableState.asStateFlow()
    private val remaining = mutableListOf<Song>()
    private var active: Round? = null
    private var nextNumber = 1

    suspend fun start(): BingoRoundState {
        mutableState.value = BingoRoundState(RoundPhase.PREPARING)
        active = null
        nextNumber = 1
        remaining.clear()
        val ids = catalog.allEligibleSongs().asSequence()
            .filter { it.available && it.id.isNotBlank() }.distinctBy { it.id }.map { it.id }.toList()
        for (id in ids) {
            val metadata = metadataProvider.metadata(id)?.takeIf { it.id.isNotBlank() } ?: continue
            val lyric = if (lyricsEnabled) lyricsProvider?.prompt(id)?.takeIf {
                it.prompt.isNotBlank() && it.answer.isNotBlank() && it.attribution.isNotBlank()
            } else null
            if (eligibleCategories(metadata, lyric, lyricsEnabled).isNotEmpty()) remaining += Song(metadata, lyric)
        }
        return nextRound()
    }

    suspend fun nextRound(): BingoRoundState {
        promptPlayer?.stop()
        active = null
        if (remaining.isEmpty()) return BingoRoundState(RoundPhase.EXHAUSTED, nextNumber - 1).also { mutableState.value = it }
        val song = remaining.removeAt(random.nextInt(remaining.size))
        val category = eligibleCategories(song.metadata, song.lyrics, lyricsEnabled).random(random)
        val round = makeRound(song, category)
        active = round
        return BingoRoundState(RoundPhase.QUESTION, nextNumber++, round.category, round.question).also { mutableState.value = it }
    }

    fun reveal(): BingoRoundState {
        val round = active ?: return mutableState.value
        if (mutableState.value.phase != RoundPhase.QUESTION) return mutableState.value
        return mutableState.value.copy(phase = RoundPhase.REVEALED, solution = round.solution).also { mutableState.value = it }
    }

    suspend fun playPrompt() {
        val round = active ?: error("There is no active round")
        (promptPlayer ?: error("No approved prompt player is configured")).play(round.songId)
    }

    suspend fun stopPrompt() { promptPlayer?.stop() }

    private fun makeRound(song: Song, category: BingoCategory): Round {
        val meta = song.metadata
        val release = meta.release
        val year = release?.yearOrNull()
        val (question, solution) = when (category) {
            BingoCategory.SONG_TITLE -> "Name the song title" to BingoSolution(meta.title!!.trim())
            BingoCategory.ARTIST -> "Name the artist" to BingoSolution(meta.artists.filter(String::isNotBlank).joinToString(", "))
            BingoCategory.RELEASE_YEAR -> {
                val actual = checkNotNull(year); val info = checkNotNull(release)
                val range = "${(actual - 3).coerceAtLeast(1)}–${(actual + 3).coerceAtMost(9999)}"
                "Guess the ${info.description.lowercase()}" to BingoSolution("${info.value} (${info.description}) · ${info.provenance}", range, info.provenance)
            }
            BingoCategory.RELEASE_DECADE -> {
                val actual = checkNotNull(year); val info = checkNotNull(release); val decade = actual / 10 * 10
                "Name the ${info.description.lowercase()} decade" to BingoSolution("${decade}s (${info.description}) · ${info.provenance}", "$decade–${(decade + 9).coerceAtMost(9999)}", info.provenance)
            }
            BingoCategory.LYRICS -> {
                val lyric = checkNotNull(song.lyrics)
                lyric.prompt to BingoSolution(lyric.answer, attribution = lyric.attribution)
            }
        }
        return Round(meta.id, category, question, solution)
    }

    private fun eligibleCategories(meta: SongMetadata, lyric: LyricsPrompt?, allowLyrics: Boolean): List<BingoCategory> = buildList {
        if (!meta.title.isNullOrBlank()) add(BingoCategory.SONG_TITLE)
        if (meta.artists.any { it.isNotBlank() }) add(BingoCategory.ARTIST)
        if (meta.release?.yearOrNull() != null) { add(BingoCategory.RELEASE_YEAR); add(BingoCategory.RELEASE_DECADE) }
        if (allowLyrics && lyric != null) add(BingoCategory.LYRICS)
    }
}
