package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import sk.vladko.pill.rules.ClawdPhase
import sk.vladko.pill.rules.ClawdRoam
import sk.vladko.pill.rules.PetPose
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Clawd: a small orange block that programs. Sixteen cells wide and ten tall: a plain 12 x 8 body, a 2 x 2 arm on each side,
// two dark bars for eyes and four legs. It has no mouth, tail or ears; it acts with its eyes, arms, legs and by squashing and
// stretching the block. Every picture is a list of rectangles in whole cells, so nothing is ever blurred or scaled in between.

internal const val CLAWD_COLS = 16

/** The rows the pet's box has: the ten of the pet and room above its head for marks and hops. */
private const val CLAWD_ROWS = 14

/** The rows of the pet itself: the feet stand on row 10. */
internal const val CLAWD_HEIGHT = 10

internal val CLAWD_BODY = Color(0xFFD97757)
private val BRICK_TOP = Color(0xFFC46A48)
private val BRICK_MID = Color(0xFFAD583A)
private val BRICK_BOTTOM = Color(0xFF94472D)
private val BRICK_NEW = Color(0xFFFFD2B8)
private val CURSOR = Color(0xFF4ADE80)

/** One rectangle of a picture, in cells from the pet's top-left cell. */
private class Cell(
    val c: Int,
    val r: Int,
    val w: Int,
    val h: Int,
    val color: Color,
)

/** The rectangles of one picture. */
internal class Cells {
    private val list = ArrayList<Cell>()

    fun add(
        c: Int,
        r: Int,
        w: Int,
        h: Int,
        color: Color,
    ) {
        if (w > 0 && h > 0) list += Cell(c, r, w, h, color)
    }

    /** A little picture of letters ([glyphColors]); '.' is empty. */
    fun glyph(
        rows: List<String>,
        c: Int,
        r: Int,
        alpha: Float = 1f,
    ) {
        if (alpha <= 0f) return
        rows.forEachIndexed { j, line ->
            line.forEachIndexed { i, ch ->
                if (ch !=
                    '.'
                ) {
                    add(c + i, r + j, 1, 1, glyphColors.getValue(ch).copy(alpha = alpha.coerceIn(0f, 1f)))
                }
            }
        }
    }

    /** Draws every rectangle with the pet's top-left cell at ([ox], [oy]), cells of [cell] pixels, [mirror]ed around the pet's middle. */
    fun draw(
        scope: DrawScope,
        ox: Float,
        oy: Float,
        cell: Int,
        mirror: Boolean,
    ) {
        fun x(c: Int) = (ox + c * cell).roundToInt().toFloat()

        fun y(r: Int) = (oy + r * cell).roundToInt().toFloat()
        for (it in list) {
            val a = if (mirror) CLAWD_COLS - it.c - it.w else it.c
            scope.drawRect(it.color, Offset(x(a), y(it.r)), Size(x(a + it.w) - x(a), y(it.r + it.h) - y(it.r)))
        }
    }
}

private val glyphColors =
    mapOf(
        'R' to Color(0xFFFF4D6D),
        'r' to Color(0xFFFFB3C1),
        'W' to Color(0xFFE6F0FF),
        'B' to Color(0xFF7FD0FF),
        'Y' to Color(0xFFFFD43B),
        'O' to Color(0xFFFFA45C),
        'H' to Color(0xFFFFE9D0),
        'G' to Color(0xFF4ADE80),
    )

