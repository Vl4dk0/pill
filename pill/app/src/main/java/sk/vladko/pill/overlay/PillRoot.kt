package sk.vladko.pill.overlay

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sk.vladko.pill.engine.PillSession
import sk.vladko.pill.engine.Registry
import sk.vladko.pill.features.Music
import sk.vladko.pill.idle.LocalPetFoot
import sk.vladko.pill.idle.PetIdle
import sk.vladko.pill.idle.petBox
import sk.vladko.pill.idle.petBoxHeight
import sk.vladko.pill.pill
import sk.vladko.pill.rules.Arms
import sk.vladko.pill.rules.COMPACT_HEIGHT_DP
import sk.vladko.pill.rules.Frame
import sk.vladko.pill.rules.Gesture
import sk.vladko.pill.rules.HoleInfo
import sk.vladko.pill.rules.Input
import sk.vladko.pill.rules.PillView
import sk.vladko.pill.rules.SHELF_BOTTOM_PAD_DP
import sk.vladko.pill.rules.Screen
import sk.vladko.pill.rules.Shown
import sk.vladko.pill.rules.Size
import sk.vladko.pill.rules.Want
import sk.vladko.pill.rules.arms
import sk.vladko.pill.rules.compactRoom
import sk.vladko.pill.rules.frameOf
import sk.vladko.pill.rules.holeInfo
import sk.vladko.pill.rules.idleFrame
import sk.vladko.pill.rules.pillFrame
import sk.vladko.pill.rules.restingFrame
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.LocalHole
import kotlin.math.ceil
import androidx.compose.ui.geometry.Size as ComposeSize

/** First guesses for the shelf content of a large view and a menu, before onSizeChanged reports the real height. */
private const val LARGE_GUESS = 120f
private const val MENU_GUESS = 76f

/** What the shown view needs: its key (one per shown thing and size), what it asks of the shell, and its content. */
private class Target(
    val key: String,
    val want: Want,
    val content: @Composable () -> Unit,
)

