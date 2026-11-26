package sk.vladko.pill.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vladko.pill.engine.CompactSides
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.overlay.LocalControls
import sk.vladko.pill.rules.HoleInfo

/** Where the camera hole is inside the content area of the current view. Views use it through [HoleRow]. */
val LocalHole = compositionLocalOf { HoleInfo(0f, 0f, 0f, 0f) }

/**
 * The extents from the hole centre that let [HoleRow] show a [leading] and a [trailing] content of these widths whole.
 * Both sides get the larger need, so the pill stays symmetric around the hole; where the hole sits near the pill's edge,
 * everything goes right of it and the right side needs both widths.
 */
fun holeRowSides(
    hole: Dp,
    leading: Dp,
    trailing: Dp,
    minSide: Dp,
    maxLeft: Dp,
    maxRight: Dp,
): CompactSides {
    val side = maxOf(hole / 2 + COMPACT_INSET + leading + 4.dp, hole / 2 + 4.dp + trailing + COMPACT_INSET)
    val left = side.coerceIn(minOf(minSide, maxLeft), maxLeft)
    val right =
        if (left - hole / 2 >= EDGE_LEADING) side else hole / 2 + 6.dp + leading + 8.dp + trailing + COMPACT_INSET
    return CompactSides(left, right.coerceIn(minOf(minSide, maxRight), maxRight))
}

/** Less room than this left of the hole makes [HoleRow] put everything right of it. */
private val EDGE_LEADING = 30.dp

/**
 * Compact layout: [leading] left of the camera hole, [trailing] right of it, never under it. Both hug the
 * pill's own ends. With [trailingFills] the trailing content starts at the hole and may use the whole right side.
 */
@Composable
fun HoleRow(
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    trailingFills: Boolean = false,
) {
    val h = LocalHole.current
    if (h.leading.dp < EDGE_LEADING) {
        // The hole sits near the edge of the pill: everything goes to the right of it.
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width((h.leading + h.hole).dp))
            Box(Modifier.padding(start = 6.dp, end = 8.dp)) { leading() }
            Box(Modifier.weight(1f).fillMaxHeight().padding(end = COMPACT_INSET), contentAlignment = Alignment.CenterStart) { trailing() }
        }
        return
    }
    Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(h.leading.dp).fillMaxHeight().padding(start = COMPACT_INSET, end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            leading()
        }
        Spacer(Modifier.width(h.hole.dp))
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, end = COMPACT_INSET),
            contentAlignment = if (trailingFills) Alignment.CenterStart else Alignment.CenterEnd,
        ) { trailing() }
    }
}

/**
 * Every large view: the canopy's content. [crownLeft] and [crownRight] sit in the status-bar row beside the camera
 * hole (keep them tiny); [shelf] lies below the status bar. [modifier] applies to the shelf only, so shelf gestures
 * (such as [tapToOpen]) do not cover the crown. The canopy shape itself is drawn by the shell.
 */
@Composable
fun CanopyContent(
    modifier: Modifier = Modifier,
    crownLeft: @Composable () -> Unit = {},
    crownRight: @Composable () -> Unit = {},
    shelf: @Composable ColumnScope.() -> Unit,
) {
    val h = LocalHole.current
    val side = ((h.crown - h.hole) / 2).dp
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(COMPACT_H), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width((h.leading.dp - side).coerceAtLeast(0.dp)))
            Row(Modifier.width(side + h.hole.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.widthIn(max = side).padding(start = 11.dp)) { crownLeft() }
            }
            Box(Modifier.width(side).fillMaxHeight().padding(end = 6.dp), contentAlignment = Alignment.CenterStart) { crownRight() }
        }
        Spacer(Modifier.height((h.shelf.dp - COMPACT_H).coerceAtLeast(0.dp)))
        Column(Modifier.fillMaxWidth().then(modifier).padding(start = 18.dp, end = 18.dp, top = SHELF_TOP_PAD), content = shelf)
    }
}

