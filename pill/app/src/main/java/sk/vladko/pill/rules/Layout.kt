package sk.vladko.pill.rules

// Where the pill sits on the screen. All values are dp, measured from the top-left of the screen (the overlay window's origin).

/** The screen as the pill sees it. [hole] is null in landscape or when the screen has no cut-out. */
data class ScreenGeometry(
    val widthDp: Float,
    val hole: Hole?,
    val statusBarDp: Float,
) {
    /** What the pill centres on: the camera hole, or a 20 dp spot at the top centre when there is none. */
    val anchor: Hole get() = hole ?: Hole(widthDp / 2f, 22f, 20f, 20f)
}

/** The camera hole: centre and size. */
data class Hole(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
)

/** What the shown view needs from the shell. */
sealed interface Want {
    /** The idle pill. */
    data object Idle : Want

    /** A compact view of a fixed width, centred on the hole. */
    data class Compact(
        val width: Float,
    ) : Want

    /** A compact view that sizes each side by its content: extents left and right of the hole centre. */
    data class CompactSides(
        val left: Float,
        val right: Float,
    ) : Want

    /** Every large view and menu. [measured] is the content height once known; until then the shelf content is guessed as [shelfGuess]. */
    data class Tall(
        val shelfGuess: Float,
        val measured: Float? = null,
    ) : Want
}

/** Where the pill is and how its outline is built. [crownCenter] is in window coordinates like [left]. */
data class Frame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val crownWidth: Float,
    val crownCenter: Float,
    val shelfTop: Float,
) {
    /** The crown's left edge inside the box. */
    val crownLeft: Float get() = crownCenter - crownWidth / 2f - left
}

/** Where the camera hole is inside the content of a view, so content keeps clear of it. */
data class HoleInfo(
    val leading: Float,
    val hole: Float,
    val crown: Float,
    val shelf: Float,
    val cutoutLeft: Float = leading,
    val cutoutWidth: Float = hole,
    val hasHole: Boolean = true,
    /** Top and height of the cut-out inside the content (zero when [hasHole] is false). */
    val cutoutTop: Float = 0f,
    val cutoutHeight: Float = 0f,
)

/** How far a compact view that sizes its sides by content may reach: [hole] is kept clear, each side is at least [minSide]. */
data class CompactRoom(
    val hole: Float,
    val minSide: Float,
    val maxLeft: Float,
    val maxRight: Float,
)

/** A frame as distances from the hole centre. The shell animates these, so the pill stays on the hole when the hole moves. */
data class Arms(
    val left: Float,
    val right: Float,
    val crownLeft: Float,
    val crownRight: Float,
    val top: Float,
    val height: Float,
)

/** Height of every compact view. */
const val COMPACT_HEIGHT_DP = 37f

/** Space between the shelf top and the content of a large view. */
const val SHELF_TOP_PAD_DP = 10f

/** Space below the content of a large view. */
const val SHELF_BOTTOM_PAD_DP = 14f
private const val MARGIN = 8f
private const val LARGE_MAX_WIDTH = 460f
private const val CROWN_MAX_WIDTH = 172f

/** The idle pill keeps at least this far from the top edge of the screen. */
private const val IDLE_MIN_TOP = 2f
private const val STATUS_BAR_MAX = 52f

/** Idle pill height: [setting] limited to hole height + 4 .. 56, or hole height + 10 when unset. */
fun idleHeight(
    setting: Float?,
    holeH: Float,
): Float {
    val min = holeH + 4f
    val max = maxOf(min, MAX_IDLE_HEIGHT_DP)
    return if (setting == null) minOf(holeH + 10f, max) else setting.coerceIn(min, max)
}

/** Null when [PillView.drawsNothing]. */
fun pillFrame(
    screen: ScreenGeometry,
    settings: Settings,
    view: PillView,
    want: Want,
): Frame? {
    if (view.drawsNothing) return null
    val hole = screen.anchor
    val shelfTop = shelfTop(screen)
    return when (want) {
        Want.Idle -> idleFrame(screen, settings)
        is Want.Compact -> centred(screen, minOf(want.width, maxWidth(screen)), compactTop(screen), COMPACT_HEIGHT_DP, shelfTop)
        is Want.CompactSides -> {
            val room = compactRoom(screen, settings)
            val l = (hole.cx - want.left.coerceIn(0f, room.maxLeft)).coerceAtLeast(MARGIN)
            val r = (hole.cx + want.right.coerceIn(0f, room.maxRight)).coerceAtMost(screen.widthDp - MARGIN)
            val w = (r - l).coerceAtLeast(0f)
            Frame(l, compactTop(screen), w, COMPACT_HEIGHT_DP, w, l + w / 2f, shelfTop)
        }
        is Want.Tall -> {
            val h = want.measured ?: (shelfTop + SHELF_TOP_PAD_DP + want.shelfGuess + SHELF_BOTTOM_PAD_DP)
            val box = centred(screen, minOf(LARGE_MAX_WIDTH, screen.widthDp - MARGIN * 2), compactTop(screen), h, shelfTop)
            // The crown, in the status-bar row, keeps the idle pill's width (at most 172 dp).
            val crown = minOf(idleWidth(screen, settings), CROWN_MAX_WIDTH, maxWidth(screen))
            box.copy(crownWidth = crown, crownCenter = place(screen, crown) + crown / 2f)
        }
    }
}

/** The idle pill: the pet's size in every view comes from it. */
fun idleFrame(
    screen: ScreenGeometry,
    settings: Settings,
): Frame =
    centred(
        screen,
        idleWidth(screen, settings),
        idleTop(screen, settings),
        idleHeight(settings.idleHeightDp, screen.anchor.h),
        shelfTop(screen),
    )

