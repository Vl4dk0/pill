package sk.vladko.pill.idle

import kotlin.math.abs

// The cat: a grey tabby with a white muzzle, chest and paws, pink nose and ears and green eyes. It is a paper doll:
// hand-drawn parts (head, ears, eyes, body, legs, tail) are laid over the 20 x 20 grid by the recipes of SHOTS_TEXT, one
// picture per frame, so every pose and every step of a movement is a drawn picture and no shape is ever stretched.

internal val CAT_PALETTE =
    mapOf(
        'o' to 0xFF8C96AB.toInt(),
        'l' to 0xFFB9C2D4.toInt(),
        'd' to 0xFF5F687D.toInt(),
        's' to 0xFF40475B.toInt(),
        'k' to 0xFF1B1E2A.toInt(),
        'c' to 0xFFF2EFF5.toInt(),
        'h' to 0xFFC8C5D6.toInt(),
        'p' to 0xFFFF8FA8.toInt(),
        'e' to 0xFFB6E84A.toInt(),
        'w' to 0xFFFFFFFF.toInt(),
        'm' to 0xFF7A2E45.toInt(),
        'y' to 0xFFFFC93C.toInt(),
    )

/** The far side of the body: each part's colour one step darker. */
private val FAR = mapOf('o' to 'd', 'l' to 'o', 'c' to 'h', 'd' to 's')

/** One hand-drawn part: rows of letters of [CAT_PALETTE], '.' empty, facing right. */
private class CatArt(
    lines: List<String>,
) {
    val w = lines.maxOf { it.length }
    val rows = lines.map { it.padEnd(w, '.') }
}

private val PARTS: Map<String, CatArt> =
    buildMap {
        var name: String? = null
        var rows = mutableListOf<String>()

        fun close() {
            name?.let { put(it, CatArt(rows)) }
            rows = mutableListOf()
        }
        PARTS_TEXT.lines().forEach {
            when {
                it.startsWith("## ") -> {
                    close()
                    name = it.drop(3).trim()
                }
                it.isNotBlank() -> rows += it.trim()
            }
        }
        close()
    }

/** A part laid at ([x], [y]); [far] draws it one shade darker, [flip] mirrors it. */
private class CatPlaced(
    val part: String,
    val x: Int,
    val y: Int,
    val far: Boolean,
    val flip: Boolean,
)

/** A picture as a list of parts, bottom layer first, and where the head is ([hx], [hy]) for the marks that leave it. */
private class CatRecipe(
    val hx: Float,
    val hy: Float,
    val parts: List<CatPlaced>,
)

/** The recipes of SHOTS_TEXT: "## name hx hy", then one part per line ("part x y [far] [flip]") or "@name [dx dy]" to lay a recipe above. */
private val RECIPES: Map<String, CatRecipe> =
    buildMap {
        var name = ""
        var hx = 0f
        var hy = 0f
        var list = mutableListOf<CatPlaced>()

        fun close() {
            if (name.isNotEmpty()) put(name, CatRecipe(hx, hy, list))
            list = mutableListOf()
        }
        SHOTS_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val f = line.split(' ')
            when {
                line.startsWith("## ") -> {
                    close()
                    name = f[1]
                    hx = f[2].toFloat()
                    hy = f[3].toFloat()
                }
                line.startsWith("@") -> {
                    val dx = f.getOrNull(1)?.toInt() ?: 0
                    val dy = f.getOrNull(2)?.toInt() ?: 0
                    getValue(f[0].drop(1)).parts.mapTo(list) { CatPlaced(it.part, it.x + dx, it.y + dy, it.far, it.flip) }
                }
                else -> list += CatPlaced(f[0], f[1].toInt(), f[2].toInt(), "far" in f.drop(3), "flip" in f.drop(3))
            }
        }
        close()
    }

/**
 * One picture of the cat: the letters of [CAT_PALETTE] over the grid, its head at ([hx], [hy]), the whole grid moved [dx] cells
 * ahead and [lift] cells up.
 */
