package sk.vladko.pill.system

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.edit

private const val PREFS_FILE = "pill"
private const val NONE = "(none)"

/** A system setting Pill changes while it runs. The old value is saved once (in the "pill" file under [savedKey]) and put back. */
class BorrowedSetting(
    private val context: Context,
    private val savedKey: String,
    private val table: Table,
    private val name: String,
    private val whileRunning: (old: String?) -> String,
) {
    enum class Table { Global, Secure }

    val permitted: Boolean
        get() = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /** True when the setting holds the [whileRunning] value right now. */
    val taken: Boolean
        get() {
            val cur = current()
            return cur == whileRunning(cur)
        }

    fun take() {
        if (!permitted) return
        val p = prefs()
        val old = current()
        if (!p.contains(savedKey)) p.edit { putString(savedKey, old ?: NONE) }
        put(whileRunning(old))
    }

    fun giveBack() {
        val p = prefs()
        if (!permitted || !p.contains(savedKey)) return
        put(readSaved(p))
        p.edit { remove(savedKey) }
    }

    /** Process start: a saved value exists but the service is not enabled -> put it back. */
    fun reconcile(serviceEnabled: Boolean) {
        if (prefs().contains(savedKey) && !serviceEnabled) giveBack()
    }

    private fun prefs(): SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun current(): String? =
        when (table) {
            Table.Global -> Settings.Global.getString(context.contentResolver, name)
            Table.Secure -> Settings.Secure.getString(context.contentResolver, name)
        }

    private fun put(value: String?) {
        when (table) {
            Table.Global -> Settings.Global.putString(context.contentResolver, name, value)
            Table.Secure -> Settings.Secure.putString(context.contentResolver, name, value)
        }
    }

    /** Reads both legacy Int (headsup_saved) and String (icons_saved / new format) stored forms. */
    private fun readSaved(p: SharedPreferences): String? =
        when (val raw = p.all[savedKey]) {
            is String -> if (raw == NONE) null else raw
            is Number -> raw.toString()
            else -> null
        }
}
