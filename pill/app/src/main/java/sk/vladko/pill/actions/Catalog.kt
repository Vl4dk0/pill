package sk.vladko.pill.actions

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import sk.vladko.pill.pill
import sk.vladko.pill.system.Apps
import sk.vladko.pill.system.WALLET_PKG
import sk.vladko.pill.ui.AIRPLANE
import sk.vladko.pill.ui.ALARM
import sk.vladko.pill.ui.BACK
import sk.vladko.pill.ui.BATTERY
import sk.vladko.pill.ui.BELL
import sk.vladko.pill.ui.BLUETOOTH
import sk.vladko.pill.ui.BRIGHTNESS
import sk.vladko.pill.ui.CAMERA
import sk.vladko.pill.ui.CONTRAST
import sk.vladko.pill.ui.DIALER
import sk.vladko.pill.ui.DND
import sk.vladko.pill.ui.FLASHLIGHT
import sk.vladko.pill.ui.HOME
import sk.vladko.pill.ui.HOTSPOT
import sk.vladko.pill.ui.INVERT
import sk.vladko.pill.ui.LOCK
import sk.vladko.pill.ui.NEXT
import sk.vladko.pill.ui.NFC
import sk.vladko.pill.ui.PLAY
import sk.vladko.pill.ui.POWER
import sk.vladko.pill.ui.PREVIOUS
import sk.vladko.pill.ui.QUICK_SETTINGS
import sk.vladko.pill.ui.RECENTS
import sk.vladko.pill.ui.ROTATE
import sk.vladko.pill.ui.SCREENSHOT
import sk.vladko.pill.ui.SPLIT
import sk.vladko.pill.ui.TIMER
import sk.vladko.pill.ui.VIBRATE
import sk.vladko.pill.ui.VOLUME
import sk.vladko.pill.ui.WALLET
import sk.vladko.pill.ui.WIFI

/** The action catalog. Ids are stable: SettingsStore saves them. `app:<package>` is any installed app, built on demand. */
object Catalog {
    private const val ADB_WRITE_SETTINGS = "Needs adb: appops set sk.vladko.pill WRITE_SETTINGS allow"
    private const val ADB_SECURE = "Needs adb: pm grant sk.vladko.pill android.permission.WRITE_SECURE_SETTINGS"
    private const val ADB_DND = "Needs adb: cmd notification allow_dnd sk.vladko.pill"
    private const val NO_SERVICE = "Pill's accessibility service is off"