internal class CatShot(
    val c: CharArray,
    val hx: Float,
    val hy: Float,
    val dx: Float,
    val lift: Float,
) {
    /** The picture in colours. */
    fun raster() = Raster().also { r -> c.forEachIndexed { i, ch -> if (ch != '.') r.c[i] = CAT_PALETTE.getValue(ch) } }

    /** The lowest cell nearest the home column: where the pin of a [PixelAction] sprite goes. */
    fun pin(): Pair<Int, Int> {
        val y = (GRID - 1 downTo 0).first { j -> (0 until GRID).any { c[j * GRID + it] != '.' } }
        val x = (0 until GRID).filter { c[y * GRID + it] != '.' }.minBy { abs(it - 10) }
        return x to y
    }

    /** The picture as a sprite named [name] for a [PixelAction], pinned at [pin]. */
    fun sprite(name: String): PixelSprite {
        val (px, py) = pin()
        val rows =
            (0 until GRID).joinToString("\n") { j ->
                (0 until GRID).joinToString("") { i ->
                    val ch = c[j * GRID + i]
                    if (i == px && j == py) ch.uppercaseChar().toString() else ch.toString()
                }
            }
        return PixelSprite(name, rows)
    }

    /** The same picture with its marks [dx] more cells ahead and [lift] more cells up. */
    fun moved(
        dx: Float,
        lift: Float,
    ) = CatShot(c, hx, hy, this.dx + dx, this.lift + lift)
}

/** The picture [name], moved [dx] cells ahead and [lift] cells up. */
internal fun catShot(
    name: String,
    dx: Float = 0f,
    lift: Float = 0f,
): CatShot {
    val r = requireNotNull(RECIPES[name]) { "no cat picture '$name'" }
    val c = CharArray(GRID * GRID) { '.' }
    for (p in r.parts) {
        val a = PARTS.getValue(p.part)
        a.rows.forEachIndexed { j, row ->
            for (i in 0 until a.w) {
                var ch = row[if (p.flip) a.w - 1 - i else i]
                if (ch == '.') continue
                if (p.far) ch = FAR[ch] ?: ch
                val gx = p.x + i
                val gy = p.y + j
                if (gx in 0 until GRID && gy in 0 until GRID) c[gy * GRID + gx] = ch
            }
        }
    }
    return CatShot(c, r.hx, r.hy, dx, lift)
}

/** Hand-drawn parts: "## name" then rows of letters. */
private const val PARTS_TEXT = """
## head
..lloood..
.lsosoooo.
lloooooood
looooooood
looooooood
.ooocpcco.
..odcccd..
...dddd...

## head_front
..lloood..
.lsosoooo.
lloooooood
looooooood
looooooood
.oocppcoo.
..odcccd..
...dddd...

## ear_l
o...
opo.
oooo

## ear_r
...o
.opo
oooo

## ear_l_up
o...
oo..
opoo
oooo

## ear_r_up
...o
..oo
oopo
oooo

## ear_l_flat
ooo.
oooo

## ear_r_flat
.ooo
oooo

## eyes
ek..ek
ek..ek

## eyes_l
ke..ke
ke..ke

## eyes_blink
oo..oo
kk..kk

## eyes_half
kk..kk
ek..ek

## eyes_wide
wk..wk
kk..kk

## eyes_up
kk..kk
ee..ee

## eyes_happy
.k....k.
k.k..k.k

## eyes_front
eke..eke
eke..eke

## eyes_front_wide
wkk..kkw
kkk..kkk

## eyes_front_blink
ooo..ooo
kkk..kkk

## body_sit
.......lld...
.....llloold.
.....loooood.
....lloooood.
..llloosooood
llloosososod.
loososooosod.
loosoooooood.
doooooooood..
.ddooooodd...
...ddddd.....

## leg_sit
oc
oc
oc
oc
cc

## tail_sit
.......s
.......o
oooooooo
.dddddd.

## tail_sit_b
.......s
oooooooo
.dddddd.

## body_stand
..lllllllo..
.llsoosoosod
dlosoosooso.
.dooooooood.
..ddddddddd.

## leg
oo
oo
oo
cc

## leg_f
oo.
oo.
.oo
.cc

## leg_b
.oo
.oo
oo.
cc.

## leg_u
oo.
.oo
.cc

## leg_s
oo
cc

## tail_up
.oos
.oo.
oo..
oo..
oo..
.oo.
.ooo

## tail_up_b
..os
.oo.
.oo.
oo..
oo..
.oo.
.ooo

## body_ball
....llllld....
..lllooooold..
.lloooosooood.
lloosoosoosood
dooosooooosood
.dooooooooood.
..ddoooooodd..
....dddddd....

## body_ball_b
....llllld....
..llloooooold.
.llooooooooood
lloooooooooood
dooooooooooood
.dooooooooood.
..ddoooooodd..
....dddddd....

## tail_wrap
.ooooooooooooooo
dddddddddddddddd

## head_yawn
..lloood..
.lsosoooo.
lloooooood
looooooood
looooooood
.oocmmmco.
..odmpmd..
...dddd...

## body_crouch
....llllld....
.llslosooslld.
llosoosoosoood
ddoooooooooodd
..dddddddddd..

## body_bow
.llld.........
dlooolld......
.loooooold....
.ddoooooood...
...dooooooold.
....ddooooood.
......dddooood
.........dddd.

## body_arch
...l.ll.l...
..lloooool..
.llooooooool
lloooooooooo
looooooooooo
looooooooooo
dddddddddddd

## tail_puff
.ll.
llol
oooo
oooo
oooo
.oo.
.ooo

## arm_fwd
ooooc
ooccc

## arm_lick
...cc
..dcc
.dod.
dod..
do...

## arm_up
.c
oo
oo
o.
o.

## scr_a
..cc
.dcc
.do.
dod.
do..

## scr_b
.cc.
dcc.
od..
do..
d...

## paw_k
ooo.
occcc

## tongue
p

## qmark
.yy.
y..y
..y.
.y..
....
.y..

## body_lie
.....llllld.....
.llllsoosoosllld
llooosoosoosoood
dooooooooooooood
.ddoooooooooodd.
...dddddddddd...

## leg_t
oo
oo
oo
oo
oo
oo
oo
cc
"""

