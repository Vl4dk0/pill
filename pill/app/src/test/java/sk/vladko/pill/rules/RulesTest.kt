package sk.vladko.pill.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Ev(
    override val key: String,
    override val priority: Int = 2,
    override val showMs: Long = 4000,
    override val kind: String = if (priority == 2) "message" else "charger",
) : PillEvent {
    override val label = key
    override val accent = Accent.App(key)
}

private class Act(
    override val id: String,
    override val priority: Int = 1,
    override val since: Long = 0,
    override val ringing: Boolean = false,
) : PillActivity {
    override val kind = if (priority == 2) "call" else "music"
    override val label = id
    override val accent = Accent.App(id)
}

private fun PillRules.shownKey() =
    when (val s = view().shown) {
        Shown.Idle -> "idle"
        is Shown.Event -> s.event.key
        is Shown.Activity -> s.activity.id
    }

private fun PillRules.post(
    e: PillEvent,
    t: Long,
) = handle(Input.Posted(e), t)

private fun PillRules.update(
    a: PillActivity,
    t: Long,
) = handle(Input.Updated(a), t)

private fun PillRules.touch(
    g: Gesture,
    t: Long,
) = handle(Input.Touch(g), t)

private fun PillRules.tick(t: Long) = handle(Input.Tick, t)

private val away = ScreenState(landscape = true)
private val defaultGestures =
    mapOf(
        IdleSlot.TapLeft to Choice.AppMenu,
        IdleSlot.TapRight to Choice.ActionsMenu,
        IdleSlot.HoldLeft to Choice.Recents,
        IdleSlot.HoldRight to Choice.Nothing,
        IdleSlot.Volume to Choice.HideIdle,
    )

private fun PillRules.settings(
    t: Long,
    collapsed: Boolean = false,
    vararg set: Pair<IdleSlot, Choice>,
) = handle(Input.SettingsAre(defaultGestures + set, collapsed), t)

private fun List<Effect>.arrivals() = filterIsInstance<Effect.Arrived>().map { it.item.label }

class RulesTest {
    @Test fun eventShownOverActivityThenActivityReturns() {
        val r = PillRules()
        r.update(Act("m"), 0)
        r.post(Ev("x"), 100)
        assertEquals("x", r.shownKey())
        r.tick(4100)
        assertEquals("m", r.shownKey())
    }

    @Test fun messageBeatsChargerAndChargerWaitsThenIsDropped() {
        val r = PillRules()
        r.post(Ev("msg", showMs = 8000), 0)
        r.post(Ev("chg", priority = 1, showMs = 3000), 100)
        r.tick(4900)
        assertEquals("chg", r.view().waiting?.key)
        r.tick(5100)
        assertNull(r.view().waiting)
        assertEquals("msg", r.shownKey())
    }

    @Test fun sameKeyTextChangesAndTimerRestarts() {
        val r = PillRules()
        r.post(Ev("chat"), 0)
        r.post(Ev("chat"), 3000)
        r.tick(5000)
        assertEquals("chat", r.shownKey())
        r.tick(7100)
        assertEquals("idle", r.shownKey())
    }

    @Test fun otherChatWaitsTwoSecondsAndOnlyNewestWaits() {
        val r = PillRules()
        r.post(Ev("a"), 0)
        r.post(Ev("b"), 500)
        r.post(Ev("c"), 800)
        assertEquals("a", r.shownKey())
        r.tick(2000)
        assertEquals("c", r.shownKey())
    }

    @Test fun largeViewBlocksEventsButCallBreaksIn() {
        val r = PillRules()
        r.update(Act("m"), 0)
        r.touch(Gesture.Tap, 10)
        assertEquals(Size.Large, r.view().size)
        r.post(Ev("x"), 100)
        assertEquals("m", r.shownKey())
        r.update(Act("call", priority = 2, ringing = true), 200)
        assertEquals("call", r.shownKey())
        assertEquals(Size.Large, r.view().size)
    }

    @Test fun musicOrderLatestFirstAndCallFirst() {
        val r = PillRules()
        r.update(Act("p1", since = 1), 0)
        r.update(Act("p2", since = 5), 0)
        assertEquals("p2", r.shownKey())
        r.update(Act("call", priority = 2), 0)
        assertEquals("call", r.shownKey())
    }

