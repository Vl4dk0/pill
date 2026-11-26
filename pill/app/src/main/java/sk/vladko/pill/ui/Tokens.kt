package sk.vladko.pill.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import sk.vladko.pill.rules.COMPACT_HEIGHT_DP
import sk.vladko.pill.rules.SHELF_TOP_PAD_DP

// One place for the numbers every view shares, so all views read as one shape language (the iPhone island's).

/** Height of the idle-shaped pill in every compact view, whatever the idle height setting is. */
val COMPACT_H = COMPACT_HEIGHT_DP.dp

/** Compact content is this far from the pill's left and right ends. */
val COMPACT_INSET = 9.dp

/** Gap between the canopy's shelf top and its content. */
val SHELF_TOP_PAD = SHELF_TOP_PAD_DP.dp

// The pill's outline (OutlinePath.kt), in dp.

/** Largest corner of a full pill, and the bottom corners of a canopy's shelf. */
const val PILL_MAX_CORNER_DP = 28f

/** Largest outer top corner of a canopy's shelf. */
const val CANOPY_SHELF_CORNER_DP = 22f

/** How far the shelf flares out from the crown at the shoulder. */
const val CANOPY_FLARE_DP = 24f

/** Round and capsule buttons in large views. */
val BUTTON_SIZE = 50.dp
val BUTTON_GLYPH = 22.dp
val BUTTON_GAP = 12.dp

val PillGray = Color(0xFF98989F)
val PillButtonBg = Color(0xFF262629)
val PillButtonHover = Color(0xFF3E3E42)
val PillGreen = Color(0xFF30D158)
val PillRed = Color(0xFFFF453A)

val IosGray = Color(0xFF8E8E93)
val IosGrayLight = Color(0xFFA1A1A6)
val IosFill = Color(0xFF3A3A3C)
val IosFillPressed = Color(0xFF4A4A4C)
val AlarmOrange = Color(0xFFFF9F0A)

// Type scale (sp): compact value, compact message/idle text, large title/secondary, small label.
const val TEXT_VALUE = 15
const val TEXT_COMPACT = 13
const val TEXT_TITLE = 17
const val TEXT_LABEL = 12

/**
 * A rounded rectangle with the iPhone's continuous corner: the curve starts earlier and is softer than a
 * circular arc. With the radius at half the height it is a plain pill.
 */
class SquircleShape(
    private val radius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }.coerceIn(0f, minOf(w, h) / 2f)
        // Where the curve leaves each edge: 1.28 x the radius, but never past the middle of a side.
        val e = minOf(r * 1.28f, w / 2f, h / 2f)
        // Handle length: a circle's 0.5523 for a plain pill, up to 0.62 for a fully smoothed corner.
        val smooth = if (r > 0f) ((e / r - 1f) / 0.28f).coerceIn(0f, 1f) else 0f
        val k = (0.5523f + 0.0677f * smooth) * e
        val p = Path()
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
        return Outline.Generic(p)
    }
}
