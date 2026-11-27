package sk.vladko.pill.idle

import androidx.compose.ui.graphics.Color
import sk.vladko.pill.rules.ClawdPhase
import sk.vladko.pill.rules.ClawdRoam
import sk.vladko.pill.rules.PET_BEAT_MS
import sk.vladko.pill.rules.PET_BLINK_MS
import sk.vladko.pill.rules.PetMark
import sk.vladko.pill.rules.PetState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// What Clawd does in every state: a pose for every frame, in whole cells (see [ClawdPose]), and the marks that leave it.

/** The eyes: [w] by [h] cells, [dy] rows from their usual top (the usual eye is a bar one cell wide and two tall). */
internal enum class ClawdEyes(
    val w: Int,
    val h: Int,
    val dy: Int,
) {
    Open(1, 2, 0),
    Wide(1, 3, -1),
    Half(1, 1, 1),
    Happy(2, 1, 0),
    Shut(2, 1, 1),
}

private val DOWN = listOf(2, 2, 2, 2)
private val NO_SHIFT = listOf(0, 0, 0, 0)

/**
 * One picture of Clawd as moves from its still pose, all in whole cells: the body moved by [dx], [dy] and squashed by [squash]
 * rows off its top (negative: stretched) and [grow] columns on each side; the eyes shifted [look] sideways and [gaze] down;
 * each arm raised ([armL], [armR]: negative up) and moved sideways ([armLx], [armRx]); the four legs' lengths and shifts;
 * [turn] radians round its upright axis.
 */
internal data class ClawdPose(
    val dx: Int = 0,
    val dy: Int = 0,
    val squash: Int = 0,
    val grow: Int = 0,
    val look: Int = 0,
    val gaze: Int = 0,
    val eyes: ClawdEyes = ClawdEyes.Open,
    val armL: Int = 0,
    val armR: Int = 0,
    val armLx: Int = 0,
    val armRx: Int = 0,
    val legs: List<Int> = DOWN,
    val legDx: List<Int> = NO_SHIFT,
    val turn: Float = 0f,
)

/** Lying down: a flat slab with its arms and legs tucked in and its eyes shut. */
internal val LIE_POSE =
    ClawdPose(dy = 1, squash = 3, grow = 1, eyes = ClawdEyes.Shut, armLx = 2, armRx = -2, legs = listOf(1, 1, 1, 1))

/** Rushing home in the air: the arms up, the legs tucked. */
internal val HURRY_POSE = ClawdPose(eyes = ClawdEyes.Wide, armL = -5, armR = -5, legs = listOf(1, 1, 1, 1))

/** [a] to [b] by [u] (0 to 1), cell by cell. */
internal fun mixPose(
    a: ClawdPose,
    b: ClawdPose,
    u: Float,
): ClawdPose {
    fun m(
        x: Int,
        y: Int,
    ) = (x + (y - x) * u).roundToInt()
    return ClawdPose(
        m(a.dx, b.dx),
        m(a.dy, b.dy),
        m(a.squash, b.squash),
        m(a.grow, b.grow),
        m(a.look, b.look),
        m(a.gaze, b.gaze),
        if (u < 0.5f) a.eyes else b.eyes,
        m(a.armL, b.armL),
        m(a.armR, b.armR),
        m(a.armLx, b.armLx),
        m(a.armRx, b.armRx),
        a.legs.zip(b.legs) { x, y -> m(x, y) },
        a.legDx.zip(b.legDx) { x, y -> m(x, y) },
        a.turn + (b.turn - a.turn) * u,
    )
}

private val BODY = CLAWD_BODY
private val EYE = Color(0xFF09090B)
private val LEG_COLS = intArrayOf(3, 5, 10, 12)

