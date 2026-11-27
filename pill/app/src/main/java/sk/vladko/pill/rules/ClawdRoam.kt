package sk.vladko.pill.rules

import kotlin.math.roundToInt

// Pure Kotlin: Clawd's excursion. It types a staircase of bricks below the pill, one brick per keystroke, walks down it, looks
// around on the last step, walks back up while the bricks are deleted behind it (backspace) and is home.
// Every number is in the pet's own cells, counted from its place in the pill: x toward the stairs (the way it faces), y down.
// The pet's place is the top-left cell of its 16 x 10 cell picture, so its feet stand on row 10.

/** What the pet does at one moment of the excursion. */
enum class ClawdPhase { Lead, Type, Ready, Down, Up, Land, Look, Prep }

/** Where the pet is ([x], [y] in cells), what it does and how far that has come ([u], 0 to 1); [n] is the keystroke, hop or brick it is at. */
class ClawdSpot(
    val x: Int,
    val y: Int,
    val phase: ClawdPhase,
    val u: Float,
    val n: Int,
)

/** One brick of the stairs: [width] cells of it are there (0 not typed yet or erased); [fresh] right after it was typed. */
class ClawdBrick(
    val width: Int,
    val fresh: Boolean,
)

object ClawdRoam {
    /** The whole excursion lasts this long (ms). */
    const val MS = 8_000L
    const val BRICKS = 5

    /** A brick is as wide as the pet's feet are apart, and three cells high. */
    const val BRICK_WIDTH = 10
    const val BRICK_HEIGHT = 3

    /** Each brick lies this many cells further along and this many cells lower than the one before. */
    const val RUN = 9
    const val RISE = 4

    private const val FIRST_KEY = 0.06f
    private const val KEY_GAP = 0.04f
    private const val TYPED = 0.27f
    private const val READY = 0.31f
    private const val HOP_DOWN = 0.032f
    private const val STEP_DOWN = 0.048f
    private const val LOOK = 0.55f
    private const val PREP = 0.74f
    private const val UP = 0.77f
    private const val HOP_UP = 0.034f
    private const val STEP_UP = 0.042f
    private const val ERASE = 0.018f
    private const val FRESH = 0.012f

    /** When keystroke [i] is typed (0 to 1 of the excursion). */
    fun keyAt(i: Int) = FIRST_KEY + KEY_GAP * i

    /** Where brick [i] lies: its left cell and top row. */
    fun brickCol(i: Int) = 3 + RUN * i

    fun brickRow(i: Int) = 11 + RISE * i

    /** When the pet has left brick [i] on its way up and the brick starts to vanish. */
    private fun erasedAt(i: Int) = UP + (BRICKS - 1 - i) * STEP_UP + HOP_UP + 0.002f

    /** Brick [i] at [p]: typed at its keystroke, erased from its far end after the pet left it. */
    fun brick(
        i: Int,
        p: Float,
    ): ClawdBrick {
        if (p < keyAt(i)) return ClawdBrick(0, false)
        val gone = (p - erasedAt(i)) / ERASE
        if (gone >= 1f) return ClawdBrick(0, false)
        val width = if (gone <= 0f) BRICK_WIDTH else (BRICK_WIDTH * (1f - gone)).toInt()
        return ClawdBrick(width, p < keyAt(i) + FRESH)
    }

    /** The cell of the pet standing on brick [i] (-1 is its place in the pill). */
    private fun stand(i: Int) = if (i < 0) 0 to 0 else RUN * i to 1 + RISE * i

    private fun hop(
        from: Int,
        to: Int,
        u: Float,
    ): Pair<Int, Int> {
        val (x0, y0) = stand(from)
        val (x1, y1) = stand(to)
        val arc = 4f * 4f * u * (1f - u)
        return (x0 + (x1 - x0) * u).roundToInt() to (y0 + (y1 - y0) * u - arc).roundToInt()
    }

    /** The pet at [p] (0 to 1 of the excursion). */
    fun at(p: Float): ClawdSpot {
        fun still(
            phase: ClawdPhase,
            u: Float,
            n: Int = 0,
            on: Int = -1,
        ) = stand(on).let { ClawdSpot(it.first, it.second, phase, u.coerceIn(0f, 1f), n) }
        return when {
            p < FIRST_KEY -> still(ClawdPhase.Lead, p / FIRST_KEY)
            p < TYPED -> still(ClawdPhase.Type, (p - FIRST_KEY) / (TYPED - FIRST_KEY), ((p - FIRST_KEY) / KEY_GAP).toInt())
            p < READY -> still(ClawdPhase.Ready, (p - TYPED) / (READY - TYPED))
            p < LOOK -> {
                val k = ((p - READY) / STEP_DOWN).toInt().coerceAtMost(BRICKS - 1)
                val u = (p - READY - k * STEP_DOWN) / HOP_DOWN
                if (u < 1f) {
                    hop(k - 1, k, u).let { ClawdSpot(it.first, it.second, ClawdPhase.Down, u, k) }
                } else {
                    still(ClawdPhase.Land, (u - 1f) / ((STEP_DOWN - HOP_DOWN) / HOP_DOWN), k, k)
                }
            }
            p < PREP -> still(ClawdPhase.Look, (p - LOOK) / (PREP - LOOK), BRICKS - 1, BRICKS - 1)
            p < UP -> still(ClawdPhase.Prep, (p - PREP) / (UP - PREP), BRICKS - 1, BRICKS - 1)
            else -> {
                val m = ((p - UP) / STEP_UP).toInt().coerceAtMost(BRICKS - 1)
                val from = BRICKS - 1 - m
                val u = (p - UP - m * STEP_UP) / HOP_UP
                if (u < 1f) {
                    hop(from, from - 1, u).let { ClawdSpot(it.first, it.second, ClawdPhase.Up, u, from) }
                } else {
                    still(ClawdPhase.Land, (u - 1f) / ((STEP_UP - HOP_UP) / HOP_UP), from - 1, from - 1)
                }
            }
        }
    }
}
