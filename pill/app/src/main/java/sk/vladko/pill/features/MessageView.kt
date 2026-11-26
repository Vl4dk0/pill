package sk.vladko.pill.features

import android.app.Notification
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sk.vladko.pill.engine.CompactSides
import sk.vladko.pill.engine.ItemView
import sk.vladko.pill.engine.PillControls
import sk.vladko.pill.overlay.LocalControls
import sk.vladko.pill.pill
import sk.vladko.pill.rules.NoteAction
import sk.vladko.pill.ui.ARCHIVE
import sk.vladko.pill.ui.ArrowUpGlyph
import sk.vladko.pill.ui.BELL
import sk.vladko.pill.ui.CLOSE
import sk.vladko.pill.ui.COMPACT_INSET
import sk.vladko.pill.ui.COPY
import sk.vladko.pill.ui.CanopyContent
import sk.vladko.pill.ui.DELETE
import sk.vladko.pill.ui.DISLIKE
import sk.vladko.pill.ui.Glyph
import sk.vladko.pill.ui.IosFill
import sk.vladko.pill.ui.IosGray
import sk.vladko.pill.ui.IosGrayLight
import sk.vladko.pill.ui.LIKE
import sk.vladko.pill.ui.LocalHole
import sk.vladko.pill.ui.MARK_READ
import sk.vladko.pill.ui.MARK_UNREAD
import sk.vladko.pill.ui.MORE_HORIZ
import sk.vladko.pill.ui.MUTE
import sk.vladko.pill.ui.OPEN_IN_NEW
import sk.vladko.pill.ui.PhoneGlyph
import sk.vladko.pill.ui.PillButtonHover
import sk.vladko.pill.ui.PillText
import sk.vladko.pill.ui.PillTextField
import sk.vladko.pill.ui.REPLY
import sk.vladko.pill.ui.SNOOZE
import sk.vladko.pill.ui.TEXT_COMPACT
import sk.vladko.pill.ui.TEXT_LABEL
import sk.vladko.pill.ui.TEXT_TITLE
import sk.vladko.pill.ui.fadeEdge
import sk.vladko.pill.ui.pillButton
import sk.vladko.pill.ui.snapRightForStatusBar
import sk.vladko.pill.ui.tapToOpen
import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.roundToInt

private val SENDER_STYLE = TextStyle(color = Color.White, fontSize = TEXT_COMPACT.sp, fontWeight = FontWeight.SemiBold)
private val BODY_STYLE = TextStyle(color = IosGrayLight, fontSize = TEXT_COMPACT.sp)

private val SENDER_START = COMPACT_INSET
private val SENDER_END = 8.dp
private val ICON_SIZE = 20.dp
private val ICON_GAP = 7.dp
private val BODY_START = 8.dp
private val BODY_END = 14.dp
private val MEASURE_SLACK = 2.dp
private val MINI = 34.dp
private val ROW_GAP = 6.dp

private const val SCROLL_START_HOLD_MS = 1000L
private const val SCROLL_END_HOLD_MS = 1500L
private const val SCROLL_END_BUFFER_MS = 200L
private const val SCROLL_DP_PER_SEC = 70f
private const val MIN_SCROLL_MS = 600L

private fun compactBody(e: Message) = e.text.ifBlank { e.appName }.replace('\n', ' ')

/**
 * A message is one line inside the pill: icon and sender left of the camera hole, the text right of it.
 * Each side extends only as far as its content needs (capped at the screen edge margin), and long text scrolls
 * once in full. The opened view morphs into a canopy: full text, reply field and action icons.
 */
object MessageView : ItemView<Message> {
    override val compactWidth: Dp = 1000.dp

    @Composable
    override fun compactSides(
        item: Message,
        hole: Dp,
        foot: Dp,
        minSide: Dp,
        maxLeft: Dp,
        maxRight: Dp,
        away: Boolean,
    ): CompactSides {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val body = compactBody(item)
        val senderPx = remember(item.title) { measurer.measure(item.title, SENDER_STYLE, softWrap = false, maxLines = 1).size.width }
        val bodyPx = remember(body) { measurer.measure(body, BODY_STYLE, softWrap = false, maxLines = 1).size.width }
        val senderDp = with(density) { senderPx.toDp() }
        val bodyDp = with(density) { bodyPx.toDp() }
        val needLeft = hole / 2 + SENDER_START + ICON_SIZE + ICON_GAP + senderDp + MEASURE_SLACK + SENDER_END
        val needRight = hole / 2 + BODY_START + bodyDp + MEASURE_SLACK + BODY_END
        val minL = minOf(minSide, maxLeft)
        val minR = minOf(minSide, maxRight)
        val left = needLeft.coerceIn(minL, maxLeft)
        val right = if (away) needRight.coerceIn(minR, maxRight) else snapRightForStatusBar(needRight, minR, maxRight)
        return CompactSides(left, right)
    }