@Composable
fun PillRoot(
    session: PillSession,
    onTouchRegion: (Region) -> Unit,
) {
    val ctx = LocalContext.current
    val view = session.engine.view
    val settings by ctx.pill.settings.value
        .collectAsState()
    val screen = session.screen.geometry
    val density = LocalDensity.current

    var measuredKey by remember { mutableStateOf("") }
    var measuredHeight by remember { mutableFloatStateOf(0f) }

    fun tall(
        key: String,
        guess: Float,
        content: @Composable () -> Unit,
    ) = Target(key, Want.Tall(guess, measuredHeight.takeIf { measuredKey == key }), content)

    val t: Target =
        when (val s = view.shown) {
            Shown.Idle ->
                when (view.size) {
                    Size.FastMenu -> tall("fast", MENU_GUESS) { CanopyContent { FastMenu(session) } }
                    Size.QuickMenu -> tall("quick", MENU_GUESS) { CanopyContent { QuickMenu(session) } }
                    Size.RecentApps -> tall("recents", MENU_GUESS) { CanopyContent { RecentApps(session) } }
                    Size.RecentMenu -> tall("recentmenu", MENU_GUESS) { CanopyContent { RecentApps(session) } }
                    // The pet is drawn by the pet layer below, so it keeps living whatever the pill shows.
                    else -> Target("idle", Want.Idle) {}
                }
            is Shown.Event, is Shown.Activity -> {
                val item =
                    when (s) {
                        is Shown.Event -> s.event
                        is Shown.Activity -> s.activity
                        Shown.Idle -> error("unreachable")
                    }
                val f = Registry.of(item)
                if (view.size == Size.Large) {
                    tall("${s.key}:L", LARGE_GUESS) { f.Large(item, session.pill) }
                } else {
                    val room = compactRoom(screen, settings)
                    val sides =
                        f.compactSides(
                            item,
                            room.hole.dp,
                            petFoot(idleFrame(screen, settings)).width,
                            room.minSide.dp,
                            room.maxLeft.dp,
                            room.maxRight.dp,
                            session.screen.state.screen == Screen.Away,
                        )
                    val want =
                        if (sides !=
                            null
                        ) {
                            Want.CompactSides(sides.left.value, sides.right.value)
                        } else {
                            Want.Compact(f.compactWidth.value)
                        }
                    Target("${s.key}:C", want) { f.Compact(item, session.pill) }
                }
            }
        }

    val large = view.size != Size.Compact
    // With nothing to draw the pill shrinks to the hole.
    val target = pillFrame(screen, settings, view.copy(visible = true), t.want) ?: restingFrame(screen, settings)
    val spec = spring<Dp>(dampingRatio = 0.72f, stiffness = 460f)
    // The arms are distances from the hole centre, so asymmetric compact pills and rotation changes stay anchored on the hole.
    val arms = target.arms(screen)

    val leftArmAnim = remember { Animatable(arms.left.dp, Dp.VectorConverter) }
    val rightArmAnim = remember { Animatable(arms.right.dp, Dp.VectorConverter) }
    val heightAnim = remember { Animatable(arms.height.dp, Dp.VectorConverter) }
    val topAnim = remember { Animatable(arms.top.dp, Dp.VectorConverter) }
    val crownLeftArmAnim = remember { Animatable(arms.crownLeft.dp, Dp.VectorConverter) }
    val crownRightArmAnim = remember { Animatable(arms.crownRight.dp, Dp.VectorConverter) }

    // A pill that is not drawn waits as the bare hole, so it grows out of the hole when it shows.
    val resting = restingFrame(screen, settings).arms(screen)
    LaunchedEffect(arms, view.visible) {
        if (!view.visible) {
            crownLeftArmAnim.snapTo(resting.crownLeft.dp)
            crownRightArmAnim.snapTo(resting.crownRight.dp)
            leftArmAnim.snapTo(resting.left.dp)
            rightArmAnim.snapTo(resting.right.dp)
            heightAnim.snapTo(resting.height.dp)
            topAnim.snapTo(resting.top.dp)
            return@LaunchedEffect
        }
        launch { crownLeftArmAnim.animateTo(arms.crownLeft.dp, spec) }
        launch { crownRightArmAnim.animateTo(arms.crownRight.dp, spec) }
        launch { leftArmAnim.animateTo(arms.left.dp, spec) }
        launch { rightArmAnim.animateTo(arms.right.dp, spec) }
        launch { heightAnim.animateTo(arms.height.dp, spec) }
        launch { topAnim.animateTo(arms.top.dp, spec) }
    }

    // The animated frame and its outline are read only in layout, outline and draw, so an animation frame does not recompose the pill.
    val frame =
        remember(screen) {
            derivedStateOf {
                frameOf(
                    screen,
                    Arms(
                        leftArmAnim.value.value,
                        rightArmAnim.value.value,
                        crownLeftArmAnim.value.value,
                        crownRightArmAnim.value.value,
                        topAnim.value.value,
                        heightAnim.value.value,
                    ),
                )
            }
        }
    val pillShape = remember(frame) { derivedStateOf { OutlineShape(frame.value) } }

    // The content is laid out at its final size and clipped by the growing shape, so it never squashes.
    val hole = holeInfo(screen, target)
    val idle = idleFrame(screen, settings)
    val foot = petFoot(idle)

    LaunchedEffect(view.size) {
        if (view.size != Size.RecentApps) session.controls.hover = null
    }

    val pillBounds = remember { Rect() }
    val touch = remember { TouchArea() }
    val currentFrame by rememberUpdatedState(frame)
    val glowProgress = rememberGlowProgress(session.glow)
    var lastSwipeDir by remember { mutableIntStateOf(0) }
    val controls = session.controls
    CompositionLocalProvider(LocalControls provides controls) {
        Box(
            Modifier.fillMaxSize().pointerInput(controls) {
                recognizeGestures(
                    onGesture = {
                        if (it == Gesture.SwipeLeft) lastSwipeDir = 1
                        if (it == Gesture.SwipeRight) lastSwipeDir = -1
                        session.engine.send(Input.Touch(it))
                    },
                    onMove = { controls.hover = controls.hit(it.x, it.y) },
                    isRight = { it.x > pillBounds.exactCenterX() },
                    isOwned = { controls.owns(it.x, it.y) },
                    isCrown = {
                        val shelfTop = currentFrame.value.shelfTop
                        val shelfY = touch.origin.y + shelfTop.dp.toPx()
                        session.engine.view.size != Size.Compact &&
                            it.y < shelfY &&
                            controls.hit(it.x, it.y) == null
                    },
                    onRelease = {
                        if (session.engine.view.size == Size.RecentApps) controls.hit(it.x, it.y)?.let(controls::click)
                        controls.hover = null
                        session.engine.send(Input.Touch(Gesture.Release))
                    },
                )
            },
        ) {
            if (!view.visible) {
                SideEffect {
                    pillBounds.setEmpty()
                    touch.forget()
                    onTouchRegion(Region())
                }
                return@Box
            }

            Box(
                Modifier
                    .layout { m, _ ->
                        val f = frame.value
                        val p = m.measure(Constraints.fixed(f.width.dp.roundToPx(), f.height.dp.roundToPx()))
                        layout(p.width, p.height) {
                            p.place(f.left.dp.roundToPx(), f.top.dp.roundToPx())
                        }
                    }.onGloballyPositioned {
                        val r = it.boundsInWindow()
                        pillBounds.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                        touch.origin = it.positionInWindow()
                        touch.size = it.size.toSize()
                        touch.update(pillShape.value, density, onTouchRegion)
                    }
                    // The shape can change without the box moving (the crown animates), so the drawn frame reports it too.
                    .drawBehind {
                        touch.size = size
                        touch.update(pillShape.value, density, onTouchRegion)
                    }.arrivalGlow(session.glow, glowProgress) { pillShape.value }
                    .graphicsLayer {
                        shape = pillShape.value
                        clip = true
                    }.background(Color.Black),
                contentAlignment = Alignment.TopStart,
            ) {
                androidx.compose.runtime.key(t.key) {
                    val slideDir = remember { if (!large && (t.key.startsWith("idle:") || t.key.startsWith("a:"))) lastSwipeDir else 0 }
                    LaunchedEffect(t.key, lastSwipeDir) { if (lastSwipeDir != 0) lastSwipeDir = 0 }
                    val fade = remember { Animatable(0f) }
                    val slide = remember { Animatable(slideDir * 14f) }
                    // The content shows after the shape has grown; an idle view swipe slides in fast.
                    LaunchedEffect(Unit) {
                        if (slideDir != 0) {
                            launch { slide.animateTo(0f, tween(180)) }
                            delay(40)
                            fade.animateTo(1f, tween(140))
                        } else {
                            delay(110)
                            fade.animateTo(1f, tween(160))
                        }
                    }
                    CompositionLocalProvider(LocalHole provides hole, LocalPetFoot provides foot) {
                        if (large) {
                            // Large content sets its own height: it is measured with no height limit, and the shape grows to fit.
                            Box(
                                Modifier
                                    // Whole pixels of each term, so the content stays on the same pixel while the shape's left edge moves.
                                    .offset {
                                        IntOffset(
                                            target.left.dp.roundToPx() -
                                                frame.value.left.dp
                                                    .roundToPx(),
                                            0,
                                        )
                                    }.graphicsLayer { alpha = fade.value }
                                    // Start-aligned, so the content stays at its final place while the shape grows round it.
                                    .wrapContentWidth(Alignment.Start, unbounded = true)
                                    .requiredWidth(target.width.dp)
                                    .wrapContentHeight(Alignment.Top, unbounded = true)
                                    .onSizeChanged {
                                        measuredKey = t.key
                                        measuredHeight = with(density) { it.height.toDp() }.value
                                    }.padding(bottom = SHELF_BOTTOM_PAD_DP.dp),
                            ) { t.content() }
                        } else {
                            Box(
                                Modifier
                                    .offset { IntOffset((target.left.dp - frame.value.left.dp + slide.value.dp).roundToPx(), 0) }
                                    .graphicsLayer { alpha = fade.value }
                                    .requiredSize(target.width.dp, target.height.dp),
                            ) { t.content() }
                        }
                    }
                }
            }
            if (settings.pet && !view.drawsNothing) {
                val roams = view.shown == Shown.Idle && view.size == Size.Compact && session.screen.state.screen == Screen.Normal
                PetPlace(view, roams)
                val box = petBox(idle)
                // The pet's layer lies over the pill with its shape; the shape clips it, except while the pet is out or hops over the edge.
                Box(
                    Modifier
                        .layout { m, _ ->
                            val f = frame.value
                            val p = m.measure(Constraints.fixed(f.width.dp.roundToPx(), f.height.dp.roundToPx()))
                            layout(p.width, p.height) { p.place(f.left.dp.roundToPx(), f.top.dp.roundToPx()) }
                        }.graphicsLayer {
                            shape = pillShape.value
                            clip = !PetIdle.overflows
                        },
                    contentAlignment = Alignment.TopStart,
                ) { PetLayer(box, holeInfo(screen, box)) { frame.value } }
            }
        }
    }
}

