package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import sk.vladko.pill.rules.PET_BEAT_MS
import sk.vladko.pill.rules.PET_BLEND_MS
import sk.vladko.pill.rules.PetAnimal
import sk.vladko.pill.rules.PetMark
import sk.vladko.pill.rules.PetPose
import sk.vladko.pill.rules.PetState
import sk.vladko.pill.rules.RoamPlan
import sk.vladko.pill.rules.RoamSpace
import sk.vladko.pill.rules.pointAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TURN_MS = 130L
private const val STEP_MS = 130L

/** The still picture of [p], or of the same body with its head turned to the viewer ([front]). */
private fun posed(
    p: PetPose,
    front: Boolean = false,
): CatShot =
    catShot(
        when (p) {
            PetPose.Sit -> if (front) "sit_front" else "sit"
            PetPose.Stand -> if (front) "stand_front" else "stand"
            PetPose.Lie -> if (front) "lie_front" else "lie"
        },
    )

/** The pictures between two bodies: standing in between, and low to lie down. */
private fun route(
    from: PetPose,
    to: PetPose,
): List<CatShot> =
    when {
        from == to -> emptyList()
        from == PetPose.Lie && to == PetPose.Sit -> listOf(catShot("crouch"), catShot("rise"))
        from == PetPose.Sit && to == PetPose.Lie -> listOf(catShot("rise"), catShot("crouch"))
        from == PetPose.Lie || to == PetPose.Lie -> listOf(catShot("crouch"))
        else -> listOf(catShot("rise"))
    }

/** The body an action starts in (a state's own body is the one it ends in). */
private fun startsIn(state: PetState): PetPose =
    when (state) {
        PetState.Tired -> PetPose.Sit
        PetState.Peek -> PetPose.Stand
        else -> state.pose
    }

/** 0 to 1 and back to 0: rises over [a]..[b] of [t], holds, falls over [c]..[d]. */
private fun ramp(
    t: Float,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
) = ease((t - a) / (b - a)) * (1f - ease((t - c) / (d - c)))

/** A hop's height (cells) at [u] (0 to 1 over the flight): a parabola of the given [peak]. */
private fun arc(
    u: Float,
    peak: Float,
) = peak * 4f * u * (1f - u)

internal object CatRig : Rig {
    override fun base(p: PetPose): Pose =
        Pose().with {
            when (p) {
                PetPose.Sit -> {
                    hx = 14f
                    hy = 8f
                }
                PetPose.Stand -> {
                    hx = 15f
                    hy = 11f
                }
                PetPose.Lie -> {
                    hx = 14f
                    hy = 13f
                }
            }
        }

    override fun rest(p: PetPose) = posed(p).raster()

    override fun pose(
        f: PetFrame,
        walked: Float,
    ) = base(f.step.pose)

    /** The cat draws whole pictures ([picture]), not a posed rig. */
    override fun paint(
        r: Raster,
        p: Pose,
    ) {}

    override val excitedHeart = false

    override fun picture(
        f: PetFrame,
        walked: Float,
    ): Drawn {
        val s = f.step
        val route = route(s.poseFrom, startsIn(s.state))
        val inHole = s.state == PetState.Peek || s.state == PetState.Hidden
        val turnMs = if (s.faceFrom != s.face && !inHole) TURN_MS else 0L
        val pre = if (s.state == PetState.Dance && s.poseFrom == PetPose.Stand) 0L else turnMs + route.size * STEP_MS
        var facing = s.face
        val shot =
            when {
                f.ms < turnMs -> {
                    facing = s.faceFrom
                    posed(s.poseFrom, front = true)
                }
                f.ms < pre -> route[((f.ms - turnMs) / STEP_MS).toInt().coerceAtMost(route.size - 1)]
                else -> act(f, f.ms - pre, s.ms - pre, walked)
            }
        val pose =
            Pose().with {
                hx = shot.hx
                hy = shot.hy
                dx = shot.dx
                lift = shot.lift
                turn = 1f
            }
        return Drawn(shot.raster(), pose, facing < 0)
    }

