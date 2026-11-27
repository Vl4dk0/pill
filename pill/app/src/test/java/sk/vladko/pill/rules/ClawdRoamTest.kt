package sk.vladko.pill.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClawdRoamTest {
    @Test fun clawdAlwaysStandsOnABrickItWroteAndLeavesNoneBehind() {
        val bricks = (0 until ClawdRoam.BRICKS)
        assertTrue(bricks.all { ClawdRoam.brick(it, 0f).width == 0 && ClawdRoam.brick(it, 1f).width == 0 })
        assertTrue(bricks.all { ClawdRoam.brick(it, 0.6f).width == ClawdRoam.BRICK_WIDTH }, "every brick is there while it looks around")
        for (i in 0..1000) {
            val p = i / 1000f
            val spot = ClawdRoam.at(p)
            if (spot.phase == ClawdPhase.Down || spot.phase == ClawdPhase.Up) continue
            if (spot.x == 0 && spot.y == 0) continue
            val under = spot.x / ClawdRoam.RUN
            assertEquals(1 + ClawdRoam.RISE * under, spot.y, "$p")
            assertTrue(ClawdRoam.brick(under, p).width > 0, "no brick under it at $p")
        }
        assertEquals(ClawdRoam.at(1f).x, 0)
    }
}