    @Composable
    override fun Compact(
        item: Message,
        pill: PillControls,
    ) {
        val h = LocalHole.current
        val pop = remember(item.key) { Animatable(0f) }
        LaunchedEffect(item.key) {
            delay(60)
            pop.animateTo(1f, tween(260))
        }
        Row(Modifier.fillMaxSize().graphicsLayer { alpha = pop.value }, verticalAlignment = Alignment.CenterVertically) {
            SenderCell(item, Modifier.width(h.leading.dp).fillMaxHeight().padding(start = SENDER_START, end = SENDER_END))
            Spacer(Modifier.width(h.hole.dp))
            FadeLine(compactBody(item), item, pill, Modifier.weight(1f).fillMaxHeight().padding(start = BODY_START, end = BODY_END))
        }
    }

    /**
     * The canopy: a crown the width of the idle pill (app icon left of the camera hole, accent dot and "now" right),
     * a shelf below the status bar with sender, full text and ONE slim row: by default the reply field (send inside)
     * plus a three-dots button that toggles the row to the action icons; without reply, the icons show directly.
     */
    @Composable
    override fun Large(
        item: Message,
        pill: PillControls,
    ) {
        val appIcon by LocalContext.current.pill.apps
            .icon(item.pkg)
            .collectAsState()
        val accent = appIcon?.let { Color(it.accentArgb) } ?: IosGray
        val reply = item.actions.firstOrNull { it.takesText }
        val extras = item.actions.filter { !it.takesText }
        var text by remember(item.key) { mutableStateOf("") }
        var showActions by remember(item.key) { mutableStateOf(false) }
        val keyboard = LocalSoftwareKeyboardController.current
        val density = LocalDensity.current
        CanopyContent(
            Modifier.tapToOpen(pill, item::open),
            crownLeft = { AppIcon(item, 20.dp) },
            crownRight = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                    PillText("now", size = TEXT_LABEL, color = IosGray)
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillText(item.title, Modifier.weight(1f), size = TEXT_TITLE, bold = true)
                PillText(item.appName, Modifier.padding(start = 10.dp), size = TEXT_LABEL, color = IosGray)
            }
            if (item.text.isNotBlank()) PillText(item.text, Modifier.padding(top = 4.dp), size = 15, lines = 6, color = Color(0xFFD1D1D6))
            if (reply == null) {
                BoxWithConstraints(Modifier.padding(top = 10.dp).fillMaxWidth().height(MINI), contentAlignment = Alignment.CenterEnd) {
                    ActionButtonsRow(extras.take(maxExtraActions(maxWidth)), item, pill)
                }
            } else {
                val progress by animateFloatAsState(
                    if (showActions) 1f else 0f,
                    tween(240, easing = FastOutSlowInEasing),
                    label = "actionsToggle",
                )
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().height(MINI),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
                ) {
                    BoxWithConstraints(Modifier.weight(1f).height(MINI).clipToBounds()) {
                        val availW = maxWidth
                        if (progress < 0.6f) {
                            val t = (progress / 0.6f).coerceIn(0f, 1f)
                            val replyW = maxOf(minOf(availW, 80.dp), availW * (1f - 0.35f * t))
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .width(replyW)
                                    .height(MINI)
                                    .graphicsLayer { alpha = 1f - t },
                            ) {
                                PillTextField(text, { text = it }, "Reply", pill, Modifier.fillMaxWidth(), height = MINI, endInset = 38.dp)
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 4.dp)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (text.isBlank()) IosFill else Color.White)
                                        .pillButton("send") {
                                            if (text.isNotBlank()) {
                                                item.press(reply, text)
                                                text = ""
                                                pill.dismiss(item.key)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { ArrowUpGlyph(if (text.isBlank()) IosGray else Color.Black, 16.dp) }
                            }
                        }
                        if (progress > 0.25f) {
                            val t = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                            val slidePx = with(density) { (20.dp * (1f - t)).roundToPx() }
                            Box(Modifier.align(Alignment.CenterEnd).offset { IntOffset(slidePx, 0) }.graphicsLayer { alpha = t }) {
                                ActionButtonsRow(extras.take(maxExtraActions(availW)), item, pill)
                            }
                        }
                    }
                    MoreToggleButton(progress, showActions) {
                        showActions = !showActions
                        if (showActions) {
                            keyboard?.hide()
                            pill.typing(false)
                        }
                    }
                }
            }
        }
    }
}

