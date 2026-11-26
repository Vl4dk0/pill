package sk.vladko.pill.idle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sk.vladko.pill.pill
import sk.vladko.pill.rules.Frame
import sk.vladko.pill.rules.HoleInfo
import sk.vladko.pill.rules.PET_BEAT_MS
import sk.vladko.pill.rules.PetAnimal
import sk.vladko.pill.rules.PetEvent
import sk.vladko.pill.rules.PetMachine
import sk.vladko.pill.rules.PetMark
import sk.vladko.pill.rules.PetPose
import sk.vladko.pill.rules.PetState
import sk.vladko.pill.rules.PetStep
import sk.vladko.pill.rules.RoamSpace
import sk.vladko.pill.rules.pointAt
import kotlin.math.abs
import kotlin.random.Random

/**
 * The pet drawn in its box (the idle pill, see [petBox]), in pixels with the camera hole (null when no hole is in the
 * box), ready to draw frames. A scene is built for one animal.
 *
 * Each frame gives the scene: [PetFrame.state], [PetFrame.progress] (0 to 1 over the step), [PetFrame.blend] (0 to 1 from
 * the pose and facing the step began with, `step.poseFrom` / `step.faceFrom`, to its own, `step.pose` / `step.face`),
 * [PetFrame.face] (-1 left to 1 right), [PetFrame.x] (0 left end to 1 right end of the walkable width), [PetFrame.blink],
 * the marks through [PetFrame.mark] (0 to 1 while a heart, "z z z", bark or sweat mark runs, else -1), [PetFrame.beat]
 * for the dance and [PetFrame.low] while the battery is low. Frames are asked only while something moves; between them
 * the last picture stays, so a picture must depend only on the frame.
 *
 * What each state must show (the default is the drawing of [PetState.drawAs], which every scene already has):
 * - Sit: still, upright; blinks. Look (LookAround): turns its head left and right. Walk: walks from `step.fromX` to
 *   `step.toX`. Sleep: lies curled, eyes shut; each Z mark lets three "z" rise one after another. Heart: happy; the Heart
 *   mark rises from the head and fades.
 * - Stretch: stretches the front legs and yawns. Groom: scratches or washes itself. Fidget: a short tail flick or shuffle.
 *   Jump: a hop or pounce in place. Hide, Hidden and Peek (a notification starts them, or the owner by hand): the pet hides BEHIND the real camera hole (the scene already cuts
 *   the hole out of the pet). It draws no hole, ring or burrow, and the pet keeps its full size in all three states: never
 *   scaled, faded or skipped. Hide: runs to the hole (`x` goes to the hole) and stops behind it. Hidden: stands still there
 *   (drawn once, no frames). Peek: leans its head out to the SIDE of the hole (`step.face`, never over the top), looks
 *   around and pulls back (a Walk follows). Forced by hand with no hole, it does the same at its spot.
 * - Cat only: Lick (licks a raised paw), Knead (lies and kneads with the front paws). Dog only: Bark (head up, mouth open on
 *   each Bark mark, with bark lines), Dig (digs with the front paws). Bird only: Wings (spreads both wings wide), Peck
 *   (pecks the ground).
 * - Monkey only: Scratch (scratches its head or armpit), Banana (pulls out a banana, peels it and eats it).
 * - Roam (a rare excursion out of the pill): the pet leaves the pill and comes back along a path of its animal (see
 *   [sk.vladko.pill.rules.pointAt]). [PetFrame.out] is where it is, in pixels from its place in the box: (0, 0) at the start,
 *   the landing at the end (right of the start for the monkey), and anything between, even far outside the pill or the
 *   screen. [PetFrame.face] already follows the path. A scene shows the body in the pose it likes and draws it at its usual
 *   place: the pet layer moves everything by [PetFrame.out] and the camera hole is not cut out. A scene that does not draw
 *   Roam leaves [PetScene.drawsRoam] false and gets the pet drawn as a Jump on a loop instead; a scene that draws it sets
 *   [PetScene.drawsRoam] and reads [PetFrame.progress] (0 to 1 over the whole excursion; a hurry home after an interruption
 *   is a Roam step of 0.3 s whose progress also runs 0 to 1, so draw from [PetFrame.out], not from the progress alone).
 * - Excited (charger plugged in): jumps and spins, happy, with sparks and a bolt; no heart (a heart belongs to Heart only). Tired (battery below 20 %): droops, yawns and lies
 *   down; a Sweat mark; [PetFrame.low] stays true while the battery is low (a low look such as half-shut eyes or a small
 *   empty battery). Startle (a menu opened): a startled hop, then looks down at the menu. Dance (music plays): dances in
 *   place on the beat ([PetFrame.beat]); it loops while the music view shows and the music plays.
 */
