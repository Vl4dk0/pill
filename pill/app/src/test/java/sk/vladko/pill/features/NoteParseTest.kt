package sk.vladko.pill.features

import sk.vladko.pill.rules.Note
import sk.vladko.pill.rules.NoteAction
import sk.vladko.pill.rules.NoteTarget
import sk.vladko.pill.rules.connectedAtMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun note(
    category: String? = null,
    pkg: String = "com.clock",
    style: String = "",
    fullScreen: Boolean = false,
    insistent: Boolean = false,
    chronometer: Boolean = false,
    ongoing: Boolean = false,
    groupSummary: Boolean = false,
    foregroundService: Boolean = false,
    importance: Int = 4,
    passesDnd: Boolean = true,
    callType: Int = 0,
    callIntents: Set<NoteTarget> = emptySet(),
    actions: List<NoteAction> = emptyList(),
    replay: Boolean = false,
    whenMs: Long = 0L,
) = Note(
    key = "0|$pkg|1|null|1000",
    pkg = pkg,
    category = category,
    style = style,
    title = "Alarm",
    text = "07:00",
    bigText = null,
    whenMs = whenMs,
    chronometer = chronometer,
    ongoing = ongoing,
    groupSummary = groupSummary,
    foregroundService = foregroundService,
    insistent = insistent,
    fullScreen = fullScreen,
    importance = importance,
    passesDnd = passesDnd,
    callType = callType,
    callerName = null,
    callIntents = callIntents,
    actions = actions,
    replay = replay,
)

private fun buttons(vararg titles: String) = titles.mapIndexed { i, t -> NoteAction(i, t, 0, false) }

class NoteParseTest {
    @Test fun alarmRingsWithFullScreenOrInsistent() {
        assertTrue(note(category = "alarm", fullScreen = true).isRingingAlarm())
        assertTrue(note(category = "alarm", insistent = true).isRingingAlarm())
        assertTrue(note(category = "alarm", fullScreen = true, insistent = true).isRingingAlarm())
        assertFalse(note(category = "msg", fullScreen = true, insistent = true).isRingingAlarm())
    }

    @Test fun upcomingAlarmIsNotRinging() {
        val upcoming = note(category = "alarm", fullScreen = false, insistent = false)
        assertTrue(upcoming.isAlarm())
        assertFalse(upcoming.isRingingAlarm())
    }

    @Test fun popUpFilterTable() {
        val own = "sk.vladko.pill"
        assertTrue(note(pkg = "com.chat").popsUp(own))
        assertFalse(note(pkg = "com.chat", importance = 3).popsUp(own))
        assertFalse(note(pkg = "com.chat", passesDnd = false).popsUp(own))
        assertFalse(note(pkg = "com.chat", ongoing = true).popsUp(own))
        assertFalse(note(pkg = "com.chat", groupSummary = true).popsUp(own))
        assertFalse(note(pkg = "com.chat", foregroundService = true).popsUp(own))
        assertFalse(note(pkg = "com.chat", category = "transport").popsUp(own))
        assertFalse(note(pkg = "com.chat", style = "android.app.Notification\$MediaStyle").popsUp(own))
        assertFalse(note(pkg = own).popsUp(own))
        assertFalse(note(pkg = "com.chat", replay = true).popsUp(own))
    }

    @Test fun callRingsByCallType() {
        assertTrue(callFacts(note(category = "call", callType = 1, ongoing = true)).ringing)
        assertTrue(callFacts(note(category = "call", callType = 3)).ringing)
        assertFalse(callFacts(note(category = "call", callType = 2, fullScreen = true)).ringing)
    }

    @Test fun callRingsByCategoryFullScreenAndNoChronometer() {
        assertTrue(callFacts(note(category = "call", fullScreen = true)).ringing)
        assertFalse(callFacts(note(category = "call", fullScreen = true, chronometer = true, ongoing = true)).ringing)
    }

    @Test fun ongoingCallByFlags() {
        assertFalse(callFacts(note(category = "call", ongoing = true, actions = buttons("Answer", "Decline"))).ringing)
        assertFalse(callFacts(note(category = "call", foregroundService = true, actions = buttons("Answer"))).ringing)
        assertTrue(callFacts(note(category = "call", actions = buttons("Decline", "Answer"))).ringing)
    }

    @Test fun callTimerStartsAtTheChronometerBaseOnlyWhenConnected() {
        assertNull(note(category = "call", callType = 2, ongoing = true, whenMs = 5_000).connectedAtMs())
        assertEquals(5_000L, note(category = "call", callType = 2, ongoing = true, chronometer = true, whenMs = 5_000).connectedAtMs())
    }

    @Test fun answerAndHangUpFromCallStyleTargets() {
        val f = callFacts(note(callType = 1, callIntents = setOf(NoteTarget.Answer, NoteTarget.Decline), actions = buttons("x", "y")))
        assertEquals(NoteTarget.Answer, f.answer)
        assertEquals(NoteTarget.Decline, f.hangup)
        val ongoing = callFacts(note(callType = 2, callIntents = setOf(NoteTarget.HangUp, NoteTarget.Decline)))
        assertEquals(NoteTarget.HangUp, ongoing.hangup)
    }

    @Test fun answerAndHangUpFromWords() {
        val f = callFacts(note(category = "call", actions = buttons("Odmietnuť", "Prijať")))
        assertTrue(f.ringing)
        assertEquals(NoteTarget.Action(1), f.answer)
        assertEquals(NoteTarget.Action(0), f.hangup)
    }

    @Test fun answerAndHangUpFromPosition() {
        val f = callFacts(note(category = "call", callType = 1, actions = buttons("No", "Yes")))
        assertEquals(NoteTarget.Action(1), f.answer)
        assertEquals(NoteTarget.Action(0), f.hangup)
        assertNull(callFacts(note(category = "call", callType = 2, actions = buttons("No", "Yes"))).answer)
    }

    @Test fun plainOngoingNoteContinuesARingingCallWithinFiveSeconds() {
        val plain = note(pkg = "org.telegram", ongoing = true, actions = buttons("End call"))
        assertFalse(plain.isCall())
        assertTrue(continuesCall(plain, "org.telegram", 4_000))
        assertFalse(continuesCall(plain, "org.telegram", 6_000))
        assertFalse(continuesCall(plain, null, 0))
    }

    @Test fun continuationNeedsSamePackageOngoingAndEndCallWord() {
        assertFalse(continuesCall(note(pkg = "com.other", ongoing = true, actions = buttons("End call")), "org.telegram", 0))
        assertFalse(continuesCall(note(pkg = "org.telegram", actions = buttons("End call")), "org.telegram", 0))
        assertFalse(continuesCall(note(pkg = "org.telegram", ongoing = true, actions = buttons("Mute")), "org.telegram", 0))
    }
}
