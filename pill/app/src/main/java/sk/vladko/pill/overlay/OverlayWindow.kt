package sk.vladko.pill.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import sk.vladko.pill.rules.PillView
import sk.vladko.pill.rules.Size

private const val WINDOW_HEIGHT_DP = 320

/** The root view: also reports touches outside the pill (they still reach the app below). */
@SuppressLint("ViewConstructor")
private class OverlayView(
    context: Context,
    private val onOutside: () -> Unit,
) : FrameLayout(context) {
    val compose = ComposeView(context).also { addView(it) }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}

/**
 * The overlay window that draws the pill on top of every app. Only the drawn outline takes touches; the rest of the
 * window passes them to the app below. Outside touches are watched only while a large view or menu is open (a tap
 * outside closes it), so in every other state the window is not in the path of other apps' touches.
 */
class OverlayWindow(
    private val service: AccessibilityService,
    onOutside: () -> Unit,
    content: @Composable () -> Unit,
) : AutoCloseable,
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedState.savedStateRegistry

    private val wm = service.getSystemService(WindowManager::class.java)
    private val root = OverlayView(service, onOutside)
    private val params =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (WINDOW_HEIGHT_DP * service.resources.displayMetrics.density).toInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Untouchable until the first drawn outline gives it a touchable region (see setTouchable).
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                title = "PillOverlay"
            }
    private var region = Region()
    private var drawsNothing = false

    /** Bounds of the touchable region in window pixels: what the pill covers now. */
    var bounds = Rect()
        private set

    init {
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.compose.setContent(content)
        wm.addView(root, params)
    }

    /** Window flags from the view: focusable while typing; watches outside touches while a large view or menu shows. */
    fun apply(view: PillView) {
        drawsNothing = view.drawsNothing
        setFlag(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, !(view.typing && view.visible))
        setFlag(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, !view.drawsNothing && view.size != Size.Compact)
        updateVisibility()
    }

    /** The drawn outline in window pixels; empty when nothing is drawn, then the whole window is untouchable. */
    fun setTouchable(region: Region) {
        if (region == this.region) return
        this.region = Region(region)
        bounds = region.bounds
        root.rootSurfaceControl?.setTouchableRegion(region)
        setFlag(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, region.isEmpty)
        updateVisibility()
    }

    /** The root view is gone once nothing is drawn and the last outline has shrunk away, so the window has no layer to compose. */
    private fun updateVisibility() {
        val visibility = if (drawsNothing && region.isEmpty) View.GONE else View.VISIBLE
        if (root.visibility != visibility) root.visibility = visibility
    }

    private fun setFlag(
        flag: Int,
        set: Boolean,
    ) {
        val had = params.flags and flag != 0
        if (had == set) return
        params.flags = if (set) params.flags or flag else params.flags and flag.inv()
        wm.updateViewLayout(root, params)
    }

    override fun close() {
        wm.removeView(root)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
