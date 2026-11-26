package sk.vladko.pill.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutTest {
    private val phone = ScreenGeometry(411f, Hole(205.5f, 22f, 20f, 20f), 24f)
    private val settings = Settings()

    private object Note : PillEvent {
        override val kind = "message"
        override val label = "hi"
        override val accent = Accent.Fixed(0)
        override val key = "k"
        override val priority = 2
        override val showMs = 4_000L

        override fun open() {}
    }

    private fun view(
        shown: Shown = Shown.Idle,
        size: Size = Size.Compact,
        collapsed: Boolean = false,
    ) = PillView(shown, size, visible = true, collapsed = collapsed, typing = false, waiting = null, activities = emptyList())

    private val wants = listOf(Want.Idle, Want.Compact(158f), Want.CompactSides(60f, 140f), Want.Tall(76f), Want.Tall(0f, 300f))

    @Test fun idleIsCentredOnTheHoleAndTwoDpFromTheTop() {
        val f = assertNotNull(pillFrame(phone, settings, view(), Want.Idle))
        assertEquals(205.5f, f.left + f.width / 2f, 0.01f)
        assertEquals(DEFAULT_IDLE_WIDTH_DP, f.width)
        assertEquals(30f, f.height)
        assertEquals(7f, f.top)
        val tall = assertNotNull(pillFrame(phone, settings.copy(idleHeightDp = 56f), view(), Want.Idle))
        assertEquals(2f, tall.top)
    }

    @Test fun everyFrameIsInsideTheScreenWithAHoleNearTheEdge() {
        for (cx in listOf(15f, 60f, 350f, 400f)) {
            val screen = phone.copy(hole = Hole(cx, 22f, 20f, 20f))
            for (want in wants) {
                val f = assertNotNull(pillFrame(screen, settings, view(), want))
                assertTrue(f.left >= 8f && f.left + f.width <= 411f - 8f + 0.01f, "$cx $want $f")
                assertTrue(f.crownLeft >= -0.01f && f.crownLeft + f.crownWidth <= f.width + 0.01f, "$cx $want $f")
            }
        }
    }

    @Test fun noHoleMeansTopCentre() {
        val landscape = ScreenGeometry(900f, null, 0f)
        val f = assertNotNull(pillFrame(landscape, settings, view(), Want.Compact(158f)))
        assertEquals(450f, f.left + f.width / 2f, 0.01f)
        assertEquals(22f - COMPACT_HEIGHT_DP / 2f, f.top)
    }

    @Test fun largeIsAtMost460WideWith8DpMarginsAndCrownAtMost172() {
        val wide =
            assertNotNull(
                pillFrame(ScreenGeometry(900f, null, 24f), settings.copy(idleWidthDp = 240f), view(size = Size.Large), Want.Tall(120f)),
            )
        assertEquals(460f, wide.width)
        assertEquals(172f, wide.crownWidth)
        val f = assertNotNull(pillFrame(phone, settings, view(size = Size.Large), Want.Tall(120f)))
        assertEquals(8f, f.left)
        assertEquals(411f - 16f, f.width)
        assertEquals(DEFAULT_IDLE_WIDTH_DP, f.crownWidth)
        assertEquals(f.shelfTop + SHELF_TOP_PAD_DP + 120f + SHELF_BOTTOM_PAD_DP, f.height)
        assertEquals(300f, assertNotNull(pillFrame(phone, settings, view(size = Size.Large), Want.Tall(120f, 300f))).height)
    }

    @Test fun collapsedIdleHasNoFrameButAnEventHas() {
        assertNull(pillFrame(phone, settings, view(collapsed = true), Want.Idle))
        assertNotNull(pillFrame(phone, settings, view(shown = Shown.Event(Note), collapsed = true), Want.Compact(158f)))
        assertNull(pillFrame(phone, settings, view().copy(visible = false), Want.Idle))
    }

    @Test fun holeInfoKeepsContentClearOfTheHole() {
        val f = assertNotNull(pillFrame(phone, settings, view(shown = Shown.Event(Note)), Want.CompactSides(60f, 140f)))
        val h = holeInfo(phone, f)
        assertEquals(44f, h.hole)
        // The gap [leading, leading + hole] inside the box covers the hole.
        assertTrue(f.left + h.leading <= 205.5f - 10f && f.left + h.leading + h.hole >= 205.5f + 10f)
        assertEquals(60f, 205.5f - f.left, 0.01f)
        assertEquals(140f, f.left + f.width - 205.5f, 0.01f)
    }
}
