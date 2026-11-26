package sk.vladko.pill.rules

// Pure Kotlin: everything Pill reads from one notification, copied out once at the edge.

data class Note(
    val key: String,
    val pkg: String,
    val category: String?,
    /** EXTRA_TEMPLATE class name, or "" when absent. */
    val style: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val whenMs: Long,
    val chronometer: Boolean,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val foregroundService: Boolean,
    val insistent: Boolean,
    val fullScreen: Boolean,
    val importance: Int,
    val passesDnd: Boolean,
    /** CallStyle call type, or 0 when absent. */
    val callType: Int,
    val callerName: String?,
    /** Which of Answer / Decline / HangUp the CallStyle extras carry. */
    val callIntents: Set<NoteTarget>,
    /** Only actions that have an intent. */
    val actions: List<NoteAction>,
    /** True when it was already there when Pill connected (not posted now). */
    val replay: Boolean,
    /** EXTRA_SUB_TEXT (for navigation: the arrival time). */
    val subText: String? = null,
    val progress: Int = 0,
    /** 0 when the notification has no determinate progress. */
    val progressMax: Int = 0,
)

data class NoteAction(
    val index: Int,
    val title: String,
    val semantic: Int,
    val takesText: Boolean,
)

sealed interface NoteTarget {
    data object Content : NoteTarget

    data class Action(
        val index: Int,
    ) : NoteTarget

    data object Answer : NoteTarget

    data object Decline : NoteTarget

    data object HangUp : NoteTarget
}

/** Wall-clock time a call connected: apps start the chronometer at the base time only once the far side has answered. */
fun Note.connectedAtMs(): Long? = whenMs.takeIf { chronometer && it > 0 }