/** Clawd in the idle pill: the pill's size gives the cell, the camera hole is cut out of the picture. */
internal class ClawdScene(
    private val w: Float,
    private val h: Float,
    private val hole: Rect?,
    fixedUnit: Float? = null,
) : PetScene {
    /** The widest free strip beside the hole (the whole pill without a hole). */
    private val room = if (hole == null) w else max(hole.left, w - hole.right)

    /** One cell in pixels: the pet fills most of the pill height and fits beside the hole. */
    private val pix =
        if (fixedUnit != null) {
            max(1, fixedUnit.toInt())
        } else if (hole == null && w < h * 3f) {
            max(1, floor(min(h, w) * 0.98f / CLAWD_ROWS).toInt())
        } else {
            max(1, min(floor(h * 0.95f / CLAWD_ROWS).toInt(), min(floor(w * 0.4f / CLAWD_COLS).toInt(), floor(room / CLAWD_COLS).toInt())))
        }
    override val unit get() = pix.toFloat()
    private val box = (CLAWD_COLS * pix).toFloat()
    private val edge = min(h * 0.28f, max(0f, (room - box) / 2f))

    /** The pet rests beside the hole, never under it. */
    private val leftEnd = if (hole == null || hole.left - edge >= box) edge else min(hole.right + pix * 2f, w - edge - box)
    private val rightEnd = if (hole == null || w - edge - hole.right >= box) w - edge - box else max(hole.left - pix * 2f - box, leftEnd)
    private val span = max(0f, rightEnd - leftEnd)

    /** The top of the pet's picture: its feet stand a little above the pill's lower edge. */
    private val top = (h - max(2f, h * 0.05f) - CLAWD_HEIGHT * pix).roundToInt().toFloat()
    private val holePath = Path().apply { hole?.let { addOval(it) } }

    override val canWalk = span >= 2 * pix
    override val holeX = if (hole == null || span <= 0f) 0.5f else ((hole.center.x - box / 2f - leftEnd) / span).coerceIn(0f, 1f)
    override val drawsRoam = true

    private fun left(x: Float) = leftEnd + span * x

    override fun middle(x: Float) = left(x) + box / 2f

    override fun DrawScope.draw(frame: PetFrame) {
        val out = frame.out
        if (out != null) {
            drawRoam(frame, out)
            return
        }
        val step = frame.step
        val walked = abs(frame.x - step.fromX) * span / pix
        val left = abs(step.toX - frame.x) * span / pix
        var pose = clawdPose(frame, walked, left)
        if (step.poseFrom != step.pose) pose = mixPose(restPose(step.poseFrom), pose, ease(frame.blend))
        if (frame.low && pose.eyes == ClawdEyes.Open) pose = pose.copy(eyes = ClawdEyes.Half)
        val cells = Cells()
        cells.pet(pose)
        cells.marks(frame, pose)
        clipPath(holePath, ClipOp.Difference) { cells.draw(this, left(frame.x), top, pix, false) }
    }

    /** The excursion: the pet layer carries the pet along its path; the stairs stay where they were typed. */
    private fun DrawScope.drawRoam(
        frame: PetFrame,
        out: Offset,
    ) {
        val hurry = frame.step.roam?.hurryFrom
        val t = (hurry ?: 0f) + (1f - (hurry ?: 0f)) * frame.progress
        val ox = left(frame.x)
        val bricks = Cells()
        for (i in 0 until ClawdRoam.BRICKS) {
            val b = ClawdRoam.brick(i, t)
            val c = ClawdRoam.brickCol(i)
            val r = ClawdRoam.brickRow(i)
            for (j in 0 until ClawdRoam.BRICK_HEIGHT) {
                val shade =
                    if (b.fresh) {
                        BRICK_NEW
                    } else if (j ==
                        0
                    ) {
                        BRICK_TOP
                    } else if (j == ClawdRoam.BRICK_HEIGHT - 1) {
                        BRICK_BOTTOM
                    } else {
                        BRICK_MID
                    }
                bricks.add(c, r + j, b.width, 1, shade)
            }
        }
        val mirror = frame.face < 0f
        translate(-out.x, -out.y) { bricks.draw(this, ox, top, pix, mirror) }
        val cells = Cells()
        cells.pet(if (hurry != null) HURRY_POSE else roamPose(t))
        cells.roamExtras(t)
        cells.draw(this, ox, top, pix, mirror)
    }
}

/** The pet and the marks of the roam that move with it. */
private fun Cells.roamExtras(t: Float) {
    val spot = ClawdRoam.at(t)
    if (spot.phase == ClawdPhase.Look && ((t * ClawdRoam.MS / 320f).toInt() % 2 == 0)) add(17, 3, 2, 4, CURSOR)
    for (i in 0 until ClawdRoam.BRICKS) {
        val q = (t - ClawdRoam.keyAt(i)) / 0.07f
        if (q in 0f..1f) glyph(TYPED_GLYPHS[i % TYPED_GLYPHS.size], -4 + (i % 2) * 20, 5 - (q * 7).toInt(), 1f - q * q)
    }
}

/** What flicks up beside a typing pet, one after another: ">", "_", "{", "}". */
internal val TYPED_GLYPHS =
    listOf(
        listOf("G..", ".G.", "..G", ".G.", "G.."),
        listOf("...", "...", "...", "...", "GGG"),
        listOf(".GG", ".G.", "G..", ".G.", ".GG"),
        listOf("GG.", ".G.", "..G", ".G.", "GG."),
    )

/** The state of [PetState.Sit], [PetState.Sleep] and the other bodies by [PetPose]. */
internal fun restPose(pose: PetPose): ClawdPose = if (pose == PetPose.Lie) LIE_POSE else ClawdPose()