/** Makes a view a named button: tappable, listed in the dump, and clickable by lifting a dragged finger on it. */
@Composable
fun Modifier.pillButton(
    id: String,
    onClick: () -> Unit,
): Modifier {
    val controls = LocalControls.current
    val current by rememberUpdatedState(onClick)
    DisposableEffect(controls, id) { onDispose { controls.removeButton(id) } }
    return this
        .onGloballyPositioned {
            val r = it.boundsInWindow()
            val b = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            controls.button(id, b) { current() }
        }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** A clean tap on this area (not on a control, which takes its own touch) closes the large view and runs [open]. A ringing call or alarm keeps its large view. */
@Composable
fun Modifier.tapToOpen(
    pill: PillControls,
    open: () -> Unit,
): Modifier {
    val current by rememberUpdatedState(open)
    return clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        pill.close()
        current()
    }
}

/** Capsule button of a large view (iOS alarm style): 50 dp tall, 17 sp semibold. */
@Composable
fun CapsuleButton(
    id: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = IosFill,
    textColor: Color = Color.White,
) {
    val hovered = LocalControls.current.hover == id
    Box(
        modifier
            .height(BUTTON_SIZE)
            .clip(RoundedCornerShape(BUTTON_SIZE / 2))
            .background(if (hovered) IosFillPressed else color)
            .pillButton(id, onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            fontSize = TEXT_TITLE.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Round button holding an icon. */
@Composable
fun PillIconButton(
    id: String,
    onClick: () -> Unit,
    size: Dp = 52.dp,
    color: Color = PillButtonBg,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .pillButton(id, onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
fun PillText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color = Color.White,
    lines: Int = 1,
    bold: Boolean = false,
    align: TextAlign? = null,
    digits: Boolean = false,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        maxLines = lines,
        textAlign = align ?: TextAlign.Unspecified,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        // Equal-width digits, so a running timer does not jiggle.
        style = if (digits) TextStyle(fontFeatureSettings = "tnum") else LocalTextStyle.current,
    )
}

/**
 * Text input. The overlay window cannot take keyboard focus normally: touching the field asks for typing, the
 * window becomes focusable, and once it has focus the field takes it and shows the keyboard.
 */
@Composable
fun PillTextField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    pill: PillControls,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    endInset: Dp = 16.dp,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var asked by remember { mutableIntStateOf(0) }
    LaunchedEffect(asked, windowFocused) {
        if (asked > 0 && windowFocused) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val ask = {
        pill.typing(true)
        asked++
        Unit
    }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(PillButtonBg)
            .pillButton("reply", ask)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    ask()
                }
            }.padding(start = 16.dp, end = endInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value,
            onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { pill.typing(it.isFocused) },
            decorationBox = { inner ->
                if (value.isEmpty()) PillText(hint, size = 15, color = PillGray)
                inner()
            },
        )
    }
}

// Status-bar icon protection for the text (right) side: battery ~20..56 dp from the right edge; further system icons sit inward in ~26 dp slots.
private val SCREEN_EDGE_MARGIN = 8.dp
private val RIGHT_EDGE_CLUSTER = 58.dp
private val RIGHT_SLOT_STEP = 26.dp

/** Rounds [needed] outward to clean right status-bar icon-slot boundaries, or [maxExtent] once inside the battery cluster. */
fun snapRightForStatusBar(
    needed: Dp,
    minExtent: Dp,
    maxExtent: Dp,
): Dp = snapForStatusBar(needed, minExtent, maxExtent, RIGHT_EDGE_CLUSTER, RIGHT_SLOT_STEP)

private fun snapForStatusBar(
    needed: Dp,
    minExtent: Dp,
    maxExtent: Dp,
    edgeCluster: Dp,
    slotStep: Dp,
): Dp {
    if (needed <= minExtent) return minExtent
    if (needed >= maxExtent) return maxExtent
    val rem = maxExtent - needed + SCREEN_EDGE_MARGIN
    if (rem < edgeCluster) return maxExtent
    val slots = ((rem - edgeCluster) / slotStep).toInt()
    val snappedRem = edgeCluster + slotStep * slots
    return (maxExtent + SCREEN_EDGE_MARGIN - snappedRem).coerceIn(minExtent, maxExtent)
}

/** Alpha mask for soft-fading clipped text edges inside an Offscreen layer. */
fun DrawScope.fadeEdge(
    startX: Float,
    endX: Float,
    alphaStart: Float,
    alphaEnd: Float,
) = drawRect(
    Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = alphaStart),
        1f to Color.Black.copy(alpha = alphaEnd),
        startX = startX,
        endX = endX,
    ),
    Offset(startX, 0f),
    Size(endX - startX, size.height),
    blendMode = BlendMode.DstIn,
)
