package sk.vladko.pill.features

import sk.vladko.pill.engine.Host
import sk.vladko.pill.engine.Source
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.Note
import sk.vladko.pill.rules.NoteAction
import sk.vladko.pill.rules.NoteTarget
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.system.NoteHandle
import sk.vladko.pill.system.NoteListener

/** A ringing alarm or timer. Handled like a call: large at once, until the notification goes or stops ringing. */
data class Alarm(
    override val id: String,
    override val since: Long,
    val pkg: String,
    val title: String,
    val detail: String,
    val buttons: List<NoteAction>,
    private val note: NoteHandle,
) : PillActivity {
    override val kind get() = "alarm"
    override val priority get() = 2
    override val ringing get() = true
    override val label get() = title
    override val accent get() = Accent.App(pkg)

    override fun open() {
        note.send(NoteTarget.Content)
    }

    fun press(button: NoteAction) {
        note.send(NoteTarget.Action(button.index))
    }
}

fun Note.isAlarm(): Boolean = category == "alarm"

/** The silent "upcoming alarm" notification has no full-screen intent and is not insistent. */
fun Note.isRingingAlarm(): Boolean = isAlarm() && (fullScreen || insistent)

val AlarmSource =
    Source { host ->
        host.notes.listen(
            object : NoteListener {
                override fun posted(
                    note: Note,
                    handle: NoteHandle,
                ): Boolean {
                    if (!note.isAlarm()) return false
                    if (note.isRingingAlarm()) host.sink.activity(alarmOf(note, handle, host)) else host.sink.ended(note.key)
                    return true
                }

                override fun removed(key: String) = host.sink.ended(key)
            },
        )
    }

private fun alarmOf(
    note: Note,
    handle: NoteHandle,
    host: Host,
): Alarm {
    // The Clock app's timer is a custom view with no title: read the texts out of it.
    val custom = if (note.title == null) handle.customTexts() else emptyList()
    return Alarm(
        id = note.key,
        since = host.now(),
        pkg = note.pkg,
        title = note.title ?: custom.firstOrNull() ?: host.apps.label(note.pkg),
        detail = note.text ?: custom.getOrNull(1).orEmpty(),
        buttons = note.actions,
        note = handle,
    )
}
