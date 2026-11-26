package sk.vladko.pill.overlay

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import sk.vladko.pill.rules.COMPACT_HEIGHT_DP
import sk.vladko.pill.rules.Frame
import sk.vladko.pill.ui.CANOPY_FLARE_DP
import sk.vladko.pill.ui.CANOPY_SHELF_CORNER_DP
import sk.vladko.pill.ui.PILL_MAX_CORNER_DP

/**
 * The pill's outline for one animation frame, in pixels inside the pill's box, written into [into]. Clip, glow and
 * touch region all use it. Three cases: no shelf yet (a pill over the crown only), crown as wide as the box (a full
 * pill, e.g. the tall idle pill), else the canopy (a crown around the camera hole flaring into a wide shelf).
 */
fun outlinePath(
    frame: Frame,
    density: Float,
    into: Path,
) {
    into.rewind()
    val w = frame.width * density
    val h = frame.height * density
    if (w < 1f || h < 1f) return
    val cL = (frame.crownLeft * density).coerceIn(0f, (w - 1f).coerceAtLeast(0f))
    val cR = ((frame.crownLeft + frame.crownWidth) * density).coerceIn((cL + 1f).coerceAtMost(w), w)
    val cW = (cR - cL).coerceAtLeast(1f)
    val sTop = frame.shelfTop * density
    when {
        h <= sTop + 1f -> {
            val r = minOf(h / 2f, cW / 2f)
            into.addRoundRect(RectF(cL, 0f, cR, h), r, r, Path.Direction.CW)
        }
        frame.crownWidth >= frame.width - 1f -> fullPill(w, h, minOf(h / 2f, w / 2f, PILL_MAX_CORNER_DP * density), into)
        else -> canopy(w, h, cL, cR, COMPACT_HEIGHT_DP * density, sTop, density, into)
    }
}

/** A rounded rectangle with the iPhone's continuous corner: the curve starts earlier and is softer than a circular arc. */
private fun fullPill(
    w: Float,
    h: Float,
    radius: Float,
    p: Path,
) {
    val r = radius.coerceIn(0f, minOf(w, h) / 2f)
    // Where the curve leaves each edge: 1.28 x the radius, but never past the middle of a side.
    val e = minOf(r * 1.28f, w / 2f, h / 2f)
    // Handle length: a circle's 0.5523 for a plain pill, up to 0.62 for a fully smoothed corner.
    val smooth = if (r > 0f) ((e / r - 1f) / 0.28f).coerceIn(0f, 1f) else 0f
    val k = (0.5523f + 0.0677f * smooth) * e
    p.moveTo(e, 0f)
    p.lineTo(w - e, 0f)
    p.cubicTo(w - e + k, 0f, w, e - k, w, e)
    p.lineTo(w, h - e)
    p.cubicTo(w, h - e + k, w - e + k, h, w - e, h)
    p.lineTo(e, h)
    p.cubicTo(e - k, h, 0f, h - e + k, 0f, h - e)
    p.lineTo(0f, e)
    p.cubicTo(0f, e - k, e - k, 0f, e, 0f)
    p.close()
}

/** The crown from [cL] to [cR] in the status-bar row, smooth concave shoulders, and the shelf from [sTop] down. */
private fun canopy(
    w: Float,
    h: Float,
    cL: Float,
    cR: Float,
    crownH: Float,
    sTop: Float,
    density: Float,
    p: Path,
) {
    val cW = (cR - cL).coerceAtLeast(1f)
    val rCrown = minOf(crownH / 2f, cW / 2f)
    val sH = h - sTop
    val rBot = minOf(PILL_MAX_CORNER_DP * density, sH, w * 0.45f)
    val maxRTop = minOf(CANOPY_SHELF_CORNER_DP * density, sH - rBot).coerceAtLeast(0f)
    val wingL = cL
    val wingR = w - cR
    val rTopL = minOf(maxRTop, wingL * 0.65f).coerceAtLeast(0f)
    val rTopR = minOf(maxRTop, wingR * 0.65f).coerceAtLeast(0f)
    val flareL = minOf(CANOPY_FLARE_DP * density, (wingL - rTopL).coerceAtLeast(0f))
    val flareR = minOf(CANOPY_FLARE_DP * density, (wingR - rTopR).coerceAtLeast(0f))
    val shoulderY = minOf(rCrown, sTop * 0.56f)
    val dy = (sTop - shoulderY).coerceAtLeast(1f)
    p.moveTo(cL + rCrown, 0f)
    p.lineTo(cR - rCrown, 0f)
    p.cubicTo(cR - rCrown * 0.42f, 0f, cR, shoulderY * 0.42f, cR, shoulderY)
    p.cubicTo(cR, shoulderY + dy * 0.72f, cR + flareR * 0.28f, sTop, cR + flareR, sTop)
    p.lineTo(w - rTopR, sTop)
    p.cubicTo(w - rTopR * 0.4f, sTop, w, sTop + rTopR * 0.4f, w, sTop + rTopR)
    p.lineTo(w, h - rBot)
    p.cubicTo(w, h - rBot * 0.4f, w - rBot * 0.4f, h, w - rBot, h)
    p.lineTo(rBot, h)
    p.cubicTo(rBot * 0.4f, h, 0f, h - rBot * 0.4f, 0f, h - rBot)
    p.lineTo(0f, sTop + rTopL)
    p.cubicTo(0f, sTop + rTopL * 0.4f, rTopL * 0.4f, sTop, rTopL, sTop)
    p.lineTo(cL - flareL, sTop)
    p.cubicTo(cL - flareL * 0.28f, sTop, cL, shoulderY + dy * 0.72f, cL, shoulderY)
    p.cubicTo(cL, shoulderY * 0.42f, cL + rCrown * 0.42f, 0f, cL + rCrown, 0f)
    p.close()
}

/** The clip of the pill's box for one [frame]. [path] gives the same outline to the glow and the touch region. */
class OutlineShape(
    private val frame: Frame,
) : Shape {
    private val outline = Path()
    private var builtFor: Size? = null

    /** The outline for a box of [size] pixels (the laid-out box, which is rounded to whole pixels). */
    fun path(
        size: Size,
        density: Float,
    ): Path {
        if (builtFor != size) {
            outlinePath(frame.copy(width = size.width / density, height = size.height / density), density, outline)
            builtFor = size
        }
        return outline
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(path(size, density.density).asComposePath())
}
