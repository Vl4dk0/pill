package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import sk.vladko.pill.rules.PetPose
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A pixel pet action written as data: a few letter-grid sprites and a key table that places them over the action's time.
 * The engine (this file) checks the data when it is built, moves between keys with easing, mirrors by facing and draws
 * crisp cells. Write one action in one file, for example `PixelBirdRoam.kt`, after `PixelMonkeyRoam.kt`.
 *
 * WHAT YOU WRITE: `internal val BirdRoam = PixelAction(palette, sprites, keys)`.
 * - palette: `mapOf('b' to 0xFF3A7BD5.toInt(), ...)`. Lower-case letters only; '.' is an empty cell.
 * - sprites: `listOf(PixelSprite("name", """ rows """), ...)`. Each row is one line of letters, all rows the same length,
 *   at most 24 x 24. The animal faces RIGHT. Exactly one cell is written in UPPER case: the pin (same colour as its lower
 *   case). The pin is the point the key table places and turns the sprite around (feet for standing, a hand for hanging).
 * - Built-in sprites `sit`, `stand`, `lie`: the pet as its other actions draw it, pin at its feet. Home is `10 19`:
 *   the rig sprites are pinned at cell (10, 19) of the 20 x 20 grid, so a key at `10 19` puts an own sprite's pin exactly
 *   where the rig sprite's feet stand. Own sprites must share that origin (their pin on the feet) for a clean hand-over.
 * - keys: a text table, one key per line, six columns:  `t  sprite  x  y  turn  ease`
 *   t: 0 to 1 over the action, rising; the first key is at 0, the last at 1.
 *   x y: the grid cell where the pin goes (see WHERE THINGS ARE). May lie outside 0..19 and may have decimals.
 *   turn: degrees, clockwise, around the pin. ease: how the move from THIS key to the NEXT one runs:
 *   `line` even, `smooth` slow at both ends, `in` starts slow, `out` ends slow, `hold` stays, then jumps at the next key.
 *   A key's sprite shows from its time until the next key. x, y and turn blend; sprites do not, they switch.
 * - The path of the action (where the whole grid is, for example off the screen edge) comes from `RoamPlan.pointAt`;
 *   your table only adds the pose on top of it. Facing left is drawn as a mirror: never draw a left-facing sprite.
 *
 * WHERE THINGS ARE (grid cells; x right, y DOWN; the numbers are for a 1080 x 2400 screen at 420 dpi, where one cell is 3 px):
 * - One cell is floor(0.95 * pill height / 20) px, so the pill is about 26 cells high. The grid's top-left cell (0, 0) sits
 *   about 4 cells below the pill's top: the pill's top edge is about row -4, its lower edge about row 22. The feet stand
 *   on row 19. Column 10 is the home column; the grid lies beside the camera hole, not under it.
 * - Facing left draws the table mirrored (x becomes 19 - x), so write every key for the pet facing right.
 * - Cells outside 0..19 are drawn. In the other states the pill's shape clips the pet; during Roam nothing clips it, so
 *   cells below the pill (y above 22), above it and past its ends show on the wallpaper and the status bar.
 * - HANGING: the pin of a hanging sprite is its gripping hand (the single upper-case cell at the top of the sprite).
 *   Key it at y 20: the hand's top then sits 2 rows inside the pill's lower edge (row 22), i.e. the hand holds the edge,
 *   and the body hangs below it. The monkey uses `13 20`: 3 columns right of home. `turn` swings the whole sprite around
 *   the hand, so the pin stays still while the body swings. Strip frame 5 of `pill.sh strip roam` shows it.
 * - ROAM, what the engine does (`RoamPlan.pointAt`): it moves the whole grid and your table plays inside it.
 *   Monkey: the grid stays home until t 0.84 (`MONKEY_LET_GO`), so the keys before it are in screen cells; from 0.84 to 1
 *   the engine carries the grid in an arc to the far side of the hole, so the last keys must already be the upright body
 *   at `10 19` (the flip sprites go in 0.84 to 0.95). Bird: the engine flies the grid out past the nearer screen edge
 *   (t 0 to 0.4), across (0.4 to 0.5) and in from the other side (0.5 to 1), 0.4 pill heights up; the table only flaps
 *   near `10 19`. Cat: the engine keeps the grid home to 0.205, carries it along the floor and over the pill's lower edge to
 *   below the pill (0.205 to 0.375), creeps and pounces there (to 0.55), holds (to 0.835) and lifts it back in an arc (to 0.94); the
 *   table adds the pose and the hops, and the scene draws a laser dot with `PixelScene.beforeRoam`.
 *   Dog: the engine keeps it home to 0.45 (sniffing, digging), lets it
 *   fall 1.2 pill heights (0.45 to 0.6), rebounds (to 0.74) and lifts it back over the pill's lower edge (0.74 to 1); the table
 *   adds the pose.
 * - CHECK: `./pill.sh petstyle <animal>`, then `./pill.sh strip roam` (any state: `strip <state>`) makes one picture of 12
 *   frames of the action with the pill's outline, and the pet must be shown (the idle pill, portrait).
 *
 * EXAMPLE (a hop):
 * ```
 * val Hop = PixelAction(
 *     mapOf('b' to 0xFF9A5B2E.toInt(), 'k' to 0xFF1A1010.toInt()),
 *     listOf(PixelSprite("ball", """
 *         .bb.
 *         bbkb
 *         bBbb
 *     """)),
 *     """
 *     t     sprite  x   y   turn  ease
 *     0     sit     10  19  0     smooth
 *     0.2   ball    10  19  0     out
 *     0.5   ball    10  12  180   in
 *     0.8   ball    10  19  360   smooth
 *     1     sit     10  19  0     line
 *     """,
 * )
 * ```
 *
 * CHECKLIST: the first and the last key are the built-in sprite the action starts and ends in, at `10 19`, turn 0 (the
 * Roam starts from `sit` and ends in `stand`), so the hand-over does not jump. A key every 0.03 to 0.08 of t. Anticipation
 * before a big move (a dip the other way), a squash (wider, lower sprite) on every landing, follow-through (ears, tail,
 * wings trail the move by one key). Eyes: 'k' cells on a light face. Give dark parts a lighter rim cell so the pet shows
 * on a dark wallpaper, and keep a dark outline cell on light parts for a light one. Keep the size of the body the same in
 * every sprite (about 12 to 16 cells tall).
 *
 * MOTION RULES: read `pill/motion-rules.md` before you draw a frame (minimum changes, recipes, review loop).
 *
 * TYPICAL MISTAKES (the build or the unit test names the sprite, row and column): rows of different length; a letter not
 * in the palette; no pin or two pins; times not rising or not from 0 to 1; a sprite name in the table that is not in the
 * list; five or seven columns in a key line; y up instead of down; a sprite facing left; turn in radians.
 */