/** Adds the rectangles of [p]: body, arms, legs and eyes. */
internal fun Cells.pet(p: ClawdPose) {
    val k = abs(cos(p.turn))
    val facing = cos(p.turn) > 0.35f
    val cx = 8 + p.dx
    val body = 12 + 2 * p.grow
    val width = (((body * k) / 2f).roundToInt() * 2).coerceAtLeast(2)
    val l = cx - width / 2
    val top = p.squash + p.dy
    val bottom = 8 + p.dy

    fun across(c: Int) = (cx + (c - cx) * k).roundToInt()
    add(l, top, width, bottom - top, BODY)
    val armRow = 4 + p.squash / 2 + p.dy
    if (k > 0.5f) {
        add(across(l - 2 + p.armLx), armRow + p.armL, 2, 2, BODY)
        add(across(l + width + p.armRx), armRow + p.armR, 2, 2, BODY)
    }
    p.legs.forEachIndexed { i, len ->
        add(across(LEG_COLS[i] + p.dx + p.legDx[i]), bottom, 1, len, BODY)
    }
    if (facing) {
        val e = p.eyes
        val row = top + 2 + e.dy + p.gaze
        add(across(l + 2) + p.look, row, e.w, e.h, EYE)
        add(across(l + width - 3) + p.look, row, e.w, e.h, EYE)
    }
}

/** A table of (time, value) pairs read at [t], moving smoothly from one to the next. */
private fun track(
    t: Float,
    vararg k: Pair<Float, Float>,
): Int {
    if (t <= k.first().first) return k.first().second.roundToInt()
    for (i in 1 until k.size) {
        if (t <= k[i].first) {
            val (t0, v0) = k[i - 1]
            val (t1, v1) = k[i]
            return lerp(v0, v1, ease((t - t0) / (t1 - t0))).roundToInt()
        }
    }
    return k.last().second.roundToInt()
}

/** 0 to 1 over [a]..[b] of [t]. */
private fun ramp(
    t: Float,
    a: Float,
    b: Float,
) = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun sign(v: Float) = if (v < 0f) -1 else 1

private fun crouch(
    n: Int = 1,
    base: ClawdPose = ClawdPose(),
) = base.copy(squash = n, grow = if (n > 1) 1 else 0, legs = if (n > 1) listOf(1, 1, 1, 1) else base.legs)

/** The pose of [f]; [walked] and [left] are the cells the pet has walked in this step and still has to walk. */
internal fun clawdPose(
    f: PetFrame,
    walked: Float,
    left: Float,
): ClawdPose {
    val s = f.ms / 1000f
    val p = f.progress
    return when (f.state) {
        PetState.Sit -> ClawdPose(eyes = blink(f))
        PetState.LookAround -> lookAround(p)
        PetState.Walk -> walk(f, walked, left)
        PetState.Hide -> hide(f, walked, left)
        PetState.Sleep -> sleep(f)
        PetState.Heart -> heart(f)
        PetState.Stretch -> stretch(p)
        PetState.Groom -> groom(f)
        PetState.Fidget -> fidget(f)
        PetState.Jump -> jump(p)
        PetState.Peek -> peek(f)
        PetState.Think -> think(f)
        PetState.Type -> typing(f)
        PetState.Excited -> excited(f)
        PetState.Tired -> tired(p)
        PetState.Startle -> startle(s, f)
        PetState.Dance -> dance(f)
        else -> ClawdPose()
    }
}

private fun blink(f: PetFrame): ClawdEyes {
    val at = f.step.blinks.firstOrNull { f.ms >= it && f.ms < it + PET_BLINK_MS } ?: return ClawdEyes.Open
    val u = (f.ms - at).toFloat() / PET_BLINK_MS
    return if (u in 0.25f..0.75f) ClawdEyes.Shut else ClawdEyes.Half
}

private fun lookAround(p: Float): ClawdPose {
    val look = track(p, 0.06f to 0f, 0.16f to -2f, 0.4f to -2f, 0.5f to 0f, 0.58f to 2f, 0.8f to 2f, 0.9f to 0f)
    val gaze = track(p, 0.38f to 0f, 0.42f to 1f, 0.5f to 1f, 0.54f to 0f)
    val eyes = if (p in 0.46f..0.5f) ClawdEyes.Half else ClawdEyes.Open
    return ClawdPose(dx = look / 2, look = look, gaze = gaze, eyes = eyes)
}

