package sk.vladko.pill.overlay

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import sk.vladko.pill.rules.Gesture
import kotlin.math.abs

/** A lift after this much mostly upward movement is a flick up (about 0.8 mm; a tap moves less). */
private val FLICK_UP = 5.dp

/**
 * Turns raw touches into [Gesture]s. It only watches (never consumes), so buttons inside the pill still
 * get their own clicks. Swipes fire as soon as the finger has moved far enough. The pill sits at the top
 * edge, so a real upward flick has only a few millimetres before the finger leaves the glass: when the
 * finger lifts (or the touch is cancelled at the edge) after a mostly upward move of [FLICK_UP], that is
 * swipe up too. After a long press the finger is followed until it lifts.
 * A touch that starts where [isOwned] says (a seek bar) is no gesture: it only counts as touch, so the
 * idle timer waits while the finger drags. A clean tap starting where [isCrown] says (the canopy crown of
 * an opened view) reports [Gesture.Outside] to close it.
 */
suspend fun PointerInputScope.recognizeGestures(
    onGesture: (Gesture) -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
    isRight: (Offset) -> Boolean = { false },
    isOwned: (Offset) -> Boolean = { false },
    isCrown: (Offset) -> Boolean = { false },
) {
    val slop = viewConfiguration.touchSlop
    val swipeUp = 12.dp.toPx()
    val flickUp = FLICK_UP.toPx()
    val swipeSide = 24.dp.toPx()
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onGesture(Gesture.Touch)
        if (isOwned(down.position)) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                onGesture(Gesture.Touch)
            }
        }
        val right = isRight(down.position)
        val crown = isCrown(down.position)
        var longPressed = false
        var swiped = false
        var moved = false
        while (true) {
            val waitMs = longPressMs - (SystemClock.uptimeMillis() - down.uptimeMillis).coerceAtLeast(0)
            val timed = !longPressed && !swiped && !moved
            val event =
                if (timed) {
                    withTimeoutOrNull(waitMs.coerceAtLeast(1)) { awaitPointerEvent(PointerEventPass.Initial) }
                } else {
                    awaitPointerEvent(PointerEventPass.Initial)
                }
            if (event == null) {
                longPressed = true
                onGesture(if (right) Gesture.LongPressRight else Gesture.LongPress)
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            val pos = change.position
            val d = pos - down.position
            if (longPressed) {
                onMove(pos)
                if (!change.pressed) {
                    onRelease(pos)
                    return@awaitEachGesture
                }
                continue
            }
            if (!swiped) {
                if (-d.y >= swipeUp && abs(d.y) > abs(d.x)) {
                    swiped = true
                    onGesture(Gesture.SwipeUp)
                } else if (d.y >= swipeUp && abs(d.y) > abs(d.x)) {
                    swiped = true
                    onGesture(Gesture.SwipeDown)
                } else if (abs(d.x) >= swipeSide && abs(d.x) > abs(d.y)) {
                    swiped = true
                    onGesture(if (d.x > 0) Gesture.SwipeRight else Gesture.SwipeLeft)
                } else if (abs(d.x) > slop || abs(d.y) > slop) {
                    moved = true
                }
            }
            // Not pressed = lifted, or cancelled when the finger ran off the edge.
            if (!change.pressed) {
                if (!swiped && -d.y >= flickUp && abs(d.y) > abs(d.x)) {
                    onGesture(Gesture.SwipeUp)
                } else if (!swiped && !moved) {
                    onGesture(
                        when {
                            crown -> Gesture.Outside
                            right -> Gesture.TapRight
                            else -> Gesture.Tap
                        },
                    )
                }
                return@awaitEachGesture
            }
        }
    }
}