/** App icon and sender name in one cell; the name takes what is left and ends with an ellipsis. */
@Composable
private fun SenderCell(
    e: Message,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(e, ICON_SIZE)
        Text(e.title, style = SENDER_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = ICON_GAP))
    }
}

/**
 * One line of text. When it does not fit, it waits at the start, scrolls smoothly at reading speed until its
 * end is fully visible, waits at the end, and reports the needed lifetime to [pill] once per event.
 */
@Composable
private fun FadeLine(
    text: String,
    e: Message,
    pill: PillControls,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val availPx = with(density) { maxWidth.toPx() }
        val widthPx = remember(text) { measurer.measure(text, BODY_STYLE, softWrap = false, maxLines = 1).size.width }
        val overPx = ceil((widthPx - availPx).coerceAtLeast(0f))
        val scrolls = overPx > 2f
        val scrollMs =
            if (scrolls) {
                (
                    with(density) {
                        overPx.toDp()
                    }.value / SCROLL_DP_PER_SEC * 1000f
                ).toLong().coerceAtLeast(MIN_SCROLL_MS)
            } else {
                0L
            }
        val neededMs = if (scrolls) SCROLL_START_HOLD_MS + scrollMs + SCROLL_END_HOLD_MS + SCROLL_END_BUFFER_MS else e.showMs
        LaunchedEffect(e, neededMs) { if (neededMs > e.showMs) pill.showFor(e, neededMs) }
        val slide = remember(e) { Animatable(0f) }
        LaunchedEffect(e, overPx) {
            slide.snapTo(0f)
            if (scrolls) {
                delay(SCROLL_START_HOLD_MS)
                slide.animateTo(overPx, tween(scrollMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), easing = LinearEasing))
            }
        }
        val fadePx = minOf(with(density) { 18.dp.toPx() }, (availPx / 3f).coerceAtLeast(1f))
        // Offscreen layer lives on the viewport box (not the unbounded Text), so arbitrarily long texts stay small.
        Box(
            Modifier.fillMaxSize().clipToBounds().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                drawContent()
                if (scrolls) {
                    val leftFade = (slide.value / fadePx).coerceIn(0f, 1f)
                    val rightFade = ((overPx - slide.value) / fadePx).coerceIn(0f, 1f)
                    if (leftFade > 0f) fadeEdge(0f, fadePx, 1f - leftFade, 1f)
                    if (rightFade > 0f) fadeEdge(size.width - fadePx, size.width, 1f, 1f - rightFade)
                }
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text,
                style = BODY_STYLE,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset(-slide.value.roundToInt(), 0) },
            )
        }
    }
}

/** How many notification actions fit beside Snooze and Open in [availW]. */
private fun maxExtraActions(availW: Dp): Int = (((availW + ROW_GAP) / (MINI + ROW_GAP)).toInt().coerceAtLeast(0) - 2).coerceAtLeast(0)

/** Notification action icons followed by Snooze and Open in app. */
@Composable
private fun ActionButtonsRow(
    extras: List<NoteAction>,
    e: Message,
    pill: PillControls,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        extras.forEachIndexed { i, a ->
            IconAction("action$i", {
                e.press(a)
                pill.dismiss(e.key)
            }) { ActionGlyph(a) }
        }
        IconAction("snooze", { if (e.snooze(3600_000)) pill.dismiss(e.key) }) { Glyph(SNOOZE, Color.White, 18.dp) }
        IconAction("open", {
            pill.close()
            e.open()
        }) { Glyph(OPEN_IN_NEW, Color.White, 17.dp) }
    }
}

/** Three-dots button that toggles between the reply field and the action icons; turns white with an X when active. */
@Composable
private fun MoreToggleButton(
    progress: Float,
    active: Boolean,
    onClick: () -> Unit,
) {
    val base = if (LocalControls.current.hover == "more" && !active) PillButtonHover else IosFill
    Box(
        Modifier
            .size(MINI)
            .clip(CircleShape)
            .background(lerp(base, Color.White, progress))
            .pillButton("more", onClick),
        contentAlignment = Alignment.Center,
    ) {
        val dotsAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
        if (dotsAlpha > 0f) Box(Modifier.graphicsLayer { alpha = dotsAlpha }) { Glyph(MORE_HORIZ, Color.White, 20.dp) }
        val closeAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
        if (closeAlpha >
            0f
        ) {
            Box(
                Modifier.graphicsLayer {
                    alpha = closeAlpha
                    rotationZ = (1f - progress) * -45f
                },
            ) { Glyph(CLOSE, Color.Black, 17.dp) }
        }
    }
}