internal interface PetScene {
    /** False when the box is too narrow for a walk. */
    val canWalk: Boolean

    /** The pet's size: the pixels of one drawing unit. The idle pill's scene gives it, and every other box draws the pet at it. */
    val unit: Float

    /** Where the camera hole is, as a fraction of the walkable width; the pet hides and peeks there. */
    val holeX: Float get() = 0.5f

    /** True when the scene draws [PetState.Roam] itself (see [PetFrame.out]); false draws the pet as a Jump on a loop. */
    val drawsRoam: Boolean get() = false

    /** The pet's middle in pixels from the box's left edge when it stands at [x] (0 left end to 1 right end). */
    fun middle(x: Float): Float

    /** Draws the pet for [frame]; the camera hole is cut out so the pet walks behind it. */
    fun DrawScope.draw(frame: PetFrame)
}

/** What to draw: [step] of the machine, [ms] after it began; [low] while the battery is low and not charging. */
internal class PetFrame(
    val step: PetStep,
    val ms: Long,
    val low: Boolean = false,
    /** Where the pet is on an excursion, in pixels from its place; null when it is not out (see [PetState.Roam]). */
    val out: Offset? = null,
    /** Which way the pet looks on the excursion (-1 to 1). */
    private val heading: Float = 0f,
) {
    val state: PetState get() = step.state
    val progress: Float get() = step.progressAt(ms)
    val blend: Float get() = step.blendAt(ms)
    val face: Float get() = if (out != null) heading else step.faceAt(ms)
    val x: Float get() = step.xAt(ms)
    val blink: Boolean get() = step.blinkAt(ms)

    /** How far the beat of the dance has come (0 to 1). */
    val beat: Float get() = (ms % PET_BEAT_MS).toFloat() / PET_BEAT_MS

    fun mark(mark: PetMark): Float = step.markAt(mark, ms)
}

/**
 * The pet that lives in the pill, as decided by [PetMachine]: the pet layer of the pill hosts it in the idle pill's box
 * whatever the pill shows, gliding to a [PetSpot] of the shown view when there is one. The owner
 * picks the animal ([PetAnimal]); the machine and the pet's place do not change with it.
 * Frames come only during a movement; while the pet sits, sleeps between "z"s or is hidden nothing runs. The view is
 * composed only while the pet can be seen, so it carries on from the same spot when it comes back; [event] reacts now
 * when the pet is seen and otherwise waits for that moment.
 */
internal object PetIdle {
    private val machine = PetMachine(Random)
    private var step: PetStep? = null

    /** How far the current step had come when the view went away. */
    private var pausedMs = 0L
    private val requests = Channel<(Long) -> PetStep?>(Channel.UNLIMITED)
    private var shown = false

    /** The current state id for `pill.sh state`, "" before the pet first showed. */
    val stateId: String get() = step?.state?.id.orEmpty()

    /** True while the battery is below 20 % and not charging. */
    var low by mutableStateOf(false)

    /** True while the pet is drawn over the pill's edge (an excursion, the excited hop): the pill then does not clip it. */
    var overflows by mutableStateOf(false)
        private set

    /** Switches the pet to [state] now; the answer for `pill.sh pet`. */
    fun play(state: PetState): String {
        if (!shown) return "unavailable: the pet is not shown"
        if (state == PetState.Roam && !machine.canRoam) return "unavailable: a view other than idle is shown"
        if (state.walks && !machine.canWalk) return "unavailable: this pill is too narrow for a walk"
        val only = state.animal
        if (only != null && only != machine.animal) return "unavailable: only the ${only.id} does ${state.id}"
        requests.trySend { machine.force(state, it) }
        return "ok"
    }

    /** [e] happens: the pet reacts now when it is seen, else when it is seen again. The answer for `pill.sh petevent`. */
    fun event(e: PetEvent): String {
        if (shown) {
            requests.trySend { machine.react(e, it) }
            return "ok"
        }
        val before = machine.current
        machine.queue(e, pausedMs)
        if (machine.current !== before) {
            step = machine.current
            pausedMs = 0
        }
        return "ok: waits until the pet is seen"
    }

    /** The free place of the shown view, set by [PetSpot]. */
    var spot by mutableStateOf<PetSpotRect?>(null)

    /** What the pill shows: see [PetMachine.place]. Changes apply now when the pet is seen, else to the paused step. */
    fun place(
        dance: Boolean,
        roams: Boolean,
    ) {
        if (shown) {
            requests.trySend {
                machine.canRoam = roams
                machine.place(dance, it)
            }
        } else {
            machine.canRoam = roams
            machine.place(dance, pausedMs)?.let {
                step = it
                pausedMs = 0
            }
        }
    }

