package sk.vladko.pill.rules

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// Pure Kotlin: the state machine of the idle pet. It decides what the pet does, for how long and where it stands, and
// how it reacts to what happens on the phone. Deterministic for a given Random, so plain JVM tests run it.

/** Which animal the pet is. Each animal has its own chances and its own special states ([PetState.animal]). */
enum class PetAnimal(
    val id: String,
    val label: String,
) {
    Cat("cat", "Cat"),
    Dog("dog", "Dog"),
    Bird("bird", "Bird"),
    Monkey("monkey", "Monkey"),
    ;

    companion object {
        val ids: List<String> = entries.map { it.id }

        /** The animal with [id]; the default when unknown. */
        fun of(id: String): PetAnimal = entries.firstOrNull { it.id == id } ?: Cat
    }
}

/** How the body is folded: upright and still, up on four legs, or lying down. */
enum class PetPose { Sit, Stand, Lie }

/**
 * What the pet does. [pose] is the body it ends in, [animal] is set for states only that animal has, [walks] states need
 * room to move. What each state must show is written on `PetScene` (idle/PetIdle.kt).
 */
enum class PetState(
    val id: String,
    val pose: PetPose,
    val animal: PetAnimal? = null,
    val walks: Boolean = false,
) {
    Sit("sit", PetPose.Sit),
    LookAround("look", PetPose.Sit),
    Walk("walk", PetPose.Stand, walks = true),
    Sleep("sleep", PetPose.Lie),
    Heart("heart", PetPose.Sit),
    Stretch("stretch", PetPose.Stand),
    Groom("groom", PetPose.Sit),
    Fidget("fidget", PetPose.Sit),
    Jump("jump", PetPose.Stand),
    Hide("hide", PetPose.Stand, walks = true),
    Hidden("hidden", PetPose.Stand),
    Peek("peek", PetPose.Sit),
    Lick("lick", PetPose.Sit, PetAnimal.Cat),
    Knead("knead", PetPose.Lie, PetAnimal.Cat),
    Bark("bark", PetPose.Stand, PetAnimal.Dog),
    Dig("dig", PetPose.Stand, PetAnimal.Dog),
    Wings("wings", PetPose.Sit, PetAnimal.Bird),
    Peck("peck", PetPose.Stand, PetAnimal.Bird),
    Scratch("scratch", PetPose.Sit, PetAnimal.Monkey),
    Banana("banana", PetPose.Sit, PetAnimal.Monkey),
    Excited("excited", PetPose.Stand),
    Tired("tired", PetPose.Lie),
    Startle("startle", PetPose.Stand),
    Dance("dance", PetPose.Stand),

    /** A rare excursion out of the pill along the animal's own path ([roamPoint]); see [PetMachine.next]. */
    Roam("roam", PetPose.Stand, walks = true),
    ;

    /** Still states draw nothing between their blinks, "z"s and pose changes; every other state moves for its whole length. */
    val still: Boolean get() = this == Sit || this == Sleep || this == Hidden

    /** The pet is behind the camera hole, or on its way there or out of it. */
    val hidesAtHole: Boolean get() = this == Hide || this == Hidden || this == Peek

    /** The first five states every scene drew from the start; a scene without its own drawing for a state draws this one. */
    val drawAs: PetState get() =
        when (this) {
            Sit, LookAround, Walk, Sleep, Heart -> this
            Hide -> Walk
            Roam -> Jump
            Excited -> Heart
            Tired -> Sleep
            else -> LookAround
        }

    companion object {
        fun of(id: String): PetState? = entries.firstOrNull { it.id == id }
    }
}

/** Something on the phone the pet reacts to. */
enum class PetEvent(
    val id: String,
) {
    /**
     * A notification arrives: the pet runs behind the camera hole, stays there a few seconds and peeks out. Only this event
     * starts Hide, Hidden and Peek (besides forcing them by hand), and only when the pet can reach a real hole (the pill has one and has room to walk); else the pet carries on.
     */
    Notify("notify"),

    /** A charger was plugged in: the pet gets excited (it stays in the charger view). */
    Plug("plug"),

    /** The battery dropped below [PET_LOW_BATTERY] %: the pet gets tired and lies down. */
    Low("low"),

    /** A large view or menu opened: the pet is startled and looks at it. */
    Open("open"),

    /** That large view closed: the pet looks around and goes on. */
    Close("close"),
    ;

    companion object {
        fun of(id: String): PetEvent? = entries.firstOrNull { it.id == id }
    }
}