/** The pictures: see [RECIPES]. */
private const val SHOTS_TEXT = """
## sit 14 8
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 4
ear_l 9 2
ear_r 15 2
eyes 11 7

## sit_blink 14 8
@sit
eyes_blink 11 7

## sit_half 14 8
@sit
eyes_half 11 7

## sit_front 14 8
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head_front 9 4
ear_l 9 2
ear_r 15 2
eyes_front 10 7

## sit_front_blink 14 8
@sit_front
eyes_front_blink 10 7

## sit_up 14 7
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 3
ear_l_up 9 0
ear_r_up 15 0
eyes 11 6

## sit_back 8 11
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 3 6 flip
ear_r 3 4 flip
ear_l 9 4 flip
eyes_l 5 9

## top_a 15 11
tail_up 0 5
body_stand 1 11
head 10 7
ear_l 10 4
ear_r 16 4
eyes 12 10

## top_b 15 11
tail_up_b 0 5
body_stand 1 11
head 10 7
ear_l 10 4
ear_r 16 4
eyes 12 10

## stand 15 11
leg 5 16 far
leg 8 16 far
leg 3 16
leg 10 16
@top_a

## walk0a 15 12
leg_f 8 16 far
leg_b 4 16 far
leg_b 2 16
leg_f 10 16
@top_a 0 1

## walk0b 15 12
leg_f 8 16 far
leg_b 4 16 far
leg_b 2 16
leg_f 10 16
@top_b 0 1

## walk1a 15 11
leg 8 16 far
leg_u 4 16 far
leg_u 3 16
leg 10 16
@top_a

## walk1b 15 11
leg 8 16 far
leg_u 4 16 far
leg_u 3 16
leg 10 16
@top_b

## walk2a 15 12
leg_b 8 16 far
leg_f 4 16 far
leg_f 3 16
leg_b 10 16
@top_a 0 1

## walk2b 15 12
leg_b 8 16 far
leg_f 4 16 far
leg_f 3 16
leg_b 10 16
@top_b 0 1

## walk3a 15 11
leg_u 8 16 far
leg 5 16 far
leg 3 16
leg_u 10 16
@top_a

## walk3b 15 11
leg_u 8 16 far
leg 5 16 far
leg 3 16
leg_u 10 16
@top_b

## lie 14 13
body_ball 2 12
head 9 9
ear_l 9 7
ear_r 15 7
eyes_blink 11 12
tail_wrap 2 18

## lie_b 14 13
body_ball_b 2 11
head 9 9
ear_l 9 7
ear_r 15 7
eyes_blink 11 12
tail_wrap 2 18

## heart_a 14 8
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 4
ear_l_up 9 1
ear_r_up 15 1
eyes_happy 10 7

## heart_b 14 8
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 4
ear_l_up 9 1
ear_r_up 15 1
eyes_happy 10 7

## stand_front 15 11
@stand
head_front 10 7
ear_l 10 4
ear_r 16 4
eyes_front 11 10

## crouch 14 11
tail_sit_b 0 16
leg_s 9 18 far
leg_s 2 18 far
leg_s 11 18
leg_s 4 18
body_crouch 1 14
head 9 8
ear_l 9 6
ear_r 15 6
eyes 11 11

## crouch_wide 14 11
@crouch
ear_l_up 9 5
ear_r_up 15 5
eyes_wide 11 11

## crouch_blink 14 11
@crouch
eyes_blink 11 11

## crouch_half 14 11
@crouch
eyes_half 11 11

## rise 15 13
leg_s 5 18 far
leg_s 8 18 far
leg_s 3 18
leg_s 10 18
@top_a 0 2

## bow 15 15
tail_up 0 2
leg_t 3 12 far
leg_t 5 12
body_bow 1 9
arm_fwd 11 18 far
arm_fwd 13 18
head 10 11
ear_l 10 8
ear_r 16 8
eyes_blink 12 14

## bow_yawn 15 15
@bow
head_yawn 10 11
eyes_blink 12 14

## arch 15 13
tail_puff 0 4
leg 3 16 far
leg 5 16
leg 8 16 far
leg 10 16
body_arch 1 10
head 10 9
ear_l_flat 10 9
ear_r_flat 16 9
eyes_wide 12 12

## lick_a 14 8
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
head 9 4
ear_l 9 2
ear_r 15 2
eyes_blink 11 7
arm_lick 11 12
tongue 14 11

## lick_b 14 9
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
head 9 5
ear_l 9 3
ear_r 15 3
eyes_blink 11 8
leg_sit 11 15
arm_lick 11 13
tongue 14 12

## scratch_a 13 9
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 8 5
ear_l 8 3
ear_r 14 3
eyes_blink 10 8
scr_a 5 7

## scratch_b 13 9
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 8 5
ear_l_flat 8 4
ear_r 14 3
eyes_blink 10 8
scr_b 5 8

## knead_a 14 11
body_lie 1 14
head 9 8
ear_l 9 6
ear_r 15 6
eyes_half 11 11
tail_wrap 1 18
paw_k 13 17

## knead_b 14 12
body_lie 1 14
head 9 9
ear_l 9 7
ear_r 15 7
eyes_half 11 12
tail_wrap 1 18
paw_k 16 17


## top_ha 15 10
tail_up 0 5
body_stand 1 11
head 10 7
ear_l_up 10 3
ear_r_up 16 3
eyes_happy 11 10

## top_hb 15 10
tail_up_b 0 5
body_stand 1 11
head 10 7
ear_l_up 10 3
ear_r_up 16 3
eyes_happy 11 10

## up 15 11
leg_f 8 16 far
leg_b 4 16 far
leg_b 2 16
leg_f 10 16
@top_b

## up_h 15 10
leg_f 8 16 far
leg_b 4 16 far
leg_b 2 16
leg_f 10 16
@top_hb

## tuck 15 11
leg_u 8 15 far
leg_u 5 15 far
leg_u 3 15
leg_u 10 15
@top_a

## tuck_h 15 10
leg_u 8 15 far
leg_u 5 15 far
leg_u 3 15
leg_u 10 15
@top_ha

## down 15 11
leg_f 8 16 far
leg 5 16 far
leg 3 16
leg_f 10 16
@top_b

## down_h 15 10
leg_f 8 16 far
leg 5 16 far
leg 3 16
leg_f 10 16
@top_hb

## stand_front_h 15 10
leg 5 16 far
leg 8 16 far
leg 3 16
leg 10 16
tail_up 0 5
body_stand 1 11
head_front 10 7
ear_l_up 10 3
ear_r_up 16 3
eyes_happy 11 10

## dance_a 15 10
leg 5 16 far
leg 8 16 far
leg 3 16
@top_ha
arm_fwd 11 14

## dance_b 15 10
leg 5 16 far
leg 3 16
leg 10 16
@top_hb
arm_fwd 9 14 far

## stand_down 15 12
leg 5 16 far
leg 8 16 far
leg 3 16
leg 10 16
tail_up_b 0 5
body_stand 1 11
head 10 8
ear_l 10 5
ear_r 16 5
eyes_blink 12 11

## top_run 15 12
tail_up_b 0 6
body_stand 1 12
head 10 8
ear_l 10 5
ear_r 16 5
eyes 12 11

## run0 15 12
leg_f 8 16 far
leg_b 4 16 far
leg_b 2 16
leg_f 10 16
@top_run

## run1 15 12
leg_u 8 16 far
leg_u 4 16 far
leg_u 3 16
leg_u 10 16
@top_run 0 -1

## run2 15 12
leg_b 8 16 far
leg_f 4 16 far
leg_f 3 16
leg_b 10 16
@top_run

## run3 15 12
leg 8 16 far
leg 5 16 far
leg 3 16
leg 10 16
@top_run 0 -1

## sit_droop 14 11
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 7
ear_l_flat 9 7
ear_r_flat 15 7
eyes_half 11 10

## sit_yawn 14 8
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head_yawn 9 4
ear_l_up 9 1
ear_r_up 15 1
eyes_happy 10 7

## lie_half 14 13
@lie
eyes_half 11 12

## alert 14 7
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 3
ear_l_up 9 0
ear_r_up 15 0
eyes_wide 11 6

## wig_a 14 11
tail_sit_b 0 16
leg_s 9 18 far
leg_s 2 18 far
leg_s 11 18
leg_s 4 18
body_crouch 0 14
head 9 8
ear_l_up 9 5
ear_r_up 15 5
eyes_wide 11 11

## wig_b 14 11
tail_sit_b 0 16
leg_s 9 18 far
leg_s 2 18 far
leg_s 11 18
leg_s 4 18
body_crouch 2 14
head 9 8
ear_l_up 9 5
ear_r_up 15 5
eyes_wide 11 11

## hit 15 11
tail_sit_b 0 16
leg_s 2 18 far
leg_s 4 18
body_crouch 1 14
arm_fwd 13 18 far
arm_fwd 14 18
head 9 8
ear_l_up 9 5
ear_r_up 15 5
eyes_wide 11 11

## sit_down 14 9
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 9 5
ear_l 9 3
ear_r 15 3
eyes_blink 11 8

## puz_a 14 8
@sit_front
qmark 13 -3

## puz_b 8 11
@sit_back
qmark 4 -1

## proud 15 10
leg 5 16 far
leg 8 16 far
leg 3 16
leg 10 16
tail_up 0 3
body_stand 1 11
head 10 6
ear_l_up 10 2
ear_r_up 16 2
eyes_happy 11 9

## lie_front 14 13
body_ball 2 12
head_front 9 9
ear_l 9 7
ear_r 15 7
eyes_front_blink 10 12
tail_wrap 2 18

## fid_a 13 8
tail_sit_b 0 18
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 8 5
ear_l_flat 8 5
ear_r_flat 14 5
eyes_wide 10 8

## fid_b 15 8
tail_sit 0 17
body_sit 1 9
leg_sit 9 15 far
leg_sit 11 15
head 10 4
ear_l_up 10 1
ear_r_up 16 1
eyes_blink 12 7

## dance_c 15 10
leg 5 16 far
leg 8 16 far
leg 3 16
leg 10 16
@top_hb

## peek_a 14 9
tail_sit_b 0 16
leg_s 9 18 far
leg_s 2 18 far
leg_s 11 18
leg_s 4 18
body_crouch 1 14
head 10 7
ear_l_up 10 4
ear_r_up 16 4
eyes_wide 12 10

## peek_front 14 9
tail_sit_b 0 16
leg_s 9 18 far
leg_s 2 18 far
leg_s 11 18
leg_s 4 18
body_crouch 1 14
head_front 10 7
ear_l 10 5
ear_r 16 5
eyes_front 11 10

## peek_back 14 9
tail_sit_b 0 16
leg_s 9 18 far
leg_s 2 18 far
leg_s 11 18
leg_s 4 18
body_crouch 1 14
head 7 7 flip
ear_r 7 5 flip
ear_l 13 5 flip
eyes_l 9 10

## leap 15 11
leg_b 4 16 far
leg_b 2 16
@top_run 0 -1
arm_fwd 8 14 far
arm_fwd 11 14
"""
