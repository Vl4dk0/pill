package sk.vladko.pill.overlay

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sk.vladko.pill.actions.Catalog
import sk.vladko.pill.actions.PillAction
import sk.vladko.pill.engine.PillSession
import sk.vladko.pill.pill
import sk.vladko.pill.system.AppItem
import sk.vladko.pill.ui.BUTTON_GLYPH
import sk.vladko.pill.ui.BUTTON_SIZE
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.IosFill
import sk.vladko.pill.ui.IosFillPressed
import sk.vladko.pill.ui.IosGray
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.TEXT_LABEL
import sk.vladko.pill.ui.pillButton

private fun launch(
    ctx: Context,
    session: PillSession,
    pkg: String,
) {
    ctx.pill.apps.launch(pkg)
    session.pill.close()
}

@Composable
private fun AppRow(
    session: PillSession,
    prefix: String,
    items: List<AppItem>,
    empty: String,
) {
    val ctx = LocalContext.current
    val controls = LocalControls.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(TILE_HEIGHT), contentAlignment = Alignment.Center) {
            PillText(empty, color = IosGray, lines = 2, align = TextAlign.Center)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        items.forEach { app ->
            val id = "$prefix:${app.pkg}"
            val hovered = controls.hover == id
            val scale by animateFloatAsState(if (hovered) 1.08f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "hover")
            Column(
                Modifier.width(ICON_SIZE + 32.dp).height(TILE_HEIGHT).pillButton(id) { launch(ctx, session, app.pkg) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppCircle(app.icon, app.label, hovered, scale)
                PillText(
                    app.label,
                    Modifier.padding(top = 8.dp).width(ICON_SIZE + 32.dp),
                    size = TEXT_LABEL,
                    color = Color.White,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

private val ICON_SIZE = 52.dp
private val RING_GAP = 3.dp
private val RING_WIDTH = 2.dp
private val TILE_HEIGHT = 74.dp

/** Every app icon in the menus: a filled circle; the selected one gets a white ring around it, concentric, with a small gap. */
@Composable
private fun AppCircle(
    icon: ImageBitmap?,
    label: String,
    selected: Boolean,
    scale: Float,
) {
    Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
        val ringMod =
            Modifier
                .size(ICON_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.drawWithContent {
                    drawContent()
                    if (selected) {
                        drawCircle(
                            Color.White,
                            size.minDimension / 2 + (RING_GAP + RING_WIDTH / 2).toPx(),
                            style = Stroke(RING_WIDTH.toPx()),
                        )
                    }
                }.clip(CircleShape)
        if (icon != null) {
            Image(icon, label, ringMod)
        } else {
            Box(ringMod.background(IosFill))
        }
    }
}

@Composable
fun FastMenu(session: PillSession) {
    val ctx = LocalContext.current
    val controls = LocalControls.current
    val apps = ctx.pill.apps
    val settings by ctx.pill.settings.value
        .collectAsState()
    val pkgs = remember(settings.appMenu) { settings.appMenu.filter(apps::installed).take(4) }
    if (pkgs.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(TILE_HEIGHT), contentAlignment = Alignment.Center) {
            PillText("No shortcuts chosen.\nPick them in Pill.", color = IosGray, lines = 2, align = TextAlign.Center)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        pkgs.forEach { pkg ->
            val id = "fast:$pkg"
            val icon by apps.icon(pkg).collectAsState()
            val label = remember(pkg) { apps.label(pkg) }
            val hovered = controls.hover == id
            val scale by animateFloatAsState(if (hovered) 1.08f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "hover")
            Column(
                Modifier.width(ICON_SIZE + 32.dp).height(TILE_HEIGHT).pillButton(id) { launch(ctx, session, pkg) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppCircle(icon?.image, label, hovered, scale)
                PillText(
                    label,
                    Modifier.padding(top = 8.dp).width(ICON_SIZE + 32.dp),
                    size = TEXT_LABEL,
                    color = Color.White,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun RecentApps(session: PillSession) {
    val ctx = LocalContext.current
    val items by produceState<List<AppItem>?>(null) {
        value = runCatching { ctx.pill.apps.recents() }.getOrDefault(emptyList())
    }
    if (items == null) {
        Box(Modifier.fillMaxWidth().height(TILE_HEIGHT))
        return
    }
    AppRow(session, "recent", items.orEmpty(), "No recent apps yet.\nUsage access may be off.")
}

@Composable
fun QuickMenu(session: PillSession) {
    val ctx = LocalContext.current
    val settings by ctx.pill.settings.value
        .collectAsState()
    val ids = settings.actionsMenu
    val actions = remember(ids) { ids.mapNotNull { Catalog.byId(it, ctx.pill.apps) } }
    val states = rememberActionStates(actions)
    if (ids.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(TILE_HEIGHT), contentAlignment = Alignment.Center) {
            PillText("No actions chosen.\nPick them in Pill.", color = IosGray, lines = 2, align = TextAlign.Center)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        actions.forEach { action ->
            val unavailable = action.unavailable(ctx.pill.actionEnv()) != null
            if (action.appPackage != null) {
                QuickAppTile(session, action.id, action.appPackage)
            } else {
                QuickTile(
                    action.id,
                    action.short,
                    action.icon,
                    active = states[action.id] == true,
                    dim = unavailable,
                ) { session.runAction(action.id, fromMenu = true) }
            }
        }
    }
}

/**
 * On/off state of the given actions, re-read every 600 ms while this is in the composition (the system
 * switches change from outside Pill too). Key = action id; absent = no state (not a Toggle).
 */
@Composable
private fun rememberActionStates(actions: List<PillAction>): Map<String, Boolean> {
    val ctx = LocalContext.current
    var states by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    LaunchedEffect(actions.map { it.id }) {
        while (true) {
            states = actions.mapNotNull { a -> a.isOn(ctx.pill.actionEnv())?.let { a.id to it } }.toMap()
            delay(600)
        }
    }
    return states
}

/** An installed app as a quick action: its icon, and a tap launches it. */
@Composable
private fun QuickAppTile(
    session: PillSession,
    id: String,
    pkg: String,
) {
    val ctx = LocalContext.current
    val controls = LocalControls.current
    val apps = ctx.pill.apps
    if (!remember(pkg) { apps.installed(pkg) }) return
    val icon by apps.icon(pkg).collectAsState()
    val label = remember(pkg) { apps.label(pkg) }
    val hovered = controls.hover == id
    Column(
        Modifier.width(ICON_SIZE + 32.dp).height(TILE_HEIGHT).pillButton(id) {
            launch(ctx, session, pkg)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppCircle(icon?.image, label, hovered, 1f)
        PillText(
            label,
            Modifier.padding(top = 8.dp).width(ICON_SIZE + 32.dp),
            size = TEXT_LABEL,
            color = Color.White,
            align = TextAlign.Center,
        )
    }
}

@Composable
private fun QuickTile(
    id: String,
    label: String,
    icon: String,
    active: Boolean = false,
    dim: Boolean = false,
    onClick: () -> Unit,
) {
    val hovered = LocalControls.current.hover == id
    Column(
        Modifier
            .width(ICON_SIZE + 32.dp)
            .height(TILE_HEIGHT)
            .graphicsLayer {
                alpha = if (dim) 0.4f else 1f
            }.pillButton(id, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(BUTTON_SIZE)
                .clip(CircleShape)
                .background(
                    if (active) {
                        Color.White
                    } else if (hovered) {
                        IosFillPressed
                    } else {
                        IosFill
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Glyph(icon, if (active) Color.Black else Color.White, BUTTON_GLYPH) }
        PillText(
            label,
            Modifier.padding(top = 4.dp).width(ICON_SIZE + 32.dp),
            size = TEXT_LABEL,
            color = Color.White,
            align = TextAlign.Center,
        )
    }
}
