package sk.vladko.pill.system

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sk.vladko.pill.actions.APP_ACTION_PREFIX
import sk.vladko.pill.rules.Choice
import sk.vladko.pill.rules.Settings

const val WALLET_PKG = "com.google.android.apps.walletnfcrel"

/** Reads and writes Pill's SharedPreferences as one validated [Settings] flow. */
class SettingsStore(
    context: Context,
    apps: Apps,
) {
    private val prefs = context.applicationContext.getSharedPreferences("pill", Context.MODE_PRIVATE)

    // Hold-right default is computed once at process start: legacy hold_right key if set, else Google Wallet when installed.
    private val holdRightDefault: Choice =
        run {
            val pkg = prefs.getString("hold_right", null) ?: WALLET_PKG
            if (pkg.isEmpty() || !apps.installed(pkg)) {
                Choice.Nothing
            } else {
                Choice.Action(APP_ACTION_PREFIX + pkg)
            }
        }

    private val _value = MutableStateFlow(Settings.parse(prefs.all, holdRightDefault))
    val value: StateFlow<Settings> = _value

    /** Applies [change], re-validates via [Settings.parse], publishes and writes changed keys with apply(). */
    fun update(change: (Settings) -> Settings) {
        val cur = _value.value
        val next = Settings.parse(change(cur).encode(), holdRightDefault)
        if (next == cur) return
        val curEnc = cur.encode()
        val nextEnc = next.encode()
        _value.value = next
        prefs.edit {
            for ((k, v) in nextEnc) {
                if (curEnc[k] == v) continue
                when (v) {
                    null -> remove(k)
                    is Float -> putFloat(k, v)
                    is Boolean -> putBoolean(k, v)
                    is String -> putString(k, v)
                }
            }
        }
    }
}