/** The gait: four phases, a diagonal pair of legs in turn lifted and stepping forward, the block hopping up a cell between them. */
private fun scuttle(
    phase: Int,
    dir: Int,
    base: ClawdPose,
): ClawdPose =
    when (phase % 4) {
        0 -> base.copy(legs = listOf(2, 1, 1, 2), legDx = listOf(0, dir, dir, 0), armL = 0, armR = 0)
        1 -> base.copy(dy = -1, armL = -1, armR = 0)
        2 -> base.copy(legs = listOf(1, 2, 2, 1), legDx = listOf(dir, 0, 0, dir), armL = 0, armR = 0)
        else -> base.copy(dy = -1, armL = 0, armR = -1)
    }

private fun walk(
    f: PetFrame,
    walked: Float,
    left: Float,
): ClawdPose {
    val dir = sign(f.face)
    val base = ClawdPose(look = dir)
    val ms = f.ms
    val end = f.step.ms
    return when {
        ms < 400 -> ClawdPose()
        ms < 700 -> crouch(1, ClawdPose(dx = -dir, look = dir))
        walked < 0.5f -> base.copy(squash = 1, dx = -dir)
        left < 0.4f && ms > end - 300 -> if (ms > end - 150) crouch(1) else ClawdPose()
        left < 0.4f -> ClawdPose()
        else -> scuttle((walked * 0.9f).toInt(), dir, base)
    }
}

/** A message arrives: the pet is startled, hops, then scuttles to the hole and ducks behind it. */
private fun hide(
    f: PetFrame,
    walked: Float,
    left: Float,
): ClawdPose {
    val dir = sign(f.face)
    val ms = f.ms
    return when {
        ms < 120 -> ClawdPose(eyes = ClawdEyes.Wide)
        ms < 260 -> ClawdPose(dy = -2, eyes = ClawdEyes.Wide, armL = -5, armR = -5, legs = listOf(1, 1, 1, 1))
        ms < 480 -> ClawdPose(eyes = ClawdEyes.Wide, armL = -1, armR = -1)
        ms < 700 -> ClawdPose(squash = 1, look = dir, dx = -dir)
        walked < 0.5f -> ClawdPose(squash = 1, look = dir, dx = -dir)
        left < 0.4f -> if (ms > f.step.ms - 160) crouch(1) else ClawdPose()
        else -> scuttle((walked * 0.9f).toInt(), dir, ClawdPose(look = dir))
    }
}

private fun sleep(f: PetFrame): ClawdPose {
    // The block breathes while a "z" rises.
    val z = f.mark(PetMark.Z)
    val breath = if (z >= 0f && ((z * 5f).toInt() % 2 == 1)) 1 else 0
    return LIE_POSE.copy(squash = 3 - breath)
}

private fun heart(f: PetFrame): ClawdPose {
    val q = (f.ms % 500) / 500f
    val up = q in 0.15f..0.55f
    val arms = (f.ms / 250) % 2 == 0L
    val over = f.ms > f.step.ms - 250
    return when {
        f.ms < 200 -> crouch(1)
        over -> ClawdPose()
        else ->
            ClawdPose(
                dy = if (up) -1 else 0,
                squash = if (q < 0.15f) 1 else 0,
                eyes = ClawdEyes.Happy,
                armL = if (arms) -3 else 0,
                armR = if (arms) 0 else -3,
                legs = if (up) listOf(1, 1, 1, 1) else DOWN,
            )
    }
}

private fun stretch(p: Float): ClawdPose {
    val squash = track(p, 0.0f to 0f, 0.12f to 2f, 0.2f to 2f, 0.3f to -2f, 0.6f to -2f, 0.72f to 1f, 0.86f to 0f)
    val arms = track(p, 0.12f to 0f, 0.3f to -6f, 0.6f to -6f, 0.74f to 0f)
    val shake = if (p in 0.34f..0.58f) (if ((p * 40).toInt() % 2 == 0) 1 else 0) else 0
    val legs =
        if (squash > 0) {
            listOf(1, 1, 1, 1)
        } else if (squash < 0) {
            listOf(3, 3, 3, 3)
        } else {
            DOWN
        }
    return ClawdPose(
        squash = squash,
        grow = if (squash >= 2) 1 else 0,
        eyes = if (p in 0.2f..0.66f) ClawdEyes.Happy else ClawdEyes.Open,
        armL = arms,
        armR = arms,
        legs = legs,
        dx = shake,
    )
}

