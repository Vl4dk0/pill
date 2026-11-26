package sk.vladko.pill.features

import sk.vladko.pill.engine.Source
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.Note
import sk.vladko.pill.rules.NoteAction
import sk.vladko.pill.rules.NoteTarget
import sk.vladko.pill.rules.PillEvent
import sk.vladko.pill.system.NoteFeed
import sk.vladko.pill.system.NoteHandle
import sk.vladko.pill.system.NoteListener

/** A notification Android would pop up. [key] is the Android notification key: same chat = same key. */
data class Message(
    override val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val appName: String,
    val actions: List<NoteAction>,
    private val note: NoteHandle,
    private val feed: NoteFeed,
) : PillEvent {
    override val kind get() = "message"
    override val priority get() = 2
    override val showMs get() = 4000L
    override val label get() = "$title: $text"
    override val accent get() = Accent.App(pkg)

    override fun open() {
        note.send(NoteTarget.Content)
    }

    /** Presses a notification button; [reply] is the typed text for a button that takes text. */
    fun press(
        a: NoteAction,
        reply: String? = null,
    ): Boolean = note.send(NoteTarget.Action(a.index), reply)

    fun snooze(ms: Long): Boolean = feed.snooze(key, ms)
}

private const val IMPORTANCE_HIGH = 4

/** True when Android would pop this notification up. Calls and alarms are claimed by their own sources first. */
fun Note.popsUp(ownPkg: String): Boolean =
    when {
        replay -> false
        pkg == ownPkg -> false
        importance < IMPORTANCE_HIGH -> false
        !passesDnd -> false
        ongoing || groupSummary || foregroundService -> false
        category == "transport" -> false
        style.contains("MediaStyle") -> false
        else -> true
    }

/** Turns every notification that Android would pop up into a message event. */
val MessageSource =
    Source { host ->
        host.notes.listen(
            object : NoteListener {
                override fun posted(
                    note: Note,
                    handle: NoteHandle,
                ): Boolean {
                    if (!note.popsUp(host.context.packageName)) return false
                    val app = host.apps.label(note.pkg)
                    host.apps.icon(note.pkg)
                    host.sink.event(
                        Message(
                            key = note.key,
                            pkg = note.pkg,
                            title = note.title ?: app,
                            text = note.bigText ?: note.text.orEmpty(),
                            appName = app,
                            actions = note.actions,
                            note = handle,
                            feed = host.notes,
                        ),
                    )
                    return true
                }
            },
        )
    }
