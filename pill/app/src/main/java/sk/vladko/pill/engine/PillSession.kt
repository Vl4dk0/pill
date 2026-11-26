package sk.vladko.pill.engine

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sk.vladko.pill.PillAccessibilityService
import sk.vladko.pill.PillApp
import sk.vladko.pill.actions.Catalog
import sk.vladko.pill.agent.ActionStatus
import sk.vladko.pill.agent.PxRect
import sk.vladko.pill.agent.Snapshot
import sk.vladko.pill.idle.PetIdle
import sk.vladko.pill.overlay.Controls
import sk.vladko.pill.overlay.Glow
import sk.vladko.pill.overlay.OverlayWindow
import sk.vladko.pill.overlay.PillRoot
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.Choice
import sk.vladko.pill.rules.Effect
import sk.vladko.pill.rules.Gesture
import sk.vladko.pill.rules.IdleSlot
import sk.vladko.pill.rules.Input
import sk.vladko.pill.rules.PET_LOW_BATTERY
import sk.vladko.pill.rules.PetEvent
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.rules.PillEvent
import sk.vladko.pill.rules.PillRules
import sk.vladko.pill.rules.PillView
import sk.vladko.pill.rules.Screen
import sk.vladko.pill.rules.ScreenGeometry
import sk.vladko.pill.rules.ScreenState
import sk.vladko.pill.rules.Shown
import sk.vladko.pill.rules.Size
import sk.vladko.pill.rules.Verdict
import sk.vladko.pill.rules.VolumePair
import sk.vladko.pill.rules.petLowCrossed
import sk.vladko.pill.system.ScreenWatch

private const val TYPING_TIMEOUT_MS = 60_000L

/** The kinds of pill items that come from a notification: when one arrives, the pet hides behind the camera hole. */
private val NOTIFICATION_KINDS = setOf("message", "call", "alarm", "navigation")

private data class SessionSettings(
    val gestures: Map<IdleSlot, Choice>,
    val collapsed: Boolean,
)

private val VOLUME_STREAMS =
    listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_VOICE_CALL,
    )