/** Where the pill shrinks to when nothing is drawn: the idle pill with no width. */
fun restingFrame(
    screen: ScreenGeometry,
    settings: Settings,
): Frame = centred(screen, 0f, idleTop(screen, settings), idleHeight(settings.idleHeightDp, screen.anchor.h), shelfTop(screen))

fun holeInfo(
    screen: ScreenGeometry,
    frame: Frame,
): HoleInfo {
    val keep = reservedHole(screen)
    val h = screen.hole
    val inPill =
        h != null &&
            h.cx + h.w / 2f > frame.left &&
            h.cx - h.w / 2f < frame.left + frame.width &&
            h.cy + h.h / 2f > frame.top &&
            h.cy - h.h / 2f < frame.top + frame.height
    val cutoutLeft = if (inPill) (h.cx - h.w / 2f - frame.left).coerceIn(0f, frame.width) else 0f
    val cutoutRight = if (inPill) (h.cx + h.w / 2f - frame.left).coerceIn(cutoutLeft, frame.width) else 0f
    return HoleInfo(
        leading = maxOf(0f, screen.anchor.cx - keep / 2f - frame.left),
        hole = keep,
        crown = frame.crownWidth,
        shelf = frame.shelfTop,
        cutoutLeft = cutoutLeft,
        cutoutWidth = cutoutRight - cutoutLeft,
        hasHole = inPill,
        cutoutTop = if (inPill) h.cy - h.h / 2f - frame.top else 0f,
        cutoutHeight = if (inPill) h.h else 0f,
    )
}

fun compactRoom(
    screen: ScreenGeometry,
    settings: Settings,
): CompactRoom {
    val cx = screen.anchor.cx
    val maxW = maxWidth(screen)
    val maxLeft = (cx - maxOf(MARGIN, cx - maxW / 2f)).coerceAtLeast(0f)
    val maxRight = (minOf(screen.widthDp - MARGIN, cx + maxW / 2f) - cx).coerceAtLeast(0f)
    return CompactRoom(reservedHole(screen), idleWidth(screen, settings) / 2f, maxLeft, maxRight)
}

fun Frame.arms(screen: ScreenGeometry): Arms {
    val cx = screen.anchor.cx
    val crownL = crownCenter - crownWidth / 2f
    return Arms(
        (cx - left).coerceAtLeast(0f),
        (left + width - cx).coerceAtLeast(0f),
        (cx - crownL).coerceAtLeast(0f),
        (crownL + crownWidth - cx).coerceAtLeast(0f),
        top,
        height,
    )
}

/** The frame of animated [arms], kept inside the screen margins and with the crown inside the box. */
fun frameOf(
    screen: ScreenGeometry,
    arms: Arms,
): Frame {
    val cx = screen.anchor.cx
    val right = screen.widthDp - MARGIN
    val boxLeft = (cx - arms.left).coerceIn(MARGIN, maxOf(MARGIN, right))
    val boxRight = (cx + arms.right).coerceIn(boxLeft, maxOf(boxLeft, right))
    val width = (boxRight - boxLeft).coerceAtLeast(0f)
    val crownL = ((cx - arms.crownLeft) - boxLeft).coerceIn(0f, width)
    val crownR = ((cx + arms.crownRight) - boxLeft).coerceIn(crownL, width)
    return Frame(
        boxLeft,
        arms.top,
        width,
        arms.height.coerceAtLeast(0f),
        crownR - crownL,
        boxLeft + (crownL + crownR) / 2f,
        shelfTop(screen),
    )
}

/** A compact view never grows wider than a large one (a landscape screen is much wider than a phone's status bar). */
private fun maxWidth(screen: ScreenGeometry) = minOf(screen.widthDp - MARGIN * 2, LARGE_MAX_WIDTH)

private fun idleWidth(
    screen: ScreenGeometry,
    settings: Settings,
) = minOf(maxOf(settings.idleWidthDp, screen.anchor.w + 24f), maxWidth(screen))

/** Compact content keeps this wide a gap around the hole, so a 158 dp pill leaves room for "100%" or a timer on each side. */
private fun reservedHole(screen: ScreenGeometry) = maxOf(screen.anchor.w + 16f, 44f)

private fun idleTop(
    screen: ScreenGeometry,
    settings: Settings,
): Float {
    val hole = screen.anchor
    return maxOf(IDLE_MIN_TOP, hole.cy - idleHeight(settings.idleHeightDp, hole.h) / 2f)
}

/** Every compact view is centred on the hole; large views keep that top and grow down. */
private fun compactTop(screen: ScreenGeometry) = maxOf(IDLE_MIN_TOP, screen.anchor.cy - COMPACT_HEIGHT_DP / 2f)

/** Where the shelf of a large view starts: below the status bar, and never inside the crown. */
private fun shelfTop(screen: ScreenGeometry) = maxOf(COMPACT_HEIGHT_DP, minOf(screen.statusBarDp, STATUS_BAR_MAX) - compactTop(screen) + 2f)

/** Left edge of a box [w] wide centred on the hole, moved inside the screen margins. */
private fun place(
    screen: ScreenGeometry,
    w: Float,
) = (screen.anchor.cx - w / 2f).coerceIn(MARGIN, maxOf(MARGIN, screen.widthDp - MARGIN - w))

private fun centred(
    screen: ScreenGeometry,
    w: Float,
    top: Float,
    h: Float,
    shelfTop: Float,
): Frame {
    val l = place(screen, w)
    return Frame(l, top, w, h, w, l + w / 2f, shelfTop)
}