/** A small mark that comes out of the pet: a heart, a "z" (three letters one after another), bark lines, a sweat drop. */
enum class PetMark { Heart, Z, Bark, Sweat }

/** One [mark] that starts [at] ms into its step and lasts [ms]. */
class PetEffect(
    val mark: PetMark,
    val at: Long,
    val ms: Long,
)

/** Time the body needs to change from one pose to another, and to turn around. */
const val PET_BLEND_MS = 900L

/** Length of one "z z z" while sleeping. */
const val PET_PUFF_MS = 1800L

/** Length of one blink while sitting. */
const val PET_BLINK_MS = 200L

/** One beat of the dance (120 beats a minute). */
const val PET_BEAT_MS = 500L

/** Chance (%) that the pet leaves the pill when its still sit ends. It never leaves from any other state. */
const val ROAM_CHANCE = 7

/** How long the pet takes to run home when something interrupts its excursion. */
const val PET_HURRY_MS = 300L

/** Below this battery level (%) the pet looks tired. */
const val PET_LOW_BATTERY = 20

/** True when the battery just went from [PET_LOW_BATTERY] % or more to below it while not charging. */
fun petLowCrossed(
    before: Int?,
    now: Int,
    charging: Boolean,
): Boolean = before != null && before >= PET_LOW_BATTERY && now < PET_LOW_BATTERY && !charging

/**
 * One stretch of the pet's life. Positions are fractions of the walkable width (0 left end, 1 right end); [face] is -1
 * when looking left and 1 when looking right. [poseFrom] and [faceFrom] are how the pet was when the step began.
 * [blinks] are the start times (ms from the step start) of the blinks, [effects] the marks that come out.
 */
class PetStep(
    val state: PetState,
    val ms: Long,
    val fromX: Float,
    val toX: Float,
    val faceFrom: Int,
    val face: Int,
    val poseFrom: PetPose,
    val blinks: List<Long>,
    val effects: List<PetEffect>,
    val roam: RoamPlan? = null,
) {
    val pose: PetPose get() = state.pose

    /** Start times of the "z z z" marks. */
    val puffs: List<Long> get() = effects.filter { it.mark == PetMark.Z }.map { it.at }

    /** Time spans (ms from the step start) in which the picture changes; outside them the pet is perfectly still. */
    val moving: List<LongRange> =
        buildList {
            if (!state.still) {
                add(0L..ms)
            } else if (poseFrom != pose || faceFrom != face) {
                add(0L..PET_BLEND_MS)
            }
            effects.forEach { add(it.at..it.at + it.ms) }
            blinks.forEach { add(it..it + PET_BLINK_MS) }
        }

    /** The same step with every position moved by [dx] (a fraction of the walkable width; it may leave 0 to 1). */
    fun shifted(dx: Float): PetStep =
        if (dx ==
            0f
        ) {
            this
        } else {
            PetStep(state, ms, fromX + dx, toX + dx, faceFrom, face, poseFrom, blinks, effects, roam)
        }

    /** How far the step has come at [t] (0 to 1). */
    fun progressAt(t: Long): Float = (t.toFloat() / ms).coerceIn(0f, 1f)

    /** How far the body has changed from [poseFrom] and [faceFrom] to [pose] and [face] at [t] (0 to 1). */
    fun blendAt(t: Long): Float = (t.toFloat() / PET_BLEND_MS).coerceIn(0f, 1f)

    /** The facing at [t], turning smoothly from [faceFrom] to [face] (-1 to 1). */
    fun faceAt(t: Long): Float = faceFrom + (face - faceFrom) * blendAt(t)

    /** How far the "z z z" running at [t] has come (0 to 1), or -1 when none runs. */
    fun puffAt(t: Long): Float = markAt(PetMark.Z, t)

    /** How far the [mark] running at [t] has come (0 to 1), or -1 when none runs. */
    fun markAt(
        mark: PetMark,
        t: Long,
    ): Float {
        val e = effects.firstOrNull { it.mark == mark && t >= it.at && t < it.at + it.ms } ?: return -1f
        return (t - e.at).toFloat() / e.ms
    }

    fun blinkAt(t: Long): Boolean = blinks.any { t >= it && t < it + PET_BLINK_MS }

    /** Where the pet stands at [t]: when it moves, it starts once it is up on its legs. */
    fun xAt(t: Long): Float {
        // An excursion starts at [fromX] and its path ([roamPoint]) carries the pet to [toX].
        if (fromX == toX || roam != null) return fromX
        val p = ((t - PET_BLEND_MS).toFloat() / (ms - PET_BLEND_MS)).coerceIn(0f, 1f)
        return fromX + (toX - fromX) * (p * p * (3f - 2f * p))
    }
}