/** A mini round button of the opened view's row. */
@Composable
private fun IconAction(
    id: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val hovered = LocalControls.current.hover == id
    Box(
        Modifier
            .size(MINI)
            .clip(CircleShape)
            .background(if (hovered) PillButtonHover else IosFill)
            .pillButton(id, onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** The notification action's icon, by its meaning; an unknown action shows its first letter. */
@Composable
private fun ActionGlyph(a: NoteAction) {
    when (val g = resolveActionIcon(a)) {
        is ActionIcon.Vector -> Glyph(g.path, Color.White, 18.dp)
        ActionIcon.Phone -> PhoneGlyph(Color.White, 17.dp)
        is ActionIcon.Letter -> Text(g.ch?.toString() ?: "•", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private sealed interface ActionIcon {
    class Vector(
        val path: String,
    ) : ActionIcon

    data object Phone : ActionIcon

    class Letter(
        val ch: Char?,
    ) : ActionIcon
}

private fun resolveActionIcon(action: NoteAction): ActionIcon {
    when (action.semantic) {
        Notification.Action.SEMANTIC_ACTION_REPLY -> return ActionIcon.Vector(REPLY)
        Notification.Action.SEMANTIC_ACTION_MARK_AS_READ -> return ActionIcon.Vector(MARK_READ)
        Notification.Action.SEMANTIC_ACTION_MARK_AS_UNREAD -> return ActionIcon.Vector(MARK_UNREAD)
        Notification.Action.SEMANTIC_ACTION_DELETE -> return ActionIcon.Vector(DELETE)
        Notification.Action.SEMANTIC_ACTION_ARCHIVE -> return ActionIcon.Vector(ARCHIVE)
        Notification.Action.SEMANTIC_ACTION_MUTE -> return ActionIcon.Vector(MUTE)
        Notification.Action.SEMANTIC_ACTION_UNMUTE -> return ActionIcon.Vector(BELL)
        Notification.Action.SEMANTIC_ACTION_THUMBS_UP -> return ActionIcon.Vector(LIKE)
        Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN -> return ActionIcon.Vector(DISLIKE)
        Notification.Action.SEMANTIC_ACTION_CALL -> return ActionIcon.Phone
    }
    val norm = Normalizer.normalize(action.title, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()

    fun has(vararg words: String) = words.any { norm.contains(it) }
    return when {
        has("unread", "neprecit", "ungelesen", "non lu", "no leido") -> ActionIcon.Vector(MARK_UNREAD)
        has(
            "read",
            "precit",
            "oznac",
            "gelesen",
            "leido",
            "olvas",
            "seen",
            "visto",
            "done",
            "hotov",
            "mark",
        ) -> ActionIcon.Vector(MARK_READ)
        has("reply", "odpoved", "odp", "respond", "answer", "antwort", "repond", "valasz") -> ActionIcon.Vector(REPLY)
        has(
            "delet",
            "remov",
            "trash",
            "vymaz",
            "zmaz",
            "odstran",
            "smaz",
            "losch",
            "supprim",
            "elimin",
            "torol",
            "dismiss",
            "zrus",
            "clear",
        ) -> ActionIcon.Vector(DELETE)
        has("archiv") -> ActionIcon.Vector(ARCHIVE)
        has("unmute", "odstlm") -> ActionIcon.Vector(BELL)
        has("mute", "stlm", "tich", "stumm", "muet", "silenc") -> ActionIcon.Vector(MUTE)
        has("dislike", "unlike", "thumbs down") -> ActionIcon.Vector(DISLIKE)
        has("like", "love", "heart", "paci", "lub", "gefalt", "aime", "gusta", "tetsz", "\uD83D\uDC4D", "\u2764") -> ActionIcon.Vector(LIKE)
        has("call", "zavol", "volat", "hovor", "anruf", "appel", "llamar") -> ActionIcon.Phone
        has("copy", "kopir", "kod", "code") -> ActionIcon.Vector(COPY)
        has("open", "otvor", "otevr", "view", "show", "ukaz", "offn", "ouvrir", "abrir") -> ActionIcon.Vector(OPEN_IN_NEW)
        else ->
            ActionIcon.Letter(
                action.title
                    .trim()
                    .firstOrNull { it.isLetterOrDigit() }
                    ?.uppercaseChar(),
            )
    }
}

@Composable
private fun AppIcon(
    e: Message,
    size: Dp,
) {
    val appIcon by LocalContext.current.pill.apps
        .icon(e.pkg)
        .collectAsState()
    val img = appIcon?.image
    if (img != null) {
        Image(img, null, Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(IosFill))
    }
}