private fun groom(f: PetFrame): ClawdPose {
    val p = f.progress
    val raise = track(p, 0.04f to 0f, 0.1f to -3f, 0.16f to -6f, 0.62f to -6f, 0.7f to -3f, 0.76f to 0f)
    val stroke = if (p in 0.18f..0.6f) (if ((f.ms / 160) % 2 == 0L) -2 else -6) else -4
    val shake = if (p in 0.8f..0.94f) (if ((f.ms / 60) % 2 == 0L) 1 else -1) else 0
    return ClawdPose(
        dx = shake,
        squash = if (p in 0.16f..0.62f && (f.ms / 160) % 2 == 1L) 1 else 0,
        eyes = if (p in 0.08f..0.76f) ClawdEyes.Happy else ClawdEyes.Open,
        armR = raise,
        armRx = if (raise < -3) stroke else 0,
    )
}

private fun fidget(f: PetFrame): ClawdPose {
    val p = f.progress
    val dx = track(p, 0.05f to 0f, 0.15f to -1f, 0.4f to -1f, 0.5f to 1f, 0.75f to 1f, 0.85f to 0f)
    val base = ClawdPose(dx = dx, look = dx)
    return if (p in 0.1f..0.8f) scuttle((f.ms / 90).toInt(), if (dx < 0) -1 else 1, base) else base
}

private fun jump(p: Float): ClawdPose {
    val air = ramp(p, 0.28f, 0.7f)
    val rise = 4f * air * (1f - air) * 2f
    return when {
        p < 0.2f -> crouch(2)
        p < 0.28f -> ClawdPose(dy = -1, squash = -1, armL = -2, armR = -2, legs = listOf(3, 3, 3, 3))
        p < 0.7f ->
            ClawdPose(
                dy = -rise.roundToInt(),
                eyes = ClawdEyes.Wide,
                armL = -5,
                armR = -5,
                legs = if (air in 0.35f..0.65f) listOf(1, 1, 1, 1) else listOf(2, 2, 2, 2),
            )
        p < 0.8f -> ClawdPose(squash = -1, legs = listOf(3, 3, 3, 3), armL = -2, armR = -2)
        p < 0.9f -> crouch(2)
        p < 0.96f -> crouch(1)
        else -> ClawdPose()
    }
}

private fun peek(f: PetFrame): ClawdPose {
    val p = f.progress
    val dir = sign(f.step.face.toFloat())
    val out = track(p, 0.04f to 0f, 0.2f to 4f, 0.72f to 4f, 0.9f to 0f)
    val look =
        track(
            p,
            0.3f to 0f,
            0.36f to dir.toFloat(),
            0.5f to dir.toFloat(),
            0.56f to -dir.toFloat(),
            0.66f to -dir.toFloat(),
            0.72f to 0f,
        )
    val gaze = if (p in 0.58f..0.64f) -1 else 0
    val wave = if (p in 0.4f..0.7f && (f.ms / 140) % 2 == 0L) -3 else 0
    return ClawdPose(
        dx = dir * out,
        look = look,
        gaze = gaze,
        armL = if (dir < 0) wave else 0,
        armR = if (dir > 0) wave else 0,
    )
}

