package sk.vladko.pill.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vladko.pill.actions.APP_ACTION_PREFIX
import sk.vladko.pill.actions.Catalog
import sk.vladko.pill.actions.PillAction
import sk.vladko.pill.pill
import sk.vladko.pill.system.AppItem
import sk.vladko.pill.ui.APPS
import sk.vladko.pill.ui.BOLT_FILLED
import sk.vladko.pill.ui.DND
import sk.vladko.pill.ui.EYE_OFF
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.RECENTS_LIST

private sealed interface Cell {
    class Header(
        val title: String,
    ) : Cell

    class Beh(
        val code: String,
        val name: String,
        val icon: String,
    ) : Cell

    class Act(
        val a: PillAction,
        val reason: String?,
    ) : Cell

    class App(
        val a: AppItem,
    ) : Cell
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Picker(
    req: PickRequest,
    apps: List<AppItem>,
    close: () -> Unit,
) {
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val cells =
        remember(query, apps, req) {
            val q = query.trim().lowercase()

            fun hit(s: String) = q.isEmpty() || s.lowercase().contains(q)
            buildList<Cell> {
                if (req.mode == PickMode.Gesture) {
                    val beh =
                        listOf(
                            Cell.Beh("appmenu", "App menu", APPS),
                            Cell.Beh("actionsmenu", "Actions menu", BOLT_FILLED),
                            Cell.Beh("recents", "Recent apps", RECENTS_LIST),
                            Cell.Beh("hideidle", "Hide idle pill", EYE_OFF),
                            Cell.Beh("none", "Nothing", DND),
                        ).filter { hit(it.name) }
                    if (beh.isNotEmpty()) {
                        add(Cell.Header("Open a menu"))
                        addAll(beh)
                    }
                }
                if (req.mode != PickMode.AppMenu) {
                    Catalog.all.filter { it.id !in req.exclude && hit(it.name) }.groupBy { it.group }.forEach { (g, list) ->
                        add(Cell.Header(g.title))
                        list.forEach { add(Cell.Act(it, it.unavailable(ctx.pill.actionEnv()))) }
                    }
                }
                val appList = apps.filter { (APP_ACTION_PREFIX + it.pkg) !in req.exclude && hit(it.label) }
                if (appList.isNotEmpty()) {
                    add(Cell.Header("Apps"))
                    appList.forEach { add(Cell.App(it)) }
                }
            }
        }

    fun choose(id: String) {
        req.onPick(id)
        close()
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = state, containerColor = Panel, contentColor = Ink) {
        // The sheet stops 64 dp short of the screen top so the status bar and the pill stay visible.
        val windowH = LocalWindowInfo.current.containerSize.height
        val sheetH = with(LocalDensity.current) { windowH.toDp() } - 64.dp - 112.dp
        Column(Modifier.fillMaxWidth().height(sheetH).imePadding()) {
            Text(req.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 24.dp))
            BasicTextField(
                query,
                { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Ink),
                modifier =
                    Modifier
                        .padding(
                            horizontal = 20.dp,
                            vertical = 12.dp,
                        ).fillMaxWidth()
                        .clip(CircleShape)
                        .background(Raised)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search", color = Dim, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            LazyVerticalGrid(
                GridCells.Fixed(4),
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(cells, span = { c ->
                    GridItemSpan(
                        when (c) {
                            is Cell.Header -> 4
                            is Cell.App -> 1
                            else -> 2
                        },
                    )
                }) { c ->
                    when (c) {
                        is Cell.Header ->
                            Text(
                                c.title,
                                color = Dim,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp),
                            )
                        is Cell.Beh ->
                            PickCell(req.current == c.code, onClick = { choose(c.code) }) {
                                Glyph(c.icon, if (c.code == "none") Dim else Ink, 24.dp)
                                CellText(c.name, null, c.code == "none")
                            }
                        is Cell.Act ->
                            PickCell(req.current == c.a.id, dim = c.reason != null, onClick = { choose(c.a.id) }) {
                                Glyph(c.a.icon, Ink, 24.dp)
                                CellText(c.a.name, c.reason)
                            }
                        is Cell.App -> {
                            val id = APP_ACTION_PREFIX + c.a.pkg
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        if (req.current ==
                                            id
                                        ) {
                                            Modifier.border(1.5.dp, Ink, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                        },
                                    ).clickable { choose(id) }
                                    .padding(vertical = 10.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(c.a.icon, 44.dp)
                                Text(
                                    c.a.label,
                                    color = Ink,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickCell(
    selected: Boolean,
    dim: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) {
                    Modifier.border(
                        1.5.dp,
                        Ink,
                        RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier.background(Raised.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                },
            ).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (dim) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun CellText(
    name: String,
    reason: String?,
    dim: Boolean = false,
) {
    Column {
        Text(name, color = if (dim) Dim else Ink, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (reason !=
            null
        ) {
            Text(reason, color = Signal, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
