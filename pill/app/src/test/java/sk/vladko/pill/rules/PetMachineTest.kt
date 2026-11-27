package sk.vladko.pill.rules

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetMachineTest {
    private fun life(
        animal: PetAnimal,
        steps: Int,
        seed: Int = 3,
    ): List<PetStep> {
        val machine = PetMachine(Random(seed))
        machine.animal = animal
        return List(steps) { machine.next() }
    }

    @Test fun eachAnimalVisitsItsStatesOnlyAndStaysInRange() {
        PetAnimal.entries.forEach { animal ->
            val steps = life(animal, 3000)
            steps.forEach { step ->
                assertTrue(step.ms in PetMachine.durationOf(step.state, animal))
                assertTrue(step.fromX in 0f..1f && step.toX in 0f..1f)
                assertTrue(step.state.animal == null || step.state.animal == animal)
                step.blinks.forEach { assertTrue(step.state == PetState.Sit && it + PET_BLINK_MS <= step.ms) }
                step.effects.forEach { assertTrue(it.at >= 0 && it.at + it.ms <= step.ms) }
            }
            val seen = steps.map { it.state }.toSet()
            PetState.entries.filter { it.animal == animal }.forEach { assertTrue(it in seen, "$animal $it") }
            listOf(PetState.Stretch, PetState.Groom, PetState.Fidget, PetState.Jump).forEach { assertTrue(it in seen, "$animal $it") }
            // Chance never hides the pet: only a notification does.
            listOf(PetState.Hide, PetState.Hidden, PetState.Peek).forEach { assertFalse(it in seen, "$animal $it") }
        }
        // The per-animal tables show: the cat sleeps most, the dog walks most, the bird jumps most.
        val counts = PetAnimal.entries.associateWith { a -> life(a, 3000).groupingBy { it.state }.eachCount() }
        assertTrue(counts.getValue(PetAnimal.Cat)[PetState.Sleep]!! > counts.getValue(PetAnimal.Dog)[PetState.Sleep]!!)
        assertTrue(counts.getValue(PetAnimal.Dog)[PetState.Walk]!! > counts.getValue(PetAnimal.Cat)[PetState.Walk]!!)
        assertTrue(counts.getValue(PetAnimal.Bird)[PetState.Jump]!! > counts.getValue(PetAnimal.Dog)[PetState.Jump]!!)
    }

    @Test fun framesRunUnderAThirdOfTheTimeAndStillStatesOnlyMoveInShortSpans() {
        PetAnimal.entries.forEach { animal ->
            val steps = life(animal, 3000, seed = 7)
            var drawn = 0L
            steps.forEach { step ->
                // Spans may overlap; count each millisecond once.
                val marks = BooleanArray(step.ms.toInt() + 1)
                step.moving.forEach { r -> for (t in r.first..minOf(r.last, step.ms)) marks[t.toInt()] = true }
                drawn += marks.count { it }
                if (step.state.still) step.moving.forEach { assertTrue(it.last - it.first <= PET_PUFF_MS, "${step.state} $it") }
            }
            val share = drawn.toDouble() / steps.sumOf { it.ms }
            assertTrue(share < 0.35, "$animal draws frames ${"%.2f".format(share)} of the time")
        }
    }

    @Test fun sleepLetsZOutEveryFewSecondsAndHeartsRise() {
        val steps = life(PetAnimal.Cat, 2000, seed = 5)
        steps.filter { it.state == PetState.Sleep }.forEach { step ->
            assertTrue(step.puffs.isNotEmpty())
            step.puffs.zipWithNext().forEach { (a, b) -> assertTrue(b - a - PET_PUFF_MS in 4_000L..8_000L) }
            val z = step.puffs.first()
            assertEquals(0f, step.puffAt(z))
            assertTrue(step.puffAt(z + PET_PUFF_MS / 2) in 0.4f..0.6f)
            assertEquals(-1f, step.puffAt(z - 1))
        }
        steps.filter { it.state == PetState.Heart }.forEach { assertTrue(it.markAt(PetMark.Heart, it.ms / 2) in 0f..1f) }
        steps.filter { it.state != PetState.Sleep }.forEach { assertTrue(it.puffs.isEmpty()) }
    }

    @Test fun eventsInterruptAndLifeResumes() {
        val m = PetMachine(Random(1))
        m.holeX = 0.4f
        m.next()
        // Seen: a notification sends it into the hole; then hidden, then it peeks out where the hole is.
        val hide = m.react(PetEvent.Notify, 500)!!
        assertEquals(PetState.Hide, hide.state)
        assertEquals(0.4f, hide.toX)
        assertNull(m.react(PetEvent.Notify, 100))
        // A charger plugged in while it hides waits until it has come out.
        assertNull(m.react(PetEvent.Plug, 200))
        assertEquals(PetState.Hidden, m.next().state)
        assertEquals(PetState.Peek, m.next().state)
        assertEquals(PetState.Excited, m.next().state)
        // Not seen: it is in the hole at once and peeks when seen again.
        m.queue(PetEvent.Notify, 0)
        assertEquals(PetState.Hidden, m.current!!.state)
        assertEquals(PetState.Peek, m.resume(10)!!.state)
        // A charger or low battery waits for the pet to be seen; low battery ends in sleep.
        m.queue(PetEvent.Plug, 0)
        assertEquals(PetState.Excited, m.resume(10)!!.state)
        assertNull(m.resume(10))
        m.queue(PetEvent.Low, 0)
        assertEquals(PetState.Tired, m.resume(10)!!.state)
        assertEquals(PetState.Sleep, m.next().state)
        assertEquals(PetState.Startle, m.react(PetEvent.Open, 0)!!.state)
        assertEquals(PetState.LookAround, m.react(PetEvent.Close, 0)!!.state)
        // In a pill too narrow to walk it never walks and a notification does not make it hide.
        m.canWalk = false
        repeat(500) { assertFalse(m.next().state.walks) }
        assertNull(m.react(PetEvent.Notify, 0))
        assertTrue(petLowCrossed(20, 19, charging = false))
        assertFalse(petLowCrossed(19, 18, charging = false) || petLowCrossed(25, 15, charging = true) || petLowCrossed(null, 10, false))
    }

    @Test fun theShownViewOnlyMakesThePetDance() {
        val m = PetMachine(Random(3))
        m.next()
        // Music plays: it dances on and on; paused, it sits calmly.
        assertEquals(PetState.Dance, m.place(dance = true, elapsedMs = 0)!!.state)
        repeat(5) { assertEquals(PetState.Dance, m.next().state) }
        assertEquals(PetState.Sit, m.place(dance = false, elapsedMs = 0)!!.state)
    }

    @Test fun aPetHeldAtASpotStillRunsToTheHoleOnANotificationAndNeverWalks() {
        val m = PetMachine(Random(5))
        m.holeX = 0.5f
        m.next()
        m.stays = true
        repeat(300) { assertFalse(m.next().state.walks) }
        val hide = m.react(PetEvent.Notify, 0)!!
        assertEquals(PetState.Hide, hide.state)
        assertEquals(0.5f, hide.toX)
        // No hole: the notification changes nothing, seen or not; forcing a hide by hand still works.
        m.hasHole = false
        m.force(PetState.Sit, 0)
        assertNull(m.react(PetEvent.Notify, 0))
        m.queue(PetEvent.Notify, 0)
        assertEquals(PetState.Sit, m.current!!.state)
        assertEquals(PetState.Hide, m.force(PetState.Hide, 0).state)
    }

    @Test fun walkEndsAtTheOtherEndAndTurnsThePet() {
        val machine = PetMachine(Random(1))
        machine.next()
        val first = machine.force(PetState.Walk, 0)
        val back = machine.force(PetState.Walk, first.ms)
        assertEquals(1f, first.fromX)
        assertEquals(0f, first.toX)
        assertEquals(0f, back.fromX)
        assertEquals(1f, back.toX)
        assertNotEquals(first.face, back.face)
        assertEquals(first.face, back.faceFrom)
    }

    @Test fun excursionStartsOnlyFromAStillSitAboutSevenPercentOfTheTime() {
        val m = PetMachine(Random(11))
        m.canRoam = true
        val steps = List(20_000) { m.next() }
        val roams = steps.zipWithNext().filter { it.second.state == PetState.Roam }
        assertTrue(roams.all { it.first.state == PetState.Sit })
        val rolls = steps.zipWithNext().count { it.first.state == PetState.Sit }
        assertTrue(roams.size * 100.0 / rolls in 5.5..8.5, "${roams.size} of $rolls")
        // Not allowed (a view shows, the screen is turned): never.
        val shut = PetMachine(Random(11))
        assertTrue(List(5_000) { shut.next() }.none { it.state == PetState.Roam })
    }

    @Test fun everyPathStartsAtHomeAndEndsAtItsLanding() {
        PetAnimal.entries.forEach { animal ->
            listOf(-1, 1).forEach { face ->
                val space = RoamSpace(unit = 100f, landDx = 80f * face, homeX = 300f, screenW = 1000f)
                val plan = RoamPlan(animal)
                val start = plan.pointAt(0f, face, space)
                val end = plan.pointAt(1f, face, space)
                assertEquals(0f, start.dx, 0.01f)
                assertEquals(0f, start.dy, 0.01f)
                assertEquals(if (animal == PetAnimal.Monkey) space.landDx else 0f, end.dx, 0.01f)
                assertEquals(0f, end.dy, 0.01f)
                // No jump between frames of a 4 s path at 30 frames a second (the bird's gap lies off screen).
                var last = start
                for (i in 1..120) {
                    val now = plan.pointAt(i / 120f, face, space)
                    // The bird flies and the dog falls out of the screen: those gaps lie off screen.
                    val off =
                        (animal == PetAnimal.Bird && (now.dx + space.homeX !in -150f..1150f)) ||
                            (animal == PetAnimal.Dog && (now.dy > 200f || last.dy > 200f))
                    if (!off) assertTrue(kotlin.math.abs(now.dx - last.dx) < 120f && kotlin.math.abs(now.dy - last.dy) < 60f, "$animal $i")
                    last = now
                }
            }
        }
    }

    @Test fun anInterruptedExcursionHurriesHomeBeforeTheReaction() {
        val m = PetMachine(Random(2))
        m.animal = PetAnimal.Dog
        m.next()
        val roam = m.force(PetState.Roam, 0)
        val hurry = m.react(PetEvent.Open, roam.ms / 2)!!
        assertEquals(PetState.Roam, hurry.state)
        assertEquals(PET_HURRY_MS, hurry.ms)
        assertEquals(0.5f, hurry.roam!!.hurryFrom!!, 0.01f)
        // A second reaction while it hurries only changes what follows; the hurry is not cut.
        assertNull(m.react(PetEvent.Plug, 100))
        assertEquals(PetState.Excited, m.next().state)
        // Not seen: no hurry.
        m.force(PetState.Roam, 0)
        assertEquals(PetState.Sit, m.force(PetState.Sit, 1000, hurry = false).state)
    }
}
