package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import sk.vladko.pill.rules.PetMark
import sk.vladko.pill.rules.PetPose
import sk.vladko.pill.rules.PetState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.reflect.KProperty

// The pixel look: each animal is a rig of simple parts (ovals, lines, a tail curve) posed in a 20 x 20 grid of cells and
// drawn as crisp square pixels. Poses blend field by field, so the shapes change one pixel at a time and never pop.
// This file holds the shared engine; PixelCatRig.kt, PixelDog.kt, PixelBird.kt and PixelMonkey.kt add the rigs.

internal const val GRID = 20

/** The row the feet stand on. */
internal const val GROUND = 19.5f

private const val SLOTS = 36
private val slot = Array(SLOTS) { Slot(it) }

private class Slot(
    val i: Int,
) {
    operator fun getValue(
        p: Pose,
        k: KProperty<*>,
    ) = p.v[i]

    operator fun setValue(
        p: Pose,
        k: KProperty<*>,
        x: Float,
    ) {
        p.v[i] = x
    }
}

/** One pose of an animal in grid cells (x right, y down), facing right. Angles are in radians. */
internal class Pose(
    val v: FloatArray = FloatArray(SLOTS),
) {
    /** Body oval: centre, radii, rotation (positive tips the front down). */
    var bx by slot[0]
    var by by slot[1]
    var brx by slot[2]
    var bry by slot[3]
    var tilt by slot[4]

    /** Head centre; [look] 1 profile ahead, 0 at the viewer, -1 over the shoulder; [nod] moves the face down. */
    var hx by slot[5]
    var hy by slot[6]
    var look by slot[7]
    var nod by slot[8]

    /** Eyes: 0 shut, 0.5 sleepy, 1 open, 2 wide, below 0 happily squeezed. */
    var eye by slot[9]
    var mouth by slot[10]

    /** Ears or crest: -1 flat, 0 rest, 1 up. */
    var ear by slot[11]

    /** Feet: front near and far, back near and far. */
    var f1x by slot[12]
    var f1y by slot[13]
    var f2x by slot[14]
    var f2y by slot[15]
    var b1x by slot[16]
    var b1y by slot[17]
    var b2x by slot[18]
    var b2y by slot[19]

    /** The folded back leg of a sitting animal: size 0 to 1 and centre. */
    var haunch by slot[20]
    var kx by slot[21]
    var ky by slot[22]

    /** Tail: root, angle (0 straight back, up is positive), curl along its length, length, 1 when bushed out. */
    var tx by slot[23]
    var ty by slot[24]
    var ta by slot[25]
    var tc by slot[26]
    var tl by slot[27]
    var puff by slot[28]

    /** Bird wings: 0 folded, 1 spread up. */
    var wing by slot[29]
    var tongue by slot[30]

    /** Whole sprite: raised by [lift] cells, moved ahead by [dx] cells; [turn] 1 the way it faces, -1 the other way. */
    var lift by slot[31]
    var dx by slot[32]
    var turn by slot[33]

    /** 1 when the tail is drawn in front of the body (curled round a lying animal). */
    var tailFront by slot[34]

    /** What the monkey holds: 0 nothing, 1 a banana, 1 to 2 peeling it, 2 to 3 eating it. */
    var prop by slot[35]

    fun with(change: Pose.() -> Unit) = Pose(v.copyOf()).apply(change)
}

internal fun lerp(
    a: Float,
    b: Float,
    t: Float,
) = a + (b - a) * t

internal fun lerp(
    a: Pose,
    b: Pose,
    t: Float,
) = Pose(FloatArray(SLOTS) { a.v[it] + (b.v[it] - a.v[it]) * t })

