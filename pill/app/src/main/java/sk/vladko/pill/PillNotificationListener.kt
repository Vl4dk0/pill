package sk.vladko.pill

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Thin shell: forwards what Android tells us to [PillApp.notes]. */
class PillNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        pill.notes.onConnected(this)
    }

    override fun onListenerDisconnected() {
        pill.notes.onDisconnected()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
    ) {
        pill.notes.onPosted(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        pill.notes.onRemoved(sbn)
    }
}
