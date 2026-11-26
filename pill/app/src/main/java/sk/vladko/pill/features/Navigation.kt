package sk.vladko.pill.features

import android.graphics.Bitmap
import sk.vladko.pill.engine.Source
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.Note
import sk.vladko.pill.rules.NoteAction
import sk.vladko.pill.rules.NoteTarget
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.system.NoteHandle
import sk.vladko.pill.system.NoteListener

/** Turn-by-turn navigation (an ongoing notification of category navigation). It is not shown while the app that posts it is in front. */
data class Navigation(
    override val id: String,
    override val since: Long,
    val pkg: String,
    val title: String,
    val text: String?,
    val subText: String?,
    /** The turn arrow: the large icon, or the small icon. */
    val arrow: Bitmap?,
    /** The short value shown right of the camera hole. */
    val value: String?,
    val progress: Int,
    val progressMax: Int,
    /** The notification's first action (for example "Exit navigation"). */
    val button: NoteAction?,
    private val note: NoteHandle,
) : PillActivity {
    override val kind get() = "navigation"
    override val priority get() = 1
    override val label get() = title
    override val accent get() = Accent.App(pkg)
    override val coveredBy get() = pkg

    override fun open() {
        note.send(NoteTarget.Content)
    }

    fun press(action: NoteAction) {
        note.send(NoteTarget.Action(action.index))
    }
}

fun Note.isNavigation(): Boolean = category == "navigation" && ongoing

val NavigationSource =
    Source { host ->
        host.notes.listen(
            object : NoteListener {
                override fun posted(
                    note: Note,
                    handle: NoteHandle,
                ): Boolean {
                    if (!note.isNavigation()) return false
                    host.sink.activity(
                        Navigation(
                            id = note.key,
                            since = host.now(),
                            pkg = note.pkg,
                            title = note.title ?: host.apps.label(note.pkg),
                            text = note.text,
                            subText = note.subText,
                            arrow = handle.picture(),
                            value = navigationValue(note.title, note.text, note.subText),
                            progress = note.progress,
                            progressMax = note.progressMax,
                            button = note.actions.firstOrNull(),
                            note = handle,
                        ),
                    )
                    return true
                }

                override fun removed(key: String) = host.sink.ended(key)
            },
        )
    }

// A number with optional decimals and a unit symbol, not inside a word or a longer number ("5 min" is not "5 m").
private val DISTANCE = Regex("""(?<![\p{L}\d])(\d+(?:[.,]\d+)?)\s?(km|mi|ft|yd|m)(?![\p{L}\d])""")
private val CLOCK = Regex("""(?<!\d)\d{1,2}:\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?""")

/** The one short value for the compact pill: the first distance in the title, then the text; else the first clock time in the sub text; else null. */
fun navigationValue(
    title: String?,
    text: String?,
    subText: String?,
): String? {
    for (s in listOfNotNull(title, text)) {
        DISTANCE.find(s)?.let { return "${it.groupValues[1]} ${it.groupValues[2]}" }
    }
    return subText?.let { CLOCK.find(it)?.value }
}
