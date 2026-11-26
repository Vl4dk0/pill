package sk.vladko.pill

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sk.vladko.pill.actions.APP_ACTION_PREFIX
import sk.vladko.pill.actions.ActionEnv
import sk.vladko.pill.engine.PillSession
import sk.vladko.pill.system.Apps
import sk.vladko.pill.system.BorrowedSetting
import sk.vladko.pill.system.NoteFeed
import sk.vladko.pill.system.SettingsStore
import sk.vladko.pill.system.Torch
import sk.vladko.pill.system.accessibilityEnabled

/** Status-bar icons Pill hides while it runs; entries the user already had stay. */
private val STATUS_ICON_SLOTS =
    listOf(
        "nfc",
        "volume",
        "mute",
        "speakerphone",
        "speed",
        "network_speed",
        "networkspeed",
        "net_speed",
        "traffic_speed",
    )

/** Process-lifetime owner of shared app state. */
class PillApp : Application() {
    lateinit var apps: Apps
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var notes: NoteFeed
        private set
    lateinit var torch: Torch
        private set
    lateinit var popups: BorrowedSetting
        private set
    lateinit var statusIcons: BorrowedSetting
        private set

    /** Ids of activities the owner flicked away. Lives with the process, so a restarted session still hides them; never stored on disk. */
    val hiddenActivities = mutableSetOf<String>()

    /** Non-null while the accessibility service is connected. */
    var session: PillSession? = null

    /** What actions use; global actions work only while a session runs. */
    fun actionEnv(): ActionEnv = ActionEnv(this, session?.let { s -> s::global }, torch)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        apps = Apps(this)
        settings = SettingsStore(this, apps)
        notes = NoteFeed(this)
        torch = Torch(this)
        popups = BorrowedSetting(this, "headsup_saved", BorrowedSetting.Table.Global, "heads_up_notifications_enabled") { "0" }
        statusIcons =
            BorrowedSetting(this, "icons_saved", BorrowedSetting.Table.Secure, "icon_blacklist") { old ->
                (old.orEmpty().split(",").filter { it.isNotBlank() } + STATUS_ICON_SLOTS).distinct().joinToString(",")
            }
        val serviceOn = accessibilityEnabled(this)
        popups.reconcile(serviceOn)
        statusIcons.reconcile(serviceOn)
        // Pre-load menu app icons so the App and Actions menus show them on the first frame.
        appScope.launch {
            settings.value
                .map { s ->
                    s.appMenu +
                        s.actionsMenu.mapNotNull { id ->
                            if (id.startsWith(APP_ACTION_PREFIX)) id.removePrefix(APP_ACTION_PREFIX) else null
                        }
                }.distinctUntilChanged()
                .collect { pkgs -> apps.preload(pkgs) }
        }
    }
}

val Context.pill: PillApp get() = applicationContext as PillApp
