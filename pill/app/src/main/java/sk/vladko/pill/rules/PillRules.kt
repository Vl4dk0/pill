package sk.vladko.pill.rules

// Pure Kotlin: decides what the pill shows. No Android classes, no clock (time is passed in), so plain JVM tests run it.

/** [hiddenIds] holds the ids of activities the owner flicked away; the caller keeps it, so a new [PillRules] still hides them. */
class PillRules(
    private val hiddenIds: MutableSet<String> = mutableSetOf(),
) {
    companion object {
        const val WAIT_LIMIT_MS = 5_000L
        const val MIN_VISIBLE_MS = 2_000L
        const val LARGE_IDLE_MS = 5_000L
    }

    private class Waiting(
        val event: PillEvent,
        val since: Long,
    )

    private class Act(
        var activity: PillActivity,
    )

    private var event: PillEvent? = null
    private var eventAt = 0L
    private var eventShownAt = 0L
    private var eventExpires = 0L
    private var eventPausedAt: Long? = null
    private val waiting = mutableListOf<Waiting>()
    private val acts = mutableListOf<Act>()
    private var selectedId: String? = null
    private var size = Size.Compact
    private var largeFor: String? = null
    private var lastTouch = 0L
    private var keyboard = false
    private var screen = Screen.Normal
    private var foreground: String? = null
    private var gestures: Map<IdleSlot, Choice> =
        mapOf(
            IdleSlot.TapLeft to Choice.AppMenu,
            IdleSlot.TapRight to Choice.ActionsMenu,
            IdleSlot.HoldLeft to Choice.Recents,
            IdleSlot.HoldRight to Choice.Nothing,
            IdleSlot.Volume to Choice.HideIdle,
        )
    private var collapsed = false

    /** The key the last arrival was for; null while idle. */
    private var arrivedKey: String? = null

    /** Activities that already had their arrival while they live. */
    private val arrivedActs = HashSet<String>()

    /** The one entry: applies [input] and everything time makes due at [now]; returns what the host must do. */
    fun handle(
        input: Input,
        now: Long,
    ): List<Effect> {
        val out = mutableListOf<Effect>()
        val wasMenu = isMenu()
        when (input) {
            is Input.Posted -> onEvent(input.event, now)
            is Input.ShowFor -> onShowFor(input.event, input.showMs)
            is Input.Updated -> onActivity(input.activity, now)
            is Input.Ended -> onActivityEnded(input.activityId)
            is Input.ScreenIs -> onScreen(input.state.screen)
            is Input.Foreground -> foreground = input.pkg
            is Input.SettingsAre -> {
                gestures = input.gestures
                collapsed = input.collapsed
            }
            is Input.Typing -> {
                keyboard = input.on
                lastTouch = now
            }
            is Input.Touch -> onGesture(input.gesture, now, out)
            // Hiding the idle pill works in any state and changes nothing else.
            Input.VolumeKeys ->
                if (gestures[IdleSlot.Volume] == Choice.HideIdle) {
                    out += Effect.SetCollapsed(!collapsed)
                } else {
                    lastTouch = now
                    settle(now)
                    if (size == Size.Compact) idleGesture(IdleSlot.Volume, hold = false, out)
                }
            Input.Close -> onGesture(Gesture.Outside, now, out)
            is Input.Dismiss ->
                if ((shown() as? Shown.Event)?.event?.key == input.eventKey) {
                    onGesture(Gesture.Outside, now, out)
                    onGesture(Gesture.SwipeUp, now, out)
                }
            Input.Tick -> {}
        }
        settle(now)
        arrival(out, wasMenu)
        return out
    }

    private fun isMenu() = shown() == Shown.Idle && size != Size.Compact

    /** Something new is shown: an event, or an activity the first time while it lives. Same chat, next song or a returning activity stay quiet. */
    private fun arrival(
        out: MutableList<Effect>,
        wasMenu: Boolean,
    ) {
        if (!isVisible()) return
        arrivedActs.retainAll(acts.map { it.activity.id }.toSet())
        val s = shown()
        if (s == Shown.Idle) {
            arrivedKey = null
            if (size != Size.Compact && !wasMenu) out += Effect.StopGlow
            return
        }
        if (s.key == arrivedKey) return
        arrivedKey = s.key
        when (s) {
            is Shown.Event -> out += Effect.Arrived(s.event)
            is Shown.Activity -> if (arrivedActs.add(s.activity.id)) out += Effect.Arrived(s.activity)
            Shown.Idle -> {}
        }
    }

    // ---- inputs ----

    private fun onEvent(
        e: PillEvent,
        now: Long,
    ) {
        if (screen == Screen.Locked) return
        val shown = event
        if (shown != null && shown.key == e.key && shown.kind == e.kind) {
            event = e
            eventShownAt = now
            eventExpires = now + e.showMs
            if (eventPausedAt != null) eventPausedAt = now
        } else {
            waiting.removeAll { it.event.kind == e.kind }
            waiting += Waiting(e, now)
        }
    }

    /** Extends the shown event's lifetime to [showMs]; never below [PillEvent.showMs], never for another instance. */
    private fun onShowFor(
        e: PillEvent,
        showMs: Long,
    ) {
        if (event !== e) return
        eventExpires = maxOf(eventExpires, eventShownAt + maxOf(e.showMs, showMs))
    }

    private fun onActivity(
        a: PillActivity,
        now: Long,
    ) {
        val old = acts.firstOrNull { it.activity.id == a.id }
        val wasRinging = old?.activity?.ringing == true
        val becameRinging = a.ringing && !wasRinging
        if (old != null) {
            old.activity = a
        } else {
            acts += Act(a)
            selectedId = null
        }
        acts.sortWith(compareByDescending<Act> { it.activity.priority }.thenByDescending { it.activity.since })
        if (becameRinging) {
            hiddenIds -= a.id
            selectedId = a.id
            event = null
            eventPausedAt = null
            size = Size.Large
            largeFor = Shown.Activity(a).key
            lastTouch = now
        } else if (wasRinging && !a.ringing && size == Size.Large && largeFor == "a:${a.id}") {
            size = Size.Compact
        }
    }

    private fun onActivityEnded(id: String) {
        acts.removeAll { it.activity.id == id }
        hiddenIds -= id
        if (selectedId == id) selectedId = null
    }

    private fun onScreen(s: Screen) {
        screen = s
        if (s == Screen.Locked) {
            event = null
            eventPausedAt = null
            waiting.clear()
            size = Size.Compact
        }
    }

    private fun onGesture(
        g: Gesture,
        now: Long,
        out: MutableList<Effect>,
    ) {
        lastTouch = now
        settle(now)
        when (size) {
            Size.Compact -> compactGesture(g, now, out)
            Size.Large ->
                when (g) {
                    Gesture.SwipeUp -> if (ringingShown()) hideActivity() else closeLarge(now)
                    Gesture.Outside -> if (!ringingShown()) closeLarge(now)
                    else -> {}
                }
            Size.FastMenu, Size.QuickMenu, Size.RecentMenu -> if (g == Gesture.SwipeUp || g == Gesture.Outside) closeLarge(now)
            Size.RecentApps -> if (g == Gesture.Release || g == Gesture.Outside) size = Size.Compact
        }
        settle(now)
    }

    // ---- output ----

    fun view(): PillView {
        val best = waiting.maxWithOrNull(compareBy<Waiting> { it.event.priority }.thenBy { it.since })
        return PillView(
            shown = shown(),
            size = size,
            visible = isVisible(),
            collapsed = collapsed,
            typing = keyboard && size == Size.Large,
            waiting = best?.event,
            activities = acts.map { ActivityInfo(it.activity, it.activity.id in hiddenIds || covered(it)) },
        )
    }

    /** The next time a [Input.Tick] is needed, or null when nothing is timed. Always later than [now]. */
    fun nextDeadline(now: Long): Long? {
        val times = mutableListOf<Long>()
        waiting.forEach { times += it.since + WAIT_LIMIT_MS }
        if (event != null && eventPausedAt == null) times += eventExpires
        // Only a waiting event that may replace the shown one makes its minimum time a deadline; a lower one waits for the end.
        val cur = event
        if (cur != null && size == Size.Compact && waiting.any { it.event.priority >= cur.priority }) times += eventAt + MIN_VISIBLE_MS
        if ((size == Size.Large || isTapMenu()) && !keyboard && !ringingShown()) times += lastTouch + LARGE_IDLE_MS
        return times.filter { it > now }.minOrNull()
    }

    // ---- internals ----

    private fun isVisible() =
        when (screen) {
            Screen.Normal -> true
            Screen.Locked -> false
            Screen.Away -> needsAttention(shown())
        }

    private fun needsAttention(s: Shown) = s is Shown.Event || (s is Shown.Activity && s.activity.ringing)

    private fun ringingShown() = (shown() as? Shown.Activity)?.activity?.ringing == true

    private fun shown(): Shown {
        acts.firstOrNull { it.activity.ringing && it.activity.id !in hiddenIds }?.let { return Shown.Activity(it.activity) }
        event?.let { return Shown.Event(it) }
        val sel = acts.firstOrNull { it.activity.id == selectedId && it.activity.id !in hiddenIds && !covered(it) }
        val act = sel ?: acts.firstOrNull { it.activity.id !in hiddenIds && !covered(it) }
        return if (act != null) Shown.Activity(act.activity) else Shown.Idle
    }

    private fun covered(a: Act) = a.activity.coveredBy?.let { it == foreground } == true

    private fun compactGesture(
        g: Gesture,
        now: Long,
        out: MutableList<Effect>,
    ) {
        val s = shown()
        when (g) {
            Gesture.Tap -> if (s == Shown.Idle) idleGesture(IdleSlot.TapLeft, hold = false, out) else openLarge(s, now)
            Gesture.TapRight -> if (s == Shown.Idle) idleGesture(IdleSlot.TapRight, hold = false, out) else openLarge(s, now)
            Gesture.SwipeDown -> out += Effect.OpenShade
            Gesture.LongPressRight ->
                if (s ==
                    Shown.Idle
                ) {
                    idleGesture(IdleSlot.HoldRight, hold = true, out)
                } else {
                    compactGesture(Gesture.LongPress, now, out)
                }
            Gesture.LongPress ->
                when (s) {
                    Shown.Idle -> idleGesture(IdleSlot.HoldLeft, hold = true, out)
                    is Shown.Event -> out += Effect.Open(s.event)
                    is Shown.Activity -> out += Effect.Open(s.activity)
                }
            Gesture.SwipeUp ->
                when (s) {
                    is Shown.Event -> event = null
                    is Shown.Activity -> hideActivity()
                    Shown.Idle -> {}
                }
            Gesture.SwipeLeft -> if (event == null) swipeSide(+1)
            Gesture.SwipeRight -> if (event == null) swipeSide(-1)
            else -> {}
        }
    }

    private fun isTapMenu() = size == Size.FastMenu || size == Size.QuickMenu || size == Size.RecentMenu

    private fun idleGesture(
        slot: IdleSlot,
        hold: Boolean,
        out: MutableList<Effect>,
    ) {
        when (val c = gestures[slot] ?: Choice.Nothing) {
            Choice.AppMenu -> size = Size.FastMenu
            Choice.ActionsMenu -> size = Size.QuickMenu
            Choice.Recents -> size = if (hold) Size.RecentApps else Size.RecentMenu
            is Choice.Action -> out += Effect.RunAction(c.id)
            Choice.HideIdle -> out += Effect.SetCollapsed(!collapsed)
            Choice.Nothing -> {}
        }
    }

    private fun openLarge(
        s: Shown,
        now: Long,
    ) {
        size = Size.Large
        largeFor = s.key
        if (s is Shown.Event) eventPausedAt = now
    }

    private fun closeLarge(now: Long) {
        size = Size.Compact
        eventPausedAt?.let {
            val paused = now - it
            eventExpires += paused
            eventShownAt += paused
        }
        eventPausedAt = null
    }

    private fun hideActivity() {
        val s = shown() as? Shown.Activity ?: return
        hiddenIds += s.activity.id
        selectedId = null
        if (size == Size.Large) size = Size.Compact
    }

    /** One carousel: activities on the left, the idle pill on the right. [dir] +1 moves right, -1 moves left. */
    private fun swipeSide(dir: Int) {
        val s = shown()
        val acts = acts.filterNot(::covered)
        if (s is Shown.Activity) {
            val cur = acts.indexOfFirst { it.activity.id == s.activity.id }
            if (cur < 0) return
            val next = cur + dir
            if (next in acts.indices) {
                val id = acts[next].activity.id
                hiddenIds -= id
                selectedId = id
            } else if (dir > 0) {
                // Right from the last activity goes to the idle pill and hides all activities.
                acts.forEach { hiddenIds += it.activity.id }
                selectedId = null
            }
            return
        }
        if (s == Shown.Idle && dir < 0 && acts.isNotEmpty()) {
            // From the idle pill one step toward the activities lands on the nearest (rightmost) one.
            val id = acts.last().activity.id
            hiddenIds -= id
            selectedId = id
        }
    }

    /** Applies everything that time and the other inputs make due. */
    private fun settle(now: Long) {
        waiting.removeAll { now - it.since >= WAIT_LIMIT_MS }
        if (event != null && eventPausedAt == null && now >= eventExpires) event = null
        if ((size == Size.Large || isTapMenu()) &&
            !keyboard &&
            !ringingShown() &&
            now - lastTouch >= LARGE_IDLE_MS
        ) {
            closeLarge(now)
        }
        if (size == Size.Compact) {
            val best = waiting.maxWithOrNull(compareBy<Waiting> { it.event.priority }.thenBy { it.since })
            val cur = event
            if (best != null &&
                (
                    cur == null ||
                        (best.event.priority >= cur.priority && now - eventAt >= MIN_VISIBLE_MS)
                )
            ) {
                waiting.remove(best)
                event = best.event
                eventAt = now
                eventShownAt = now
                eventExpires = now + best.event.showMs
            }
        }
        // A large view or menu belongs to what was shown when it opened.
        val s = shown()
        if (size == Size.Large && s.key != largeFor) closeLarge(now)
        if ((isTapMenu() || size == Size.RecentApps) && (s != Shown.Idle || screen != Screen.Normal)) size = Size.Compact
        if (screen == Screen.Away && size == Size.Large && !needsAttention(s)) size = Size.Compact
        if (size != Size.Large) keyboard = false
    }
}
