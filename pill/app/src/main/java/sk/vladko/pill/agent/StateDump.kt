package sk.vladko.pill.agent

import org.json.JSONArray
import org.json.JSONObject
import sk.vladko.pill.rules.Choice
import sk.vladko.pill.rules.IdleSlot
import sk.vladko.pill.rules.PillView
import sk.vladko.pill.rules.ScreenGeometry
import sk.vladko.pill.rules.ScreenState
import sk.vladko.pill.rules.Settings
import sk.vladko.pill.rules.Shown
import sk.vladko.pill.rules.Size
import sk.vladko.pill.rules.idleHeight

/** Screen-pixel rectangle without an Android dependency, so StateDump runs in plain JVM tests. */
data class PxRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        val Empty = PxRect(0, 0, 0, 0)
    }
}

class ActionStatus(
    val id: String,
    val kind: String,
    val group: String,
    val reason: String?,
    val on: Boolean?,
)

class Snapshot(
    val view: PillView,
    val settings: Settings,
    val boundsPx: PxRect,
    val screen: ScreenState,
    val geometry: ScreenGeometry,
    val density: Float,
    val buttons: Map<String, PxRect>,
    val reported: Map<String, JSONObject?>,
    val glowRunning: Boolean,
    val glowColor: String,
    val torchOn: Boolean,
    val popupsOff: Boolean,
    val popupsPermission: Boolean,
    val statusIconsHidden: Boolean,
    val actions: List<ActionStatus>,
    /** The pet's current state id, "" before it first showed. */
    val petState: String = "",
)

/** The JSON that `pill.sh state` prints. Field names are fixed; later work only adds fields. */
object StateDump {
    private fun rect(r: PxRect) = JSONArray(listOf(r.left, r.top, r.right, r.bottom))

    fun json(s: Snapshot): JSONObject {
        val v = s.view
        val shown =
            when (val cur = v.shown) {
                Shown.Idle -> JSONObject().put("type", "idle")
                is Shown.Event ->
                    JSONObject()
                        .put(
                            "type",
                            "event",
                        ).put("kind", cur.event.kind)
                        .put("key", cur.event.key)
                        .put("label", cur.event.label)
                is Shown.Activity ->
                    JSONObject()
                        .put(
                            "type",
                            "activity",
                        ).put("kind", cur.activity.kind)
                        .put("id", cur.activity.id)
                        .put("label", cur.activity.label)
            }
        val hole = s.geometry.anchor
        // Back to whole pixels as Android reported them: dp to px loses a little in Float, the 0.001 keeps a whole pixel whole.
        val d = s.density
        val cfg = s.settings
        return JSONObject()
            .put("shown", shown)
            .put("size", v.size.name.lowercase())
            .put("visible", v.visible)
            .put("hiddenBy", s.screen.hiddenBy)
            .put("bounds", rect(if (v.drawsNothing) PxRect.Empty else s.boundsPx))
            .put("collapsed", v.collapsed)
            .put("torch", s.torchOn)
            .put(
                "menu",
                when (v.size) {
                    Size.FastMenu -> "fast"
                    Size.QuickMenu -> "quick"
                    Size.RecentApps, Size.RecentMenu -> "recent"
                    else -> ""
                },
            ).put(
                "gestures",
                JSONObject().also { o ->
                    IdleSlot.entries.forEach { o.put(it.id, (cfg.gestures[it] ?: Choice.Nothing).code) }
                },
            ).put("waiting", v.waiting?.let { JSONObject().put("kind", it.kind).put("key", it.key).put("label", it.label) })
            .put(
                "activities",
                JSONArray(
                    v.activities.map {
                        JSONObject()
                            .put("id", it.activity.id)
                            .put("kind", it.activity.kind)
                            .put("hidden", it.hidden)
                            .put("ringing", it.activity.ringing)
                            .put("label", it.activity.label)
                    },
                ),
            ).put("buttons", JSONObject().also { o -> s.buttons.forEach { (k, b) -> o.put(k, rect(b)) } })
            .put("seek", (if (v.size == Size.Large) s.reported["seek"] else null) ?: JSONObject.NULL)
            .put("popupsOff", s.popupsOff)
            .put("popupsPermission", s.popupsPermission)
            .put(
                "hole",
                JSONArray(
                    listOf(hole.cx - hole.w / 2, hole.cy - hole.h / 2, hole.cx + hole.w / 2, hole.cy + hole.h / 2).map {
                        (
                            it *
                                d +
                                0.001f
                        ).toInt()
                    },
                ),
            ).put("idleWidth", cfg.idleWidthDp.toDouble())
            .put("idleHeight", idleHeight(cfg.idleHeightDp, hole.h).toDouble())
            .put("pet", cfg.pet)
            .put("petState", s.petState)
            .put("petAnimal", cfg.petAnimal)
            .put("glow", JSONObject().put("running", s.glowRunning).put("color", s.glowColor))
            .put("statusIconsHidden", s.statusIconsHidden)
            .put("players", JSONArray(cfg.playersSeen.map { JSONObject().put("pkg", it).put("shown", it !in cfg.playersOff) }))
            .put("fastApps", JSONArray(cfg.appMenu))
            .put("quickActions", JSONArray(cfg.actionsMenu))
            .put("actionsMenu", JSONArray(cfg.actionsMenu))
            .put("appMenu", JSONArray(cfg.appMenu))
            .put(
                "actions",
                JSONArray(
                    s.actions.map { a ->
                        JSONObject()
                            .put("id", a.id)
                            .put("kind", a.kind)
                            .put("group", a.group)
                            .put("available", a.reason == null)
                            .put("reason", a.reason ?: "")
                            .put("on", a.on ?: JSONObject.NULL)
                    },
                ),
            )
    }
}
