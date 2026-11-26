package sk.vladko.pill.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vladko.pill.idle.PetIdle
import sk.vladko.pill.pill
import sk.vladko.pill.rules.DEFAULT_IDLE_WIDTH_DP
import sk.vladko.pill.rules.MAX_IDLE_HEIGHT_DP
import sk.vladko.pill.rules.MAX_IDLE_WIDTH_DP
import sk.vladko.pill.rules.MIN_IDLE_WIDTH_DP
import sk.vladko.pill.rules.PetAnimal
import sk.vladko.pill.rules.PetState
import sk.vladko.pill.rules.ThemeMode
import sk.vladko.pill.rules.idleHeight
import sk.vladko.pill.system.screenGeometry
import kotlin.math.roundToInt

@Composable
internal fun PillTab() {
    val ctx = LocalContext.current
    val settings by ctx.pill.settings.value
        .collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pill", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text("Drag a slider and watch the real pill at the top of the screen.", color = Dim, fontSize = 14.sp, lineHeight = 20.sp)
    }
    val config = LocalConfiguration.current
    val holeH = remember(config) { screenGeometry(ctx).anchor.h }
    val minH = minOf(holeH + 4f, MAX_IDLE_HEIGHT_DP)
    val width = settings.idleWidthDp.coerceIn(MIN_IDLE_WIDTH_DP, MAX_IDLE_WIDTH_DP)
    val height = idleHeight(settings.idleHeightDp, holeH).coerceIn(minH, MAX_IDLE_HEIGHT_DP)
    val custom = settings.idleWidthDp != DEFAULT_IDLE_WIDTH_DP || settings.idleHeightDp != null
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Idle size", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (custom) {
                Text(
                    "Reset",
                    color = Dim,
                    fontSize = 14.sp,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable {
                                ctx.pill.settings.update { it.copy(idleWidthDp = DEFAULT_IDLE_WIDTH_DP, idleHeightDp = null) }
                            }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        SliderRow("Width", width, MIN_IDLE_WIDTH_DP..MAX_IDLE_WIDTH_DP) { v ->
            ctx.pill.settings.update { it.copy(idleWidthDp = v.roundToInt().toFloat()) }
        }
        SliderRow("Height", height, minH..MAX_IDLE_HEIGHT_DP) { v ->
            ctx.pill.settings.update { it.copy(idleHeightDp = v.roundToInt().toFloat()) }
        }
    }
    Panel {
        SwitchRow("Arrival glow", "A light runs around the pill when something new appears", settings.arrivalGlow) { on ->
            ctx.pill.settings.update { it.copy(arrivalGlow = on) }
        }
    }
    Panel {
        SwitchRow("Pet", "A little animal lives in the idle pill and dances to music", settings.pet) { on ->
            ctx.pill.settings.update { it.copy(pet = on) }
        }
    }
    if (settings.pet) {
        Segmented("Pet", PetAnimal.entries, settings.petAnimal, { it.id }, { it.label }) { animal ->
            ctx.pill.settings.update { it.copy(petAnimal = animal.id) }
        }
        PetAnimations(PetAnimal.of(settings.petAnimal))
    }
    Segmented("Theme", ThemeMode.entries, settings.theme, { it }, { it.label }) { mode ->
        ctx.pill.settings.update { it.copy(theme = mode) }
    }
    val players = settings.playersSeen
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Players", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp))
        if (players.isEmpty()) {
            Text(
                "No player seen yet. Play something and it appears here.",
                color = Dim,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        } else {
            Panel(gap = 4.dp) {
                players.forEach { pkg ->
                    val app = rememberApp(pkg)
                    SwitchRow(app?.label ?: pkg, "Show in the pill", pkg !in settings.playersOff, icon = app?.icon) { shown ->
                        ctx.pill.settings.update { it.copy(playersOff = if (shown) it.playersOff - pkg else it.playersOff + pkg) }
                    }
                }
            }
        }
    }
}

/** The label of a pet state on its chip. */
private fun PetState.chip(): String =
    when (this) {
        PetState.LookAround -> "Look around"
        PetState.Hide -> "Hide and peek"
        PetState.Lick -> "Lick paw"
        PetState.Wings -> "Flap wings"
        PetState.Roam -> "Leave the pill"
        else -> id.replaceFirstChar { it.uppercase() }
    }

/**
 * Every action [animal] does, one chip each; a tap plays it in the real pill now and the pet then goes on with its life.
 * Built from the machine's own states, so a new state appears by itself. Hide covers the hole spells (Hidden, Peek) that follow it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetAnimations(animal: PetAnimal) {
    val states = PetState.entries.filter { (it.animal == null || it.animal == animal) && it != PetState.Hidden && it != PetState.Peek }
    Panel {
        Text("Animations", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            states.forEach { state ->
                Text(
                    state.chip(),
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(Raised)
                            .clickable { PetIdle.play(state) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** A title and a row of choices; [key] tells which option is [selected]. */
@Composable
private fun <T, K> Segmented(
    title: String,
    options: List<T>,
    selected: K,
    key: (T) -> K,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(Raised).padding(3.dp)) {
                options.forEach { option ->
                    val on = key(option) == selected
                    Text(
                        label(option),
                        color = if (on) Bg else Dim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(if (on) Ink else Color.Transparent)
                                .clickable { onSelect(option) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} dp", color = Ink, fontSize = 15.sp, fontFamily = Mono)
        }
        Slider(
            value,
            onChange,
            valueRange = range,
            colors =
                SliderDefaults.colors(
                    thumbColor = Ink,
                    activeTrackColor = Ink,
                    inactiveTrackColor = Raised,
                    activeTickColor = Ink,
                    inactiveTickColor = Raised,
                ),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    sub: String,
    on: Boolean,
    icon: ImageBitmap? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            AppIcon(icon, 36.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            on,
            onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Bg,
                    checkedTrackColor = Ink,
                    checkedBorderColor = Ink,
                    uncheckedThumbColor = Dim,
                    uncheckedTrackColor = Raised,
                    uncheckedBorderColor = Line,
                ),
        )
    }
}
