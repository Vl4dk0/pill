package sk.vladko.pill.engine

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sk.vladko.pill.rules.Effect
import sk.vladko.pill.rules.Input
import sk.vladko.pill.rules.PillRules
import sk.vladko.pill.rules.PillView

/** Runs the rules against the real clock. Main thread only. */
class Engine(
    private val rules: PillRules,
    private val now: () -> Long,
    /** Called when the view changed (log line). */
    private val onView: (PillView) -> Unit,
    private val run: (Effect) -> Unit,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { send(Input.Tick) }

    /** What the overlay draws. Compose state: the overlay reads it directly. */
    var view: PillView by mutableStateOf(rules.view())
        private set

    /** handle -> publish the view if it changed -> re-arm the one timer at nextDeadline(now) -> run the effects. */
    fun send(input: Input) {
        val effects = rules.handle(input, now())
        val v = rules.view()
        if (v != view) {
            view = v
            onView(v)
        }
        handler.removeCallbacks(tick)
        rules.nextDeadline(now())?.let { handler.postAtTime(tick, it) }
        effects.forEach(run)
    }

    override fun close() {
        handler.removeCallbacks(tick)
    }
}
