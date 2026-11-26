package sk.vladko.pill.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vladko.pill.engine.CompactSides
import sk.vladko.pill.engine.ItemView
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.idle.LocalPetFoot
import sk.vladko.pill.idle.PetSpot
import sk.vladko.pill.pill
import sk.vladko.pill.ui.BoltGlyph
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.HoleRow
import sk.vladko.pill.ui.IosGray
import sk.vladko.pill.ui.PillGreen
import sk.vladko.pill.ui.PillRed
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.TEXT_LABEL
import sk.vladko.pill.ui.TEXT_TITLE
import sk.vladko.pill.ui.TEXT_VALUE
import sk.vladko.pill.ui.holeRowSides

/** The percent as [PillText] draws it, for measuring. */
private val PERCENT_STYLE = TextStyle(fontSize = TEXT_VALUE.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

object ChargerView : ItemView<Charger> {
    override val compactWidth: Dp = 158.dp

    // With the pet on, the pill grows until the pet's spot and the whole percent fit.
    @Composable
    override fun compactSides(
        item: Charger,
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
        val measurer = rememberTextMeasurer()
        val textPx =
            remember(item.percent) { measurer.measure("${item.percent}%", PERCENT_STYLE, softWrap = false, maxLines = 1).size.width }
        val text = with(LocalDensity.current) { textPx.toDp() } + 2.dp
        return if (pet) holeRowSides(hole, foot, text, minSide, maxLeft, maxRight) else null
    }

    /** Green while charging, red when the battery is nearly empty. */
    private fun tint(e: Charger): Color = if (e.percent < 20) PillRed else PillGreen

    @Composable
    override fun Compact(
        item: Charger,
        pill: PillControls,
    ) {
        val pet =
            LocalContext.current.pill.settings.value
                .collectAsState()
                .value.pet
        HoleRow(
            // With the pet on, the pet lives where the bolt would be.
            leading = { if (pet) PetSpot(Modifier.size(LocalPetFoot.current)) else BoltGlyph(tint(item), 22.dp) },
            trailing = { PillText("${item.percent}%", size = TEXT_VALUE, color = tint(item), bold = true, digits = true) },
        )
    }

    @Composable
    override fun Large(
        item: Charger,
        pill: PillControls,
    ) {
        CanopyContent(
            crownLeft = { BoltGlyph(tint(item), 18.dp) },
            crownRight = { PillText("${item.percent}%", size = TEXT_LABEL, color = tint(item), bold = true, digits = true) },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoltGlyph(tint(item), 36.dp)
                Column {
                    PillText("Charging", size = TEXT_TITLE, bold = true)
                    PillText("${item.percent}%", size = TEXT_TITLE, color = IosGray, digits = true)
                }
            }
        }
    }
}
