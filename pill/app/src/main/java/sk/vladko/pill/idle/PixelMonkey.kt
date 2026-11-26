package sk.vladko.pill.idle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import sk.vladko.pill.rules.PET_BEAT_MS
import sk.vladko.pill.rules.PET_BLEND_MS
import sk.vladko.pill.rules.PetMark
import sk.vladko.pill.rules.PetPose
import sk.vladko.pill.rules.PetState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Hand-crafted pixel-art monkey: warm chestnut-cocoa fur with honey-amber top-left rim (visible on the black pill)
// and deep espresso-plum shadows (crisp on light wallpaper), cream-peach widow's-peak face patch, 2x2 catch-light eyes,
// long arms with mitten hands, and a curled tail.

internal val MONKEY_PALETTE: Map<Char, Int> =
    mapOf(
        'o' to 0xFF23141B.toInt(),
        'd' to 0xFF562B24.toInt(),
        'f' to 0xFF8C4A27.toInt(),
        'l' to 0xFFC97B3E.toInt(),
        'r' to 0xFFE8A45C.toInt(),
        's' to 0xFFD9956A.toInt(),
        'p' to 0xFFF5C99B.toInt(),
        'h' to 0xFFFFE5C2.toInt(),
        'k' to 0xFF1A1016.toInt(),
        'w' to 0xFFFFFAF0.toInt(),
        'c' to 0xFFFF7E93.toInt(),
        'm' to 0xFF9E2B3E.toInt(),
        'y' to 0xFFFFD83B.toInt(),
        'g' to 0xFFD49617.toInt(),
        'n' to 0xFFFFF5CC.toInt(),
        't' to 0xFF4A3118.toInt(),
    )

private val MARK_HEART = listOf(".RR.RR.", "RrRRRRR", "RRRRRRR", ".RRRRR.", "..RRR..", "...R...")
private val MARK_HEART_SM = listOf("R.R", "RRR", ".R.")
private val MARK_Z_BIG = listOf("WWWW", "..W.", ".W..", "WWWW")
private val MARK_Z_SM = listOf("WWW", ".W.", "WWW")
private val MARK_DROP = listOf(".B", "BB", "BB")
private val MARK_BOLT = listOf("..YY", ".YY.", "YYYY", ".YY.", "YY..")
private val MARK_SPARK = listOf(".Y.", "YWY", ".Y.")
private val MARK_BANG = listOf("Y", "Y", "Y", ".", "Y")
private val MARK_BATTERY = listOf("WWWWW.", "WR..WW", "WWWWW.")

