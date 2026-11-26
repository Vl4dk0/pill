package sk.vladko.pill.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import sk.vladko.pill.engine.Source
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.PillEvent

private const val GREEN_ARGB = 0xFF30D158.toInt()

data class Charger(
    val percent: Int,
) : PillEvent {
    override val kind get() = "charger"
    override val key get() = "charger"
    override val priority get() = 1
    override val showMs get() = 3_000L
    override val label get() = "charging $percent%"
    override val accent get() = Accent.Fixed(GREEN_ARGB)

    override fun open() {}
}

/** One event each time a charger is plugged in. */
val ChargerSource =
    Source { host ->
        host.onBroadcast(Intent.ACTION_POWER_CONNECTED) {
            host.sink.event(Charger(batteryPercent(host.context)))
        }
    }

/** The level from Android's sticky battery broadcast (it follows `dumpsys battery set level`). */
private fun batteryPercent(context: Context): Int {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    return (battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale).coerceIn(0, 100)
}