/** Slow in, slow out. */
internal fun ease(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** The pose at [t] between keyframes (time to pose, times rising), eased between each pair. */
internal fun keys(
    t: Float,
    vararg k: Pair<Float, Pose>,
): Pose {
    if (t <= k.first().first) return k.first().second
    for (i in 1 until k.size) {
        if (t <= k[i].first) {
            val (t0, p0) = k[i - 1]
            val (t1, p1) = k[i]
            return lerp(p0, p1, ease((t - t0) / (t1 - t0)))
        }
    }
    return k.last().second
}

/** A sine wave of [period] ms at [ms], shifted by [shift] of a period. */
internal fun wave(
    ms: Long,
    period: Long,
    shift: Float = 0f,
) = sin(((ms % period).toFloat() / period + shift) * 2f * PI.toFloat())

/** 0 to 1 and back over [a]..[b] of [t] (a smooth bump), 0 outside. */
internal fun bump(
    t: Float,
    a: Float,
    b: Float,
) = if (t <= a || t >= b) 0f else sin((t - a) / (b - a) * PI.toFloat())

/** The colours of one frame, row by row; 0 is empty. */
internal class Raster {
    val c = IntArray(GRID * GRID)

    fun dot(
        x: Float,
        y: Float,
        col: Int,
    ) {
        val i = floor(x).toInt()
        val j = floor(y).toInt()
        if (i in 0 until GRID && j in 0 until GRID) c[j * GRID + i] = col
    }

    /** Fills an oval; [paint] gets the cell's place inside it (-1 to 1 on both axes, unrotated) and its cell. */
    fun oval(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        tilt: Float = 0f,
        paint: (u: Float, v: Float, i: Int, j: Int) -> Int,
    ) {
        if (rx <= 0.05f || ry <= 0.05f) return
        val r = max(rx, ry) + 1f
        val cs = cos(tilt)
        val sn = sin(tilt)
        for (j in floor(cy - r).toInt()..floor(cy + r).toInt()) {
            for (i in floor(cx - r).toInt()..floor(cx + r).toInt()) {
                val dx = i + 0.5f - cx
                val dy = j + 0.5f - cy
                val u = (dx * cs + dy * sn) / rx
                val v = (-dx * sn + dy * cs) / ry
                if (u * u + v * v <= 1f) {
                    val col = paint(u, v, i, j)
                    if (col != 0) dot(i + 0.5f, j + 0.5f, col)
                }
            }
        }
    }

    fun oval(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        col: Int,
        tilt: Float = 0f,
    ) = oval(cx, cy, rx, ry, tilt) { _, _, _, _ -> col }

    /** A line one cell wide (two when [thick]). */
    fun line(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        col: Int,
        thick: Boolean = false,
    ) {
        val n = (max(abs(x1 - x0), abs(y1 - y0)) * 3f).toInt() + 1
        for (k in 0..n) {
            val x = lerp(x0, x1, k.toFloat() / n)
            val y = lerp(y0, y1, k.toFloat() / n)
            dot(x, y, col)
            if (thick) dot(x + 0.9f, y, col)
        }
    }

    /** The tail of [p] from its root; [col] gets how far along it the cell is (0 to 1). */
    fun tail(
        p: Pose,
        thick: Boolean,
        col: (Float) -> Int,
    ) {
        var x = p.tx
        var y = p.ty
        val n = max(1, (p.tl * 2f).toInt())
        for (k in 0..n) {
            val f = k.toFloat() / n
            dot(x, y, col(f))
            if (thick && f < 0.85f) dot(x, y - 0.9f, col(f))
            val a = p.ta + p.tc * f
            x -= cos(a) * 0.5f
            y -= sin(a) * 0.5f
        }
    }
}

/** Where a leg leaves the body: front or back, turned with the body. */
internal fun Pose.hip(front: Boolean): Pair<Float, Float> {
    val lx = (if (front) 0.55f else -0.55f) * brx
    val ly = 0.35f * bry
    return bx + lx * cos(tilt) - ly * sin(tilt) to by + lx * sin(tilt) + ly * cos(tilt)
}

/**
 * Two eyes at [ey], [gap] apart seen from the front, sliding together as the head turns to profile; [ahead] is where the
 * eye sits in profile. [iris] and [pupil] colour an open eye, [lid] a shut one.
 */
internal fun Raster.eyes(
    p: Pose,
    ey: Float,
    gap: Float,
    ahead: Float,
    iris: Int,
    pupil: Int,
    lid: Int,
) {
    val look = p.look
    for (side in intArrayOf(-1, 1)) {
        // In profile only the near eye shows.
        if (abs(look) > 0.6f && side * look < 0) continue
        val x = p.hx + ahead * look + side * gap * (1f - abs(look))
        when {
            p.eye < 0f -> {
                dot(x, ey - 0.5f, lid)
                dot(x - 0.9f, ey + 0.4f, lid)
                dot(x + 0.9f, ey + 0.4f, lid)
            }
            p.eye < 0.3f -> {
                dot(x, ey, lid)
                dot(x + if (abs(look) > 0.6f) -look else side.toFloat(), ey, lid)
            }
            p.eye < 0.75f -> dot(x, ey, pupil)
            else -> {
                dot(x, ey, pupil)
                dot(x, ey - 1f, iris)
                if (p.eye > 1.5f) dot(x, ey - 2f, iris)
            }
        }
    }
}

/** A small picture that leaves the animal: a heart, a "z", bark lines. [x], [y] in grid cells, facing right. */
internal class Mark(
    val rows: List<String>,
    val x: Float,
    val y: Float,
    val alpha: Float,
)

private val HEART = listOf(".RR.RR.", "RrRRRRR", "RRRRRRR", ".RRRRR.", "..RRR..", "...R...")
private val HEART_SMALL = listOf("R.R", "RRR", ".R.")
private val Z_BIG = listOf("WWWW", "..W.", ".W..", "WWWW")
private val Z_SMALL = listOf("WWW", ".W.", "WWW")
private val DROP = listOf(".B", "BB", "BB")
private val BOLT = listOf("..YY", ".YY.", "YYYY", ".YY.", "YY..")
private val SPARK = listOf(".Y.", "YWY", ".Y.")
private val BANG = listOf("Y", "Y", "Y", ".", "Y")
private val BATTERY = listOf("WWWWW.", "WR..WW", "WWWWW.")

private fun glyphColor(ch: Char): Int =
    when (ch) {
        'R' -> 0xFFFF4D6D.toInt()
        'r' -> 0xFFFFB3C1.toInt()
        'W' -> 0xFFE6F0FF.toInt()
        'B' -> 0xFF7FD0FF.toInt()
        'Y' -> 0xFFFFD43B.toInt()
        'D' -> 0xFFA8743F.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

/**
 * One picture a rig drew whole: its [raster] facing right, [pose] for where marks leave it (the head [Pose.hx], [Pose.hy])
 * and how far the grid is lifted or moved ahead, and whether it is drawn [mirror]ed (facing left).
 */
internal class Drawn(
    val raster: Raster,
    val pose: Pose,
    val mirror: Boolean,
)

/** How one animal is built and moves. */
internal interface Rig {
    /** The resting pose for a body fold. */
    fun base(p: PetPose): Pose

    /** The pose for [f]; [walked] is how many cells the animal has walked in this step. */
    fun pose(
        f: PetFrame,
        walked: Float,
    ): Pose

    fun paint(
        r: Raster,
        p: Pose,
    )

    /** A rig that draws whole sprites answers here for every frame; null draws the pose of [pose] and [paint] as usual. */
    fun picture(
        f: PetFrame,
        walked: Float,
    ): Drawn? = null

    /** False when the happy hop of a plugged-in charger shows no heart. */
    val excitedHeart: Boolean get() = true

    /** The drawing of the still body [p], for a [PixelAction] that starts or ends in it. */
    fun rest(p: PetPose): Raster = Raster().also { paint(it, base(p)) }

    /** Where the mouth is, for bark lines. */
    fun mouth(p: Pose): Pair<Float, Float> = p.hx + 3f to p.hy + 1f

    /** Marks of the animal's own (dirt, feathers). */
    fun extras(
        f: PetFrame,
        p: Pose,
        out: MutableList<Mark>,
    ) {}
}

/** The pill in pixels, the animal's pixel size and where the camera hole is (cut out of the drawing). */
internal open class PixelScene(
    val w: Float,
    val h: Float,
    val hole: Rect?,
    private val rig: Rig,
    fixedUnit: Float? = null,
    /** The scene's own Roam, written as data; null draws the default. */
    private val roam: PixelAction? = null,
) : PetScene {
    /** The widest free strip beside the hole (the whole pill without a hole). */
    private val room = if (hole == null) w else max(hole.left, w - hole.right)

    /**
     * One grid cell in screen pixels: the animal fills most of the pill height, at most 40% of its width, and fits beside
     * the hole. A small box without a hole (the music pill) is filled.
     */
    val pix =
        if (fixedUnit != null) {
            max(1, fixedUnit.toInt())
        } else if (hole == null && w < h * 3f) {
            max(1, floor(min(h, w) * 0.98f / GRID).toInt())
        } else {
            max(1, min(floor(h * 0.95f / GRID).toInt(), min(floor(w * 0.4f / GRID).toInt(), floor(room / GRID).toInt())))
        }
    override val unit get() = pix.toFloat()
    private val edge = min(h * 0.28f, max(0f, (room - GRID * pix) / 2f))
    private val box = (GRID * pix).toFloat()

    /** The animal rests beside the hole, never under it: at the pill's end when the hole leaves room there, else next to it. */
    private val leftEnd =
        if (hole == null || hole.left - edge >= box) edge else min(hole.right + pix * 2f, w - edge - box)
    private val rightEnd =
        if (hole == null || w - edge - hole.right >= box) w - edge - box else max(hole.left - pix * 2f - box, leftEnd)
    private val span = max(0f, rightEnd - leftEnd)

    // The animal stands low in a tall box; in a box shorter than the grid (a compact pill) its feet stay a little above the bottom.
    private val top = min((h - box) * 0.7f, h - box - h * 0.04f)
    private val holePath = Path().apply { hole?.let { addOval(it) } }

    override val canWalk = span >= 2 * pix

    override val holeX = if (hole == null || span <= 0f) 0.5f else ((hole.center.x - box / 2f - leftEnd) / span).coerceIn(0f, 1f)

    private fun left(x: Float) = leftEnd + span * x

    override fun middle(x: Float) = left(x) + box / 2f

    override val drawsRoam get() = roam != null

    /**
     * Draws what the Roam shows besides the pet, under it: [t] is how far the excursion has come (0 to 1), the grid's top-left
     * is at ([ox], [oy]) with cells of [cell] pixels, [out] is how far the pet layer moves everything, [hurry] is true while
     * the pet hurries home.
     */
    protected open fun DrawScope.beforeRoam(
        t: Float,
        mirror: Boolean,
        ox: Float,
        oy: Float,
        cell: Float,
        out: Offset,
        hurry: Boolean,
    ) {}

    override fun DrawScope.draw(frame: PetFrame) {
        val step = frame.step
        if (roam != null && frame.out != null) {
            // A hurry home plays the rest of the excursion in its short time.
            val from = step.roam?.hurryFrom ?: 0f
            val t = from + (1f - from) * frame.progress
            val face = if (frame.face == 0f) step.face.toFloat() else frame.face
            beforeRoam(t, face < 0f, left(frame.x), top, pix.toFloat(), frame.out, step.roam?.hurryFrom != null)
            with(roam) {
                draw(t, face < 0f, left(frame.x), top, pix.toFloat()) { pose ->
                    rig.rest(pose)
                }
            }
            return
        }
        val walked = abs(frame.x - step.fromX) * span / pix
        val drawn = rig.picture(frame, walked)
        val p: Pose
        val r = Raster()
        val mirror: Boolean
        if (drawn != null) {
            p = drawn.pose
            mirror = drawn.mirror
            drawn.raster.c.copyInto(r.c)
        } else {
            val raw = rig.pose(frame, walked)
            val blended = if (step.poseFrom != step.pose) lerp(rig.base(step.poseFrom), raw, ease(frame.blend)) else raw
            val facing = frame.face * blended.turn
            var q = turned(blended, abs(facing))
            if (frame.low && q.eye > 0.5f) q = q.with { eye = 0.5f }
            p = q
            rig.paint(r, p)
            mirror = if (facing == 0f) frame.face < 0f else facing < 0f
        }
        val marks = marks(frame, p)

        // The box the grid fills: its centre and cell size; hiding and peeking move it into and out of the hole.
        var cell = pix.toFloat()
        var cx = left(frame.x) + box / 2f + (if (mirror) -p.dx else p.dx) * cell
        var cy = top + box / 2f - p.lift * cell
        val hx = hole?.center?.x ?: cx
        val hy = hole?.center?.y ?: cy
        val t = frame.progress
        // A rig that draws whole pictures stays full size behind the hole: it walks to it and leans out beside it.
        if (drawn != null) {
            // Drawn as it is.
        } else if (frame.state == PetState.Hide) {
            val u = ease((t - 0.55f) / 0.45f)
            cell *= 1f - 0.85f * u
            cx = lerp(cx, hx, u)
            cy = lerp(cy, hy, u)
        } else if (frame.state == PetState.Peek) {
            val dir = if (step.face < 0) -1f else 1f
            val sit = rig.base(PetPose.Sit)
            val small = cell * 0.75f
            val out = ease(t / 0.22f) * ((hole?.width ?: 0f) / 2f + 2f * small)
            val px = hx + dir * (out - (sit.hx - GRID / 2f) * small)
            val py = hy - (sit.hy - GRID / 2f) * small
            val u = ease((t - 0.62f) / 0.38f)
            cell = lerp(small, cell, u)
            cx = lerp(px, cx, u)
            cy = lerp(py, cy, u)
        }
        drawGrid(r, marks, mirror, cx - GRID * cell / 2f, cy - GRID * cell / 2f, cell)
    }

    private fun DrawScope.drawGrid(
        r: Raster,
        marks: List<Mark>,
        mirror: Boolean,
        ox: Float,
        oy: Float,
        cell: Float,
    ) {
        fun x(i: Float) = (ox + i * cell).roundToInt().toFloat()

        fun y(j: Float) = (oy + j * cell).roundToInt().toFloat()
        clipPath(holePath, ClipOp.Difference) {
            for (j in 0 until GRID) {
                var i = 0
                while (i < GRID) {
                    val col = r.c[j * GRID + i]
                    if (col == 0) {
                        i++
                        continue
                    }
                    var end = i
                    while (end + 1 < GRID && r.c[j * GRID + end + 1] == col) end++
                    val a = (if (mirror) GRID - 1 - end else i).toFloat()
                    val b = (if (mirror) GRID - i else end + 1).toFloat()
                    drawRect(Color(col), Offset(x(a), y(j.toFloat())), Size(x(b) - x(a), y(j + 1f) - y(j.toFloat())))
                    i = end + 1
                }
            }
            val dot = max(1, cell.roundToInt()).toFloat()
            for (m in marks) {
                if (m.alpha <= 0f) continue
                val width = m.rows.maxOf { it.length }
                val gx = if (mirror) GRID - m.x - width else m.x
                m.rows.forEachIndexed { rj, line ->
                    line.forEachIndexed { ri, ch ->
                        if (ch != '.') {
                            val color = Color(glyphColor(ch)).copy(alpha = m.alpha.coerceIn(0f, 1f))
                            drawRect(color, Offset(x(gx + ri), y(m.y + rj)), Size(dot, dot))
                        }
                    }
                }
            }
        }
    }

    /** The marks of [f]: heart, "z z z", bark lines, sweat, sparks, a low battery, the animal's own. */
    private fun marks(
        f: PetFrame,
        p: Pose,
    ): List<Mark> =
        buildList {
            val heart = if (f.state == PetState.Excited && !rig.excitedHeart) -1f else f.mark(PetMark.Heart)
            if (heart >= 0f) {
                val small = heart < 0.12f
                val alpha = 1f - ease((heart - 0.6f) / 0.4f)
                val x = p.hx + 3.5f + 1.2f * sin(heart * 9f) - if (small) 1f else 3f
                add(Mark(if (small) HEART_SMALL else HEART, x, p.hy - 7f - 6f * ease(heart), alpha))
            }
            val z = f.mark(PetMark.Z)
            if (z >= 0f) {
                for (i in 0..2) {
                    val q = (z - i * 0.2f) / 0.6f
                    if (q <= 0f || q >= 1f) continue
                    val alpha = min(1f, q * 5f) * (1f - ease((q - 0.55f) / 0.45f))
                    add(Mark(if (i == 0) Z_SMALL else Z_BIG, p.hx + 1f + q * 3f + sin(q * 6f), p.hy - 3f - q * 8f, alpha))
                }
            }
            val bark = f.mark(PetMark.Bark)
            if (bark >= 0f) {
                val (mx, my) = rig.mouth(p)
                val d = 1.5f + bark * 3f
                val alpha = 1f - ease((bark - 0.5f) / 0.5f)
                for (k in -1..1) {
                    val a = k * 0.6f
                    add(Mark(listOf("Y"), mx + d * cos(a), my + d * sin(a) - 1f, alpha))
                    add(Mark(listOf("Y"), mx + (d + 1f) * cos(a), my + (d + 1f) * sin(a) - 1f, alpha))
                }
            }
            val sweat = f.mark(PetMark.Sweat)
            if (sweat >= 0f) {
                val q = (sweat * 3f) % 1f
                add(Mark(DROP, p.hx - 3.5f, p.hy - 2.5f + q * 3f, bump(q, 0f, 1f) * 1.4f))
            }
            if (f.low && (f.state == PetState.Tired || f.state == PetState.Sleep)) add(Mark(BATTERY, 0f, 0f, 1f))
            if (f.state == PetState.Excited) {
                val fade = 1f - ease((f.progress - 0.85f) / 0.15f)
                add(Mark(BOLT, 0f, 1f + wave(f.ms, 600), fade))
                for (k in 0..2) {
                    val a = f.ms / 400f + k * 2.1f
                    add(Mark(SPARK, 9f + 8f * cos(a), 9f + 5f * sin(a), fade * bump(((f.ms + k * 230) % 700) / 700f, 0f, 1f)))
                }
            }
            if (f.state == PetState.Startle && f.progress < 0.55f) add(Mark(BANG, p.hx + 3f, p.hy - 8f, 1f))
            rig.extras(f, p, this)
        }
}

/** Squeezes the pose sideways while it turns round ([side] 0 at the middle of a turn, 1 side on), so a turn does not flip at once. */
private fun turned(
    p: Pose,
    side: Float,
): Pose {
    if (side >= 0.999f) return p
    val k = 0.5f + 0.5f * ease(side)
    val mid = GRID / 2f
    return p.with {
        bx = mid + (bx - mid) * k
        hx = mid + (hx - mid) * k
        f1x = mid + (f1x - mid) * k
        f2x = mid + (f2x - mid) * k
        b1x = mid + (b1x - mid) * k
        b2x = mid + (b2x - mid) * k
        kx = mid + (kx - mid) * k
        tx = mid + (tx - mid) * k
        brx *= k
        look *= ease(side)
    }
}