internal class PixelAction(
    palette: Map<Char, Int>,
    sprites: List<PixelSprite>,
    keys: String,
) {
    private val keys: List<Key>
    private val own: Map<String, Picture>

    init {
        palette.keys.forEach { require(it.isLowerCase()) { "palette letter '$it' must be lower case" } }
        own = sprites.associate { s -> s.name to s.picture(palette) }
        require(own.size == sprites.size) { "two sprites have the same name" }
        val names = own.keys + BUILT_IN.keys
        this.keys =
            keys
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("t ") }
                .mapIndexed { i, line ->
                    val c = line.split(Regex("\\s+"))
                    val at = "key ${i + 1} '$line'"
                    require(c.size == 6) { "$at: needs 6 columns (t sprite x y turn ease), found ${c.size}" }

                    fun num(k: Int) = requireNotNull(c[k].toFloatOrNull()) { "$at: '${c[k]}' is not a number" }
                    require(c[1] in names) { "$at: unknown sprite '${c[1]}' (known: ${names.joinToString()})" }
                    val ease = Ease.entries.firstOrNull { it.id == c[5] }
                    requireNotNull(ease) { "$at: unknown ease '${c[5]}' (use ${Ease.entries.joinToString { it.id }})" }
                    Key(num(0), c[1], num(2), num(3), num(4), ease)
                }
        require(this.keys.size >= 2) { "needs at least two keys" }
        require(this.keys.first().t == 0f) { "the first key must be at t 0" }
        require(this.keys.last().t == 1f) { "the last key must be at t 1" }
        this.keys.zipWithNext().forEachIndexed { i, (a, b) ->
            require(b.t > a.t) { "key ${i + 2}: time ${b.t} is not after ${a.t}" }
        }
    }

    /**
     * Draws the action at [t] (0 to 1) into the pet's 20 x 20 grid whose top-left corner is at ([ox], [oy]) with cells of
     * [cell] pixels, mirrored when [mirror]. [rig] gives the built-in sprites.
     */
    fun DrawScope.draw(
        t: Float,
        mirror: Boolean,
        ox: Float,
        oy: Float,
        cell: Float,
        rig: (PetPose) -> Raster,
    ) {
        val i = keys.indexOfLast { it.t <= t }.coerceAtLeast(0)
        val a = keys[i]
        val b = keys.getOrElse(i + 1) { a }
        val u = if (b === a) 0f else a.ease.at((t - a.t) / (b.t - a.t))
        val pic = own[a.sprite] ?: BUILT_IN.getValue(a.sprite).let { Picture.of(rig(it)) }
        val px = lerp(a.x, b.x, u) + 0.5f
        val py = lerp(a.y, b.y, u) + 0.5f
        val turn = lerp(a.turn, b.turn, u) * PI.toFloat() / 180f
        val cs = cos(turn)
        val sn = sin(turn)
        val reach = hypot(pic.w.toFloat(), pic.h.toFloat()) + 1f

        fun x(i: Int) = (ox + i * cell).roundToInt().toFloat()

        fun y(j: Int) = (oy + j * cell).roundToInt().toFloat()
        for (j in floor(py - reach).toInt()..ceil(py + reach).toInt()) {
            for (i in floor(px - reach).toInt()..ceil(px + reach).toInt()) {
                // The cell's centre, turned back into the sprite around the pin.
                val dx = i + 0.5f - px
                val dy = j + 0.5f - py
                val sx = floor(pic.pinX + 0.5f + dx * cs + dy * sn).toInt()
                val sy = floor(pic.pinY + 0.5f - dx * sn + dy * cs).toInt()
                if (sx !in 0 until pic.w || sy !in 0 until pic.h) continue
                val col = pic.c[sy * pic.w + sx]
                if (col == 0) continue
                val gx = if (mirror) GRID - 1 - i else i
                drawRect(Color(col), Offset(x(gx), y(j)), Size(x(gx + 1) - x(gx), y(j + 1) - y(j)))
            }
        }
    }

    private class Key(
        val t: Float,
        val sprite: String,
        val x: Float,
        val y: Float,
        val turn: Float,
        val ease: Ease,
    )
}

