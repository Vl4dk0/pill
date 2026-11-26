package sk.vladko.pill.features

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import sk.vladko.pill.engine.CompactSides
import sk.vladko.pill.engine.ItemView
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.idle.LocalPetFoot
import sk.vladko.pill.idle.PetSpot
import sk.vladko.pill.overlay.LocalControls
import sk.vladko.pill.pill
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.HoleRow
import sk.vladko.pill.ui.IosFill
import sk.vladko.pill.ui.IosGray
import sk.vladko.pill.ui.IosGrayLight
import sk.vladko.pill.ui.PillGray
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.SquircleShape
import sk.vladko.pill.ui.TEXT_LABEL
import sk.vladko.pill.ui.TEXT_TITLE
import sk.vladko.pill.ui.holeRowSides
import sk.vladko.pill.ui.pillButton

object MusicView : ItemView<Music> {
    override val compactWidth: Dp = 159.dp

    // With the pet on, the pill grows until the pet's spot fits whole beside the cover.
    @Composable
    override fun compactSides(
        item: Music,
        hole: Dp,
        foot: Dp,
        minSide: Dp,
        maxLeft: Dp,
        maxRight: Dp,
        away: Boolean,
    ): CompactSides? {
        val pet =
            LocalContext.current.pill.settings.value
                .collectAsState()
                .value.pet
        return if (pet) holeRowSides(hole, 23.dp, foot, minSide, maxLeft, maxRight) else null
    }

    @Composable
    override fun Compact(
        item: Music,
        pill: PillControls,
    ) {
        val pet =
            LocalContext.current.pill.settings.value
                .collectAsState()
                .value.pet
        HoleRow(
            leading = { Cover(item, 23.dp, 6.dp) },
            // With the pet on, the pet dances where the bars would be.
            trailing = { if (pet) PetSpot(Modifier.size(LocalPetFoot.current)) else Waveform(item, 22.dp, 14.dp) },
        )
    }