    private fun act(
        f: PetFrame,
        a: Long,
        len: Long,
        walked: Float,
    ): CatShot {
        val t = (a.toFloat() / max(1L, len)).coerceIn(0f, 1f)
        return when (f.state) {
            PetState.Sit ->
                catShot(
                    if (f.blink) {
                        "sit_blink"
                    } else if (f.low) {
                        "sit_half"
                    } else {
                        "sit"
                    },
                )
            PetState.LookAround -> look(t)
            PetState.Walk -> walk(f, walked, false)
            PetState.Hide -> if (f.progress > 0.9f) catShot("crouch") else walk(f, walked, true)
            PetState.Hidden -> catShot("crouch")
            PetState.Sleep -> {
                val z = f.mark(PetMark.Z)
                catShot(if (z >= 0f && (z * 3f).toInt() % 2 == 0) "lie_b" else "lie")
            }
            PetState.Heart -> heart(t, a)
            PetState.Stretch -> stretch(t)
            PetState.Groom -> groom(t, a)
            PetState.Fidget -> fidget(t, a)
            PetState.Jump -> jump(t)
            PetState.Peek -> peek(t)
            PetState.Lick -> lick(t, a)
            PetState.Knead -> knead(t, a)
            PetState.Excited -> excited(t, a)
            PetState.Tired -> tired(t)
            PetState.Startle -> startle(t)
            PetState.Dance -> dance(f.ms)
            else -> catShot("sit")
        }
    }

    /** Looks at the viewer, up, over its shoulder, and at the viewer again before it settles. */
    private fun look(t: Float): CatShot =
        catShot(
            when {
                t < 0.08f || t > 0.94f -> "sit"
                t < 0.2f || t in 0.46f..0.52f || t in 0.78f..0.86f -> "sit_front"
                t < 0.4f -> "sit_up"
                t < 0.44f -> "sit_front_blink"
                t < 0.78f -> "sit_back"
                else -> "sit"
            },
        )

    /** A walk: legs by the cells [walked], the body down at each foot-down, the tail swaying at its own pace. */
    private fun walk(
        f: PetFrame,
        walked: Float,
        hurry: Boolean,
    ): CatShot {
        val going = f.ms >= PET_BLEND_MS && f.progress < 0.97f && walked > 0.05f
        if (!going) {
            // A cat about to dash crouches and wiggles first.
            if (hurry) return catShot(if ((f.ms / 110) % 2 == 0L) "wig_a" else "wig_b")
            return catShot("stand")
        }
        val phase = floor(walked / 1.2f).toInt() + 1
        if (hurry) return catShot("run${phase and 3}")
        return catShot("walk${phase and 3}${if ((phase / 3) % 2 == 0) "a" else "b"}")
    }

    /** Sits up happy: the tail wags and it hops a little while the heart rises. */
    private fun heart(
        t: Float,
        a: Long,
    ): CatShot {
        if (t < 0.06f || t > 0.94f) return catShot("sit")
        return catShot(if ((a / 160) % 2 == 0L) "heart_a" else "heart_b", lift = if (a % 480 < 200) 1f else 0f)
    }

    /** Bows down with the rump up and yawns, holds the stretch, then rises. */
    private fun stretch(t: Float): CatShot =
        catShot(
            when {
                t < 0.06f || t > 0.9f -> "stand"
                t < 0.2f -> "bow"
                t < 0.62f -> if (t in 0.26f..0.5f) "bow_yawn" else "bow"
                else -> "rise"
            },
        )

    /** Scratches behind its ear with a hind paw, the head tipped into it and the eyes shut. */
    private fun groom(
        t: Float,
        a: Long,
    ): CatShot {
        if (t < 0.06f || t > 0.94f) return catShot("sit")
        return catShot(if ((a / 90) % 2 == 0L) "scratch_a" else "scratch_b")
    }

    /** A twitch: the ears flatten, the tail flicks and the head jerks. */
    private fun fidget(
        t: Float,
        a: Long,
    ): CatShot {
        if (t < 0.06f || t > 0.9f) return catShot("sit")
        return catShot(if ((a / 85) % 2 == 0L) "fid_a" else "fid_b")
    }

    /** Crouches and wiggles, springs, tucks at the top, reaches down and lands with a squash. */
    private fun jump(t: Float): CatShot =
        when {
            t < 0.04f || t > 0.95f -> catShot("stand")
            t < 0.22f -> catShot(if ((t * 100).toInt() % 6 < 3) "wig_a" else "wig_b")
            t < 0.76f -> {
                val u = (t - 0.22f) / 0.54f
                val air = arc(u, 6f)
                catShot(
                    when {
                        u < 0.3f -> "up"
                        u < 0.62f -> "tuck"
                        else -> "down"
                    },
                    lift = air,
                )
            }
            t < 0.86f -> catShot("crouch")
            else -> catShot("rise")
        }

    /** Behind the hole: the head leans out to the side, eyes wide, looks at the viewer and the other way, then pulls back. */
    private fun peek(t: Float): CatShot {
        val lean = 7f * ramp(t, 0.1f, 0.28f, 0.74f, 0.9f)
        return when {
            t < 0.1f -> catShot("crouch")
            t < 0.3f -> catShot("peek_a", dx = lean)
            t < 0.46f -> catShot("peek_front", dx = lean)
            t < 0.66f -> catShot("peek_back", dx = lean)
            t < 0.74f -> catShot("peek_a", dx = lean)
            t < 0.9f -> catShot("crouch", dx = lean)
            t < 0.95f -> catShot("rise")
            else -> catShot("sit")
        }
    }