    private val BRIGHTNESS_STEPS = listOf(25, 90, 160, 255)
    private val TIMEOUT_STEPS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)

    private fun writeSettings(e: ActionEnv) = if (Settings.System.canWrite(e.context)) null else ADB_WRITE_SETTINGS

    private fun secure(e: ActionEnv) =
        if (e.context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        ) {
            null
        } else {
            ADB_SECURE
        }

    private fun service(e: ActionEnv) = if (e.global != null) null else NO_SERVICE

    private fun dnd(e: ActionEnv) =
        if (e.context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) null else ADB_DND

    private fun global(code: Int): (ActionEnv) -> Unit = { it.global?.invoke(code) }

    private fun audio(e: ActionEnv) = e.context.getSystemService(AudioManager::class.java)

    private fun mediaKey(
        c: ActionEnv,
        key: Int,
    ) {
        audio(c).dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio(c).dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    /** Reason text when no app handles [i] (package visibility is declared in the manifest). */
    private fun handled(i: Intent): (ActionEnv) -> String? =
        { e ->
            if (i.resolveActivity(e.context.packageManager) != null) null else "No app on this phone handles this"
        }

    private fun page(
        id: String,
        name: String,
        short: String,
        icon: String,
        desc: String,
        i: Intent,
    ) = PillAction(id, name, short, icon, ActionGroup.Pages, desc, Behaviour.Opens { i }, unavailable = handled(i))

    private fun place(
        id: String,
        name: String,
        short: String,
        icon: String,
        desc: String,
        i: Intent,
    ) = PillAction(id, name, short, icon, ActionGroup.Places, desc, Behaviour.Opens { i }, unavailable = handled(i))

    private fun secureToggle(
        id: String,
        name: String,
        short: String,
        icon: String,
        desc: String,
        on: (ActionEnv) -> Boolean,
        set: (ActionEnv, Boolean) -> Unit,
    ) = PillAction(id, name, short, icon, ActionGroup.Display, desc, Behaviour.Toggle(on, set), unavailable = ::secure)

    private fun secInt(
        e: ActionEnv,
        key: String,
    ) = Settings.Secure.getInt(e.context.contentResolver, key, 0)

    /** Ring <-> vibrate. Silent needs Do-Not-Disturb access, so it is never set; from silent the next tap rings. */
    private fun setRinger(
        e: ActionEnv,
        vibrate: Boolean,
    ) {
        val next = if (vibrate) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        runCatching { audio(e).ringerMode = next }.onFailure { Log.w("PILL", "ringer failed", it) }
    }

    /** Every catalog action except the open-any-app ones, in picker order. */
    val all: List<PillAction> =
        listOf(
            // ---- Switches ----
            PillAction(
                "torch",
                "Torch",
                "Torch",
                FLASHLIGHT,
                ActionGroup.Switches,
                "Camera flash on or off.",
                Behaviour.Toggle(isOn = { it.torch.on }, set = { e, _ -> e.torch.toggle() }),
                unavailable = {
                    when {
                        it.global == null -> NO_SERVICE
                        !it.torch.available -> "This phone has no flash"
                        else -> null
                    }
                },
            ),
            PillAction(
                "ringer",
                "Ring or vibrate",
                "Ringer",
                VIBRATE,
                ActionGroup.Switches,
                "Switches the ringer between ring and vibrate.",
                Behaviour.Toggle(
                    isOn = { audio(it).ringerMode != AudioManager.RINGER_MODE_NORMAL },
                    set = { e, on -> setRinger(e, on) },
                ),
            ),
            PillAction(
                "dnd",
                "Do Not Disturb",
                "DND",
                DND,
                ActionGroup.Switches,
                "Silences calls and alerts except priority ones.",
                Behaviour.Toggle(
                    isOn = {
                        it.context.getSystemService(NotificationManager::class.java).currentInterruptionFilter !=
                            NotificationManager.INTERRUPTION_FILTER_ALL
                    },
                    set = { e, on ->
                        e.context
                            .getSystemService(NotificationManager::class.java)
                            .setInterruptionFilter(
                                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
                            )
                    },
                ),
                unavailable = ::dnd,
            ),
            PillAction(
                "battery_saver",
                "Battery saver",
                "Saver",
                BATTERY,
                ActionGroup.Switches,
                "Turns Android's battery saver on or off.",
                Behaviour.Toggle(
                    isOn = { Settings.Global.getInt(it.context.contentResolver, "low_power", 0) == 1 },
                    set = { e, on -> Settings.Global.putInt(e.context.contentResolver, "low_power", if (on) 1 else 0) },
                ),
                unavailable = ::secure,
            ),
            PillAction(
                "auto_rotate",
                "Auto-rotate",
                "Rotate",
                ROTATE,
                ActionGroup.Switches,
                "Lets the screen turn with the phone.",
                Behaviour.Toggle(
                    isOn = { Settings.System.getInt(it.context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1 },
                    set = {
                        e,
                        on,
                        ->
                        Settings.System.putInt(e.context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
                    },
                ),
                unavailable = ::writeSettings,
            ),
            // ---- Display ----
            PillAction(
                "auto_brightness",
                "Auto brightness",
                "Auto light",
                BRIGHTNESS,
                ActionGroup.Display,
                "Brightness follows the light around you.",
                Behaviour.Toggle(
                    isOn = {
                        Settings.System.getInt(it.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    },
                    set = { e, on ->
                        Settings.System.putInt(
                            e.context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        )
                    },
                ),
                unavailable = ::writeSettings,
            ),
            PillAction(
                "brightness_step",
                "Brightness steps",
                "Brightness",
                BRIGHTNESS,
                ActionGroup.Display,
                "Each press sets the next brightness: 10, 35, 63, 100 percent. Turns auto brightness off.",
                Behaviour.Step {
                    val cur = Settings.System.getInt(it.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    val next = BRIGHTNESS_STEPS.firstOrNull { s -> s > cur + 4 } ?: BRIGHTNESS_STEPS.first()
                    Settings.System.putInt(
                        it.context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )
                    Settings.System.putInt(it.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
                },
                unavailable = ::writeSettings,
            ),
            PillAction(
                "screen_timeout",
                "Screen timeout",
                "Timeout",
                TIMER,
                ActionGroup.Display,
                "Each press sets the next time before the screen sleeps: 15 s, 30 s, 1, 2, 5, 10 min.",
                Behaviour.Step {
                    val cur = Settings.System.getInt(it.context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
                    val next = TIMEOUT_STEPS.firstOrNull { s -> s > cur } ?: TIMEOUT_STEPS.first()
                    Settings.System.putInt(it.context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, next)
                },
                unavailable = ::writeSettings,
            ),
            secureToggle(
                "grayscale",
                "Grayscale",
                "Gray",
                CONTRAST,
                "Shows the screen in black and white.",
                on = { secInt(it, "accessibility_display_daltonizer_enabled") == 1 && secInt(it, "accessibility_display_daltonizer") == 0 },
                set = { c, on ->
                    Settings.Secure.putInt(c.context.contentResolver, "accessibility_display_daltonizer", 0)
                    Settings.Secure.putInt(c.context.contentResolver, "accessibility_display_daltonizer_enabled", if (on) 1 else 0)
                },
            ),
            secureToggle(
                "invert",
                "Invert colours",
                "Invert",
                INVERT,
                "Reverses the colours of the screen.",
                on = { secInt(it, "accessibility_display_inversion_enabled") == 1 },
                set = {
                    c,
                    on,
                    ->
                    Settings.Secure.putInt(c.context.contentResolver, "accessibility_display_inversion_enabled", if (on) 1 else 0)
                },
            ),
            // ---- System buttons (accessibility global actions) ----
            PillAction(
                "screenshot",
                "Screenshot",
                "Shot",
                SCREENSHOT,
                ActionGroup.System,
                "Takes a screenshot (the menu leaves the picture first).",
                Behaviour.Command(delayMs = 200, run = global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)),
                unavailable = ::service,
            ),
            PillAction(
                "lock",
                "Lock screen",
                "Lock",
                LOCK,
                ActionGroup.System,
                "Locks the phone.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)),
                unavailable = ::service,
            ),
            PillAction(
                "power",
                "Power menu",
                "Power",
                POWER,
                ActionGroup.System,
                "Opens the power-off menu.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)),
                unavailable = ::service,
            ),
            PillAction(
                "shade",
                "Notifications",
                "Alerts",
                BELL,
                ActionGroup.System,
                "Pulls down the notification shade.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)),
                unavailable = ::service,
            ),
            PillAction(
                "qs",
                "Quick settings",
                "Settings",
                QUICK_SETTINGS,
                ActionGroup.System,
                "Opens Android's quick settings panel.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)),
                unavailable = ::service,
            ),
            PillAction(
                "recents",
                "Recent apps screen",
                "Recents",
                RECENTS,
                ActionGroup.System,
                "Opens Android's app switcher.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_RECENTS)),
                unavailable = ::service,
            ),
            PillAction(
                "home",
                "Home",
                "Home",
                HOME,
                ActionGroup.System,
                "Goes to the home screen.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_HOME)),
                unavailable = ::service,
            ),
            PillAction(
                "back",
                "Back",
                "Back",
                BACK,
                ActionGroup.System,
                "Presses Back.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_BACK)),
                unavailable = ::service,
            ),
            PillAction(
                "split",
                "Split screen",
                "Split",
                SPLIT,
                ActionGroup.System,
                "Splits the screen with the current app.",
                Behaviour.Command(run = global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)),
                unavailable = ::service,
            ),
            // ---- Media and sound ----
            PillAction(
                "media_play",
                "Play / pause",
                "Play",
                PLAY,
                ActionGroup.Media,
                "Plays or pauses the current media.",
                Behaviour.Command(keepsMenu = true, run = { mediaKey(it, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }),
            ),
            PillAction(
                "media_next",
                "Next track",
                "Next",
                NEXT,
                ActionGroup.Media,
                "Skips to the next track.",
                Behaviour.Command(keepsMenu = true, run = { mediaKey(it, KeyEvent.KEYCODE_MEDIA_NEXT) }),
            ),
            PillAction(
                "media_prev",
                "Previous track",
                "Previous",
                PREVIOUS,
                ActionGroup.Media,
                "Goes back to the previous track.",
                Behaviour.Command(keepsMenu = true, run = { mediaKey(it, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }),
            ),
            PillAction(
                "volume",
                "Volume panel",
                "Volume",
                VOLUME,
                ActionGroup.Media,
                "Shows Android's volume slider.",
                Behaviour.Command(
                    run = { audio(it).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) },
                ),
            ),
            // ---- Settings pages (one tap to the right place; Android gives apps no direct switch) ----
            page(
                "page_internet",
                "Wi-Fi and mobile data",
                "Internet",
                WIFI,
                "Opens the Internet panel: Wi-Fi and mobile data switches.",
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
            ),
            page(
                "page_bluetooth",
                "Bluetooth settings",
                "Bluetooth",
                BLUETOOTH,
                "Opens the connected devices page, where Bluetooth is switched.",
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            ),
            page(
                "page_hotspot",
                "Hotspot settings",
                "Hotspot",
                HOTSPOT,
                "Opens the hotspot and tethering page.",
                Intent("android.settings.TETHER_SETTINGS"),
            ),
            page("page_nfc", "NFC panel", "NFC", NFC, "Opens the NFC panel.", Intent(Settings.Panel.ACTION_NFC)),
            page(
                "page_network",
                "Network and airplane mode",
                "Network",
                AIRPLANE,
                "Opens the network settings page, where airplane mode is.",
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            ),
            page(
                "page_settings",
                "Android settings",
                "Settings",
                QUICK_SETTINGS,
                "Opens the Android settings app.",
                Intent(Settings.ACTION_SETTINGS),
            ),
            // ---- Open something ----
            place("camera", "Camera", "Camera", CAMERA, "Opens the camera app.", Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            place("alarm_new", "New alarm", "Alarm", ALARM, "Opens the clock app to set an alarm.", Intent(AlarmClock.ACTION_SET_ALARM)),
            place("timer_new", "New timer", "Timer", TIMER, "Opens the clock app to set a timer.", Intent(AlarmClock.ACTION_SET_TIMER)),
            place("alarms", "Alarm list", "Alarms", ALARM, "Opens the list of alarms.", Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            place("dialer", "Phone dial pad", "Dial", DIALER, "Opens the dial pad.", Intent(Intent.ACTION_DIAL)),
            PillAction(
                "wallet",
                "Google Wallet",
                "Wallet",
                WALLET,
                ActionGroup.Places,
                "Opens Google Wallet.",
                Behaviour.Opens { it.context.packageManager.getLaunchIntentForPackage(WALLET_PKG) },
                unavailable = {
                    if (it.context.pill.apps
                            .installed(WALLET_PKG)
                    ) {
                        null
                    } else {
                        "Google Wallet is not installed"
                    }
                },
            ),
        )

    /** One action by id: a catalog id, or `app:<package>`. Null for an id that no longer exists. */
    fun byId(
        id: String,
        apps: Apps,
    ): PillAction? {
        all.firstOrNull { it.id == id }?.let { return it }
        if (!id.startsWith(APP_ACTION_PREFIX)) return null
        val pkg = id.removePrefix(APP_ACTION_PREFIX)
        if (pkg.isBlank()) return null
        val label = apps.label(pkg)
        return PillAction(
            id,
            label,
            label,
            "",
            ActionGroup.Places,
            "Opens $label.",
            Behaviour.Opens { it.context.packageManager.getLaunchIntentForPackage(pkg) },
            unavailable = { if (apps.installed(pkg)) null else "App is not installed" },
            appPackage = pkg,
        )
    }
}
