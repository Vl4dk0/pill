package sk.vladko.pill.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTest {
    @Test fun defaultsFromAnEmptyMap() {
        val s = Settings.parse(emptyMap(), Choice.Nothing)
        assertEquals(DEFAULT_IDLE_WIDTH_DP, s.idleWidthDp)
        assertNull(s.idleHeightDp)
        assertEquals(false, s.collapsed)
        assertEquals(ThemeMode.System, s.theme)
        assertEquals(true, s.arrivalGlow)
        assertEquals(false, Settings.parse(mapOf("arrival_glow" to false), Choice.Nothing).arrivalGlow)
        assertEquals(true, Settings.parse(mapOf("arrival_glow" to "x"), Choice.Nothing).arrivalGlow)
        assertEquals(
            mapOf(
                IdleSlot.TapLeft to Choice.AppMenu,
                IdleSlot.TapRight to Choice.ActionsMenu,
                IdleSlot.HoldLeft to Choice.Recents,
                IdleSlot.HoldRight to Choice.Nothing,
                IdleSlot.Volume to Choice.HideIdle,
            ),
            s.gestures,
        )
        assertEquals(emptyList(), s.appMenu)
        assertEquals(DEFAULT_ACTIONS_MENU, s.actionsMenu)
        assertEquals(emptyList(), s.playersSeen)
        assertEquals(emptySet(), s.playersOff)
    }

    @Test fun valuesAreClampedAndListsLimitedToFour() {
        val low =
            Settings.parse(
                mapOf(
                    "idle_width_dp" to 5f,
                    "idle_height_dp" to -1f,
                    "fast_apps" to "a, ,b,a,c,d,e",
                    "quick_actions" to "t1,t2,,t2,t3,t4,t5",
                ),
                Choice.Nothing,
            )
        assertEquals(MIN_IDLE_WIDTH_DP, low.idleWidthDp)
        assertNull(low.idleHeightDp)
        assertEquals(listOf("a", "b", "c", "d"), low.appMenu)
        assertEquals(listOf("t1", "t2", "t3", "t4"), low.actionsMenu)

        val high =
            Settings.parse(
                mapOf(
                    "idle_width_dp" to 999f,
                    "idle_height_dp" to 120f,
                    "quick_actions" to "",
                ),
                Choice.Nothing,
            )
        assertEquals(MAX_IDLE_WIDTH_DP, high.idleWidthDp)
        assertEquals(MAX_IDLE_HEIGHT_DP, high.idleHeightDp)
        assertEquals(emptyList(), high.actionsMenu)
    }

    @Test fun everyChoiceCodeRoundTripsAndABadCodeFallsBack() {
        val choices =
            listOf(
                Choice.AppMenu,
                Choice.ActionsMenu,
                Choice.Recents,
                Choice.HideIdle,
                Choice.Nothing,
                Choice.Action("torch"),
                Choice.Action("app:com.example.app"),
            )
        choices.forEach { c ->
            assertEquals(c, Choice.parse(c.code))
        }
        assertNull(Choice.parse("bogus"))
        assertNull(Choice.parse("action:"))
        val s =
            Settings.parse(
                mapOf("gesture_tapLeft" to "bogus", "gesture_tapRight" to "action:torch"),
                Choice.Nothing,
            )
        assertEquals(Choice.AppMenu, s.gestures[IdleSlot.TapLeft])
        assertEquals(Choice.Action("torch"), s.gestures[IdleSlot.TapRight])
    }

    @Test fun parseOfEncodeIsTheSame() {
        val custom =
            Settings(
                idleWidthDp = 180f,
                idleHeightDp = 36f,
                collapsed = true,
                theme = ThemeMode.Light,
                gestures =
                    mapOf(
                        IdleSlot.TapLeft to Choice.Action("torch"),
                        IdleSlot.TapRight to Choice.Nothing,
                        IdleSlot.HoldLeft to Choice.AppMenu,
                        IdleSlot.HoldRight to Choice.Action("app:com.example"),
                        IdleSlot.Volume to Choice.Recents,
                    ),
                appMenu = listOf("com.a", "com.b"),
                actionsMenu = listOf("torch", "dnd"),
                playersSeen = listOf("p1", "p2"),
                playersOff = setOf("p2"),
            )
        assertEquals(custom, Settings.parse(custom.encode(), Choice.Nothing))

        val withNullHeightAndEmptyMenus =
            Settings(
                idleHeightDp = null,
                gestures = Settings.defaultGestures(Choice.Nothing),
                appMenu = emptyList(),
                actionsMenu = emptyList(),
            )
        assertEquals(withNullHeightAndEmptyMenus, Settings.parse(withNullHeightAndEmptyMenus.encode(), Choice.Action("app:wallet")))
    }

    @Test fun holdRightDefaultIsUsedOnlyWhenNothingIsStored() {
        val wallet = Choice.Action("app:com.google.android.apps.walletnfcrel")
        assertEquals(wallet, Settings.parse(emptyMap(), wallet).gestures[IdleSlot.HoldRight])
        assertEquals(Choice.Nothing, Settings.parse(mapOf("gesture_holdRight" to "none"), wallet).gestures[IdleSlot.HoldRight])
        assertEquals(
            Choice.Action("torch"),
            Settings.parse(mapOf("gesture_holdRight" to "action:torch"), wallet).gestures[IdleSlot.HoldRight],
        )
        assertEquals(wallet, Settings.parse(mapOf("gesture_holdRight" to "invalid"), wallet).gestures[IdleSlot.HoldRight])
    }

    @Test fun petSwitchMigratesFromTheOldIdleViewList() {
        assertTrue(Settings.parse(emptyMap(), Choice.Nothing).pet)
        assertTrue(Settings.parse(mapOf("idle_views" to "steps,pet", "idle_view" to "steps"), Choice.Nothing).pet)
        assertFalse(Settings.parse(mapOf("idle_views" to "empty,steps,bogus"), Choice.Nothing).pet)
        assertTrue(Settings.parse(mapOf("idle_views" to "empty", "pet" to true), Choice.Nothing).pet)
        assertFalse(Settings.parse(mapOf("pet" to false), Choice.Nothing).pet)
    }
}