    /**
     * Draws the pet in [box] (see [petBox]) with its camera hole [hole]. One scene lives across every view the pill shows,
     * so a view opening does not move the pet; while a view marks a [PetSpot], the pet glides there and back. The glide is a
     * slide of the pet's place along the walkable width ([PetStep.shifted]), so the camera hole stays cut out where it
     * really is. When a notification arrives, the pet leaves the spot, runs to the real
     * hole, peeks to the side of it and glides back. Without a real hole (or room to walk to it) the notification changes nothing.
     */
    @Composable
    fun Content(
        box: Frame,
        hole: HoleInfo,
    ) {
        val density = LocalDensity.current.density
        val settings by LocalContext.current.pill.settings.value
            .collectAsState()
        val animal = PetAnimal.of(settings.petAnimal)
        val scene =
            remember(box, hole, density, animal) {
                petScene(animal, box.width * density, box.height * density, hole.cutout(density))
            }
        val start =
            remember {
                machine.canWalk = scene.canWalk
                machine.stays = spot != null
                machine.hasHole = hole.hasHole
                machine.holeX = scene.holeX
                machine.animal = animal
                step ?: machine.next().also { step = it }
            }
        SideEffect {
            machine.hasHole = hole.hasHole
            machine.holeX = scene.holeX
            machine.animal = animal
        }
        // Outside the pill the pet is drawn without the camera hole: the hole must not be cut from a body that left it.
        val outScene =
            remember(scene) { lazy { petScene(animal, box.width * density, box.height * density, null, scene.unit) } }
        val screenW = LocalWindowInfo.current.containerSize.width
        var frame by remember { mutableStateOf(PetFrame(start, rest(start, pausedMs))) }
        // How far the pet is drawn from its own place in the box to the middle of the spot, or none. The sideways part is a
        // slide along the walkable width, so the scene cuts the camera hole where it really is; only a pill too narrow to
        // walk moves the drawing itself.
        val slides = abs(scene.middle(1f) - scene.middle(0f)) > 1f
        val slide = remember { Animatable(0f) }
        val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        LaunchedEffect(scene) {
            // At a spot the pet stays put, so it does not walk away from it.
            launch { snapshotFlow { spot != null }.collect { machine.stays = it } }
            snapshotFlow {
                val s = spot
                // Hiding, the pet leaves the spot for the camera hole, and goes back to the spot when it is out again.
                val toHole = hole.hasHole && scene.canWalk && frame.state.hidesAtHole
                if (s == null || toHole) {
                    Offset.Zero
                } else {
                    Offset(
                        s.rect.center.x - box.left * density - scene.middle(frame.x),
                        s.rect.center.y - (box.top + box.height / 2f) * density,
                    )
                }
            }.collect {
                val sideways = if (slides) it.x / (scene.middle(1f) - scene.middle(0f)) else 0f
                val down = Offset(if (slides) 0f else it.x, it.y)
                if (slide.targetValue != sideways) launch { slide.animateTo(sideways, spring(stiffness = Spring.StiffnessMediumLow)) }
                if (shift.targetValue != down) launch { shift.animateTo(down, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
        }
        LaunchedEffect(Unit) { snapshotFlow { frame.state }.collect { overflows = it == PetState.Roam || it == PetState.Excited } }
        DisposableEffect(Unit) { onDispose { overflows = false } }
        LaunchedEffect(Unit) {
            shown = true
            while (requests.tryReceive().isSuccess) continue
            machine.resume(pausedMs)?.let {
                step = it
                pausedMs = 0
            }
            var current = step ?: start
            var began = nowMs() - pausedMs
            try {
                while (true) {
                    var elapsed = nowMs() - began
                    if (elapsed >= current.ms) {
                        current = machine.next().also { step = it }
                        began = nowMs()
                        elapsed = 0
                    }
                    frame = PetFrame(current, rest(current, elapsed))
                    val asked =
                        if (current.moving.any { elapsed in it }) {
                            val shownAt = nowMs()
                            do withFrameNanos { } while (nowMs() - shownAt < FRAME_MS)
                            requests.tryReceive().getOrNull()
                        } else {
                            val next =
                                current.moving
                                    .map { it.first }
                                    .filter { it > elapsed }
                                    .minOrNull() ?: current.ms
                            withTimeoutOrNull(next - elapsed) { requests.receive() }
                        }
                    asked?.invoke(nowMs() - began)?.let {
                        current = it
                        step = it
                        began = nowMs()
                    }
                }
            } finally {
                shown = false
                pausedMs = nowMs() - began
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val step = frame.step
            val roam = step.roam
            if (roam != null) {
                val s = outScene.value
                val unit = box.height * density
                val home = box.left * density + scene.middle(step.fromX)
                val p =
                    roam.pointAt(
                        step.progressAt(frame.ms),
                        step.face,
                        RoamSpace(unit, scene.middle(step.toX) - scene.middle(step.fromX), home, screenW.toFloat()),
                    )
                val out = Offset(p.dx, p.dy)
                // The scene without a hole stands a little off the one with it; the difference is made up.
                val dx = scene.middle(step.fromX) - s.middle(step.fromX)
                val f =
                    if (s.drawsRoam) {
                        PetFrame(step, frame.ms, low, out, p.heading)
                    } else {
                        PetFrame(jumpLoop(step, frame.ms), frame.ms % JUMP_LOOP_MS, low, out, p.heading)
                    }
                translate(shift.value.x + out.x + dx, shift.value.y + out.y) { with(s) { draw(f) } }
                return@Canvas
            }
            val f = PetFrame(step.shifted(slide.value), frame.ms, low)
            translate(shift.value.x, shift.value.y) { with(scene) { draw(f) } }
        }
    }

    /** The picture time to show at [ms]: the exact time while moving, else the end of the last movement. */
    private fun rest(
        step: PetStep,
        ms: Long,
    ): Long =
        step.moving.firstOrNull { ms in it }?.let { ms } ?: step.moving
            .map { it.last }
            .filter { it <= ms }
            .maxOrNull() ?: 0L

    private fun nowMs() = System.nanoTime() / 1_000_000L
}

/** A scene that does not draw [PetState.Roam] shows a hop that repeats this often (ms). */
private const val JUMP_LOOP_MS = 1_400L

/** The Jump step a scene without its own Roam drawing is asked for [ms] into an excursion: it settles into the stand once, then hops on. */
private fun jumpLoop(
    roam: PetStep,
    ms: Long,
): PetStep =
    PetStep(
        PetState.Jump,
        JUMP_LOOP_MS,
        roam.fromX,
        roam.fromX,
        roam.face,
        roam.face,
        if (ms < JUMP_LOOP_MS) roam.poseFrom else PetPose.Stand,
        emptyList(),
        emptyList(),
    )

/** About 30 frames a second while something moves. */
private const val FRAME_MS = 30L

/** A place a view keeps free for the pet, in window pixels; the newest one placed wins. */
internal class PetSpotRect(
    val owner: Any,
    val rect: Rect,
)

/**
 * The tallest idle pill (dp) the pet grows with. A taller idle pill keeps the pet's size: the pet must fit a compact
 * pill, which is [sk.vladko.pill.rules.COMPACT_HEIGHT_DP] high whatever the idle height. At this height the pixel pet
 * is five cells to the grid unit, and the tallest pose still fits the compact pill.
 */
private const val PET_MAX_BOX_DP = 40.5f

/** The height (dp) of the box that gives the pet its size: the idle pill's height, at most [PET_MAX_BOX_DP]. */
internal fun petBoxHeight(idle: Frame) = minOf(idle.height, PET_MAX_BOX_DP)

/** The pet's box in every view: the idle pill, [petBoxHeight] high and centred on it. */
internal fun petBox(idle: Frame) = petBoxHeight(idle).let { idle.copy(top = idle.top + (idle.height - it) / 2f, height = it) }

/** What a compact view keeps free for the pet: the pet's width in dp, the compact pill's height. Set by the pet layer. */
internal val LocalPetFoot = compositionLocalOf { DpSize(40.dp, 30.dp) }

/**
 * Keeps [modifier]'s area free for the pet: the pet glides from its place in the idle pill to the middle of it (the bolt of
 * the compact charger, the bars of the compact music view, the right of the crown of the large music view). Only its place
 * counts, the pet's size does not follow it.
 */
@Composable
internal fun PetSpot(modifier: Modifier) {
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { if (PetIdle.spot?.owner === owner) PetIdle.spot = null } }
    Box(
        modifier.onGloballyPositioned {
            // Not boundsInWindow: it is cut by the pill's growing shape, which would resize the spot every frame of an opening.
            val r = Rect(it.positionInWindow(), it.size.toSize())
            if (PetIdle.spot?.owner !== owner || PetIdle.spot?.rect != r) PetIdle.spot = PetSpotRect(owner, r)
        },
    )
}

/** The camera hole in pixels, null when no hole is in the box. */
private fun HoleInfo.cutout(density: Float) =
    if (hasHole) {
        Rect(cutoutLeft, cutoutTop, cutoutLeft + cutoutWidth, cutoutTop + cutoutHeight).let {
            Rect(it.left * density, it.top * density, it.right * density, it.bottom * density)
        }
    } else {
        null
    }
