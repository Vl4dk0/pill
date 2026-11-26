package sk.vladko.pill.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.rules.PillEvent
import sk.vladko.pill.rules.PillItem
import sk.vladko.pill.system.Apps
import sk.vladko.pill.system.NoteFeed
import sk.vladko.pill.system.SettingsStore
import kotlin.reflect.KClass
import kotlin.reflect.cast

/** Gets things from Android and hands them to the pill. close() undoes start(). State lives in what start() creates. */
fun interface Source {
    fun start(host: Host): AutoCloseable
}

class Host(
    val context: Context,
    val sink: Sink,
    val notes: NoteFeed,
    val settings: SettingsStore,
    val apps: Apps,
    val scope: CoroutineScope,
    val now: () -> Long,
) {
    fun onBroadcast(
        vararg actions: String,
        block: (Intent) -> Unit,
    ): AutoCloseable {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    i: Intent,
                ) = block(i)
            }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        context.registerReceiver(receiver, filter)
        return AutoCloseable { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/** Call on the main thread. */
interface Sink {
    fun event(e: PillEvent)

    fun activity(a: PillActivity)

    fun ended(id: String)
}

/** Independent extents left and right of the camera-hole centre for a compact view. */
data class CompactSides(
    val left: Dp,
    val right: Dp,
)

/** What a view may ask of the pill. */
interface PillControls {
    fun close()

    fun dismiss(eventKey: String)

    fun typing(on: Boolean)

    fun showFor(
        event: PillEvent,
        showMs: Long,
    )
}

/** How one type looks. */
interface ItemView<T : PillItem> {
    val compactWidth: Dp

    /** Optional per-side extents from the hole centre when left and right size by content; null sizes symmetrically with [compactWidth]. */
    @Composable
    fun compactSides(
        item: T,
        hole: Dp,
        foot: Dp,
        minSide: Dp,
        maxLeft: Dp,
        maxRight: Dp,
        away: Boolean,
    ): CompactSides? = null

    @Composable fun Compact(
        item: T,
        pill: PillControls,
    )

    @Composable fun Large(
        item: T,
        pill: PillControls,
    )
}

/** One line in [Registry] per type: its model class, its source and its view. */
class Feature<T : PillItem>(
    val type: KClass<T>,
    val source: Source,
    val view: ItemView<T>,
) {
    val compactWidth: Dp get() = view.compactWidth

    @Composable
    fun compactSides(
        item: PillItem,
        hole: Dp,
        foot: Dp,
        minSide: Dp,
        maxLeft: Dp,
        maxRight: Dp,
        away: Boolean,
    ): CompactSides? = view.compactSides(type.cast(item), hole, foot, minSide, maxLeft, maxRight, away)

    @Composable fun Compact(
        item: PillItem,
        pill: PillControls,
    ) = view.Compact(type.cast(item), pill)

    @Composable fun Large(
        item: PillItem,
        pill: PillControls,
    ) = view.Large(type.cast(item), pill)
}
