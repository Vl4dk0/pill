package sk.vladko.pill.overlay

import android.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import sk.vladko.pill.rules.Frame
import sk.vladko.pill.ui.PILL_MAX_CORNER_DP

/**
 * The pill's outline for one animation frame, in pixels inside the pill's box, written into [into]. Clip, glow and
 * touch region all use it. It is always one rounded rectangle with the iPhone's continuous corner: the whole box.
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
    fullPill(w, h, minOf(h / 2f, w / 2f, PILL_MAX_CORNER_DP * density), into)
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