/** "think": the eyes look up, a spark turns over the head, the idea arrives and the block hops for joy. */
private fun think(f: PetFrame): ClawdPose {
    val ms = f.ms
    val end = f.step.ms
    val idea = end - 800
    val look =
        track(
            ms.toFloat(),
            300f to 0f,
            500f to 1f,
            1100f to 1f,
            1300f to -1f,
            (idea - 300).toFloat() to -1f,
            (idea - 100).toFloat() to 0f,
        )
    val hop = ramp(ms.toFloat(), idea + 100f, idea + 600f)
    return when {
        ms < 160 -> ClawdPose()
        ms < 300 -> crouch(1)
        ms < idea ->
            ClawdPose(
                squash =
                    if (ms >
                        560
                    ) {
                        1
                    } else {
                        0
                    },
                gaze = -1,
                look = look,
                eyes = ClawdEyes.Open,
                armR = if (ms > 600) -2 else 0,
                dx = if (look < 0) -1 else 0,
            )
        ms < idea + 100 -> ClawdPose(eyes = ClawdEyes.Wide, armL = -2, armR = -2)
        hop < 1f ->
            ClawdPose(
                dy = -(4f * hop * (1f - hop) * 2f).roundToInt(),
                eyes = ClawdEyes.Happy,
                armL = -5,
                armR = -5,
                legs = if (hop in 0.2f..0.8f) listOf(1, 1, 1, 1) else DOWN,
            )
        ms < end - 120 -> crouch(1, ClawdPose(eyes = ClawdEyes.Happy))
        else -> ClawdPose()
    }
}

/** The legs that tap, in the order a typist might hit the keys. */
private val TAPS = intArrayOf(0, 2, 1, 3, 2, 0, 3, 1, 1, 2, 0, 3, 2, 1, 0, 3)

/** "type": the legs drum on the floor, the arms bounce, the eyes look down at the keys. */
private fun typing(f: PetFrame): ClawdPose {
    val ms = f.ms
    val end = f.step.ms
    val tap = (ms / 75).toInt()
    val leg = TAPS[tap % TAPS.size]
    val legs = DOWN.mapIndexed { i, n -> if (i == leg) 1 else n }
    return when {
        ms < 200 -> crouch(1, ClawdPose(gaze = 1))
        ms > end - 200 -> ClawdPose()
        else ->
            ClawdPose(
                gaze = 1,
                dy = if (tap % 4 == 0) -1 else 0,
                legs = legs,
                armL = if (tap % 2 == 0) 1 else 0,
                armR = if (tap % 2 == 1) 1 else 0,
            )
    }
}

private fun excited(f: PetFrame): ClawdPose {
    val end = f.step.ms
    val period = (end - 400) / 3f
    if (f.ms >= end - 400) return if (f.ms > end - 150) ClawdPose() else crouch(1, ClawdPose(eyes = ClawdEyes.Wide))
    val q = (f.ms % period.toLong()) / period
    val air = ramp(q, 0.2f, 0.85f)
    return when {
        q < 0.2f -> crouch(2, ClawdPose(eyes = ClawdEyes.Wide))
        q < 0.85f ->
            ClawdPose(
                dy = -(4f * air * (1f - air) * 2f).roundToInt() - 1,
                turn = (2f * PI * air).toFloat(),
                eyes = ClawdEyes.Wide,
                armL = -5,
                armR = -5,
                legs = listOf(1, 1, 1, 1),
            )
        q < 0.95f -> crouch(2, ClawdPose(eyes = ClawdEyes.Wide))
        else -> crouch(1, ClawdPose(eyes = ClawdEyes.Wide))
    }
}

private fun tired(p: Float): ClawdPose {
    val squash = track(p, 0.1f to 0f, 0.3f to 1f, 0.55f to 2f, 0.75f to 3f)
    val flat = squash >= 3
    return ClawdPose(
        dy = if (flat) 1 else 0,
        squash = squash,
        grow = if (squash >= 2) 1 else 0,
        eyes =
            if (flat || p > 0.6f) {
                ClawdEyes.Shut
            } else if (p > 0.15f) {
                ClawdEyes.Half
            } else {
                ClawdEyes.Open
            },
        gaze = if (p in 0.15f..0.6f) 1 else 0,
        armL = if (flat) 0 else track(p, 0.1f to 0f, 0.4f to 2f),
        armR = if (flat) 0 else track(p, 0.1f to 0f, 0.4f to 2f),
        armLx = if (flat) 2 else 0,
        armRx = if (flat) -2 else 0,
        legs = if (squash >= 2) listOf(1, 1, 1, 1) else DOWN,
        legDx = if (squash in 1..2) listOf(-1, 0, 0, 1) else NO_SHIFT,
    )
}

