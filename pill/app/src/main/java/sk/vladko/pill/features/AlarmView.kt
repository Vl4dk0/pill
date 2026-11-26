package sk.vladko.pill.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sk.vladko.pill.engine.ItemView
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.ui.ALARM
import sk.vladko.pill.ui.AlarmOrange
import sk.vladko.pill.ui.BUTTON_GAP
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.CapsuleButton
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.HoleRow
import sk.vladko.pill.ui.IosFill
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.TEXT_LABEL
import sk.vladko.pill.ui.TEXT_TITLE
import sk.vladko.pill.ui.TEXT_VALUE
import sk.vladko.pill.ui.tapToOpen

object AlarmView : ItemView<Alarm> {
    override val compactWidth: Dp = 190.dp

    @Composable
    override fun Compact(
        item: Alarm,
        pill: PillControls,
    ) {
        HoleRow(
            leading = { Glyph(ALARM, AlarmOrange, 18.dp) },
            trailing = { PillText(item.title, size = TEXT_VALUE, color = AlarmOrange, bold = true, digits = true) },
        )
    }

    @Composable
    override fun Large(
        item: Alarm,
        pill: PillControls,
    ) {
        CanopyContent(
            Modifier.tapToOpen(pill, item::open),
            crownLeft = { Glyph(ALARM, AlarmOrange, 18.dp) },
            crownRight = { PillText(item.title, size = TEXT_LABEL, color = AlarmOrange, bold = true, digits = true) },
        ) {
            PillText(item.detail.ifBlank { item.title }, Modifier.padding(bottom = 12.dp), size = TEXT_TITLE, bold = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                item.buttons.take(3).forEachIndexed { i, b ->
                    val name =
                        b.title
                            .lowercase()
                            .trim()
                            .replace(Regex("[^a-z0-9]+"), "_")
                            .trim('_')
                            .ifBlank { "button$i" }
                    val stop = name.contains("stop") || name.contains("dismiss")
                    CapsuleButton(name, b.title, {
                        item.press(b)
                    }, Modifier.weight(1f), color = if (stop) AlarmOrange else IosFill, textColor = if (stop) Color.Black else Color.White)
                }
            }
        }
    }
}