/** What a compact view keeps free for the pet: [PET_FOOT_WIDTH] times the pet box's height wide, at most the compact pill's height high. */
private fun petFoot(idle: Frame): DpSize {
    val box = petBoxHeight(idle)
    return DpSize(box.dp * PET_FOOT_WIDTH, minOf(box, COMPACT_HEIGHT_DP).dp)
}

/** What a view keeps free for the pet, as a multiple of the pet box's height: the widest pose of the widest look. */
private const val PET_FOOT_WIDTH = 1.3f

/**
 * Tells the pet what the shown view asks: it dances while the music view shows and the music plays.
 */
@Composable
private fun PetPlace(
    view: PillView,
    roams: Boolean,
) {
    val item =
        when (val s = view.shown) {
            is Shown.Event -> s.event
            is Shown.Activity -> s.activity
            Shown.Idle -> null
        }
    val dance = (item as? Music)?.playing == true
    LaunchedEffect(dance, roams) { PetIdle.place(dance, roams) }
}

/**
 * The pet, drawn over the pill content whenever the pill shows and the pet is on, always in [box] (see
 * [sk.vladko.pill.idle.petBox]): it stays where it stands whatever view opens. [frame] is the animated shape; the box is
 * placed from it in layout, so the box stays put on screen while the shape changes.
 */
