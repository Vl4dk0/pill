package sk.vladko.pill

import org.json.JSONArray
import org.json.JSONObject
import sk.vladko.pill.agent.ActionStatus
import sk.vladko.pill.agent.PxRect
import sk.vladko.pill.agent.Snapshot
import sk.vladko.pill.agent.StateDump
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.ActivityInfo
import sk.vladko.pill.rules.Choice
import sk.vladko.pill.rules.Command
import sk.vladko.pill.rules.Hole
import sk.vladko.pill.rules.IdleSlot
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.rules.PillEvent
import sk.vladko.pill.rules.PillView
import sk.vladko.pill.rules.ScreenGeometry
import sk.vladko.pill.rules.ScreenState
import sk.vladko.pill.rules.Settings
import sk.vladko.pill.rules.Shown
import sk.vladko.pill.rules.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentTest {
    private val known = { id: String -> id in setOf("torch", "lock", "dnd") || (id.startsWith("app:") && id.length > 4) }

    private fun JSONObject.keyNames(): Set<String> = keys().asSequence().toSet()

    @Test
    fun commandsParse() {
        assertEquals(
            Command.SetGesture(IdleSlot.TapLeft, Choice.AppMenu),
            Command.parse("sk.vladko.pill.SET_GESTURE", mapOf("slot" to "tapLeft", "choice" to "appmenu")::get, known).getOrThrow(),
        )
        assertEquals(
            Command.SetGesture(IdleSlot.HoldRight, Choice.Action("torch")),
            Command.parse("sk.vladko.pill.SET_GESTURE", mapOf("slot" to "holdRight", "choice" to "torch")::get, known).getOrThrow(),
        )
        assertEquals(
            Command.SetGesture(IdleSlot.Volume, Choice.Action("app:com.example")),
            Command.parse("sk.vladko.pill.SET_GESTURE", mapOf("slot" to "volume", "choice" to "app:com.example")::get, known).getOrThrow(),
        )
        assertEquals(
            Command.SetActionsMenu(listOf("torch", "lock", "app:com.example")),
            Command.parse("sk.vladko.pill.SET_ACTIONS_MENU", mapOf("ids" to "torch,lock,app:com.example")::get, known).getOrThrow(),
        )
        assertEquals(
            Command.SetAppMenu(listOf("com.a", "com.b")),
            Command.parse("sk.vladko.pill.SET_APP_MENU", mapOf("pkgs" to "com.a,com.b")::get, known).getOrThrow(),
        )
        assertEquals(
            Command.ClearNotifications,
            Command.parse("sk.vladko.pill.CLEAR_NOTIFICATIONS", { null }, known).getOrThrow(),
        )
        assertEquals(
            Command.RunAction("torch"),
            Command.parse("sk.vladko.pill.RUN_ACTION", mapOf("id" to "torch")::get, known).getOrThrow(),
        )
    }

    @Test
    fun badSlotChoiceAndActionAreErrors() {
        val badSlot = Command.parse("sk.vladko.pill.SET_GESTURE", mapOf("slot" to "bad", "choice" to "none")::get, known)
        assertTrue(badSlot.isFailure)
        assertEquals("slot is one of tapLeft, tapRight, holdLeft, holdRight, volume", badSlot.exceptionOrNull()?.message)

        val badChoice = Command.parse("sk.vladko.pill.SET_GESTURE", mapOf("slot" to "tapLeft", "choice" to "bogus")::get, known)
        assertTrue(badChoice.isFailure)
        assertEquals("unknown choice 'bogus'", badChoice.exceptionOrNull()?.message)

        val badAction = Command.parse("sk.vladko.pill.RUN_ACTION", mapOf("id" to "bogus")::get, known)
        assertTrue(badAction.isFailure)
        assertEquals("unknown action 'bogus'", badAction.exceptionOrNull()?.message)

        val unknown = Command.parse("sk.vladko.pill.NOPE", { null }, known)
        assertTrue(unknown.isFailure)
        assertEquals("unknown command", unknown.exceptionOrNull()?.message)
    }

    @Test
    fun unknownMenuIdIsAnErrorBeforeSaving() {
        val res = Command.parse("sk.vladko.pill.SET_ACTIONS_MENU", mapOf("ids" to "torch,bogus,lock")::get, known)
        assertTrue(res.isFailure)
        assertEquals("unknown action 'bogus'", res.exceptionOrNull()?.message)
    }

    @Test
    fun stateJsonHasExactlyTheContractKeys() {
        val ev =
            object : PillEvent {
                override val kind = "message"
                override val key = "k1"
                override val priority = 2
                override val showMs = 4_000L
                override val label = "Alice: hi"
                override val accent = Accent.App("com.chat")

                override fun open() {}
            }
        val act =
            object : PillActivity {
                override val id = "com.music"
                override val kind = "music"
                override val priority = 1
                override val since = 10L
                override val label = "Song - Artist playing"
                override val accent = Accent.App("com.music")

                override fun open() {}
            }
        val seekJson =
            JSONObject()
                .put("bounds", JSONArray(listOf(10, 20, 200, 50)))
                .put("supported", true)
                .put("position", 1000L)
                .put("duration", 60_000L)
        val snap =
            Snapshot(
                view =
                    PillView(
                        shown = Shown.Activity(act),
                        size = Size.Large,
                        visible = true,
                        collapsed = false,
                        typing = false,
                        waiting = ev,
                        activities = listOf(ActivityInfo(act, hidden = false)),
                    ),
                settings = Settings.parse(emptyMap(), Choice.Nothing).copy(playersSeen = listOf("com.music")),
                boundsPx = PxRect(10, 10, 500, 300),
                screen = ScreenState(),
                geometry = ScreenGeometry(412f, Hole(206f, 22f, 20f, 20f), 42f),
                density = 2.625f,
                buttons = mapOf("playpause" to PxRect(100, 100, 150, 150)),
                reported = mapOf("seek" to seekJson),
                glowRunning = true,
                glowColor = "#FFFFFF",
                torchOn = false,
                popupsOff = true,
                popupsPermission = true,
                statusIconsHidden = true,
                actions = listOf(ActionStatus("torch", "toggle", "switches", null, false)),
            )

        val json = StateDump.json(snap)
        val expectedTop =
            setOf(
                "shown",
                "size",
                "menu",
                "collapsed",
                "torch",
                "gestures",
                "appMenu",
                "actionsMenu",
                "actions",
                "visible",
                "hiddenBy",
                "bounds",
                "waiting",
                "activities",
                "buttons",
                "popupsOff",
                "popupsPermission",
                "hole",
                "idleWidth",
                "idleHeight",
                "pet",
                "petState",
                "petAnimal",
                "glow",
                "statusIconsHidden",
                "players",
                "fastApps",
                "quickActions",
                "seek",
            )
        assertEquals(expectedTop, json.keyNames())
        assertEquals(setOf("type", "kind", "id", "label"), json.getJSONObject("shown").keyNames())
        assertEquals(setOf("tapLeft", "tapRight", "holdLeft", "holdRight", "volume"), json.getJSONObject("gestures").keyNames())
        assertEquals(setOf("running", "color"), json.getJSONObject("glow").keyNames())
        assertEquals(setOf("bounds", "supported", "position", "duration"), json.getJSONObject("seek").keyNames())
        assertEquals(setOf("id", "kind", "group", "available", "reason", "on"), json.getJSONArray("actions").getJSONObject(0).keyNames())
        assertEquals(setOf("kind", "key", "label"), json.getJSONObject("waiting").keyNames())
        assertEquals(setOf("id", "kind", "hidden", "ringing", "label"), json.getJSONArray("activities").getJSONObject(0).keyNames())
        assertEquals(setOf("pkg", "shown"), json.getJSONArray("players").getJSONObject(0).keyNames())

        // Also check shown keys for Event and Idle, and that seek is null outside Large.
        val evJson =
            StateDump.json(
                Snapshot(
                    view = snap.view.copy(shown = Shown.Event(ev), size = Size.Compact, waiting = null),
                    settings = snap.settings,
                    boundsPx = snap.boundsPx,
                    screen = snap.screen,
                    geometry = snap.geometry,
                    density = snap.density,
                    buttons = emptyMap(),
                    reported = snap.reported,
                    glowRunning = false,
                    glowColor = "",
                    torchOn = false,
                    popupsOff = true,
                    popupsPermission = true,
                    statusIconsHidden = true,
                    actions = snap.actions,
                ),
            )
        assertEquals(setOf("type", "kind", "key", "label"), evJson.getJSONObject("shown").keyNames())
        assertTrue(evJson.isNull("seek"))
    }
}
