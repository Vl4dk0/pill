package sk.vladko.pill.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class VolumePairTest {
    @Test fun singleKeyPasses() {
        val vp = VolumePair()
        assertEquals(Verdict.Pass, vp.onKey(up = false, volumeUp = true, repeat = 0, timeMs = 100))
        assertEquals(Verdict.Pass, vp.onKey(up = false, volumeUp = true, repeat = 1, timeMs = 200))
        assertEquals(Verdict.Pass, vp.onKey(up = true, volumeUp = true, repeat = 0, timeMs = 300))
    }

    @Test fun secondKeyWithin150msFiresOnceAndIsSwallowedUntilUp() {
        val vp = VolumePair()
        assertEquals(Verdict.Pass, vp.onKey(up = false, volumeUp = true, repeat = 0, timeMs = 100))
        assertEquals(Verdict.SwallowAndFire, vp.onKey(up = false, volumeUp = false, repeat = 0, timeMs = 250))
        assertEquals(Verdict.Swallow, vp.onKey(up = false, volumeUp = false, repeat = 1, timeMs = 280))
        assertEquals(Verdict.Swallow, vp.onKey(up = true, volumeUp = false, repeat = 0, timeMs = 320))
        assertEquals(Verdict.Pass, vp.onKey(up = true, volumeUp = true, repeat = 0, timeMs = 330))
        assertEquals(Verdict.Pass, vp.onKey(up = false, volumeUp = false, repeat = 0, timeMs = 500))
    }

    @Test fun after150msThereIsNoPair() {
        val vp = VolumePair()
        assertEquals(Verdict.Pass, vp.onKey(up = false, volumeUp = true, repeat = 0, timeMs = 100))
        assertEquals(Verdict.Pass, vp.onKey(up = false, volumeUp = false, repeat = 0, timeMs = 251))
        assertEquals(Verdict.Pass, vp.onKey(up = true, volumeUp = false, repeat = 0, timeMs = 300))
        assertEquals(Verdict.Pass, vp.onKey(up = true, volumeUp = true, repeat = 0, timeMs = 310))
    }

    @Test fun eitherOrderWorks() {
        val vp1 = VolumePair()
        assertEquals(Verdict.Pass, vp1.onKey(up = false, volumeUp = true, repeat = 0, timeMs = 100))
        assertEquals(Verdict.SwallowAndFire, vp1.onKey(up = false, volumeUp = false, repeat = 0, timeMs = 150))

        val vp2 = VolumePair()
        assertEquals(Verdict.Pass, vp2.onKey(up = false, volumeUp = false, repeat = 0, timeMs = 100))
        assertEquals(Verdict.SwallowAndFire, vp2.onKey(up = false, volumeUp = true, repeat = 0, timeMs = 150))
    }
}
