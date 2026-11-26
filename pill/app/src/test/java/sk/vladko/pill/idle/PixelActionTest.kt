package sk.vladko.pill.idle

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PixelActionTest {
    private val palette = mapOf('b' to 1)
    private val ball = PixelSprite("ball", "bB\nbb")

    private fun fails(
        why: String,
        build: () -> Unit,
    ) {
        val e = assertFailsWith<IllegalArgumentException> { build() }
        assertTrue(why in e.message.orEmpty(), e.message)
    }

    @Test fun badDataIsNamedAtBuildAndTheMonkeysRoamIsValid() {
        MonkeyRoam
        val keys = "0 sit 10 19 0 smooth\n1 stand 10 19 0 line"
        fails("row 2 has 1 cells") { PixelAction(palette, listOf(PixelSprite("s", "bB\nb")), keys) }
        fails("letter 'x' is not in the palette") { PixelAction(palette, listOf(PixelSprite("s", "bB\nxb")), keys) }
        fails("exactly one UPPER-case pin") { PixelAction(palette, listOf(PixelSprite("s", "bb")), keys) }
        fails("needs 6 columns") { PixelAction(palette, listOf(ball), "0 sit 10 19 smooth\n1 stand 10 19 0 line") }
        fails("unknown sprite 'bal'") { PixelAction(palette, listOf(ball), "0 bal 10 19 0 smooth\n1 stand 10 19 0 line") }
        fails(
            "is not after",
        ) { PixelAction(palette, listOf(ball), "0 sit 10 19 0 smooth\n0.5 ball 1 1 0 in\n0.4 ball 1 1 0 in\n1 sit 1 1 0 line") }
        fails("unknown ease") { PixelAction(palette, listOf(ball), "0 sit 10 19 0 fast\n1 stand 10 19 0 line") }
    }
}
