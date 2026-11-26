package sk.vladko.pill.system

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings

private const val PILL_PKG = "sk.vladko.pill"

/** One permission or adb grant, for the settings screen. [fix] is what the user does when it is off. */
class PermissionStatus(
    val name: String,
    val why: String,
    val on: Boolean,
    val fix: String,
)

fun accessibilityEnabled(context: Context): Boolean =
    Settings.Secure
        .getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        .orEmpty()
        .contains(context.packageName)

fun permissions(context: Context): List<PermissionStatus> {
    val a11y = accessibilityEnabled(context)
    val listener =
        Settings.Secure
            .getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty()
            .contains(context.packageName)
    val nm = context.getSystemService(NotificationManager::class.java)

    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    return listOf(
        PermissionStatus("Accessibility service", "Draws the pill and runs the system buttons", a11y, "Settings > Accessibility > Pill"),
        PermissionStatus(
            "Notification access",
            "Reads messages, calls and music",
            listener,
            "Settings > Notifications > Device & app notifications > Pill",
        ),
        PermissionStatus(
            "Secure settings",
            "Hides Android's pop-ups; battery saver, grayscale, invert",
            granted("android.permission.WRITE_SECURE_SETTINGS"),
            "adb: pm grant $PILL_PKG android.permission.WRITE_SECURE_SETTINGS",
        ),
        PermissionStatus(
            "Usage access",
            "Shows recent apps",
            appOpAllowed(context, "android:get_usage_stats"),
            "adb: appops set $PILL_PKG GET_USAGE_STATS allow",
        ),
        PermissionStatus(
            "Modify system settings",
            "Auto-rotate, brightness, screen timeout",
            Settings.System.canWrite(context),
            "adb: appops set $PILL_PKG WRITE_SETTINGS allow",
        ),
        PermissionStatus(
            "Do Not Disturb access",
            "Do Not Disturb switch",
            nm.isNotificationPolicyAccessGranted,
            "adb: cmd notification allow_dnd $PILL_PKG",
        ),
    )
}

private fun appOpAllowed(
    context: Context,
    op: String,
): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    return ops.checkOpNoThrow(op, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}
