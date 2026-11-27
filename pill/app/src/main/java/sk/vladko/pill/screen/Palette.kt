package sk.vladko.pill.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.vladko.pill.pill
import sk.vladko.pill.system.AppItem

/** Nothing-phone grey and white (dark, grey so the black pill shows against it) or warm paper and ink (light). Red is only for "something is off". */
internal class Pal(
    val bg: Color,
    val panel: Color,
    val raised: Color,
    val line: Color,
    val ink: Color,
    val dim: Color,
    val signal: Color,
    val signalBg: Color,
)

internal val DarkPal =
    Pal(
        Color(0xFF2A2A2D),
        Color(0xFF343438),
        Color(0xFF3F3F44),
        Color(0xFF4B4B51),
        Color(0xFFF2F2EF),
        Color(0xFFA2A2A8),
        Color(0xFFFF4B3E),
        Color(0xFF3D2522),
    )
internal val LightPal =
    Pal(
        Color(0xFFF5F2EC),
        Color(0xFFFFFFFF),
        Color(0xFFEBE7DF),
        Color(0xFFDCD7CD),
        Color(0xFF161615),
        Color(0xFF6B6963),
        Color(0xFFD7332A),
        Color(0xFFFCEAE7),
    )
internal val LocalPal = staticCompositionLocalOf { DarkPal }

internal val Bg: Color @Composable get() = LocalPal.current.bg
internal val Panel: Color @Composable get() = LocalPal.current.panel
internal val Raised: Color @Composable get() = LocalPal.current.raised
internal val Line: Color @Composable get() = LocalPal.current.line
internal val Ink: Color @Composable get() = LocalPal.current.ink
internal val Dim: Color @Composable get() = LocalPal.current.dim
internal val Signal: Color @Composable get() = LocalPal.current.signal
internal val SignalBg: Color @Composable get() = LocalPal.current.signalBg

internal val Mono = FontFamily.Monospace

/** What the picker offers and what it does with the pick. */
internal enum class PickMode { Gesture, ActionsMenu, AppMenu }

internal class PickRequest(
    val title: String,
    val mode: PickMode,
    val current: String?,
    val exclude: Set<String>,
    val onPick: (String) -> Unit,
)

@Composable
internal fun Panel(
    gap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(24.dp),
            ).background(Panel)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
internal fun rememberApp(pkg: String?): AppItem? {
    if (pkg == null) return null
    val apps = LocalContext.current.pill.apps
    val icon by apps.icon(pkg).collectAsState()
    val label = remember(pkg) { apps.label(pkg) }
    return icon?.let { AppItem(pkg, label, it.image) }
}

@Composable
internal fun AppIcon(
    img: ImageBitmap,
    size: Dp,
) {
    Image(img, null, Modifier.size(size).clip(CircleShape))
}
