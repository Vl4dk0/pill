package sk.vladko.pill.system

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sk.vladko.pill.rules.Hole
import sk.vladko.pill.rules.ScreenGeometry
import sk.vladko.pill.rules.ScreenState

/** Reads the screen width, camera hole and status-bar height, in dp. */
fun screenGeometry(context: Context): ScreenGeometry =
    geometryFrom(context.getSystemService(WindowManager::class.java).currentWindowMetrics, context.resources.displayMetrics.density)

private fun geometryFrom(
    metrics: WindowMetrics,
    density: Float,
): ScreenGeometry {
    val b = metrics.bounds
    // Landscape: the cutout is at the side, so there is nothing to centre on.
    val rect =
        if (b.width() > b.height()) {
            null
        } else {
            metrics.windowInsets.displayCutout?.let { cut ->
                cut.cutoutPath?.let { p -> RectF().also { p.computeBounds(it, true) } } ?: cut.boundingRectTop.let(::RectF)
            }
        }
    val hole =
        rect?.takeUnless { it.isEmpty }?.let {
            Hole(
                it.centerX() / density,
                it.centerY() / density,
                it.width() / density,
                it.height() / density,
            )
        }
    val statusBar = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top / density
    return ScreenGeometry(b.width() / density, hole, statusBar)
}

/** Watches lock, landscape, status-bar visibility and display cut-out changes. */
class ScreenWatch(
    private val service: AccessibilityService,
    private val onChange: (ScreenState, ScreenGeometry) -> Unit,
) : AutoCloseable {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val km = service.getSystemService(KeyguardManager::class.java)
    private val pm = service.getSystemService(PowerManager::class.java)
    private val dm = service.getSystemService(DisplayManager::class.java)

    var state: ScreenState by mutableStateOf(ScreenState())
        private set
    var geometry: ScreenGeometry by mutableStateOf(geometryFrom(wm.currentWindowMetrics, service.resources.displayMetrics.density))
        private set

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                i: Intent,
            ) = windowChanged()
        }

    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit

            override fun onDisplayRemoved(id: Int) = Unit

            override fun onDisplayChanged(id: Int) {
                val metrics = wm.currentWindowMetrics
                val g = geometryFrom(metrics, service.resources.displayMetrics.density)
                val s = readState(metrics)
                val changed = g != geometry || s != state
                geometry = g
                state = s
                if (changed) onChange(s, g)
            }
        }

    init {
        val metrics = wm.currentWindowMetrics
        geometry = geometryFrom(metrics, service.resources.displayMetrics.density)
        state = readState(metrics)
        dm.registerDisplayListener(displayListener, null)
        service.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
    }

    /** Called from onAccessibilityEvent and screen broadcasts; skips work when no screen fact changed. */
    fun windowChanged() {
        val metrics = wm.currentWindowMetrics
        val s = readState(metrics)
        if (s == state) return
        val g = geometryFrom(metrics, service.resources.displayMetrics.density)
        geometry = g
        state = s
        onChange(s, g)
    }

    override fun close() {
        runCatching { service.unregisterReceiver(screenReceiver) }
        dm.unregisterDisplayListener(displayListener)
    }

    private fun readState(metrics: WindowMetrics): ScreenState {
        val b = metrics.bounds
        // The window metrics (not the overlay window's own insets) tell whether the status bar is shown.
        return ScreenState(
            locked = km.isKeyguardLocked || !pm.isInteractive,
            landscape = b.width() >= b.height(),
            statusBarHidden = !metrics.windowInsets.isVisible(WindowInsets.Type.statusBars()),
        )
    }
}