/** One letter-grid sprite of a [PixelAction]: [rows] as written (indent is trimmed), the animal facing right. */
internal class PixelSprite(
    val name: String,
    rows: String,
) {
    private val rows = rows.trimIndent().lines().filter { it.isNotBlank() }

    internal fun picture(palette: Map<Char, Int>): Picture {
        val at = "sprite '$name'"
        require(rows.isNotEmpty()) { "$at has no rows" }
        val w = rows.first().length
        require(rows.size <= 24 && w <= 24) { "$at is $w x ${rows.size} cells, at most 24 x 24" }
        val pins = mutableListOf<Pair<Int, Int>>()
        val c = IntArray(w * rows.size)
        rows.forEachIndexed { j, row ->
            require(row.length == w) { "$at row ${j + 1} has ${row.length} cells, row 1 has $w" }
            row.forEachIndexed { i, ch ->
                if (ch.isUpperCase()) pins += i to j
                if (ch != '.') {
                    val col = palette[ch.lowercaseChar()]
                    requireNotNull(col) { "$at row ${j + 1} column ${i + 1}: letter '$ch' is not in the palette" }
                    c[j * w + i] = col
                }
            }
        }
        require(pins.size == 1) { "$at needs exactly one UPPER-case pin cell, found ${pins.size}" }
        return Picture(w, rows.size, c, pins[0].first, pins[0].second)
    }
}

/** A sprite's cells (0 empty) with its pin cell. */
internal class Picture(
    val w: Int,
    val h: Int,
    val c: IntArray,
    val pinX: Int,
    val pinY: Int,
) {
    companion object {
        /** A rig drawing as a sprite pinned at its feet (cell 10, 19). */
        fun of(r: Raster) = Picture(GRID, GRID, r.c, 10, 19)
    }
}

private val BUILT_IN = mapOf("sit" to PetPose.Sit, "stand" to PetPose.Stand, "lie" to PetPose.Lie)

private enum class Ease(
    val id: String,
) {
    Line("line"),
    Smooth("smooth"),
    In("in"),
    Out("out"),
    Hold("hold"),
    ;

    fun at(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return when (this) {
            Line -> c
            Smooth -> ease(c)
            In -> c * c
            Out -> 1f - (1f - c) * (1f - c)
            Hold -> 0f
        }
    }
}