/**
 * How a [PetState.Roam] step runs. [hurryFrom] is null for a whole excursion; for the hurry home that cuts one short it is
 * how far (0 to 1) the excursion had come, and the step is [PET_HURRY_MS] long.
 */
class RoamPlan(
    val animal: PetAnimal,
    val hurryFrom: Float? = null,
)

/** The space an excursion plays in, all in pixels: [unit] the pet's height, [landDx] how far the landing is right of the start, [homeX] the pet's middle from the screen's left edge, [screenW] the screen's width. */
class RoamSpace(
    val unit: Float,
    val landDx: Float,
    val homeX: Float,
    val screenW: Float,
)

/** Where the pet is [dx], [dy] from where it started (pixels, y grows down) and which way it looks (-1 left to 1 right). */
class RoamPoint(
    val dx: Float,
    val dy: Float,
    val heading: Float,
)

private fun ease(t: Float): Float = t * t * (3f - 2f * t)

/** How far [p] is from [a] to [b], 0 to 1. */
private fun seg(
    p: Float,
    a: Float,
    b: Float,
): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)

private fun mix(
    a: Float,
    b: Float,
    t: Float,
) = a + (b - a) * t

private const val TAU = 2f * PI.toFloat()

/** How far (0 to 1) a monkey's excursion has come when it lets go of the pill's edge and flies to its landing. */
const val MONKEY_LET_GO = 0.84f

/**
 * Where the pet is at [p] (0 to 1) of an excursion that looks toward [face] (-1 or 1). Every path starts at (0, 0) and ends at
 * ([RoamSpace.landDx], 0). For the hurry home the path runs from where the excursion had come to its end.
 * Monkey: stays at its place until [MONKEY_LET_GO] (its scene drops it under the pill and swings it), then flies in an
 * arc to the far side of the hole. Bird: flies off the
 * nearer screen edge and back in from the other. Cat: chases a laser dot out of the pill and down below it, pounces on it
 * there and jumps back. Dog: sniffs, digs
 * through the pill's floor, falls out of the screen and walks back in from the pill's end.
 */
fun RoamPlan.pointAt(
    p: Float,
    face: Int,
    space: RoamSpace,
): RoamPoint {
    val from = hurryFrom ?: return excursion(animal, p, face, space)
    val a = excursion(animal, from, face, space)
    val b = excursion(animal, 1f, face, space)
    val e = ease(p)
    return RoamPoint(mix(a.dx, b.dx, e), mix(a.dy, b.dy, e) - 0.2f * space.unit * sin(p * PI.toFloat()), mix(a.heading, b.heading, e))
}