    @Test fun hiddenActivityStaysHiddenUntilSwipeOrEnd() {
        val r = PillRules()
        r.update(Act("m"), 0)
        r.touch(Gesture.SwipeUp, 10)
        assertEquals("idle", r.shownKey())
        r.update(Act("m", since = 9), 20)
        assertEquals("idle", r.shownKey())
        r.touch(Gesture.SwipeRight, 30)
        assertEquals("m", r.shownKey())
        r.touch(Gesture.SwipeUp, 40)
        r.handle(Input.Ended("m"), 50)
        r.update(Act("m"), 60)
        assertEquals("m", r.shownKey())
    }

    @Test fun hiddenActivityStaysHiddenInANewRulesWithTheSameSet() {
        val hidden = mutableSetOf<String>()
        val first = PillRules(hidden)
        first.update(Act("m"), 0)
        first.touch(Gesture.SwipeUp, 10)
        val second = PillRules(hidden)
        second.update(Act("m"), 0)
        assertEquals("idle", second.shownKey())
    }

    @Test fun largeViewStopsMessageTimerAndClosesAfterFiveSeconds() {
        val r = PillRules()
        r.post(Ev("a"), 0)
        r.touch(Gesture.Tap, 1000)
        r.tick(5500)
        assertEquals("a", r.shownKey())
        r.tick(6100)
        assertEquals(Size.Compact, r.view().size)
        assertEquals("a", r.shownKey())
        r.tick(9200)
        assertEquals("idle", r.shownKey())
    }

    @Test fun idleTouches() {
        val r = PillRules()
        assertEquals(listOf<Effect>(Effect.StopGlow), r.touch(Gesture.Tap, 0))
        assertEquals(Size.FastMenu, r.view().size)
        assertTrue(r.touch(Gesture.Outside, 10).isEmpty())
        assertEquals(listOf<Effect>(Effect.StopGlow), r.touch(Gesture.LongPress, 20))
        assertEquals(Size.RecentApps, r.view().size)
        assertTrue(r.touch(Gesture.Release, 30).isEmpty())
        assertEquals(Size.Compact, r.view().size)
    }

    @Test fun longPressOpensSourceAndHiddenEventKeepsNothing() {
        val r = PillRules()
        r.post(Ev("a"), 0)
        assertTrue(r.touch(Gesture.LongPress, 10).single() is Effect.Open)
        r.touch(Gesture.SwipeUp, 20)
        assertEquals("idle", r.shownKey())
    }

    @Test fun answeredCallShrinksToCompact() {
        val r = PillRules()
        r.update(Act("call", priority = 2, ringing = true), 0)
        assertEquals(Size.Large, r.view().size)
        r.update(Act("call", priority = 2), 100)
        assertEquals(Size.Compact, r.view().size)
    }

    @Test fun awayShowsOnlyWhatNeedsAttention() {
        val r = PillRules()
        r.update(Act("music"), 0)
        r.handle(Input.ScreenIs(away), 10)
        assertTrue(!r.view().visible)
        r.post(Ev("a"), 20)
        assertTrue(r.view().visible)
        r.tick(4100)
        assertTrue(!r.view().visible)
        r.update(Act("call", priority = 2, ringing = true), 5000)
        assertTrue(r.view().visible && r.view().size == Size.Large)
        r.handle(Input.Ended("call"), 6000)
        assertTrue(!r.view().visible)
        r.handle(Input.ScreenIs(ScreenState()), 7000)
        assertTrue(r.view().visible)
    }

    @Test fun lockedShowsNothingAndDropsEvents() {
        val r = PillRules()
        r.post(Ev("a"), 0)
        r.handle(Input.ScreenIs(ScreenState(locked = true)), 10)
        assertTrue(!r.view().visible)
        r.post(Ev("b"), 20)
        r.handle(Input.ScreenIs(ScreenState()), 30)
        assertEquals("idle", r.shownKey())
    }

    /** The engine re-arms its timer at nextDeadline(); a passed deadline would spin the main thread. */
    @Test fun deadlineIsNeverInThePast() {
        val r = PillRules()
        r.post(Ev("msg", priority = 2, showMs = 8000), 0)
        r.post(Ev("chg", priority = 1), 100)
        r.update(Act("m"), 200)
        for (t in 0L..20_000L step 50) {
            r.tick(t)
            if (t == 3000L) r.post(Ev("msg", priority = 2, showMs = 8000), t)
            if (t == 6000L) r.post(Ev("chg", priority = 1), t)
            r.nextDeadline(t)?.let { assertTrue(it > t, "deadline $it at $t") }
        }
    }