    /** Raises a paw to its mouth and licks it, the head dipping on each stroke. */
    private fun lick(
        t: Float,
        a: Long,
    ): CatShot {
        if (t < 0.1f || t > 0.92f) return catShot("sit")
        return catShot(if ((a / 260) % 2 == 0L) "lick_a" else "lick_b")
    }

    /** Lies with its head up and kneads with the front paws in turn, the eyes half shut. */
    private fun knead(
        t: Float,
        a: Long,
    ): CatShot =
        catShot(
            when {
                t < 0.08f || t > 0.94f -> "lie"
                t < 0.16f || t > 0.86f -> "lie_half"
                else -> if ((a / 320) % 2 == 0L) "knead_a" else "knead_b"
            },
        )

    /** Joyful hops with the eyes shut for happiness; at the top of the middle hops it looks at the viewer. */
    private fun excited(
        t: Float,
        a: Long,
    ): CatShot {
        if (t < 0.03f || t > 0.95f) return catShot("stand")
        val h = a % 700
        val front = t in 0.35f..0.65f
        return when {
            h < 110 -> catShot("crouch")
            h < 560 -> {
                val u = (h - 110) / 450f
                catShot(
                    when {
                        front && u in 0.25f..0.75f -> "stand_front_h"
                        u < 0.3f -> "up_h"
                        u < 0.65f -> "tuck_h"
                        else -> "down_h"
                    },
                    lift = arc(u, 5f),
                )
            }
            else -> catShot("crouch")
        }
    }

    /** Droops, yawns, lets its head sink, then lies down to sleep. */
    private fun tired(t: Float): CatShot =
        catShot(
            when {
                t < 0.16f -> "sit_half"
                t < 0.42f -> "sit_yawn"
                t < 0.56f -> "sit_droop"
                t < 0.72f -> "crouch_half"
                t < 0.9f -> "lie_half"
                else -> "lie"
            },
        )

    /** Leaps back with its fur up and eyes wide, freezes, then looks down at the menu. */
    private fun startle(t: Float): CatShot =
        when {
            t < 0.05f || t > 0.97f -> catShot("stand")
            t < 0.34f -> catShot("arch", dx = -1f, lift = arc((t - 0.05f) / 0.29f, 4f))
            t < 0.7f -> catShot("arch", dx = -1f)
            t < 0.92f -> catShot("stand_down", dx = -1f)
            else -> catShot("stand")
        }

    /** A bounce on the beat: the front paws lift in turn, the head nods and it sways; loops every two beats. */
    private fun dance(ms: Long): CatShot {
        val beat = (ms % PET_BEAT_MS).toFloat() / PET_BEAT_MS
        val odd = (ms / PET_BEAT_MS) % 2 == 1L
        val up = beat < 0.5f
        val sway = cos(2.0 * PI * (ms % (2 * PET_BEAT_MS)) / (2 * PET_BEAT_MS)).toFloat()
        return catShot(
            if (up) (if (odd) "dance_b" else "dance_a") else "dance_c",
            dx = sway,
            lift = if (up) 2f * sin(beat * 2f * PI.toFloat()) else 0f,
        )
    }
}

private const val DOT_APPEARS = 0.02f
private const val DOT_GOES_OUT = 0.585f

/** The time of the path the dot is on at [t]: it runs a little ahead of the cat, and waits where the cat pounces. */
private fun dotPathTime(t: Float): Float = minOf(0.55f, t + 0.05f * ease((t - 0.19f) / 0.06f))

/** The dot's place in the cat's grid (cells, facing right) at [t]: beside the cat first, then ahead of it, then under its paws. */
private fun dotCell(t: Float): Pair<Float, Float> {
    val x = lerp(lerp(17f, 24f, ease((t - 0.19f) / 0.04f)), 18f, ease((t - 0.375f) / 0.105f))
    return x to 18f
}

/**
 * The cat's excursion: a laser dot shows beside it and runs off, the cat notices it (the head snaps up, the pupils widen, the
 * rump wiggles), chases it out of the pill and down below it, creeps up, pounces on it, finds nothing under its paws, looks
 * around puzzled, then jumps back into the pill and stands proud. The path ([sk.vladko.pill.rules.pointAt]) carries it; this
 * adds the poses and the hops.
 */