private fun monkeyMarkColor(ch: Char): Int =
    when (ch) {
        'R' -> 0xFFFF4D6D.toInt()
        'r' -> 0xFFFFB3C1.toInt()
        'W' -> 0xFFE6F0FF.toInt()
        'B' -> 0xFF7FD0FF.toInt()
        'Y' -> 0xFFFFD43B.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

private fun pic(
    name: String,
    rows: String,
): Picture = PixelSprite(name, rows).picture(MONKEY_PALETTE)

/** Converts a 20x20 [Picture] to a [Raster] for [PixelAction] built-in poses (`sit`, `stand`, `lie`). */
private fun Picture.toRaster(): Raster =
    Raster().also { r ->
        for (j in 0 until min(h, GRID)) {
            for (i in 0 until min(w, GRID)) {
                r.c[j * GRID + i] = c[j * w + i]
            }
        }
    }

// Core monkey sprites (20x20, facing right, pin at feet col 10 row 19).
internal object MonkeySprites {
    val sit =
        pic(
            "sit",
            """
            ....................
            ....................
            ....................
            ........rrrr........
            .......rllllff......
            .....rlffhhfhpfo....
            ....rlfsfhpppppfro..
            ....rlfspwkpwkpfdo..
            ....odfspkkpkkpfdo..
            .....odfcpphppcdo...
            ......odfppsppdo....
            ..rrr..ooffffddo....
            .rl.rf.rlffhhfflo...
            .rf..o.rlfhppfdflo..
            ..fd...rlfpppfdofl..
            ...fd..rlfppsfdpph..
            ....fd.dffssffdpps..
            .....fodfffddfffoo..
            ......odddf..oddf...
            .......oppPs.opps...
            """,
        )

    val sitBlink =
        pic(
            "sit_blink",
            """
            ....................
            ....................
            ....................
            ........rrrr........
            .......rllllff......
            .....rlffhhfhpfo....
            ....rlfsfhpppppfro..
            ....rlfspppppppfdo..
            ....odfspkkpkkpfdo..
            .....odfcpphppcdo...
            ......odfppsppdo....
            ..rrr..ooffffddo....
            .rl.rf.rlffhhfflo...
            .rf..o.rlfhppfdflo..
            ..fd...rlfpppfdofl..
            ...fd..rlfppsfdpph..
            ....fd.dffssffdpps..
            .....fodfffddfffoo..
            ......odddf..oddf...
            .......oppPs.opps...
            """,
        )

    val sitLow =
        pic(
            "sit_low",
            """
            ....................
            ....................
            ....................
            ........rrrr........
            .......rllllff......
            .....rlffhhfhpfo....
            ....rlfsfhpppppfro..
            ....rlfspsspsspfdo..
            ....odfspkkpkkpfdo..
            .....odfcpphppcdo...
            ......odfppsppdo....
            ..rrr..ooffffddo....
            .rl.rf.rlffhhfflo...
            .rf..o.rlfhppfdflo..
            ..fd...rlfpppfdofl..
            ...fd..rlfppsfdpph..
            ....fd.dffssffdpps..
            .....fodfffddfffoo..
            ......odddf..oddf...
            .......oppPs.opps...
            """,
        )

    val sitFront =
        pic(
            "sit_front",
            """
            ....................
            ....................
            ....................
            .......rrrrrr.......
            ......rllllfff......
            .....rlfhhfhffo.....
            ...rlfsfhppppfsfo...
            ...rlfspwkpwkpsfo...
            ...odfspkkpkkpsdo...
            ....odfcpphppcd.....
            .....odfppsppdo.....
            ..rr..ooffffddo.....
            .rlrf.rlfhhhffdo....
            .rf.o.rlfpppffdo....
            ..fd.rlffpppffld....
            ...fdlffpppsffld....
            ....fdppfssffppd....
            .....fppsfffsppo....
            ......oddf.oddf.....
            ......opPs.opps.....
            """,
        )

    val stand =
        pic(
            "stand",
            """
            ....................
            ....................
            ....................
            ....................
            ...........rrrr.....
            ..........rllllff...
            ........rlffhhfhpf..
            ..rrr..rlfsfhpppppf.
            .rl.rf.rlfspwkpwkpf.
            .rf..o.odfspkkpkkpf.
            ..fd....odfcpphppcd.
            ...fd.rrrodfppsppdo.
            ....fdrllfffffdddo..
            .....fllffhhffflfo..
            .....offffppffdofl..
            ......offfppffdofl..
            ......dffd..dddofl..
            ......dff....dd.fl..
            ......off....dd.pp..
            ......opps..Dds.pps.
            """,
        )

    val standLow =
        pic(
            "stand_low",
            """
            ....................
            ....................
            ....................
            ....................
            ...........rrrr.....
            ..........rllllff...
            ........rlffhhfhpf..
            ..rrr..rlfsfhpppppf.
            .rl.rf.rlfspsspsspf.
            .rf..o.odfspkkpkkpf.
            ..fd....odfcpphppcd.
            ...fd.rrrodfppsppdo.
            ....fdrllfffffdddo..
            .....fllffhhffflfo..
            .....offffppffdofl..
            ......offfppffdofl..
            ......dffd..dddofl..
            ......dff....dd.fl..
            ......off....dd.pp..
            ......opps..Dds.pps.
            """,
        )

    val standFront =
        pic(
            "stand_front",
            """
            ....................
            ....................
            ....................
            ....................
            .......rrrrrr.......
            ......rllllfff......
            .....rlfhhfhffo.....
            ...rlfsfhppppfsfo...
            ...rlfspwkpwkpsfo...
            ...odfspkkpkkpsdo...
            ....odfcpphppcd.....
            ..rr.odfppsppdo.....
            .rlrf.rlfhhhffdo....
            .rf.o.rlfpppfflo....
            ..fd..rlfpppfflo....
            ...fd.dffssffddo....
            ....fddff..dffo.....
            .....off....ffo.....
            .....opp....ppo.....
            .....opps..Opps.....
            """,
        )

    val transSitStand =
        pic(
            "trans_sit_stand",
            """
            ....................
            ....................
            ....................
            ....................
            .........rrrr.......
            ........rllllff.....
            ......rlffhhfhpfo...
            ..rrr.rlfsfhpppppf..
            .rl.rfrlfspwkpwkpf..
            .rf..oodfspkkpkkpf..
            ..fd...odfcpphppcd..
            ...fd..rodfppsppdo..
            ....fdrllfffffddo...
            .....fllffhhffflfo..
            .....offffppffdofl..
            ......dfffppffdofl..
            ......dfff..dddofl..
            ......oddf...dd.pp..
            .......ddf...dd.pps.
            .......oppP..dds....
            """,
        )

    val lie =
        pic(
            "lie",
            """
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ......rrrrr.........
            ....rrlllllff.......
            ...rlllffffffrrr....
            ..rllffffffffllff...
            ..rlffrrrffffhhfpf..
            ..rffrl.rlfshpppppf.
            ..dffrf..ofspkkpkkf.
            ..odf.fll.dfcpphppd.
            ...odddfffodfppsppph
            ....ooooooOoopppppps
            """,
        )

    val lieBreath =
        pic(
            "lie_breath",
            """
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            .....rrrrrrr........
            ...rrrllllllff......
            ..rrlllffffff.......
            ..rllffffffffrrr....
            ..rlffrrrffffllff...
            ..rlfrl.rlfffhhfpf..
            ..rffrf..ofshpppppf.
            ..dff.fll.fspkkpkkf.
            ..odf..ff.dfcpphppd.
            ...odddfffodfppsppph
            ....ooooooOoopppppps
            """,
        )

    val transLie =
        pic(
            "trans_lie",
            """
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            .........rrrr.......
            ........rllllff.....
            ......rlffhhfhpfo...
            .....rlfsfhpppppf...
            .....rlfspsspsspf...
            ..rr.odfspkkpkkpf...
            .rlrf.odfcpphppcd...
            .rf.orllffppsppdo...
            ..fdrlfffhhhffddo...
            ...fdlfffpppffllo...
            ....fdfffppsffppd...
            .....odfffffdpppo...
            ......odddffddppo...
            .......oooOoopps....
            """,
        )

    // 4-frame knuckle-walk gait: always keeps two diagonal limbs grounded on row 19 with a 1-cell vertical bob and tail sway.
    val walk =
        listOf(
            pic(
                "walk_1",
                """
                ....................
                ....................
                ....................
                ....................
                ...........rrrr.....
                ..........rllllff...
                ........rlffhhfhpf..
                .rrr...rlfsfhpppppf.
                rl.rf..rlfspwkpwkpf.
                rf..o..odfspkkpkkpf.
                .fd.....odfcpphppcd.
                ..fd..rrrodfppsppdo.
                ...fdrllffffffdddo..
                ....fllffhhfffflfo..
                ....offffppffdd.fl..
                .....offfppffd..fl..
                .....dff..ddd...fl..
                ....dff....dd....fl.
                ...off.....dd....pp.
                ...opps...Dds....pps
                """,
            ),
            pic(
                "walk_2",
                """
                ....................
                ....................
                ....................
                ...........rrrr.....
                ..........rllllff...
                ........rlffhhfhpf..
                ..rrr..rlfsfhpppppf.
                .rl.rf.rlfspwkpwkpf.
                .rf..o.odfspkkpkkpf.
                ..fd....odfcpphppcd.
                ...fd.rrrodfppsppdo.
                ....fdrllfffffdddo..
                .....fllffhhffflfo..
                .....offffppffdofl..
                ......offfppffddfl..
                ......dff.dd..dofl..
                .......dffdd...dfl..
                .......oppsdd...fl..
                ...........dd...pp..
                ..........Dds...pps.
                """,
            ),
            pic(
                "walk_3",
                """
                ....................
                ....................
                ....................
                ....................
                ...........rrrr.....
                ..........rllllff...
                ........rlffhhfhpf..
                ...rrr.rlfsfhpppppf.
                ..rl.rfrlfspwkpwkpf.
                ..rf..oodfspkkpkkpf.
                ...fd...odfcpphppcd.
                ....fdrllodfppsppdo.
                .....fllffffffdddo..
                .....offffhhffflfo..
                ......offfppffdofl..
                ......ddd.dff.ddfl..
                .....ddd...dff.dd.fl
                ....ddd....dff..ddpp
                ....dd.....off...dds
                ....dds....Opps.....
                """,
            ),
            pic(
                "walk_4",
                """
                ....................
                ....................
                ....................
                ...........rrrr.....
                ..........rllllff...
                ........rlffhhfhpf..
                ..rrr..rlfsfhpppppf.
                .rl.rf.rlfspwkpwkpf.
                .rf..o.odfspkkpkkpf.
                ..fd....odfcpphppcd.
                ...fd.rrrodfppsppdo.
                ....fdrllfffffdddo..
                .....fllffhhffflfo..
                .....offffppffdofl..
                ......offfppffd.fl..
                ......dffd..dd...pph
                ......dff...dd...pps
                ......off...dd......
                ......opp...dd......
                ......oppS..dds.....
                """,
            ),
        )

    // Hide: the startled crouch before the run to the hole.
    val hideCrouch =
        pic(
            "hide_crouch",
            """
            ....................
            ....................
            ....................
            ....................
            ....................
            ...........rrrr.....
            ..........rllllff...
            ..rrr...rlffhhfhpf..
            .rl.rf.rlfsfhpppppf.
            .rf..o.rlfswkpwkppf.
            ..fd...odfskkpkkppf.
            ...fd..odfcpphppcd..
            ....fdrllffpmmppfl..
            .....fllffppffddfl..
            .....odff...ddd.pph.
            .....opps...Dds.pps.
            """,
        )

    // Dance sprites (2-beat / 1000ms funky groove loop): wide clear arm punches + alternating foot kicks + tail whip!
    val danceDownR =
        pic(
            "dance_down_r",
            """
            ....................
            ....................
            ....................
            ...............pph..
            ........rrrr...pps..
            .......rllllff..fl..
            .....rlffhhfhpfffl..
            ....rlfsfhpppppfl...
            ....rlfspkkpkkpf....
            ....odfscppcppcd....
            .....odfpmmmmpd.....
            ..rr..odfppsppdo....
            .rlrf..ooffffddo....
            .rf.o.rlfhhhffdo....
            ..fd.pphfpppffdo....
            ...fdppsffssffdo....
            ....fodff..dff......
            .....off...off......
            .....oppP..opps.....
            """,
        )

    val danceUpR =
        pic(
            "dance_up_r",
            """
            ................pph.
            ................pps.
            ........rrrr....fl..
            .......rllllff..fl..
            .....rlffhhfhpfffl..
            ....rlfsfhpppppfl...
            ....rlfspkkpkkpf....
            ....odfscppcppcd....
            .....odfpmmmmpd.....
            .rrr..odfppsppdo....
            rl.rf..ooffffddo....
            rf..oflrfhhhffdo....
            .fd.pphlfpppffdo....
            ..fdppsdffssffdo....
            ...fd..dff...dff....
            .......off....opps..
            .......oppP.........
            """,
        )

    val danceDownL =
        pic(
            "dance_down_l",
            """
            ....................
            ....................
            ....................
            ..pph...............
            ..pps..rrrr.........
            ...fl.rllllff.......
            ...flrlffhhfhpf.....
            ....flfsfhpppppf....
            .....rlfspkkpkkpf...
            .....odfscppcppcd...
            ......odfpmmmmpd....
            .......odfppsppdo.rr
            .......ooffffddo.rlr
            ......rlfhhhffdo.rf.
            ......rlfpppfpph.fd.
            ......dffssffppsfd..
            .......dff..dffof...
            .......off...off....
            .......oppP..opps...
            """,
        )

    val danceUpL =
        pic(
            "dance_up_l",
            """
            .pph................
            .pps................
            ..fl...rrrr.........
            ..fl..rllllff.......
            ..fl.rlffhhfhpfo....
            ...flrlfsfhpppppf...
            .....rlfspkkpkkpf...
            .....odfscppcppcd...
            ......odfpmmmmpd....
            .......odfppsppdorrr
            .......ooffffddofr.l
            ......rlffhhfflo.o.f
            .....rlfpppfflpphfd.
            ......dffssffdppsfd.
            ......dff...dff.....
            .....opps....off....
            .............Opps...
            """,
        )

    val sitRaster: Raster = sit.toRaster()
    val standRaster: Raster = stand.toRaster()
    val lieRaster: Raster = lie.toRaster()

    fun rigRaster(pose: PetPose): Raster =
        when (pose) {
            PetPose.Sit -> sitRaster
            PetPose.Stand -> standRaster
            PetPose.Lie -> lieRaster
        }
}

/**
 * The pixel monkey scene: hand-crafted pixel-art sprites and [PixelAction] animations, sharing the exact sizing,
 * walkable width, camera-hole cutout and unit contract of [PixelScene].
 */
internal class PixelMonkeyScene(
    val w: Float,
    val h: Float,
    val hole: Rect?,
    fixedUnit: Float? = null,
) : PetScene {
    private val room = if (hole == null) w else max(hole.left, w - hole.right)

    private val pix =
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

    private val leftEnd =
        if (hole == null || hole.left - edge >= box) edge else min(hole.right + pix * 2f, w - edge - box)
    private val rightEnd =
        if (hole == null || w - edge - hole.right >= box) w - edge - box else max(hole.left - pix * 2f - box, leftEnd)
    private val span = max(0f, rightEnd - leftEnd)
    private val top = min((h - box) * 0.7f, h - box - h * 0.04f)
    private val holePath = Path().apply { hole?.let { addOval(it) } }

    override val canWalk = span >= 2 * pix
    override val holeX = if (hole == null || span <= 0f) 0.5f else ((hole.center.x - box / 2f - leftEnd) / span).coerceIn(0f, 1f)
    override val drawsRoam = true

    private fun left(x: Float) = leftEnd + span * x

    override fun middle(x: Float) = left(x) + box / 2f

    override fun DrawScope.draw(frame: PetFrame) {
        val step = frame.step

        // Roam excursion (drawn outside the pill without hole clipping).
        if (frame.out != null) {
            val from = step.roam?.hurryFrom ?: 0f
            val t = from + (1f - from) * frame.progress
            val face = if (frame.face == 0f) step.face.toFloat() else frame.face
            with(MonkeyRoam) {
                draw(t, face < 0f, left(frame.x), top, pix.toFloat(), MonkeySprites::rigRaster)
            }
            return
        }

        val cell = pix.toFloat()
        val ox = left(frame.x)
        val oy = top
        val mirror = if (frame.face == 0f) step.face < 0 else frame.face < 0f

        clipPath(holePath, ClipOp.Difference) {
            when (frame.state) {
                PetState.Sit -> drawSit(frame, ox, oy, cell, mirror)
                PetState.Sleep -> drawSleep(frame, ox, oy, cell, mirror)
                PetState.Walk -> drawWalk(frame, ox, oy, cell, mirror)
                PetState.Hide -> drawHide(frame, cell)
                PetState.Peek -> drawPeek(frame, ox, cell)
                PetState.Hidden -> drawHidden(frame, ox, cell)
                PetState.Dance -> drawDance(frame, ox, oy, cell, mirror)
                PetState.LookAround -> drawAction(PixelMonkeyActions.look, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Heart -> drawAction(PixelMonkeyActions.heart, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Stretch -> drawAction(PixelMonkeyActions.stretch, PetPose.Stand, frame, ox, oy, cell, mirror)
                PetState.Groom -> drawAction(PixelMonkeyActions.groom, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Fidget -> drawAction(PixelMonkeyActions.fidget, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Jump -> drawAction(PixelMonkeyActions.jump, PetPose.Stand, frame, ox, oy, cell, mirror)
                PetState.Scratch -> drawAction(PixelMonkeyActions.scratch, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Banana -> drawAction(PixelMonkeyActions.banana, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Excited -> drawAction(PixelMonkeyActions.excited, PetPose.Stand, frame, ox, oy, cell, mirror, snappy = true)
                PetState.Tired -> drawAction(PixelMonkeyActions.tired, PetPose.Sit, frame, ox, oy, cell, mirror)
                PetState.Startle -> drawAction(PixelMonkeyActions.startle, PetPose.Stand, frame, ox, oy, cell, mirror, snappy = true)
                else -> drawSit(frame, ox, oy, cell, mirror)
            }
            drawMarks(frame, ox, oy, cell, mirror)
        }
    }

    private fun DrawScope.drawAction(
        action: PixelAction,
        startPose: PetPose,
        frame: PetFrame,
        ox: Float,
        oy: Float,
        cell: Float,
        mirror: Boolean,
        snappy: Boolean = false,
    ) {
        val step = frame.step
        val t = frame.progress
        // Smooth pose hand-over when starting from a different base pose; snappy reactions skip it to react in 1 key.
        if (!snappy && step.poseFrom != startPose && t < 0.10f) {
            val sprite = transitionSprite(step.poseFrom, startPose, t / 0.10f)
            drawPicture(sprite, mirror, ox, oy, cell)
            return
        }
        with(action) {
            draw(t, mirror, ox, oy, cell, MonkeySprites::rigRaster)
        }
    }

    private fun DrawScope.drawSit(
        frame: PetFrame,
        ox: Float,
        oy: Float,
        cell: Float,
        mirror: Boolean,
    ) {
        val step = frame.step
        if (step.poseFrom != PetPose.Sit && frame.blend < 1f) {
            drawPicture(transitionSprite(step.poseFrom, PetPose.Sit, frame.blend), mirror, ox, oy, cell)
            return
        }
        if (step.faceFrom != step.face && abs(frame.face) < 0.35f) {
            drawPicture(MonkeySprites.sitFront, mirror, ox, oy, cell)
            return
        }
        val pic =
            when {
                frame.blink -> MonkeySprites.sitBlink
                frame.low -> MonkeySprites.sitLow
                else -> MonkeySprites.sit
            }
        drawPicture(pic, mirror, ox, oy, cell)
    }

    private fun DrawScope.drawSleep(
        frame: PetFrame,
        ox: Float,
        oy: Float,
        cell: Float,
        mirror: Boolean,
    ) {
        val step = frame.step
        if (step.poseFrom != PetPose.Lie && frame.blend < 1f) {
            drawPicture(transitionSprite(step.poseFrom, PetPose.Lie, frame.blend), mirror, ox, oy, cell)
            return
        }
        val z = frame.mark(PetMark.Z)
        val inhaling = z in 0.12f..0.55f
        drawPicture(if (inhaling) MonkeySprites.lieBreath else MonkeySprites.lie, mirror, ox, oy, cell)
    }

    private fun DrawScope.drawWalk(
        frame: PetFrame,
        ox: Float,
        oy: Float,
        cell: Float,
        mirror: Boolean,
    ) {
        val step = frame.step
        if (frame.ms < PET_BLEND_MS) {
            if (step.faceFrom != step.face && abs(frame.face) < 0.35f) {
                drawPicture(MonkeySprites.standFront, mirror, ox, oy, cell)
                return
            }
            if (step.poseFrom != PetPose.Stand) {
                drawPicture(transitionSprite(step.poseFrom, PetPose.Stand, frame.blend), mirror, ox, oy, cell)
                return
            }
            val lean = ease(frame.blend) * 6f
            drawPicture(if (frame.low) MonkeySprites.standLow else MonkeySprites.stand, mirror, ox, oy, cell, turnDeg = lean)
            return
        }
        val walked = abs(frame.x - step.fromX) * span / pix
        val idx = (floor(walked / 1.4f).toInt() % MonkeySprites.walk.size + MonkeySprites.walk.size) % MonkeySprites.walk.size
        val remain = abs(step.toX - frame.x) * span / pix
        val sprite = if (remain < 0.6f) MonkeySprites.stand else MonkeySprites.walk[idx]
        // Independent gentle body sway period (1.3s) so the walk loop never feels mechanical.
        val swayDeg = if (remain < 0.6f) 0f else 3f * wave(frame.ms, 1300L)
        drawPicture(sprite, mirror, ox, oy, cell, turnDeg = swayDeg)
    }

    /**
     * Runs to the camera hole at full size and stops behind it: the hole's cutout covers the middle of the body.
     * It ends on the pose [drawHidden] shows, so the hidden monkey stands exactly where the run stopped.
     */
    private fun DrawScope.drawHide(
        frame: PetFrame,
        cell: Float,
    ) {
        val step = frame.step
        val t = frame.progress
        val mirror = step.face < 0
        val startLeft = left(step.fromX)
        val endLeft = left(step.toX)
        val dir = if (endLeft >= startLeft) 1f else -1f
        when {
            t < 0.08f -> {
                val u = ease(t / 0.08f)
                drawPicture(MonkeySprites.hideCrouch, mirror, startLeft - dir * cell * 1.4f * u, top, cell, turnDeg = -10f * u)
            }
            t < 0.9f -> {
                val x = lerp(startLeft, endLeft, ease((t - 0.08f) / 0.82f))
                val dist = abs(x - startLeft) / cell
                val idx = (floor(dist / 1.1f).toInt() % MonkeySprites.walk.size + MonkeySprites.walk.size) % MonkeySprites.walk.size
                drawPicture(MonkeySprites.walk[idx], mirror, x, top, cell, turnDeg = 8f)
            }
            else -> drawPicture(MonkeySprites.stand, mirror, endLeft, top, cell)
        }
    }

    /** Stands still behind the hole; drawn once, so it costs no frames. */
    private fun DrawScope.drawHidden(
        frame: PetFrame,
        ox: Float,
        cell: Float,
    ) = drawPicture(MonkeySprites.stand, frame.step.face < 0, ox, top, cell)

    /**
     * Leans its head out to the side of the hole, with its feet still behind it, looks around, then leans back to
     * the hidden stand. The monkey keeps full size and never looks over the top of the hole.
     */
    private fun DrawScope.drawPeek(
        frame: PetFrame,
        ox: Float,
        cell: Float,
    ) {
        val t = frame.progress
        val dir = if (frame.step.face < 0) -1f else 1f
        val holeCells = (hole?.width ?: (box * 0.3f)) / 2f / cell
        // The head must clear the rim by its own half width (4.5 cells). Leaning 30 degrees moves it 5.5 cells; the rest is a shift.
        val lean = 30f
        val extra = max(0f, holeCells + 4.5f - 5.5f - 3f) * cell
        val out =
            when {
                t < 0.22f -> ease(t / 0.22f)
                t < 0.72f -> 1f
                else -> 1f - ease((t - 0.72f) / 0.28f)
            }
        val sway = if (t in 0.22f..0.72f) 0.1f * sin(frame.ms / 260f) else 0f
        val sprite = if (t in 0.4f..0.55f) MonkeySprites.standFront else MonkeySprites.stand
        val shift = dir * extra * out
        drawPicture(sprite, dir < 0f, ox + shift, top, cell, turnDeg = lean * (out + sway))
    }

    /** Seamless 2-beat (1000ms) funky monkey groove: bounces on every beat, wide arm punches and alternating foot kicks. */
    private fun DrawScope.drawDance(
        frame: PetFrame,
        ox: Float,
        oy: Float,
        cell: Float,
        mirror: Boolean,
    ) {
        val twoBeatMs = PET_BEAT_MS * 2L
        val phase = (frame.ms % twoBeatMs).toFloat() / twoBeatMs
        val beat = frame.beat
        val bounce = sin(beat * PI.toFloat())
        val sway = sin(phase * 2f * PI.toFloat())
        val sprite =
            when {
                phase < 0.22f -> MonkeySprites.danceDownR
                phase < 0.50f -> MonkeySprites.danceUpR
                phase < 0.72f -> MonkeySprites.danceDownL
                else -> MonkeySprites.danceUpL
            }
        val dx = sway * 1.6f
        val dy = 0.8f - 2.4f * bounce
        val turn = sway * 11f
        drawPicture(
            sprite,
            mirror,
            ox,
            oy,
            cell,
            pinTargetX = 10f + dx,
            pinTargetY = 19f + dy,
            turnDeg = turn,
        )
    }

    private fun transitionSprite(
        from: PetPose,
        to: PetPose,
        u: Float,
    ): Picture =
        when {
            u < 0.25f -> basePicture(from)
            u > 0.75f -> basePicture(to)
            from == PetPose.Lie || to == PetPose.Lie -> MonkeySprites.transLie
            else -> MonkeySprites.transSitStand
        }

    private fun basePicture(pose: PetPose): Picture =
        when (pose) {
            PetPose.Sit -> MonkeySprites.sit
            PetPose.Stand -> MonkeySprites.stand
            PetPose.Lie -> MonkeySprites.lie
        }

    /** Draws a single [Picture] aligned to the 20x20 grid at ([ox], [oy]), optionally shifted or turned around its pin. */
    private fun DrawScope.drawPicture(
        pic: Picture,
        mirror: Boolean,
        ox: Float,
        oy: Float,
        cell: Float,
        pinTargetX: Float = pic.pinX.toFloat(),
        pinTargetY: Float = pic.pinY.toFloat(),
        turnDeg: Float = 0f,
    ) {
        fun sx(i: Int) = (ox + i * cell).roundToInt().toFloat()

        fun sy(j: Int) = (oy + j * cell).roundToInt().toFloat()

        if (abs(turnDeg) < 0.5f) {
            val dx = (pinTargetX - pic.pinX).roundToInt()
            val dy = (pinTargetY - pic.pinY).roundToInt()
            for (j in 0 until pic.h) {
                var i = 0
                while (i < pic.w) {
                    val col = pic.c[j * pic.w + i]
                    if (col == 0) {
                        i++
                        continue
                    }
                    var end = i
                    while (end + 1 < pic.w && pic.c[j * pic.w + end + 1] == col) end++
                    val a = if (mirror) GRID - 1 - (end + dx) else i + dx
                    val b = if (mirror) GRID - (i + dx) else end + dx + 1
                    val gy = j + dy
                    drawRect(Color(col), Offset(sx(a), sy(gy)), Size(sx(b) - sx(a), sy(gy + 1) - sy(gy)))
                    i = end + 1
                }
            }
            return
        }

        val px = pinTargetX + 0.5f
        val py = pinTargetY + 0.5f
        val rad = turnDeg * PI.toFloat() / 180f
        val cs = cos(rad)
        val sn = sin(rad)
        val reach = hypot(pic.w.toFloat(), pic.h.toFloat()) + 1f
        for (j in floor(py - reach).toInt()..ceil(py + reach).toInt()) {
            for (i in floor(px - reach).toInt()..ceil(px + reach).toInt()) {
                val dx = i + 0.5f - px
                val dy = j + 0.5f - py
                val ix = floor(pic.pinX + 0.5f + dx * cs + dy * sn).toInt()
                val iy = floor(pic.pinY + 0.5f - dx * sn + dy * cs).toInt()
                if (ix !in 0 until pic.w || iy !in 0 until pic.h) continue
                val col = pic.c[iy * pic.w + ix]
                if (col == 0) continue
                val gx = if (mirror) GRID - 1 - i else i
                drawRect(Color(col), Offset(sx(gx), sy(j)), Size(sx(gx + 1) - sx(gx), sy(j + 1) - sy(j)))
            }
        }
    }

    private fun DrawScope.drawMarks(
        frame: PetFrame,
        ox: Float,
        oy: Float,
        cell: Float,
        mirror: Boolean,
    ) {
        val marks =
            buildList {
                val hx = if (frame.state == PetState.Sleep) 13f else 11f
                val hy = if (frame.state == PetState.Sleep) 14f else 6f
                val heart = frame.mark(PetMark.Heart)
                if (heart >= 0f && frame.state != PetState.Excited) {
                    val small = heart < 0.12f
                    val alpha = 1f - ease((heart - 0.62f) / 0.38f)
                    val x = hx + 4f + 1.2f * sin(heart * 9f) - if (small) 1f else 3f
                    // Start clearly above the crown so the heart never overlaps the eyes or forehead during a hop.
                    add(Mark(if (small) MARK_HEART_SM else MARK_HEART, x, hy - 7.5f - 6.5f * ease(heart), alpha))
                }
                val z = frame.mark(PetMark.Z)
                if (z >= 0f) {
                    for (i in 0..2) {
                        val q = (z - i * 0.2f) / 0.6f
                        if (q <= 0f || q >= 1f) continue
                        val alpha = min(1f, q * 5f) * (1f - ease((q - 0.55f) / 0.45f))
                        add(Mark(if (i == 0) MARK_Z_SM else MARK_Z_BIG, hx + 1f + q * 3f + sin(q * 6f), hy - 3f - q * 8f, alpha))
                    }
                }
                val sweat = frame.mark(PetMark.Sweat)
                if (sweat >= 0f) {
                    val q = (sweat * 3f) % 1f
                    add(Mark(MARK_DROP, hx - 6.5f, hy - 1f + q * 3.5f, bump(q, 0f, 1f) * 1.4f))
                }
                if (frame.low && (frame.state == PetState.Tired || frame.state == PetState.Sleep)) {
                    add(Mark(MARK_BATTERY, 0f, 0f, 1f))
                }
                if (frame.state == PetState.Excited) {
                    val fade = 1f - ease((frame.progress - 0.85f) / 0.15f)
                    add(Mark(MARK_BOLT, 0f, 0.5f + wave(frame.ms, 600), fade))
                    for (k in 0..2) {
                        val a = frame.ms / 400f + k * 2.1f
                        add(
                            Mark(
                                MARK_SPARK,
                                9f + 8.5f * cos(a),
                                7f + 5f * sin(a),
                                fade * bump(((frame.ms + k * 230) % 700) / 700f, 0f, 1f),
                            ),
                        )
                    }
                }
                if (frame.state == PetState.Startle && frame.progress < 0.55f) {
                    add(Mark(MARK_BANG, hx + 5f, hy - 8f, 1f))
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
                        val color = Color(monkeyMarkColor(ch)).copy(alpha = m.alpha.coerceIn(0f, 1f))
                        val rx = (ox + (gx + ri) * cell).roundToInt().toFloat()
                        val ry = (oy + (m.y + rj) * cell).roundToInt().toFloat()
                        drawRect(color, Offset(rx, ry), Size(dot, dot))
                    }
                }
            }
        }
    }
}