private fun startle(
    s: Float,
    f: PetFrame,
): ClawdPose {
    val end = f.step.ms / 1000f
    return when {
        s < 0.08f -> ClawdPose(eyes = ClawdEyes.Wide, armL = -2, armR = -2, legs = listOf(1, 1, 1, 1), dy = -1)
        s < 0.3f ->
            ClawdPose(
                dy = -2,
                squash = -1,
                eyes = ClawdEyes.Wide,
                armL = -5,
                armR = -5,
                legs = listOf(1, 1, 1, 1),
                legDx = listOf(-1, -1, 1, 1),
            )
        s < 0.4f -> ClawdPose(dy = -1, eyes = ClawdEyes.Wide, armL = -5, armR = -5, legs = listOf(3, 3, 3, 3))
        s < 0.52f -> crouch(1, ClawdPose(eyes = ClawdEyes.Wide))
        s < end - 0.3f -> ClawdPose(gaze = 1, squash = 1)
        s < end - 0.12f -> ClawdPose(gaze = 1)
        else -> ClawdPose()
    }
}

/** Twelve beats that loop: bounce and pump, sidesteps, wiggle, a spin and a big jump. */
private fun dance(f: PetFrame): ClawdPose {
    val beat = ((f.ms / PET_BEAT_MS) % 12).toInt()
    val q = f.beat
    val left = beat % 2 == 0
    val up = q in 0.12f..0.62f
    val land = q < 0.12f
    return when (beat) {
        in 0..3 ->
            ClawdPose(
                dy = if (up) -1 else 0,
                squash = if (land) 1 else 0,
                look = if (left) -1 else 1,
                armL = if (left) -3 else 0,
                armR = if (left) 0 else -3,
                legs = if (up) listOf(1, 1, 1, 1) else DOWN,
            )
        in 4..7 -> {
            val away = if (left) -2 else 2
            val slide = track(q, 0f to 0f, 0.5f to away.toFloat(), 1f to 0f)
            ClawdPose(
                dx = slide,
                dy = if (up) -1 else 0,
                look = away / 2,
                armL =
                    if (beat >= 6) {
                        -4
                    } else if (left) {
                        -2
                    } else {
                        0
                    },
                armR =
                    if (beat >= 6) {
                        -4
                    } else if (left) {
                        0
                    } else {
                        -2
                    },
                legs = if (slide != 0) listOf(1, 2, 2, 1) else DOWN,
                legDx = if (slide != 0) listOf(away / 2, 0, 0, away / 2) else NO_SHIFT,
            )
        }
        8, 9 ->
            ClawdPose(
                squash = if (left) 1 else 0,
                grow = if (left) 1 else -1,
                look = if (left) -1 else 1,
                armL = if (left) 0 else -2,
                armR = if (left) -2 else 0,
            )
        10 -> {
            val air = ramp(q, 0.1f, 0.9f)
            ClawdPose(
                dy = -(4f * air * (1f - air) * 2f).roundToInt(),
                turn = (2f * PI * air).toFloat(),
                armL = -5,
                armR = -5,
                legs = if (air > 0f) listOf(1, 1, 1, 1) else DOWN,
            )
        }
        else -> {
            val air = ramp(q, 0.1f, 0.85f)
            when {
                q < 0.1f -> crouch(2)
                q < 0.85f ->
                    ClawdPose(
                        dy = -(4f * air * (1f - air) * 2f).roundToInt() - 1,
                        eyes = ClawdEyes.Happy,
                        armL = -5,
                        armR = -5,
                        legs = listOf(1, 1, 1, 1),
                    )
                else -> crouch(1)
            }
        }
    }
}

