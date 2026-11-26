package sk.vladko.pill.rules

// Pure Kotlin: the types the rules read and write. No Android classes, so plain JVM tests use them.

/** Where the arrival glow takes its colour from. */
sealed interface Accent {
    /** The main colour of the app icon. */
    data class App(
        val pkg: String,
    ) : Accent

    data class Fixed(
        val argb: Int,
    ) : Accent
}

/** Anything the pill can show. */
interface PillItem {
    /** "message", "charger", "music", "call", "alarm": printed in state and the log. */
    val kind: String
    val label: String
    val accent: Accent

    /** Long press, or a clean tap on the large view: go to the source. */
    fun open() {}
}

/** A thing that lives a few seconds. Same [key] = same chat/source: it updates the shown one. */
interface PillEvent : PillItem {
    val key: String
    val priority: Int
    val showMs: Long
}

/** A thing that lives until the source ends it. */
interface PillActivity : PillItem {
    val id: String
    val priority: Int

    /** When it became the most recent of its kind (music: last started to play). Newer comes first. */
    val since: Long

    /** True while the user must answer (incoming call): shows large at once and stays large. */
    val ringing: Boolean get() = false

    /** The package of an app that already shows this activity itself: while it is in front, the pill does not show it. */
    val coveredBy: String? get() = null
}

/** RecentApps is the hold-drag-release picker; RecentMenu is the same apps as a normal tap menu. */
enum class Size { Compact, Large, FastMenu, QuickMenu, RecentApps, RecentMenu }

/** [TapRight] and [LongPressRight] are a tap or hold on the right half of the pill; anything but the idle pill treats them like [Tap] and [LongPress]. */
enum class Gesture { Touch, Tap, TapRight, LongPress, LongPressRight, SwipeUp, SwipeDown, SwipeLeft, SwipeRight, Release, Outside }

/**
 * What the screen allows. Normal: the pill shows everything. Away (fullscreen app, landscape): no idle pill and no compact
 * activity (music, ongoing call), but things that need attention (events, a ringing call or alarm) still show, at the top.
 * Locked: nothing shows and events are dropped (Android's lock screen shows notifications itself).
 */
enum class Screen { Normal, Away, Locked }

/** The raw screen facts. */
data class ScreenState(
    val locked: Boolean = false,
    val landscape: Boolean = false,
    val statusBarHidden: Boolean = false,
) {
    val screen: Screen get() =
        when {
            locked -> Screen.Locked
            landscape || statusBarHidden -> Screen.Away
            else -> Screen.Normal
        }

    /** Why the screen is not Normal (state.hiddenBy). */
    val hiddenBy: String get() =
        when {
            locked -> "lock"
            landscape -> "landscape"
            statusBarHidden -> "statusbar"
            else -> ""
        }
}

sealed interface Shown {
    data object Idle : Shown {
        override val key = "idle"
    }

    data class Event(
        val event: PillEvent,
    ) : Shown {
        override val key get() = "e:${event.key}"
    }

    data class Activity(
        val activity: PillActivity,
    ) : Shown {
        override val key get() = "a:${activity.id}"
    }

    /** "idle", "e:<key>" or "a:<id>". The one place that builds it. */
    val key: String
}

/** Everything that reaches the rules. */
sealed interface Input {
    data class Posted(
        val event: PillEvent,
    ) : Input

    /** The shown [event]'s view needs [showMs] to show it in full (one marquee pass). Never shortens; ignored for any other instance. */
    data class ShowFor(
        val event: PillEvent,
        val showMs: Long,
    ) : Input

    data class Updated(
        val activity: PillActivity,
    ) : Input

    data class Ended(
        val activityId: String,
    ) : Input

    /** The app in front changed ([pkg] is null when unknown). */
    data class Foreground(
        val pkg: String?,
    ) : Input

    data class ScreenIs(
        val state: ScreenState,
    ) : Input

    data class SettingsAre(
        val gestures: Map<IdleSlot, Choice>,
        val collapsed: Boolean,
    ) : Input

    data class Typing(
        val on: Boolean,
    ) : Input

    data class Touch(
        val gesture: Gesture,
    ) : Input

    /** Volume up + down pressed together. */
    data object VolumeKeys : Input

    /** A view finished its job: close the large view or menu. A ringing call or alarm stays large. */
    data object Close : Input

    /** The shown event was handled (replied, action pressed): close and take it off the pill. */
    data class Dismiss(
        val eventKey: String,
    ) : Input

    data object Tick : Input
}

data class ActivityInfo(
    val activity: PillActivity,
    val hidden: Boolean,
)

data class PillView(
    val shown: Shown,
    val size: Size,
    val visible: Boolean,
    /** The user hid the idle pill (Choice.HideIdle). */
    val collapsed: Boolean,
    /** The reply field has the keyboard: the large view does not close by itself. */
    val typing: Boolean,
    val waiting: PillEvent?,
    val activities: List<ActivityInfo>,
) {
    /** True when no pixel is drawn: the screen hides the pill, or the idle pill is collapsed. */
    val drawsNothing: Boolean get() = !visible || (collapsed && shown == Shown.Idle && size == Size.Compact)
}

/** Something the host must do because of an input. */
sealed interface Effect {
    /** Go to the source of [item] (long press). */
    data class Open(
        val item: PillItem,
    ) : Effect

    /** Run one catalog action (an idle gesture chose it). */
    data class RunAction(
        val id: String,
    ) : Effect

    /** The host saves it; it returns as [Input.SettingsAre]. */
    data class SetCollapsed(
        val on: Boolean,
    ) : Effect

    /** Swipe down on the compact pill: pull down the notification shade. */
    data object OpenShade : Effect

    /** Something new is shown: run the arrival glow. */
    data class Arrived(
        val item: PillItem,
    ) : Effect

    /** Opening a menu ends any running arrival glow for good. */
    data object StopGlow : Effect
}
