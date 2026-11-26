package sk.vladko.pill.overlay

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PathMeasure
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.get
import androidx.core.graphics.scale
import android.graphics.Color as AndroidColor
import android.graphics.Path as AndroidPath

/** Arrival glow: when something new appears, a light runs around the pill. The session starts it, [arrivalGlow] draws it. */
class Glow {
    companion object {
        const val DURATION_MS = 1000L
    }

    /** One arrival glow: a new id restarts the animation. */
    class Run(
        val id: Long,
        val color: Color,
        val startedAt: Long,
    )

    var current: Run? by mutableStateOf(null)
        private set
    private var counter = 0L

    fun start(color: Color) {
        current = Run(++counter, color, SystemClock.uptimeMillis())
    }

    /** Ends the running glow for good (a menu opened). */
    fun stop() {
        current = null
    }

    fun running(): Boolean = current?.let { SystemClock.uptimeMillis() - it.startedAt < DURATION_MS } == true
}

/** The picture's main colour (average of its colourful pixels, lifted so it shows on black and on light wallpapers); white when it has none. */
fun accentOf(b: Bitmap?): Color {
    if (b == null) return Color.White
    val s = b.scale(12, 12)
    val hsv = FloatArray(3)
    var x = 0f
    var y = 0f
    var sat = 0f
    var wsum = 0f
    for (px in 0 until 12) {
        for (py in 0 until 12) {
            val c = s[px, py]
            if (AndroidColor.alpha(c) < 128) continue
            AndroidColor.colorToHSV(c, hsv)
            val w = hsv[1] * hsv[2]
            val a = Math.toRadians(hsv[0].toDouble())
            x += (Math.cos(a) * w).toFloat()
            y += (Math.sin(a) * w).toFloat()
            sat += hsv[1] * w
            wsum += w
        }
    }
    if (wsum < 3f) return Color.White
    val hue = ((Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())) + 360) % 360).toFloat()
    return Color(AndroidColor.HSVToColor(floatArrayOf(hue, (sat / wsum).coerceIn(0.55f, 0.95f), 0.95f)))
}

/** Runs [progress] from 0 to 1 whenever a new glow starts. Keep it in one place above the pill so a change of branch does not replay it. */
@Composable
fun rememberGlowProgress(glow: Glow): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(1f) }
    val run = glow.current
    LaunchedEffect(run?.id) {
        if (run != null && glow.running()) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Glow.DURATION_MS.toInt(), easing = LinearEasing))
        }
    }
    return progress
}

/**
 * A comet with a soft tail runs once around the outline of [shape] (the same path that clips the pill) at an even speed, over a faint glow of the
 * whole outline that swells and fades once. Drawn on top, outside the clip, so it is also visible beyond the edge;
 * it follows the shape while the shape is still animating. It draws only; touch areas are untouched.
 */
@Composable
fun Modifier.arrivalGlow(
    glow: Glow,
    progress: Animatable<Float, AnimationVector1D>,
    shape: () -> OutlineShape,
): Modifier {
    val d = LocalDensity.current.density
    val core =
        remember(d) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * d
            }
        }
    val soft =
        remember(d) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 7f * d
                maskFilter = BlurMaskFilter(7f * d, BlurMaskFilter.Blur.NORMAL)
            }
        }
    val breath =
        remember(d) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f * d
                maskFilter = BlurMaskFilter(9f * d, BlurMaskFilter.Blur.NORMAL)
            }
        }
    val measure = remember { PathMeasure() }
    val seg = remember { AndroidPath() }
    return drawWithContent {
        drawContent()
        val p = progress.value
        val run = glow.current
        if (run == null || p >= 1f) return@drawWithContent
        val path = shape().path(size, density)
        measure.setPath(path, true)
        val len = measure.length
        if (len <= 1f) return@drawWithContent
        val fade = minOf(p / 0.08f, 1f) * minOf((1f - p) / 0.3f, 1f)
        // Ease in and out of the run, so the comet gathers speed and slows before it fades.
        val e = p * p * (3f - 2f * p)
        val head = e * len
        val tail = len * 0.3f
        drawIntoCanvas { c ->
            val cv = c.nativeCanvas
            breath.color = run.color.copy(alpha = (Math.sin(Math.PI * p).toFloat() * 0.5f * fade).coerceIn(0f, 1f)).toArgb()
            cv.drawPath(path, breath)
            val n = 18
            for (i in 0 until n) {
                val a = head - tail * (i + 1) / n
                val b = head - tail * i / n
                seg.rewind()
                if (a >= 0f) {
                    measure.getSegment(a, b, seg, true)
                } else {
                    measure.getSegment(len + a, len, seg, true)
                    measure.getSegment(0f, maxOf(b, 0f), seg, true)
                }
                val k = Math.pow(1.0 - i.toDouble() / n, 1.6).toFloat() * fade
                val tint = lerp(run.color, Color.White, 0.3f * (1f - i.toFloat() / n))
                soft.color = tint.copy(alpha = k * 0.8f).toArgb()
                core.color = tint.copy(alpha = k).toArgb()
                cv.drawPath(seg, soft)
                cv.drawPath(seg, core)
            }
        }
    }
}
