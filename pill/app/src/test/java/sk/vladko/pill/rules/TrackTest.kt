package sk.vladko.pill.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackTest {
    private val a = Track("Moon Road", "Lena")
    private val b = Track("Blue Door", "Lena")

    @Test
    fun onlyANewPlayingTrackIsAnnounced() {
        assertTrue(announcesTrack(null, a, playing = true))
        assertFalse(announcesTrack(a, a, playing = true))
        assertFalse(announcesTrack(a, b, playing = false))
        assertTrue(announcesTrack(a, b, playing = true))
    }

    @Test
    fun lineNamesArtistFirst() {
        assertEquals("Lena - Moon Road", a.line)
        assertEquals("Moon Road", Track("Moon Road", "").line)
    }
}
