package sk.vladko.pill.features

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationValueTest {
    @Test
    fun `first distance in the title wins over the text, decimals and units are kept`() {
        assertEquals("200 m", navigationValue("200 m Turn left onto Main St", "1,5 km on A1", "Arrive 23:10"))
        assertEquals("1,5 km", navigationValue("Turn left", "then 1,5 km straight", null))
        assertEquals("300 ft", navigationValue("In 300 ft turn right", null, null))
    }

    @Test
    fun `no distance falls back to the first clock time in the sub text`() {
        assertEquals("23:10", navigationValue("Head south-west on Main St", null, "Arrive 23:10"))
        assertEquals("11:10 PM", navigationValue("Head south", "5 min left", "Arrive 11:10 PM"))
    }

    @Test
    fun `nothing when there is no distance and no time`() {
        assertNull(navigationValue("Head south", "12 min", "Arrive soon"))
    }
}