/** Clawd on its excursion at [t] (0 to 1): typing, hopping down and up the stairs, looking around. Its place on the path comes from the path itself. */
internal fun roamPose(t: Float): ClawdPose {
    val spot = ClawdRoam.at(t)
    val u = spot.u
    val ms = t * ClawdRoam.MS
    return when (spot.phase) {
        ClawdPhase.Lead -> if (u > 0.6f) crouch(1, ClawdPose(gaze = 1)) else ClawdPose(gaze = if (u > 0.3f) 1 else 0, look = 1)
        ClawdPhase.Type -> {
            val since = (t - ClawdRoam.keyAt(spot.n)) / 0.04f
            val tap = (ms / 70).toInt()
            val legs = DOWN.mapIndexed { i, n -> if (i == TAPS[tap % TAPS.size]) 1 else n }
            if (since in 0f..0.12f) {
                crouch(1, ClawdPose(gaze = 1, grow = 1, armL = 1, armR = 1))
            } else {
                ClawdPose(gaze = 1, dy = if (tap % 3 == 0) -1 else 0, legs = legs, armL = tap % 2, armR = (tap + 1) % 2)
            }
        }
        ClawdPhase.Ready -> if (u < 0.5f) crouch(2, ClawdPose(gaze = 1)) else crouch(2, ClawdPose(look = 1, eyes = ClawdEyes.Wide))
        ClawdPhase.Down ->
            when {
                u < 0.25f -> ClawdPose(squash = -1, legs = listOf(3, 3, 3, 3), armL = -2, armR = -2, look = 1)
                u < 0.75f -> ClawdPose(armL = -5, armR = -5, legs = listOf(1, 1, 1, 1), look = 1, eyes = ClawdEyes.Wide)
                else -> ClawdPose(squash = -1, legs = listOf(3, 3, 3, 3), armL = -2, armR = -2, look = 1, gaze = 1)
            }
        ClawdPhase.Up ->
            when {
                u < 0.3f -> ClawdPose(squash = -1, legs = listOf(3, 3, 3, 3), armL = -5, armR = -5, look = -1, gaze = -1)
                u < 0.8f -> ClawdPose(armL = -5, armR = -5, legs = listOf(1, 1, 1, 1), look = -1, gaze = -1, eyes = ClawdEyes.Wide)
                else -> ClawdPose(squash = -1, legs = listOf(3, 3, 3, 3), armL = -5, armR = -5, look = -1)
            }
        ClawdPhase.Land -> if (u < 0.55f) crouch(1, ClawdPose(grow = 1)) else ClawdPose()
        ClawdPhase.Look -> {
            val look = track(u, 0.05f to 1f, 0.15f to 2f, 0.3f to 2f, 0.4f to -2f, 0.55f to -2f, 0.65f to 1f)
            val happy = u > 0.72f
            val bounce = if (u in 0.78f..0.9f) -1 else 0
            ClawdPose(
                dx = look / 2,
                dy = bounce,
                look = look,
                gaze = if (u in 0.18f..0.3f) -1 else 0,
                eyes =
                    if (u in 0.62f..0.66f) {
                        ClawdEyes.Half
                    } else if (happy) {
                        ClawdEyes.Happy
                    } else {
                        ClawdEyes.Open
                    },
                armL = if (happy) -3 else 0,
                armR = if (happy) -3 else 0,
                legs = if (bounce != 0) listOf(1, 1, 1, 1) else DOWN,
            )
        }
        ClawdPhase.Prep -> crouch(2, ClawdPose(look = -1))
    }
}