private fun excursion(
    animal: PetAnimal,
    p: Float,
    face: Int,
    s: RoamSpace,
): RoamPoint {
    val h = s.unit
    val dir = face.toFloat()
    return when (animal) {
        PetAnimal.Monkey -> {
            // The scene hangs and swings the monkey below its place; at the let-go it flies in an arc to the landing.
            val u = seg(p, MONKEY_LET_GO, 1f)
            RoamPoint(s.landDx * ease(u), -0.45f * h * sin(u * PI.toFloat()), dir)
        }
        PetAnimal.Bird -> {
            val exit = if (dir < 0f) -2f * h else s.screenW + 2f * h
            val enter = if (dir < 0f) s.screenW + 2f * h else -2f * h
            when {
                p < 0.4f -> {
                    val u = seg(p, 0f, 0.4f)
                    val bob = 0.12f * h * sin(TAU * 3f * u) * u
                    RoamPoint(mix(s.homeX, exit, u * u) - s.homeX, -0.4f * h * sin(u * PI.toFloat() / 2f) + bob, dir)
                }
                // Off screen both ways: it stays past the exit edge, then is past the enter edge. Nothing shows in between.
                p < 0.5f -> RoamPoint(exit - s.homeX, -0.4f * h, dir)
                else -> {
                    val u = seg(p, 0.5f, 1f)
                    val bob = 0.12f * h * sin(TAU * 3f * u) * (1f - u)
                    RoamPoint(mix(enter, s.homeX, 1f - (1f - u) * (1f - u)) - s.homeX, -0.4f * h * (1f - ease(u)) + bob, dir)
                }
            }
        }
        PetAnimal.Cat -> {
            // Chases a laser dot out of the pill: along the floor, over the lower edge, down below it; creeps, pounces there,
            // then jumps back up into the pill.
            val out = 0.62f * h
            val pounce = 0.98f * h
            when {
                p < 0.205f -> RoamPoint(0f, 0f, dir)
                p < 0.375f -> RoamPoint(dir * out * seg(p, 0.205f, 0.375f), h * seg(p, 0.3f, 0.375f).let { it * it }, dir)
                p < 0.48f -> RoamPoint(dir * (out + 0.12f * h * seg(p, 0.41f, 0.48f)), h, dir)
                p < 0.55f -> RoamPoint(dir * (out + 0.12f * h + (pounce - out - 0.12f * h) * ease(seg(p, 0.48f, 0.55f))), h, dir)
                p < 0.835f -> RoamPoint(dir * pounce, h, dir)
                p < 0.94f -> {
                    val u = ease(seg(p, 0.835f, 0.94f))
                    RoamPoint(dir * pounce * (1f - u), h * (1f - u), dir)
                }
                else -> RoamPoint(0f, 0f, dir)
            }
        }
        PetAnimal.Dog -> {
            // Sniffs and digs on the pill's floor, falls out of the screen, and after a pause walks in from the nearer end.
            val fall = 26f * h
            val side = if (s.homeX < s.screenW / 2f) -1f else 1f
            when {
                p < 0.14f -> RoamPoint(dir * 0.06f * h * sin(TAU * 2f * seg(p, 0f, 0.14f)), 0f, dir)
                p < 0.36f -> RoamPoint(0f, 0.05f * h * ease(seg(p, 0.28f, 0.36f)), dir)
                p < 0.54f -> RoamPoint(0f, 0.05f * h + fall * seg(p, 0.36f, 0.54f).let { it * it }, dir)
                p < 0.66f -> RoamPoint(side * 1.7f * h, fall, -side)
                p < 0.88f -> RoamPoint(side * 1.7f * h * (1f - seg(p, 0.66f, 0.88f)), 0f, -side)
                else -> RoamPoint(0f, 0f, dir)
            }
        }
    }
}