private val CatRoam: PixelAction =
    run {
        val used = mutableSetOf<String>()
        val keys =
            buildString {
                fun key(
                    t: Float,
                    name: String,
                    dy: Int = 0,
                ) {
                    val (x, y) = if (name == "sit" || name == "stand") 10 to 19 else catShot(name).pin()
                    if (name != "sit" && name != "stand") used += name
                    appendLine("$t $name $x ${y + dy} 0 hold")
                }
                key(0f, "sit")
                key(0.06f, "alert")
                key(0.115f, "wig_a")
                key(0.14f, "wig_b")
                key(0.165f, "wig_a")
                key(0.19f, "wig_b")
                key(0.205f, "up")
                key(0.225f, "run0")
                key(0.245f, "run1")
                key(0.265f, "run2")
                key(0.285f, "run3")
                key(0.3f, "run0")
                key(0.31f, "up", -2)
                key(0.325f, "tuck", -3)
                key(0.34f, "tuck", -2)
                key(0.355f, "down")
                key(0.375f, "crouch")
                key(0.41f, "crouch_wide")
                key(0.435f, "wig_a")
                key(0.455f, "wig_b")
                key(0.475f, "wig_a")
                key(0.49f, "up", -2)
                key(0.505f, "leap", -5)
                key(0.52f, "leap", -4)
                key(0.535f, "leap", -1)
                key(0.545f, "hit")
                key(0.6f, "crouch_wide")
                key(0.625f, "crouch_half")
                key(0.65f, "sit_down")
                key(0.7f, "puz_a")
                key(0.75f, "puz_b")
                key(0.79f, "puz_a")
                key(0.81f, "sit_up")
                key(0.83f, "crouch")
                key(0.845f, "up", -1)
                key(0.86f, "up", -4)
                key(0.875f, "up", -6)
                key(0.89f, "tuck", -7)
                key(0.905f, "tuck", -6)
                key(0.92f, "down", -3)
                key(0.935f, "down", -1)
                key(0.945f, "crouch")
                key(0.965f, "rise")
                key(0.98f, "proud")
                key(1f, "stand")
            }
        PixelAction(CAT_PALETTE, used.map { catShot(it).sprite(it) }, keys)
    }

/** The cat's scene: the pixel pet that sits, walks, hides and leaves the pill after a laser dot. */
internal class PixelCatScene(
    w: Float,
    h: Float,
    hole: Rect?,
    fixedUnit: Float? = null,
) : PixelScene(w, h, hole, CatRig, fixedUnit, CatRoam) {
    /** The red laser dot: a bright 2 x 2 spot with a faint glow, drawn under the cat so its paws cover it. */
    override fun DrawScope.beforeRoam(
        t: Float,
        mirror: Boolean,
        ox: Float,
        oy: Float,
        cell: Float,
        out: Offset,
        hurry: Boolean,
    ) {
        if (hurry || t < DOT_APPEARS || t > DOT_GOES_OUT) return
        val dir = if (mirror) -1 else 1
        val ahead = RoamPlan(PetAnimal.Cat).pointAt(dotPathTime(t), dir, RoamSpace(this@PixelCatScene.h, 0f, 0f, 0f))
        val (dx, dy) = dotCell(t)
        val size = if (t < 0.035f || t > 0.57f) 1 else 2
        val ix = (dx + 0.6f * sin(t * 90f)).roundToInt()
        val iy = (dy + 0.6f * cos(t * 70f)).roundToInt()
        val left = if (mirror) GRID - size - ix else ix

        fun spot(
            cx: Int,
            cy: Int,
            w: Int,
            color: Color,
        ) {
            val x0 = (ox + cx * cell + ahead.dx - out.x).roundToInt().toFloat()
            val y0 = (oy + cy * cell + ahead.dy - out.y).roundToInt().toFloat()
            val x1 = (ox + (cx + w) * cell + ahead.dx - out.x).roundToInt().toFloat()
            val y1 = (oy + (cy + w) * cell + ahead.dy - out.y).roundToInt().toFloat()
            drawRect(color, Offset(x0, y0), Size(x1 - x0, y1 - y0))
        }
        val glow = Color(0xFFFF2B2B).copy(alpha = if (size == 2) 0.35f else 0f)
        if (size == 2) {
            spot(left - 1, iy, 1, glow)
            spot(left + 2, iy, 1, glow)
            spot(left, iy - 1, 1, glow)
            spot(left + 1, iy - 1, 1, glow)
            spot(left, iy + 2, 1, glow)
            spot(left + 1, iy + 2, 1, glow)
            spot(left - 1, iy + 1, 1, glow)
            spot(left + 2, iy + 1, 1, glow)
        }
        spot(left, iy, size, Color(0xFFFF2B2B))
        if (size == 2) spot(if (mirror) left + 1 else left, iy, 1, Color(0xFFFF9A9A))
    }
}