/** The marks of [f] that leave the pet: heart, "z", sweat, a low battery, the idea, typed characters, sparks and bolt, the "!". */
internal fun Cells.marks(
    f: PetFrame,
    pose: ClawdPose,
) {
    val heart = f.mark(PetMark.Heart)
    if (heart >= 0f) {
        val alpha = 1f - ease((heart - 0.6f) / 0.4f)
        glyph(
            if (heart <
                0.12f
            ) {
                HEART_SMALL
            } else {
                HEART
            },
            5 + (sin(heart * 9f) * 1.2f).roundToInt(),
            -2 - (3f * ease(heart)).roundToInt(),
            alpha,
        )
    }
    val z = f.mark(PetMark.Z)
    if (z >= 0f) {
        for (i in 0..2) {
            val q = (z - i * 0.2f) / 0.6f
            if (q <= 0f || q >= 1f) continue
            val alpha = minOf(1f, q * 5f) * (1f - ease((q - 0.55f) / 0.45f))
            glyph(Z_SMALL, 11 + (q * 3f).roundToInt(), 2 - (q * 7f).roundToInt(), alpha)
        }
    }
    val sweat = f.mark(PetMark.Sweat)
    if (sweat >= 0f) {
        val q = (sweat * 3f) % 1f
        glyph(DROP, 0, 1 + (q * 3f).toInt(), bump(q, 0f, 1f) * 1.4f)
    }
    if (f.low && (f.state == PetState.Tired || f.state == PetState.Sleep)) glyph(BATTERY, 0, -5)
    when (f.state) {
        PetState.Think -> thought(f)
        PetState.Type -> typed(f)
        PetState.Excited -> sparks(f)
        PetState.Startle -> if (f.ms in 260..760) glyph(BANG, 7, -5)
        else -> {}
    }
}

private fun Cells.thought(f: PetFrame) {
    val ms = f.ms
    val idea = f.step.ms - 800
    if (ms < 300) return
    val spin = (ms / 110) % 2 == 0L
    when {
        ms < idea ->
            glyph(
                if (ms <
                    560
                ) {
                    (if (spin) STAR_PLUS_S else STAR_CROSS_S)
                } else {
                    (if (spin) STAR_PLUS else STAR_CROSS)
                },
                if (ms <
                    560
                ) {
                    7
                } else {
                    6
                },
                if (ms < 560) -3 else -4,
            )
        ms < idea + 450 -> {
            val q = (ms - idea) / 450f
            glyph(if (spin) STAR_PLUS else STAR_CROSS, 6, -4 - (q * 1.5f).roundToInt(), 1f - ease(q))
            if (q > 0.15f) {
                val d = (q * 4f).roundToInt()
                for ((sx, sy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) glyph(DOT, 8 + sx * (3 + d), -4 + sy * (3 + d), 1f - ease(q))
            }
        }
    }
}

private fun Cells.typed(f: PetFrame) {
    for (i in 0 until 12) {
        val born = 180 + i * 230
        val q = (f.ms - born) / 640f
        if (q !in 0f..1f) continue
        val left = i % 2 == 0
        glyph(TYPED_GLYPHS[i % TYPED_GLYPHS.size], if (left) -4 + (i % 3) else 17 - (i % 3), 3 - (q * 6f).roundToInt(), 1f - q * q)
    }
}

private fun Cells.sparks(f: PetFrame) {
    val fade = 1f - ease((f.progress - 0.85f) / 0.15f)
    glyph(BOLT, 17, -1 + wave(f.ms, 600).roundToInt(), fade)
    for (k in 0..2) {
        val a = f.ms / 400f + k * 2.1f
        val alpha = fade * bump(((f.ms + k * 230) % 700) / 700f, 0f, 1f)
        glyph(SPARK, 7 + (7f * cos(a)).roundToInt(), 2 + (5f * sin(a)).roundToInt(), alpha)
    }
}

private val HEART = listOf("RR.RR", "RRRRR", ".RRR.", "..R..")
private val HEART_SMALL = listOf("R.R", "RRR", ".R.")
private val Z_SMALL = listOf("WWW", ".W.", "WWW")
private val DROP = listOf(".B", "BB", "BB")
private val BATTERY = listOf("WWWWW.", "WR..WW", "WWWWW.")
private val BANG = listOf("YY", "YY", "YY", "..", "YY")
private val BOLT = listOf("..YY", ".YY.", "YYYY", ".YY.", "YY..")
private val SPARK = listOf(".Y.", "YHY", ".Y.")
private val DOT = listOf("O")
private val STAR_PLUS = listOf("..O..", "..O..", "OOHOO", "..O..", "..O..")
private val STAR_CROSS = listOf("O...O", ".O.O.", "..H..", ".O.O.", "O...O")
private val STAR_PLUS_S = listOf(".O.", "OHO", ".O.")
private val STAR_CROSS_S = listOf("O.O", ".H.", "O.O")
