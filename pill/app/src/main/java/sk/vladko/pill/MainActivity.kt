package sk.vladko.pill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sk.vladko.pill.screen.SettingsScreen
import android.graphics.Color as AndroidColor

/** Hosts the settings screen; all UI lives in `sk.vladko.pill.screen`. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            SettingsScreen { dark ->
                val style = {
                    if (dark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                }
                enableEdgeToEdge(statusBarStyle = style(), navigationBarStyle = style())
            }
        }
    }
}
