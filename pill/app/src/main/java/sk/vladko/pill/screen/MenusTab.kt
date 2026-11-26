package sk.vladko.pill.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vladko.pill.actions.APP_ACTION_PREFIX
import sk.vladko.pill.actions.Catalog
import sk.vladko.pill.pill
import sk.vladko.pill.rules.MAX_MENU_ITEMS
import sk.vladko.pill.ui.ADD
import sk.vladko.pill.ui.CHEVRON_LEFT
import sk.vladko.pill.ui.CHEVRON_RIGHT
import sk.vladko.pill.ui.DELETE
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.SWAP_HORIZ

@Composable
internal fun MenusTab(openPicker: (PickRequest) -> Unit) {
    val ctx = LocalContext.current
    val settings by ctx.pill.settings.value
        .collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Menus", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text(
            "Up to four items each, shown left to right. Tap an item to move, swap or remove it.",
            color = Dim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
    MenuTray("App menu", settings.appMenu, PickMode.AppMenu, { list -> ctx.pill.settings.update { it.copy(appMenu = list) } }, openPicker)
    MenuTray("Actions menu", settings.actionsMenu, PickMode.ActionsMenu, { list ->
        ctx.pill.settings.update {
            it.copy(actionsMenu = list)
        }
    }, openPicker)
}

/** A tray of four slots; tapping a filled slot selects it and shows Left / Right / Swap / Remove under the tray. */
@Composable
private fun MenuTray(
    title: String,
    items: List<String>,
    mode: PickMode,
    save: (List<String>) -> Unit,
    openPicker: (PickRequest) -> Unit,
) {
    val max = MAX_MENU_ITEMS

    // The App menu stores bare package names; the picker speaks `app:<package>`.
    fun toPick(s: String) = if (mode == PickMode.AppMenu) APP_ACTION_PREFIX + s else s

    fun fromPick(s: String) = if (mode == PickMode.AppMenu) s.removePrefix(APP_ACTION_PREFIX) else s
    val exclude = items.map(::toPick).toSet()
    var selected by rememberSaveable(title) { mutableStateOf<Int?>(null) }
    val sel = selected?.takeIf { it < items.size }

    fun move(to: Int) {
        save(items.toMutableList().also { l -> l.add(to, l.removeAt(sel!!)) })
        selected = to
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${items.size} of $max", color = Dim, fontSize = 13.sp, fontFamily = Mono)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(24.dp),
                ).background(Panel)
                .border(1.dp, Line, RoundedCornerShape(24.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(max) { i ->
                val raw = items.getOrNull(i)
                if (raw != null) {
                    MenuSlot(raw, mode, i == sel, Modifier.weight(1f)) { selected = if (i == sel) null else i }
                } else {
                    EmptySlot(Modifier.weight(1f)) {
                        selected = null
                        openPicker(PickRequest(title, mode, null, exclude) { p -> save(items + fromPick(p)) })
                    }
                }
            }
        }
        if (sel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotControl("Left", CHEVRON_LEFT, sel > 0, Modifier.weight(1f)) { move(sel - 1) }
                SlotControl("Right", CHEVRON_RIGHT, sel < items.size - 1, Modifier.weight(1f)) { move(sel + 1) }
                SlotControl("Swap", SWAP_HORIZ, true, Modifier.weight(1f)) {
                    openPicker(
                        PickRequest(title, mode, toPick(items[sel]), exclude - toPick(items[sel])) { p ->
                            save(items.toMutableList().also { l -> l[sel] = fromPick(p) })
                        },
                    )
                }
                SlotControl("Remove", DELETE, true, Modifier.weight(1f)) {
                    save(items.filterIndexed { j, _ -> j != sel })
                    selected = null
                }
            }
        }
    }
}

@Composable
private fun MenuSlot(
    raw: String,
    mode: PickMode,
    on: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val aid = if (mode == PickMode.AppMenu) APP_ACTION_PREFIX + raw else raw
    val action = Catalog.byId(aid, ctx.pill.apps)
    val app = rememberApp(if (aid.startsWith(APP_ACTION_PREFIX)) aid.removePrefix(APP_ACTION_PREFIX) else null)
    val reason = action?.unavailable(ctx.pill.actionEnv())
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(96.dp)
            .clip(shape)
            .background(if (on) Raised else Color.Transparent)
            .border(if (on) 1.5.dp else 1.dp, if (on) Ink else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .alpha(if (reason != null || (app == null && action == null)) 0.4f else 1f)
                .then(if (app == null) Modifier.clip(CircleShape).background(Raised) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (app != null) {
                AppIcon(app.icon, 46.dp)
            } else {
                action?.icon?.takeIf { it.isNotEmpty() }?.let { Glyph(it, Ink, 24.dp) }
            }
        }
        Text(
            app?.label ?: action?.short ?: action?.name ?: aid,
            color = if (reason != null) Dim else Ink,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EmptySlot(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).border(1.5.dp, Line, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(ADD, Dim, 20.dp)
        }
        Text("+ Add", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SlotControl(
    label: String,
    path: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(
                46.dp,
            ).clip(
                CircleShape,
            ).background(Panel)
            .border(1.dp, Line, CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(path, if (enabled) Ink else Line, 16.dp)
        Text(
            label,
            color = if (enabled) Ink else Line,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}
