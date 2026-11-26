package sk.vladko.pill.features

import android.graphics.Bitmap
import sk.vladko.pill.engine.Source
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.Note
import sk.vladko.pill.rules.NoteTarget
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.rules.connectedAtMs
import sk.vladko.pill.system.NoteHandle
import sk.vladko.pill.system.NoteListener

/** A call, found by its call notification. The buttons are the notification's own intents. */
data class Call(
    override val id: String,
    override val since: Long,
    val pkg: String,
    val name: String,
    override val ringing: Boolean,
    /** Wall-clock time the call connected, or null while it still dials (no timer is shown then). */
    val connectedAtWall: Long?,
    val answerTarget: NoteTarget?,
    val hangupTarget: NoteTarget?,
    private val note: NoteHandle,
) : PillActivity {
    override val kind get() = "call"
    override val priority get() = 2
    override val label get() = "$name ${if (ringing) {
        "ringing"
    } else if (connectedAtWall == null) {
        "dialing"
    } else {
        "ongoing"
    }}"
    override val accent get() = Accent.App(pkg)

    /** The caller's picture, when the notification carries one. */
    val photo: Bitmap? get() = note.callerPhoto()

    fun answer() {
        answerTarget?.let(note::send)
    }

    fun hangup() {
        hangupTarget?.let(note::send)
    }

    override fun open() {
        note.send(NoteTarget.Content)
    }
}

/** Button words in a few languages, used only when a notification has no CallStyle intents. */
private val ANSWER_WORDS =
    listOf(
        "answer",
        "accept",
        "prijat",
        "prijať",
        "annehmen",
        "répondre",
        "accepter",
        "contestar",
        "responder",
        "aceptar",
        "rispondi",
        "accetta",
        "antwoord",
        "odbierz",
        "přijmout",
    )
private val HANGUP_WORDS =
    listOf(
        "hang",
        "end",
        "decline",
        "reject",
        "ukonč",
        "zavesi",
        "zavesiť",
        "odmiet",
        "beenden",
        "ablehnen",
        "raccrocher",
        "refuser",
        "colgar",
        "rechazar",
        "rifiuta",
        "termina",
        "recus",
        "encerrar",
        "odrzuć",
        "zakończ",
        "odmítnout",
    )

/** How long after a ringing call went away a plain ongoing notification may continue it. */
private const val CONTINUE_WINDOW_MS = 5000L

private const val CALL_TYPE_INCOMING = 1
private const val CALL_TYPE_SCREENING = 3

data class CallFacts(
    val ringing: Boolean,
    val answer: NoteTarget?,
    val hangup: NoteTarget?,
)

fun Note.isCall(): Boolean = category == "call" || style.contains("CallStyle")

private fun Note.byWords(words: List<String>): NoteTarget? =
    actions.firstOrNull { a -> words.any { a.title.contains(it, true) } }?.let { NoteTarget.Action(it.index) }

/** Ringing or not, and which intents answer and hang up: CallStyle targets, then button words, then position. */
fun callFacts(note: Note): CallFacts {
    val ringing =
        when {
            note.callType != 0 -> note.callType == CALL_TYPE_INCOMING || note.callType == CALL_TYPE_SCREENING
            note.category == "call" && note.fullScreen && !note.chronometer -> true
            note.category == "call" && (note.ongoing || note.foregroundService) -> false
            else -> note.byWords(ANSWER_WORDS) != null
        }
    // The system puts Decline / Hang up first and Answer last.
    val answer =
        NoteTarget.Answer.takeIf { it in note.callIntents } ?: note.byWords(ANSWER_WORDS)
            ?: if (ringing && note.actions.size >= 2) NoteTarget.Action(note.actions.last().index) else null
    val hangup =
        NoteTarget.HangUp.takeIf { it in note.callIntents } ?: NoteTarget.Decline.takeIf { it in note.callIntents }
            ?: note.byWords(HANGUP_WORDS) ?: note.actions.firstOrNull()?.let { NoteTarget.Action(it.index) }
    return CallFacts(ringing, answer, hangup)
}

/**
 * Some apps (Telegram) replace the ringing call with a plain ongoing notification that has no call category.
 * One that is ongoing, has an end-call button and comes from the package whose ringing call went away
 * [sinceRemovedMs] ago continues that call.
 */
fun continuesCall(
    note: Note,
    lastRingingPkg: String?,
    sinceRemovedMs: Long,
): Boolean =
    lastRingingPkg != null &&
        note.pkg == lastRingingPkg &&
        sinceRemovedMs <= CONTINUE_WINDOW_MS &&
        note.ongoing &&
        note.byWords(HANGUP_WORDS) != null

val CallSource =
    Source { host ->
        host.notes.listen(
            object : NoteListener {
                /** Package of each claimed call key. */
                private val calls = HashMap<String, String>()

                /** Keys of plain notifications taken over as the continuation of a call (see [continuesCall]). */
                private val continued = HashSet<String>()
                private val ringingKeys = HashSet<String>()
                private val ongoingSince = HashMap<String, Long>()
                private var lastRingingPkg: String? = null
                private var lastRingingAt = 0L

                override fun posted(
                    note: Note,
                    handle: NoteHandle,
                ): Boolean {
                    val call = note.isCall() || note.key in continued
                    if (!call) {
                        // A call key updated into a plain notification keeps the last call until it goes.
                        if (note.key in calls) return true
                        if (!continuesCall(note, lastRingingPkg, host.now() - lastRingingAt)) return false
                        continued.add(note.key)
                        lastRingingPkg = null
                    }
                    val facts = callFacts(note)
                    calls[note.key] = note.pkg
                    if (facts.ringing) ringingKeys.add(note.key) else ringingKeys.remove(note.key)
                    val now = System.currentTimeMillis()
                    // A continuation was answered by definition, so its own clock may be missing; any other
                    // ongoing call without a started chronometer is still dialing.
                    val connected =
                        when {
                            facts.ringing -> null
                            else -> note.connectedAtMs() ?: if (note.key in continued) ongoingSince.getOrPut(note.key) { now } else null
                        }
                    host.sink.activity(
                        Call(
                            id = note.key,
                            since = host.now(),
                            pkg = note.pkg,
                            name = note.callerName ?: note.title.orEmpty(),
                            ringing = facts.ringing,
                            connectedAtWall = connected,
                            answerTarget = facts.answer,
                            hangupTarget = facts.hangup,
                            note = handle,
                        ),
                    )
                    return true
                }

                override fun removed(key: String) {
                    val pkg = calls.remove(key)
                    if (ringingKeys.remove(key)) {
                        lastRingingPkg = pkg
                        lastRingingAt = host.now()
                    }
                    ongoingSince.remove(key)
                    continued.remove(key)
                    host.sink.ended(key)
                }
            },
        )
    }