@Composable
private fun PetLayer(
    box: Frame,
    hole: HoleInfo,
    frame: () -> Frame,
) {
    Box(
        Modifier
            .offset { IntOffset((box.left - frame().left).dp.roundToPx(), (box.top - frame().top).dp.roundToPx()) }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(box.width.dp, box.height.dp),
    ) { PetIdle.Content(box, hole) }
}

/** The drawn outline in window pixels: the touchable region is made from it, so it is exactly what is drawn. */
private class TouchArea {
    var origin = Offset.Zero
    var size = ComposeSize.Zero
    private val moved = Path()
    private val bounds = RectF()

    /** What the last region was made from, so an unchanged outline is not rebuilt nor sent again. */
    private var madeFor: Triple<OutlineShape, Offset, ComposeSize>? = null

    /** Sends [report] the region of [shape] when it differs from the last one sent. */
    fun update(
        shape: OutlineShape,
        density: Density,
        report: (Region) -> Unit,
    ) {
        val key = Triple(shape, origin, size)
        if (key == madeFor) return
        madeFor = key
        if (size.width < 1f || size.height < 1f) return report(Region())
        shape.path(size, density.density).offset(origin.x, origin.y, moved)
        moved.computeBounds(bounds, true)
        val clip = Region(bounds.left.toInt(), bounds.top.toInt(), ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt())
        report(Region().apply { setPath(moved, clip) })
    }

    /** Makes the next [update] send its region even when the outline is the same. */
    fun forget() {
        madeFor = null
    }
}
