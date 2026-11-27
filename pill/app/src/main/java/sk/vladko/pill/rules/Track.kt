package sk.vladko.pill.rules

// Pure Kotlin: decides when a song is announced in the pill.

/** What a player says it plays. */
data class Track(
    val title: String,
    val artist: String,
) {
    /** "Artist - Title", or only the title when there is no artist. */
    val line: String get() = if (artist.isBlank()) title else "$artist - $title"
}

/**
 * True when [current] is playing and is not the track already announced for this player ([announced], null when none).
 * Pause, resume, seeking and progress keep the same track, so they stay quiet.
 */
fun announcesTrack(
    announced: Track?,
    current: Track,
    playing: Boolean,
): Boolean = playing && current != announced
