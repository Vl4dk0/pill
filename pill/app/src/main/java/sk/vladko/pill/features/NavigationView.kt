package sk.vladko.pill.features

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.vladko.pill.engine.ItemView
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.CapsuleButton
import sk.vladko.pill.ui.HoleRow
import sk.vladko.pill.ui.IosFill
import sk.vladko.pill.ui.IosGray
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.TEXT_COMPACT
import sk.vladko.pill.ui.TEXT_LABEL
import sk.vladko.pill.ui.TEXT_TITLE
import sk.vladko.pill.ui.TEXT_VALUE
import sk.vladko.pill.ui.tapToOpen

object NavigationView : ItemView<Navigation> {
    override val compactWidth: Dp = 159.dp

    @Composable
    override fun Compact(
        item: Navigation,
        pill: PillControls,
    ) {
        HoleRow(
            leading = { Arrow(item, 22.dp) },
            trailing = { item.value?.let { PillText(it, size = TEXT_VALUE, bold = true, digits = true) } },
        )
    }

    @Composable
    override fun Large(
        item: Navigation,
        pill: PillControls,
    ) {
        CanopyContent(
            Modifier.tapToOpen(pill, item::open),
            crownLeft = { Arrow(item, 18.dp) },
            crownRight = { item.value?.let { PillText(it, size = TEXT_LABEL, bold = true, digits = true) } },
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Arrow(item, 44.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    PillText(item.title, size = TEXT_TITLE, bold = true, lines = 2)
                    item.text?.let { PillText(it, size = TEXT_COMPACT, color = IosGray) }
                    item.subText?.let { PillText(it, size = TEXT_COMPACT, color = IosGray) }
                }
            }
            if (item.progressMax > 0) {
                val done = (item.progress.toFloat() / item.progressMax).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(IosFill),
                ) {
                    Box(Modifier.fillMaxWidth(done).height(3.dp).background(Color.White))
                }
            }
            item.button?.let { b ->
                CapsuleButton("navaction", b.title, { item.press(b) }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The turn arrow, shown as is but tinted white. */
@Composable
private fun Arrow(
    item: Navigation,
    size: Dp,
) {
    val img = remember(item.arrow) { item.arrow?.asImageBitmap() }
    if (img != null) {
        Image(img, null, Modifier.size(size), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White))
    }
}