    @Composable
    override fun Large(
        item: Music,
        pill: PillControls,
    ) {
        val pet =
            LocalContext.current.pill.settings.value
                .collectAsState()
                .value.pet
        val seek = remember(item) { SeekState(item) }
        val hint = remember { SeekHint() }
        val scope = rememberCoroutineScope()
        val cur by rememberUpdatedState(item)
        val st by rememberUpdatedState(seek)
        val slop = LocalViewConfiguration.current.touchSlop
        // A clean tap on the shelf (not on a control or the crown) opens the player. With seek support it waits a
        // short while for a second tap; a double tap skips 5 s back (left half) or forward (right half), each further
        // tap adds 5 s. Controls and the seek bar consume their own touches.
        val shelfTap =
            Modifier.pointerInput(Unit) {
                var chain = 0
                var job: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up =
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() } ?: return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > slop) return@awaitEachGesture
                    up.consume()
                    val x = up.position.x
                    val width = size.width
                    job?.cancel()
                    if (!cur.canSeek) {
                        cur.also {
                            pill.close()
                            it.open()
                        }
                        return@awaitEachGesture
                    }
                    if (chain == 0) {
                        chain = 1
                        job =
                            scope.launch {
                                delay(DOUBLE_TAP_MS)
                                chain = 0
                                pill.close()
                                cur.open()
                            }
                    } else {
                        chain++
                        val dir = if (x > width / 2f) 1 else -1
                        st.skip(dir * SKIP_MS)
                        hint.show(dir, (chain - 1) * 5)
                        job =
                            scope.launch {
                                delay(DOUBLE_TAP_MS)
                                chain = 0
                            }
                    }
                }
            }
        Box(Modifier.fillMaxWidth()) {
            CanopyContent(
                modifier = shelfTap,
                crownLeft = { Cover(item, 20.dp, 5.dp) },
                // With the pet on, the pet dances where the bars would be, opposite the cover.
                crownRight = { if (pet) PetSpot(Modifier.fillMaxSize()) else Waveform(item, 22.dp, 14.dp) },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    PillText(item.title, size = TEXT_TITLE, bold = true)
                    PillText(item.artist, size = TEXT_LABEL + 2, color = IosGray)
                }
                Spacer(Modifier.height(4.dp))
                Progress(item, seek)
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Transport("prev", item::previous, 48.dp) { SkipIcon(forward = false) }
                    Transport("playpause", item::playPause, 48.dp) { if (item.playing) PauseIcon() else PlayIcon() }
                    Transport("next", item::next, 48.dp) { SkipIcon(forward = true) }
                }
            }
            hint.dir.let { d ->
                if (d != 0) {
                    PillText(
                        "${if (d > 0) "+" else "-"}${hint.secs} s",
                        size = 16,
                        bold = true,
                        modifier =
                            Modifier
                                .align(
                                    if (d >
                                        0
                                    ) {
                                        Alignment.CenterEnd
                                    } else {
                                        Alignment.CenterStart
                                    },
                                ).offset(y = 40.dp)
                                .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cover(
    a: Music,
    size: Dp,
    radius: Dp,
) {
    val img = remember(a.cover) { a.cover?.asImageBitmap() }
    val shape = SquircleShape(radius)
    if (img != null) {
        Image(img, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(shape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
            PillText("\u266A", size = (size.value * 0.5f).toInt(), color = PillGray)
        }
    }
}

private const val DOUBLE_TAP_MS = 220L
private const val SKIP_MS = 5_000L

/** Position shown on the bar. After a seek it holds the new position until the player reports a state. */
private class SeekState(
    private val a: Music,
) {
    var pos by mutableLongStateOf(a.position())

    /** The finger's place on the bar (0..1) while it is down. */
    var drag by mutableStateOf<Float?>(null)
    var sought by mutableStateOf(false)

    /** The player's state stamp when the seek was sent; a new one means the player reported the new position. */
    var soughtStamp = 0L

    fun skipTo(ms: Long) {
        val t = ms.coerceIn(0, a.durationMs)
        pos = t
        sought = true
        soughtStamp = a.stateStamp()
        a.seekTo(t)
    }

    fun skip(deltaMs: Long) = skipTo((if (sought) pos else a.position()) + deltaMs)
}

/** The "+5 s" / "-5 s" text on one side; it fades out by itself. */
private class SeekHint {
    var dir by mutableIntStateOf(0)
    var secs = 0
    private var gen = 0

    fun show(
        d: Int,
        s: Int,
    ) {
        dir = d
        secs = s
        val g = ++gen
        Handler(Looper.getMainLooper()).postDelayed({
            if (g ==
                gen
            ) {
                dir = 0
            }
        }, 700)
    }
}

/** Elapsed time, a seek bar and total time. With seek support a touch on the bar (tap or drag) moves the position; the player is told when the finger lifts. */
@Composable
private fun Progress(
    a: Music,
    seek: SeekState,
) {
    val controls = LocalControls.current
    val pos = seek.pos
    val drag = seek.drag
    LaunchedEffect(a) {
        while (true) {
            if (seek.sought && a.stateStamp() != seek.soughtStamp) seek.sought = false
            if (seek.drag == null && !seek.sought) seek.pos = a.position()
            delay(500)
        }
    }
    val fraction = drag ?: if (a.durationMs > 0) (pos.toFloat() / a.durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = drag?.let { (it * a.durationMs).toLong() } ?: pos
    val area = remember { Rect() }
    val curCanSeek by rememberUpdatedState(a.canSeek)
    val curShownMs by rememberUpdatedState(shownMs)
    val curDuration by rememberUpdatedState(a.durationMs)
    DisposableEffect(controls, a.canSeek) {
        val owned = if (a.canSeek) controls.own(area) else null
        onDispose { owned?.close() }
    }
    DisposableEffect(controls) {
        val rep =
            controls.report("seek") {
                JSONObject()
                    .put("bounds", JSONArray(listOf(area.left, area.top, area.right, area.bottom)))
                    .put("supported", curCanSeek)
                    .put("position", curShownMs)
                    .put("duration", curDuration)
            }
        onDispose { rep.close() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PillText(time(shownMs), Modifier.width(36.dp), size = TEXT_LABEL, color = IosGray, digits = true)
        Box(
            Modifier
                .weight(1f)
                .height(32.dp)
                .onGloballyPositioned {
                    val r = it.boundsInWindow()
                    area.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                }.then(
                    if (a.canSeek) {
                        Modifier.pointerInput(a) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val w = size.width.toFloat()
                                seek.drag = (down.position.x / w).coerceIn(0f, 1f)
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    seek.drag = (change.position.x / w).coerceIn(0f, 1f)
                                    if (!change.pressed) break
                                }
                                seek.drag?.let { f -> seek.skipTo((f * a.durationMs).toLong()) }
                                seek.drag = null
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val thick by animateDpAsState(if (drag != null) 12.dp else 7.dp, spring(stiffness = 600f), label = "barH")
            Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                val h = thick.toPx()
                val y = (size.height - h) / 2
                // No thumb at rest; the bar grows while a finger is on it.
                drawRoundRect(IosFill, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
                drawRoundRect(IosGrayLight, Offset(0f, y), Size(size.width * fraction, h), CornerRadius(h / 2))
            }
        }
        PillText(
            "-" + time((a.durationMs - shownMs).coerceAtLeast(0)),
            Modifier.width(40.dp),
            size = TEXT_LABEL,
            color = IosGray,
            align = TextAlign.End,
            digits = true,
        )
    }
}

private fun time(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
private fun Transport(
    id: String,
    onClick: () -> Unit,
    size: Dp,
    icon: @Composable () -> Unit,
) {
    Box(Modifier.size(size).clip(CircleShape).pillButton(id, onClick), contentAlignment = Alignment.Center) { icon() }
}

private const val WAVE_PERIOD_MS = 1400L
private const val WAVE_STEP_MS = 33L

/** Thin bars in the artwork's colour that move while playing and rest low while paused. */
@Composable
private fun Waveform(
    a: Music,
    width: Dp,
    maxHeight: Dp,
) {
    val color = Color(a.coverAccentArgb)
    val playing = a.playing
    // The bars step about 30 times a second, not at the display rate; paused, nothing runs, so the main thread stays idle.
    val phase = remember { mutableFloatStateOf(0f) }
    if (playing) {
        LaunchedEffect(Unit) {
            while (true) {
                phase.floatValue = (SystemClock.uptimeMillis() % WAVE_PERIOD_MS) * (2 * Math.PI).toFloat() / WAVE_PERIOD_MS
                delay(WAVE_STEP_MS)
            }
        }
    }
    Canvas(Modifier.size(width, maxHeight)) {
        val n = 6
        val bar = 2.dp.toPx()
        val gap = (size.width - n * bar) / (n - 1)
        for (i in 0 until n) {
            // Each bar runs at its own speed and offset, so the row never repeats exactly in step.
            val wave = (Math.sin(phase.floatValue * (1 + i % 3) + i * 1.7) * 0.5 + 0.5).toFloat()
            val f = if (playing) 0.25f + 0.75f * wave else 0.18f
            val h = size.height * f
            drawRoundRect(
                if (playing) color else IosGray,
                Offset(i * (bar + gap), (size.height - h) / 2),
                Size(bar, h),
                CornerRadius(
                    bar / 2,
                ),
            )
        }
    }
}

@Composable private fun PlayIcon() =
    Canvas(Modifier.size(34.dp)) {
        val p =
            Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.08f)
                lineTo(size.width * 0.92f, size.height / 2)
                lineTo(
                    size.width * 0.2f,
                    size.height * 0.92f,
                )
                close()
            }
        drawPath(p, Color.White)
    }

@Composable private fun PauseIcon() =
    Canvas(Modifier.size(34.dp)) {
        val w = size.width * 0.25f
        drawRoundRect(Color.White, Offset(size.width * 0.15f, size.height * 0.08f), Size(w, size.height * 0.84f), CornerRadius(4f))
        drawRoundRect(Color.White, Offset(size.width * 0.6f, size.height * 0.08f), Size(w, size.height * 0.84f), CornerRadius(4f))
    }

/** Two triangles, pointing right for next and left for previous. */
@Composable private fun SkipIcon(forward: Boolean) =
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height

        fun tri(
            x0: Float,
            x1: Float,
        ) = Path().apply {
            moveTo(x0, h * 0.16f)
            lineTo(x1, h / 2)
            lineTo(x0, h * 0.84f)
            close()
        }
        if (forward) {
            drawPath(tri(0f, w * 0.52f), Color.White)
            drawPath(tri(w * 0.48f, w), Color.White)
        } else {
            drawPath(tri(w, w * 0.48f), Color.White)
            drawPath(tri(w * 0.52f, 0f), Color.White)
        }
    }