/** Everything that lives while the accessibility service is connected. A new connection is a new session with empty rules. */
class PillSession(
    private val service: PillAccessibilityService,
    private val app: PillApp,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = mutableListOf<AutoCloseable>()
    private val volumePair = VolumePair()
    private var volumesBefore: Map<Int, Int> = emptyMap()
    private var lastLog = ""
    private var lastScreen = Screen.Normal
    private var large = false
    private var battery: Int? = null
    private var foreground: String? = null

    val engine = Engine(PillRules(app.hiddenActivities), SystemClock::uptimeMillis, ::onView, ::run)
    val controls = Controls()
    val glow = Glow()
    val screen: ScreenWatch
    val window: OverlayWindow

    /** The reply field gives the keyboard back after a minute without leaving it. */
    private val typingTimeout = Runnable { engine.send(Input.Typing(false)) }
    private var typing = false

    init {
        window = OverlayWindow(service, onOutside = { engine.send(Input.Touch(Gesture.Outside)) }) { PillRoot(this, window::setTouchable) }
        running += window
        app.popups.take()
        running += AutoCloseable { app.popups.giveBack() }
        app.statusIcons.take()
        running += AutoCloseable { app.statusIcons.giveBack() }
        screen = ScreenWatch(service, ::onScreenChanged)
        running += screen
        lastScreen = screen.state.screen
        engine.send(Input.ScreenIs(screen.state))
        runCatching { app.torch.watch() }.onSuccess { running += it }.onFailure { Log.w("PILL", "torch watch failed", it) }
        val sink =
            object : Sink {
                override fun event(e: PillEvent) = engine.send(Input.Posted(e))

                override fun activity(a: PillActivity) = engine.send(Input.Updated(a))

                override fun ended(id: String) = engine.send(Input.Ended(id))
            }
        val host = Host(app, sink, app.notes, app.settings, app.apps, scope, SystemClock::uptimeMillis)
        Registry.features.forEach { running += it.source.start(host) }
        running += watchBattery()
        scope.launch {
            app.settings.value
                .map { SessionSettings(it.gestures, it.collapsed) }
                .distinctUntilChanged()
                .collect { s -> engine.send(Input.SettingsAre(s.gestures, s.collapsed)) }
        }
        onView(engine.view)
    }

    /** A window of [pkg] came up. The shade, system dialogs and Pill's own windows do not count as the app in front. */
    fun appInFront(pkg: String) {
        if (pkg == foreground || pkg == service.packageName || pkg == "com.android.systemui" || pkg == "android") return
        foreground = pkg
        engine.send(Input.Foreground(pkg))
    }

    /** Runs an accessibility global action (shade, screenshot, lock, power menu). */
    fun global(action: Int): Boolean = service.performGlobalAction(action)

    /** View-facing controls: separated from AutoCloseable.close() so the two close() methods cannot collide. */
    val pill: PillControls =
        object : PillControls {
            override fun close() = engine.send(Input.Close)

            override fun dismiss(eventKey: String) = engine.send(Input.Dismiss(eventKey))

            override fun typing(on: Boolean) = engine.send(Input.Typing(on))

            override fun showFor(
                event: PillEvent,
                showMs: Long,
            ) = engine.send(Input.ShowFor(event, showMs))
        }

    /**
     * A single volume key goes through at once. When the other key goes down within 150 ms, that second key
     * is swallowed (down, repeats and up) and the Volume gesture runs. The first key already stepped the volume, so
     * the volumes read before it are put back.
     */
    fun onKey(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val up = event.action == KeyEvent.ACTION_UP
        val verdict = volumePair.onKey(up, k == KeyEvent.KEYCODE_VOLUME_UP, event.repeatCount, event.eventTime)
        if (!up && event.repeatCount == 0 && verdict == Verdict.Pass) {
            val audio = app.getSystemService(AudioManager::class.java)
            volumesBefore = VOLUME_STREAMS.associateWith { audio.getStreamVolume(it) }
        }
        if (verdict == Verdict.SwallowAndFire) {
            val audio = app.getSystemService(AudioManager::class.java)
            val before = volumesBefore
            handler.postDelayed({
                before.forEach { (s, v) -> runCatching { if (audio.getStreamVolume(s) != v) audio.setStreamVolume(s, v, 0) } }
            }, 120)
            engine.send(Input.VolumeKeys)
        }
        return verdict != Verdict.Pass
    }

    private fun onScreenChanged(
        state: ScreenState,
        geometry: ScreenGeometry,
    ) {
        if (state.screen != lastScreen) {
            lastScreen = state.screen
            if (engine.view.typing) engine.send(Input.Typing(false))
        }
        engine.send(Input.ScreenIs(state))
    }

    /**
     * Runs a catalog action by id. From the Actions menu ([fromMenu]) an action that does not keep the menu
     * closes it first; [sk.vladko.pill.actions.PillAction.delayMs] is waited after that.
     */
    fun runAction(
        id: String,
        fromMenu: Boolean,
    ) {
        val action = Catalog.byId(id, app.apps) ?: return
        if (fromMenu && !action.keepsMenu) engine.send(Input.Close)
        val env = app.actionEnv()
        if (action.delayMs > 0) handler.postDelayed({ action.run(env) }, action.delayMs) else action.run(env)
    }

    private fun run(effect: Effect) {
        when (effect) {
            is Effect.Open -> effect.item.open()
            is Effect.RunAction -> runAction(effect.id, fromMenu = false)
            is Effect.SetCollapsed -> app.settings.update { it.copy(collapsed = effect.on) }
            is Effect.Arrived -> {
                // The pet stays in the charger view and gets excited at once; a notification sends it behind the camera hole.
                when (effect.item.kind) {
                    "charger" -> PetIdle.event(PetEvent.Plug)
                    in NOTIFICATION_KINDS -> PetIdle.event(PetEvent.Notify)
                }
                if (app.settings.value.value.arrivalGlow) {
                    glow.start(
                        when (val a = effect.item.accent) {
                            is Accent.App ->
                                app.apps
                                    .icon(a.pkg)
                                    .value
                                    ?.let { Color(it.accentArgb) } ?: Color.White
                            is Accent.Fixed -> Color(a.argb)
                        },
                    )
                }
            }
            Effect.StopGlow -> glow.stop()
            Effect.OpenShade -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    /** Window flags follow the view; one stable log line per state change, so an agent can read `adb logcat -s PILL`. */
    private fun onView(v: PillView) {
        window.apply(v)
        // A large view or menu opened: the pet in its crown looks at it, and goes on when it closes.
        val nowLarge = v.size != Size.Compact && v.visible && !v.drawsNothing
        if (nowLarge != large) {
            large = nowLarge
            PetIdle.event(if (nowLarge) PetEvent.Open else PetEvent.Close)
        }
        if (v.typing != typing) {
            typing = v.typing
            handler.removeCallbacks(typingTimeout)
            if (typing) handler.postDelayed(typingTimeout, TYPING_TIMEOUT_MS)
        }
        val shown =
            when (val s = v.shown) {
                Shown.Idle -> "idle"
                is Shown.Event -> "event:${s.event.kind}:${s.event.label}"
                is Shown.Activity -> "activity:${s.activity.kind}:${s.activity.label}"
            }
        val line =
            "shown=$shown size=${v.size.name.lowercase()} visible=${v.visible} " +
                "waiting=${v.waiting?.label} activities=${v.activities.map { it.activity.id + if (it.hidden) ":hidden" else "" }}"
        if (line != lastLog) {
            lastLog = line
            Log.i("PILL", line)
        }
    }

    /** Builds a plain snapshot of the current session state for `StateDump`. */
    fun snapshot(): Snapshot {
        val env = app.actionEnv()
        val cfg = app.settings.value.value

        fun px(r: Rect) = PxRect(r.left, r.top, r.right, r.bottom)
        return Snapshot(
            view = engine.view,
            settings = cfg,
            boundsPx = px(window.bounds),
            screen = screen.state,
            geometry = screen.geometry,
            density = app.resources.displayMetrics.density,
            buttons = controls.buttons().mapValues { (_, r) -> px(r) },
            reported = controls.reported(),
            glowRunning = glow.running(),
            glowColor = glow.current?.let { "#%06X".format(it.color.toArgb() and 0xFFFFFF) } ?: "",
            torchOn = app.torch.on,
            popupsOff = app.popups.taken,
            popupsPermission = app.popups.permitted,
            statusIconsHidden = app.statusIcons.taken,
            actions =
                Catalog.all.map { a ->
                    ActionStatus(a.id, a.kindName, a.group.name.lowercase(), a.unavailable(env), a.isOn(env))
                },
            petState = PetIdle.stateId,
        )
    }

    /** Battery level for the pet: a low look while below 20 % and not charging, and one tired reaction per drop below 20 %. */
    private fun watchBattery(): AutoCloseable {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale
                    val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    PetIdle.low = level < PET_LOW_BATTERY && !charging
                    if (petLowCrossed(battery, level, charging)) PetIdle.event(PetEvent.Low)
                    battery = level
                }
            }
        app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        return AutoCloseable { app.unregisterReceiver(receiver) }
    }

    override fun close() {
        scope.cancel()
        running.asReversed().forEach { runCatching { it.close() }.onFailure { e -> Log.w("PILL", "close failed", e) } }
        running.clear()
        handler.removeCallbacksAndMessages(null)
        engine.close()
    }
}
