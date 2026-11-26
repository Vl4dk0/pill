package sk.vladko.pill.overlay

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

/** Named buttons, hover, areas that take their own touches, and extra state fields of a view. Main thread. */
class Controls {
    private class Button(
        var bounds: Rect,
        var onClick: () -> Unit,
    )

    private val all = LinkedHashMap<String, Button>()
    private val owned = mutableListOf<Rect>()
    private val reports = LinkedHashMap<String, () -> JSONObject?>()

    var hover: String? by mutableStateOf(null)

    /** Add or update a named button and its hit bounds. */
    fun button(
        name: String,
        bounds: Rect,
        onClick: () -> Unit,
    ) {
        val existing = all[name]
        if (existing == null) {
            all[name] = Button(Rect(bounds), onClick)
        } else {
            existing.bounds = Rect(bounds)
            existing.onClick = onClick
        }
    }

    fun removeButton(name: String) {
        all.remove(name)
    }

    /** Registers an area that handles its own touches (the seek bar); close() removes it. */
    fun own(area: Rect): AutoCloseable {
        owned += area
        return AutoCloseable { owned.remove(area) }
    }

    /** Registers a live extra state field (e.g. "seek" from MusicView); close() removes it. */
    fun report(
        name: String,
        value: () -> JSONObject?,
    ): AutoCloseable {
        reports[name] = value
        return AutoCloseable { if (reports[name] === value) reports.remove(name) }
    }

    fun hit(
        x: Float,
        y: Float,
    ): String? = all.entries.firstOrNull { it.value.bounds.contains(x.toInt(), y.toInt()) }?.key

    fun click(name: String) {
        all[name]?.onClick?.invoke()
    }

    fun owns(
        x: Float,
        y: Float,
    ): Boolean = owned.any { it.contains(x.toInt(), y.toInt()) }

    fun buttons(): Map<String, Rect> = all.mapValues { Rect(it.value.bounds) }

    fun reported(): Map<String, JSONObject?> = reports.mapValues { it.value() }
}

val LocalControls = staticCompositionLocalOf<Controls> { error("no Controls") }