class PetMachine(
    private val random: Random,
    x: Float = 1f,
    facing: Int = -1,
) {
    private var x = x
    private var facing = facing
    private var pose = PetPose.Sit

    /** A reaction waiting for the pet to be seen again (charger, low battery). */
    private var pending: PetState? = null

    var current: PetStep? = null
        private set

    /** Which animal rolls the next states. */
    var animal = PetAnimal.Cat

    /** False when the pill has no room to walk; the pet then never walks and a notification does not make it hide. */
    var canWalk = true

    /** A view keeps the pet at its spot: it does not walk or roam there, but a notification still sends it to the camera hole. */
    var stays = false

    /** False when the pill has no camera hole: a notification then does nothing to the pet. */
    var hasHole = true

    /** The pill shows only its idle view (portrait, nothing else open): the pet may leave the pill. */
    var canRoam = false

    /** What follows the hurry home that cuts an excursion short. */
    private var afterHurry: PetState? = null

    /** Where the camera hole is, as a fraction of the walkable width; the pet hides and peeks there. */
    var holeX = 0.5f

    /** How far from [holeX] (a fraction of the walkable width) the hole still covers the pet. Only a walk or the hide reaction may be there. */
    var holeHalf = 0f

    /** The state that follows the walk the pet finishes because it was cut short under the hole. */
    private var afterWalk: PetState? = null

    /** Where the walk that leaves the hole ends; null for the usual far end. */
    private var walkTo: Float? = null

    /** The music view shows and the music plays: the pet dances. */
    private var dancing = false

    /** The pet is out on an excursion and not yet hurrying home. */
    private val roaming: Boolean get() = current?.let { it.state == PetState.Roam && it.roam?.hurryFrom == null } == true

    private fun covered(at: Float): Boolean = hasHole && abs(at - holeX) < holeHalf

    private val hiding: Boolean get() = current?.state == PetState.Hide || current?.state == PetState.Hidden

    /**
     * The step that follows the current one: hide leads to hidden and hidden to a peek, after which life goes on by chance;
     * otherwise dance, a waiting reaction, else chance. Out of the peek the pet always walks away from the hole first.
     * Chance never picks Hide, Hidden or Peek.
     */
    fun next(): PetStep {
        val from = current?.state
        val wait = pending
        val state =
            when {
                from == null -> PetState.Sit
                from == PetState.Roam && current?.roam?.hurryFrom != null -> afterHurry.also { afterHurry = null } ?: PetState.Sit
                from == PetState.Hide -> PetState.Hidden
                from == PetState.Hidden -> PetState.Peek
                from == PetState.Peek -> PetState.Walk
                from == PetState.Walk && afterWalk != null -> afterWalk.also { afterWalk = null } ?: PetState.Sit
                dancing -> PetState.Dance
                wait != null -> wait.also { pending = null }
                // Only a still sit can end in an excursion.
                from == PetState.Sit && canRoam && canWalk && !stays && random.nextInt(100) < ROAM_CHANCE -> PetState.Roam
                else -> roll(from)
            }
        return start(state)
    }

    /** The pet can run to a real camera hole. */
    private val reachesHole: Boolean get() = canWalk && hasHole

    /**
     * What the pill shows now, [elapsedMs] into the current step: [dance] starts the dance (after a hide that is going on)
     * and, when it ends, the pet sits calmly. Returns the step that starts now, or null to go on.
     */
    fun place(
        dance: Boolean,
        elapsedMs: Long,
    ): PetStep? {
        dancing = dance
        val s = current?.state
        return when {
            dancing && s != PetState.Dance && !hiding -> goTo(PetState.Dance, elapsedMs)
            !dancing && s == PetState.Dance -> goTo(PetState.Sit, elapsedMs)
            !canRoam && roaming -> goTo(PetState.Sit, elapsedMs)
            else -> null
        }
    }

    /**
     * Starts [state] now, or after the hurry home when the pet is out; null while it already hurries (the hurry then ends
     * in [state]).
     */
    private fun goTo(
        state: PetState,
        elapsedMs: Long,
    ): PetStep? {
        val c = current
        if (c?.state == PetState.Roam && c.roam?.hurryFrom != null && elapsedMs < c.ms) {
            afterHurry = state
            return null
        }
        return force(state, elapsedMs)
    }

    /**
     * Starts [state] now; [elapsedMs] is how far the interrupted step had come, so the pet stays where it was. An excursion
     * is not cut: the pet hurries home first ([PET_HURRY_MS], eased, no jump) and [state] follows. [hurry] false (the pet
     * is not seen) skips that.
     */
    fun force(
        state: PetState,
        elapsedMs: Long,
        hurry: Boolean = true,
    ): PetStep {
        val c = current
        if (c?.state == PetState.Roam) {
            // The pet stands where the excursion lands: [start] already set it.
            val plan = c.roam
            if (hurry && plan?.hurryFrom == null && elapsedMs < c.ms) {
                afterHurry = state
                return hurryHome(c, c.progressAt(elapsedMs))
            }
            pose = c.pose
            facing = c.face
            return start(state)
        }
        current?.let {
            x = it.xAt(elapsedMs)
            if (elapsedMs >= PET_BLEND_MS / 2) {
                pose = it.pose
                facing = it.face
            } else {
                pose = it.poseFrom
                facing = it.faceFrom
            }
        }
        // Under the hole only a walk goes on: a pet that rests there is half covered, so it walks out first.
        val cut = current
        if (cut != null && covered(x) && !state.hidesAtHole && state != PetState.Walk) {
            afterWalk = state
            walkTo =
                if (cut.state == PetState.Walk) {
                    cut.toX
                } else if (cut.face > 0) {
                    1f
                } else {
                    0f
                }
            return start(PetState.Walk)
        }
        return start(state)
    }

    /** The pet is seen and [event] happens [elapsedMs] into the current step: the reaction starts now, or null for none. */
    fun react(
        event: PetEvent,
        elapsedMs: Long,
    ): PetStep? {
        // In the hole, a reaction waits until the pet comes out.
        if (hiding) {
            queue(event, elapsedMs)
            return null
        }
        return when (event) {
            PetEvent.Notify -> if (reachesHole) goTo(PetState.Hide, elapsedMs) else null
            PetEvent.Plug -> goTo(PetState.Excited, elapsedMs)
            PetEvent.Low -> goTo(PetState.Tired, elapsedMs)
            PetEvent.Open -> goTo(PetState.Startle, elapsedMs)
            PetEvent.Close -> goTo(PetState.LookAround, elapsedMs)
        }
    }

    /** [event] happens while the pet is not seen: it is in the hole at once, or the reaction waits for [resume]. */
    fun queue(
        event: PetEvent,
        elapsedMs: Long,
    ) {
        when (event) {
            PetEvent.Notify -> if (reachesHole && !hiding) force(PetState.Hidden, elapsedMs, hurry = false)
            PetEvent.Plug -> pending = PetState.Excited
            PetEvent.Low -> pending = PetState.Tired
            PetEvent.Open, PetEvent.Close -> {}
        }
    }

    /** The pet is seen again [elapsedMs] into its step: it peeks out of the hole, or a waiting reaction starts; null to go on. */
    fun resume(elapsedMs: Long): PetStep? {
        // An excursion does not go on behind the owner's back: the pet is home when it is seen again.
        if (current?.state == PetState.Roam) return force(pending.also { pending = null } ?: PetState.Sit, elapsedMs, hurry = false)
        if (hiding) return force(PetState.Peek, elapsedMs)
        val wait = pending ?: return null
        pending = null
        return force(wait, elapsedMs)
    }

    /** The step that runs the pet from where [excursion] had come ([from], 0 to 1) to where the excursion ends. */
    private fun hurryHome(
        excursion: PetStep,
        from: Float,
    ): PetStep {
        val plan = RoamPlan(excursion.roam?.animal ?: animal, from)
        val step =
            PetStep(
                PetState.Roam,
                PET_HURRY_MS,
                excursion.fromX,
                excursion.toX,
                excursion.face,
                excursion.face,
                PetPose.Stand,
                emptyList(),
                emptyList(),
                plan,
            )
        current = step
        return step
    }

    private fun start(state: PetState): PetStep {
        afterHurry = null
        if (state != PetState.Walk) afterWalk = null
        val toX =
            when {
                state == PetState.Walk -> walkTo ?: if (x < 0.5f) 1f else 0f
                // The monkey lands on the other side of the hole.
                state == PetState.Roam && animal == PetAnimal.Monkey -> (2f * holeX - x).coerceIn(0f, 1f)
                (state == PetState.Hide || state == PetState.Hidden) && reachesHole -> holeX
                else -> x
            }
        walkTo = null
        val face =
            when {
                // The bird leaves by the nearer screen edge.
                state == PetState.Roam && animal == PetAnimal.Bird -> if (x < holeX) -1 else 1
                toX != x -> if (toX > x) 1 else -1
                // Out of the hole it looks toward the far end, where it walks next.
                state == PetState.Peek -> if (x < 0.5f) 1 else -1
                // After a walk the pet turns round to face the pill.
                current?.state == PetState.Walk -> if (x >= 0.5f) -1 else 1
                else -> facing
            }
        val range = durations.getValue(state)
        val ms = random.nextLong(range.first, range.last + 1)
        val blinks =
            buildList {
                if (state != PetState.Sit) return@buildList
                var at = PET_BLEND_MS + random.nextLong(3_000, 7_001)
                while (at + PET_BLINK_MS < ms) {
                    add(at)
                    at += PET_BLINK_MS + random.nextLong(3_000, 7_001)
                }
            }
        val roam = if (state == PetState.Roam) RoamPlan(animal) else null
        val step = PetStep(state, ms, x, toX, facing, face, pose, blinks, effectsOf(state, ms), roam)
        current = step
        x = toX
        facing = face
        pose = step.pose
        return step
    }

    private fun effectsOf(
        state: PetState,
        ms: Long,
    ): List<PetEffect> =
        buildList {
            when (state) {
                PetState.Sleep -> {
                    var at = PET_BLEND_MS + random.nextLong(1_000, 3_001)
                    while (at + PET_PUFF_MS < ms) {
                        add(PetEffect(PetMark.Z, at, PET_PUFF_MS))
                        at += PET_PUFF_MS + random.nextLong(4_000, 8_001)
                    }
                }
                PetState.Heart -> add(PetEffect(PetMark.Heart, 200, ms - 200))
                PetState.Bark -> for (i in 0 until (ms / 700).toInt()) add(PetEffect(PetMark.Bark, 300 + i * 700L, 350))
                PetState.Tired -> add(PetEffect(PetMark.Sweat, 300, ms - 300))
                else -> {}
            }
        }

    private fun roll(from: PetState): PetState {
        val row =
            when (from) {
                PetState.Sleep -> mapOf(PetState.Stretch to 60, PetState.Sit to 40)
                PetState.Tired -> mapOf(PetState.Sleep to 1)
                PetState.Walk -> awake + perAnimal.getValue(animal) + (PetState.Walk to 12)
                else -> awake + perAnimal.getValue(animal)
            }.filter { (state, weight) -> weight > 0 && ((canWalk && !stays) || !state.walks) }.toList()
        if (row.isEmpty()) return PetState.Sit
        var pick = random.nextInt(row.sumOf { it.second })
        for ((state, weight) in row) {
            if (pick < weight) return state
            pick -= weight
        }
        return row.last().first
    }

    companion object {
        /** How long each state lasts (ms). A still state is a long time without any drawing. */
        val durations: Map<PetState, LongRange> =
            mapOf(
                PetState.Sit to 5_000L..16_000L,
                PetState.LookAround to 2_500L..4_000L,
                PetState.Walk to 3_000L..5_000L,
                PetState.Sleep to 30_000L..90_000L,
                PetState.Heart to 2_200L..3_200L,
                PetState.Stretch to 2_400L..3_400L,
                PetState.Groom to 3_000L..5_000L,
                PetState.Fidget to 1_200L..2_000L,
                PetState.Jump to 1_200L..1_800L,
                PetState.Hide to 1_200L..1_600L,
                PetState.Hidden to 3_000L..8_000L,
                PetState.Peek to 2_500L..3_500L,
                PetState.Lick to 3_000L..5_000L,
                PetState.Knead to 3_000L..4_500L,
                PetState.Bark to 1_800L..2_600L,
                PetState.Dig to 2_500L..4_000L,
                PetState.Wings to 2_000L..3_000L,
                PetState.Peck to 2_000L..3_500L,
                PetState.Scratch to 2_500L..3_500L,
                PetState.Banana to 4_000L..5_000L,
                PetState.Excited to 3_000L..4_000L,
                PetState.Tired to 3_000L..4_000L,
                PetState.Startle to 1_200L..1_500L,
                PetState.Dance to 6_000L..6_000L,
                PetState.Roam to 3_500L..5_000L,
            )

        /** Chances of the next state for every animal while awake. Sit to Sit means it stays still longer. */
        private val awake: Map<PetState, Int> =
            mapOf(
                PetState.Sit to 34,
                PetState.LookAround to 10,
                PetState.Walk to 10,
                PetState.Sleep to 6,
                PetState.Heart to 5,
                PetState.Stretch to 4,
                PetState.Groom to 5,
                PetState.Fidget to 8,
                PetState.Jump to 4,
            )

        /** What each animal does more or less, and its own states: the cat sleeps more, the dog walks more, the bird hops and flies more, the monkey fidgets and jumps more. */
        val perAnimal: Map<PetAnimal, Map<PetState, Int>> =
            mapOf(
                PetAnimal.Cat to mapOf(PetState.Sleep to 11, PetState.Walk to 7, PetState.Lick to 7, PetState.Knead to 5),
                PetAnimal.Dog to
                    mapOf(
                        PetState.Walk to 13,
                        PetState.Sleep to 5,
                        PetState.Heart to 6,
                        PetState.Bark to 6,
                        PetState.Dig to 4,
                    ),
                PetAnimal.Bird to
                    mapOf(PetState.Jump to 8, PetState.Walk to 10, PetState.Sleep to 5, PetState.Wings to 6, PetState.Peck to 6),
                PetAnimal.Monkey to
                    mapOf(PetState.Fidget to 6, PetState.Jump to 4, PetState.Sleep to 4, PetState.Scratch to 6, PetState.Banana to 5),
            )
    }
}