    @Test fun newestWaiterOfEqualPriorityWins() {
        val r = PillRules()
        r.post(Ev("msg", priority = 2, showMs = 4000), 0)
        r.post(Ev("old", priority = 1, kind = "a"), 100)
        r.post(Ev("new", priority = 1, kind = "b"), 200)
        assertEquals("new", r.view().waiting?.key)
        r.tick(4000)
        assertEquals("new", r.shownKey())
    }

    @Test fun extendedDurationKeepsEventUntilDone() {
        val r = PillRules()
        val e = Ev("long")
        r.post(e, 0)
        r.handle(Input.ShowFor(e, 12_000), 50)
        // Shorter or stale requests do nothing.
        r.handle(Input.ShowFor(e, 2_000), 100)
        r.handle(Input.ShowFor(Ev("long"), 30_000), 100)
        r.tick(11_900)
        assertEquals("long", r.shownKey())
        r.tick(12_050)
        assertEquals("idle", r.shownKey())
    }

    @Test fun extendedEventObeysSameChatUpdateAndTwoSecondRule() {
        val r = PillRules()
        val first = Ev("chat")
        r.post(first, 0)
        r.handle(Input.ShowFor(first, 20_000), 50)
        // Same-chat update resets the lifetime to the new message's duration (and can extend again).
        val second = Ev("chat")
        r.post(second, 3_000)
        r.tick(6_900)
        assertEquals("chat", r.shownKey())
        r.tick(7_100)
        assertEquals("idle", r.shownKey())

        // Different chat still replaces an extended message once it has been visible 2 seconds.
        val longA = Ev("a")
        r.post(longA, 10_000)
        r.handle(Input.ShowFor(longA, 25_000), 10_050)
        r.post(Ev("b"), 10_500)
        r.tick(11_900)
        assertEquals("a", r.shownKey())
        r.tick(12_000)
        assertEquals("b", r.shownKey())
    }

    @Test fun closeClosesLargeAndMenuButNotRinging() {
        val r = PillRules()
        r.touch(Gesture.Tap, 0)
        assertEquals(Size.FastMenu, r.view().size)
        r.handle(Input.Close, 10)
        assertEquals(Size.Compact, r.view().size)
        r.update(Act("m"), 20)
        r.touch(Gesture.Tap, 30)
        r.handle(Input.Close, 40)
        assertEquals(Size.Compact, r.view().size)
        r.update(Act("call", priority = 2, ringing = true), 50)
        r.handle(Input.Close, 60)
        assertEquals(Size.Large, r.view().size)
        assertEquals("call", r.shownKey())
    }

    @Test fun dismissRemovesTheShownEventAndClosesLarge() {
        val r = PillRules()
        r.update(Act("m"), 0)
        r.post(Ev("a"), 10)
        r.touch(Gesture.Tap, 20)
        r.handle(Input.Dismiss("other"), 30)
        assertEquals(Size.Large, r.view().size)
        r.handle(Input.Dismiss("a"), 40)
        assertEquals(Size.Compact, r.view().size)
        assertEquals("m", r.shownKey())
    }

    @Test fun typingKeepsLargeOpenAndClearsWhenItCloses() {
        val r = PillRules()
        r.post(Ev("a", showMs = 4000), 0)
        r.touch(Gesture.Tap, 100)
        r.handle(Input.Typing(true), 200)
        assertTrue(r.view().typing)
        r.tick(20_000)
        assertEquals(Size.Large, r.view().size)
        r.handle(Input.Close, 20_100)
        assertTrue(!r.view().typing)
        r.touch(Gesture.Tap, 20_200)
        assertTrue(!r.view().typing)
        r.tick(25_300)
        assertEquals(Size.Compact, r.view().size)
    }

    @Test fun volumeHideIdleTogglesInAnyStateAndKeepsLargeOpen() {
        val r = PillRules()
        assertEquals(listOf<Effect>(Effect.SetCollapsed(true)), r.handle(Input.VolumeKeys, 0))
        r.settings(10, collapsed = true)
        assertTrue(r.view().drawsNothing)
        r.update(Act("m"), 20)
        r.touch(Gesture.Tap, 30)
        assertEquals(listOf<Effect>(Effect.SetCollapsed(false)), r.handle(Input.VolumeKeys, 40))
        assertEquals(Size.Large, r.view().size)
    }

