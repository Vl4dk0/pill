package sk.vladko.fakeplayer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.widget.TextView

/** A plain test screen. With `--ez fullscreen true` it hides the status bar, like a video or a game. */
class ScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        setContentView(TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Fake app"
            textSize = 28f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(40, 60, 120))
            setTextColor(Color.WHITE)
        })
        if (intent.getBooleanExtra("fullscreen", false)) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    }
}
