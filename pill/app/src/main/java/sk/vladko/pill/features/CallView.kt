package sk.vladko.pill.features

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sk.vladko.pill.engine.ItemView
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.ui.AlarmOrange
import sk.vladko.pill.ui.BUTTON_GAP
import sk.vladko.pill.ui.BUTTON_GLYPH
import sk.vladko.pill.ui.BUTTON_SIZE
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.HoleRow
import sk.vladko.pill.ui.IosGray
import sk.vladko.pill.ui.PhoneGlyph
import sk.vladko.pill.ui.PillGreen
import sk.vladko.pill.ui.PillIconButton
import sk.vladko.pill.ui.PillRed
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.TEXT_TITLE
import sk.vladko.pill.ui.TEXT_VALUE
import sk.vladko.pill.ui.tapToOpen

object CallView : ItemView<Call> {
    override val compactWidth: Dp = 184.dp

    @Composable
    override fun Compact(
        item: Call,
        pill: PillControls,
    ) {
        HoleRow(
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val connectedAt = item.connectedAtWall
                    if (connectedAt != null) {
                        PhoneGlyph(PillGreen, 15.dp)
                        PillText(duration(connectedAt), size = TEXT_VALUE, color = PillGreen, bold = true, digits = true)
                    } else {
                        PhoneGlyph(Color.White, 15.dp)
                        PillText("Calling", size = TEXT_VALUE, color = IosGray)
                    }
                }
            },
            trailing = { if (item.connectedAtWall != null) CallWave() },
        )
    }

    @Composable
    override fun Large(
        item: Call,
        pill: PillControls,
    ) {
        val connectedAt = item.connectedAtWall
        CanopyContent(
            Modifier.tapToOpen(pill, item::open),
            crownLeft = { PhoneGlyph(if (item.ringing || connectedAt == null) Color.White else PillGreen, 15.dp) },
            crownRight = { if (connectedAt != null) CallWave() },
        ) {
            Row(
                Modifier.fillMaxWidth().height(BUTTON_SIZE),
                horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(item.name, BUTTON_SIZE, item.photo)
                Column(Modifier.weight(1f)) {
                    if (item.ringing) {
                        PillText("Incoming call", size = TEXT_VALUE, color = IosGray)
                        PillText(item.name.ifBlank { "Unknown" }, size = 20, bold = true)
                    } else if (connectedAt == null) {
                        PillText("Calling", size = TEXT_VALUE, color = IosGray)
                        PillText(item.name.ifBlank { "Unknown" }, size = TEXT_TITLE, bold = true)
                    } else {
                        PillText(duration(connectedAt), size = TEXT_VALUE, color = PillGreen, bold = true, digits = true)
                        PillText(item.name.ifBlank { "Unknown" }, size = TEXT_TITLE, bold = true)
                    }
                }
                if (item.ringing) {
                    PillIconButton("decline", item::hangup, BUTTON_SIZE, PillRed) { PhoneGlyph(Color.White, BUTTON_GLYPH, hangUp = true) }
                    PillIconButton("answer", item::answer, BUTTON_SIZE, PillGreen) { PhoneGlyph(Color.White, BUTTON_GLYPH) }
                } else {
                    PillIconButton("hangup", item::hangup, BUTTON_SIZE, PillRed) { PhoneGlyph(Color.White, BUTTON_GLYPH, hangUp = true) }
                }
            }
        }
    }
}

/**
 * Decorative level bars right of the hole, green on the left half and orange on the right, like the iPhone's
 * two voices. Android gives no call audio level to an app, so the motion shows "call in progress", not real sound.
 */
@Composable
private fun CallWave() {
    val t = rememberInfiniteTransition(label = "callwave")
    val phase = t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    Canvas(Modifier.size(width = 30.dp, height = 18.dp)) {
        val n = 12
        val bar = 1.5.dp.toPx()
        val gap = (size.width - n * bar) / (n - 1)
        for (i in 0 until n) {
            val wave = (Math.sin(phase.value * (1 + i % 3) + i * 1.3) * 0.5 + 0.5).toFloat()
            val h = size.height * (0.15f + 0.85f * wave * (0.55f + 0.45f * Math.sin(i * 0.9).toFloat().let { it * it }))
            drawRoundRect(
                if (i <
                    n / 2
                ) {
                    PillGreen
                } else {
                    AlarmOrange
                },
                Offset(i * (bar + gap), (size.height - h) / 2),
                Size(bar, h),
                CornerRadius(bar / 2),
            )
        }
    }
}

/** Grey circle with the caller's initial, or a handset when the name is a number. */
@Composable
private fun Avatar(
    name: String,
    size: Dp,
    photo: Bitmap? = null,
) {
    val initial = name.trim().firstOrNull()?.takeIf { it.isLetter() }
    val img = remember(photo) { photo?.asImageBitmap() }
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF6E6E73)), contentAlignment = Alignment.Center) {
        if (img != null) {
            Image(img, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else if (initial != null) {
            PillText(initial.uppercase(), size = 20, bold = true)
        } else {
            PhoneGlyph(Color.White, size * 0.5f)
        }
    }
}

@Composable
private fun duration(connectedAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val s = ((now - connectedAt) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