    @Test fun idleGestureChoices() {
        val r = PillRules()
        r.settings(
            0,
            false,
            IdleSlot.TapLeft to Choice.Recents,
            IdleSlot.HoldLeft to Choice.Recents,
            IdleSlot.TapRight to Choice.Action("torch"),
            IdleSlot.HoldRight to Choice.Nothing,
            IdleSlot.Volume to Choice.ActionsMenu,
        )
        r.touch(Gesture.Tap, 10)
        assertEquals(Size.RecentMenu, r.view().size)
        r.handle(Input.Close, 20)
        r.touch(Gesture.LongPress, 30)
        assertEquals(Size.RecentApps, r.view().size)
        r.touch(Gesture.Release, 40)
        assertEquals(listOf<Effect>(Effect.RunAction("torch")), r.touch(Gesture.TapRight, 50))
        assertEquals(Size.Compact, r.view().size)
        assertTrue(r.touch(Gesture.LongPressRight, 60).isEmpty())
        assertEquals(Size.Compact, r.view().size)
        r.handle(Input.VolumeKeys, 70)
        assertEquals(Size.QuickMenu, r.view().size)
    }

    @Test fun collapsedIdleDrawsNothingButEventsDo() {
        val r = PillRules()
        r.settings(0, collapsed = true)
        assertTrue(r.view().drawsNothing)
        r.post(Ev("a"), 10)
        assertTrue(!r.view().drawsNothing)
        r.tick(5000)
        assertTrue(r.view().drawsNothing)
        r.touch(Gesture.Tap, 5100)
        assertTrue(!r.view().drawsNothing)
    }

    @Test fun arrivalForNewEventAndNewChat() {
        val r = PillRules()
        assertEquals(listOf("a"), r.post(Ev("a"), 0).arrivals())
        r.post(Ev("b"), 100)
        assertEquals(listOf("b"), r.tick(2100).arrivals())
        assertEquals(listOf("m"), r.update(Act("m"), 7000).arrivals())
    }

    @Test fun noArrivalForSameChatNextSongOrReturningActivity() {
        val r = PillRules()
        r.post(Ev("a"), 0)
        assertTrue(r.post(Ev("a"), 1000).arrivals().isEmpty())
        r.tick(6000)
        r.update(Act("m"), 6100)
        assertTrue(r.update(Act("m", since = 5), 6200).arrivals().isEmpty())
        r.post(Ev("b"), 6300)
        assertTrue(r.tick(10_400).arrivals().isEmpty())
        assertEquals("m", r.shownKey())
    }

    @Test fun noArrivalWhileInvisible() {
        val r = PillRules()
        r.handle(Input.ScreenIs(away), 0)
        assertTrue(r.update(Act("m"), 10).arrivals().isEmpty())
        assertEquals(listOf("m"), r.handle(Input.ScreenIs(ScreenState()), 20).arrivals())
    }

    @Test fun carouselHasActivitiesThenTheIdlePill() {
        val r = PillRules()
        r.update(Act("m1", since = 1), 2)
        r.update(Act("m2", since = 5), 3)
        assertEquals("m2", r.shownKey())
        r.touch(Gesture.SwipeRight, 10)
        assertEquals("m2", r.shownKey())
        // Moving right steps through activities, then onto the idle pill, hiding them all; further right stays.
        r.touch(Gesture.SwipeLeft, 20)
        assertEquals("m1", r.shownKey())
        assertTrue(r.touch(Gesture.SwipeLeft, 30).isEmpty())
        assertEquals("idle", r.shownKey())
        assertTrue(r.view().activities.all { it.hidden })
        r.touch(Gesture.SwipeLeft, 40)
        assertEquals("idle", r.shownKey())
        // Back from idle lands on the nearest activity, then the next one.
        r.touch(Gesture.SwipeRight, 50)
        assertEquals("m1", r.shownKey())
        r.touch(Gesture.SwipeRight, 60)
        assertEquals("m2", r.shownKey())
        // Without activities a sideways swipe on the idle pill does nothing.
        val empty = PillRules()
        assertTrue(empty.touch(Gesture.SwipeRight, 10).isEmpty() && empty.touch(Gesture.SwipeLeft, 20).isEmpty())
        assertEquals("idle", empty.shownKey())
    }
}
