package sk.vladko.pill.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.vladko.pill.actions.APP_ACTION_PREFIX
import sk.vladko.pill.actions.Catalog
import sk.vladko.pill.pill
import sk.vladko.pill.rules.Choice
import sk.vladko.pill.rules.IdleSlot
import sk.vladko.pill.ui.APPS
import sk.vladko.pill.ui.BOLT_FILLED
import sk.vladko.pill.ui.DND
import sk.vladko.pill.ui.EYE_OFF
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.RECENTS_LIST

private fun choiceIconPath(
    c: Choice,
    ctx: Context,
): String? =
    when (c) {
        Choice.AppMenu -> APPS
        Choice.ActionsMenu -> BOLT_FILLED
        Choice.Recents -> RECENTS_LIST
        Choice.HideIdle -> EYE_OFF
        Choice.Nothing -> DND
        is Choice.Action -> Catalog.byId(c.id, ctx.pill.apps)?.icon?.ifEmpty { null }
    }

private fun choiceName(
    ctx: Context,
    c: Choice,
): String =
    when (c) {
        Choice.AppMenu -> "App menu"
        Choice.ActionsMenu -> "Actions menu"
        Choice.Recents -> "Recent apps"
        Choice.HideIdle -> "Hide idle pill"
        Choice.Nothing -> "Nothing"
        is Choice.Action -> Catalog.byId(c.id, ctx.pill.apps)?.name ?: "Unknown action"
    }

private val IdleSlot.label
    get() =
        when (this) {
            IdleSlot.TapLeft -> "Tap left"
            IdleSlot.TapRight -> "Tap right"
            IdleSlot.HoldLeft -> "Hold left"
            IdleSlot.HoldRight -> "Hold right"
            IdleSlot.Volume -> "Volume up + down"
        }

@Composable
internal fun GesturesTab(openPicker: (PickRequest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Gestures", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text(
            "Five ways to use the idle pill. Tap a tile to change it, then try it on the pill.",
            color = Dim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf(
            "Tap" to (IdleSlot.TapLeft to IdleSlot.TapRight),
            "Hold" to (IdleSlot.HoldLeft to IdleSlot.HoldRight),
        ).forEach { (name, pair) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GestureTile(pair.first, left = true, Modifier.weight(1f), openPicker)
                    // The camera hole between the two halves.
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Bg)
                                .border(1.5.dp, Line, CircleShape),
                        )
                    }
                    GestureTile(pair.second, left = false, Modifier.weight(1f), openPicker)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Both volume keys", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            GestureTile(IdleSlot.Volume, left = null, Modifier.fillMaxWidth(), openPicker)
        }
    }
}

@Composable
private fun GestureTile(
    slot: IdleSlot,
    left: Boolean?,
    modifier: Modifier,
    openPicker: (PickRequest) -> Unit,
) {
    val ctx = LocalContext.current
    val settings by ctx.pill.settings.value
        .collectAsState()
    val choice = settings.gestures[slot] ?: Choice.Nothing
    val action = (choice as? Choice.Action)?.let { Catalog.byId(it.id, ctx.pill.apps) }
    val reason = action?.unavailable(ctx.pill.actionEnv())
    val app =
        rememberApp(
            action?.appPackage
                ?: (choice as? Choice.Action)?.id?.takeIf { it.startsWith(APP_ACTION_PREFIX) }?.removePrefix(APP_ACTION_PREFIX),
        )
    val big = 44.dp
    val small = 10.dp
    val shape: Shape =
        when (left) {
            true -> RoundedCornerShape(big, small, small, big)
            false -> RoundedCornerShape(small, big, big, small)
            null -> RoundedCornerShape(big)
        }
    val name = app?.label ?: choiceName(ctx, choice)
    val code =
        when (choice) {
            is Choice.Action -> choice.id
            else -> choice.code
        }
    val align =
        when (left) {
            true -> TextAlign.Start
            false -> TextAlign.End
            null -> TextAlign.Center
        }
    Column(
        modifier
            .height(if (left == null) 104.dp else 132.dp)
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .clickable {
                openPicker(
                    PickRequest(slot.label, PickMode.Gesture, code, emptySet()) { id ->
                        val c = Choice.parse(id) ?: Choice.Action(id)
                        ctx.pill.settings.update { it.copy(gestures = it.gestures + (slot to c)) }
                    },
                )
            }.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment =
            when (left) {
                true -> Alignment.Start
                false -> Alignment.End
                null -> Alignment.CenterHorizontally
            },
    ) {
        Box(Modifier.alpha(if (reason != null) 0.4f else 1f)) {
            if (app != null) {
                AppIcon(app.icon, 34.dp)
            } else {
                choiceIconPath(choice, ctx)?.let { Glyph(it, if (choice == Choice.Nothing) Dim else Ink, 34.dp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            color =
                if (choice ==
                    Choice.Nothing
                ) {
                    Dim
                } else {
                    Ink
                },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            lineHeight = 18.sp,
        )
        if (reason !=
            null
        ) {
            Text(
                reason,
                color = Signal,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
            )
        }
    }
}
