package sk.vladko.pill.system

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sk.vladko.pill.rules.Note
import sk.vladko.pill.rules.NoteAction
import sk.vladko.pill.rules.NoteTarget

private const val RECENT_LIMIT = 16
private const val PHOTO_PX = 96

/** The live side of a notification. Compares by key, so a model that holds it stays a plain value. */
class NoteHandle(
    val key: String,
    private val feed: NoteFeed,
) {
    /** Sends an intent of the newest notification with this key. False and a log line when it fails. */
    fun send(
        target: NoteTarget,
        reply: String? = null,
    ): Boolean = feed.send(key, target, reply)

    /** 96 px caller photo, loaded once per key. */
    fun callerPhoto(): Bitmap? = feed.callerPhoto(key)

    /** 96 px picture of the notification's large icon, or its small icon when there is none. Loaded on each call. */
    fun picture(): Bitmap? = feed.picture(key)

    /** Texts of a custom RemoteViews layout, read once per key. */
    fun customTexts(): List<String> = feed.customTexts(key)

    override fun equals(other: Any?): Boolean = other is NoteHandle && other.key == key

    override fun hashCode(): Int = key.hashCode()
}

interface NoteListener {
    /** Return true to claim it: later listeners do not see it, and [removed] comes only for claimed keys. */
    fun posted(
        note: Note,
        handle: NoteHandle,
    ): Boolean

    fun removed(key: String) {}
}

/**
 * The only code that reads [StatusBarNotification]. Turns notifications into pure [Note] values and routes
 * each one to the first [NoteListener] that claims it. Keeps the last notification of the 16 most recently
 * removed claimed keys so an item still on the pill can be opened.
 */
class NoteFeed(
    context: Context,
) {
    private class Claimed(
        var sbn: StatusBarNotification,
        var owner: NoteListener,
    )

    private val appContext = context.applicationContext
    private val listeners = mutableListOf<NoteListener>()
    private val active = LinkedHashMap<String, Claimed>()
    private val recent =
        object : LinkedHashMap<String, StatusBarNotification>(RECENT_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StatusBarNotification>?): Boolean = size > RECENT_LIMIT
        }
    private val cachedPhotos = HashMap<String, Bitmap?>()
    private val cachedTexts = HashMap<String, List<String>>()
    private var inFlight: StatusBarNotification? = null
    private var service: NotificationListenerService? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** Listeners are asked in the order they were added. Replays unclaimed active notifications when already connected. */
    fun listen(l: NoteListener): AutoCloseable {
        listeners += l
        val s = service
        if (s != null) {
            val list = runCatching { s.activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            val ranking = runCatching { s.currentRanking }.getOrNull()
            for (sbn in list) {
                if (sbn.key in active) continue
                offerTo(l, sbn, ranking, replay = true)
            }
        }
        return AutoCloseable {
            listeners -= l
            val dropped = active.entries.filter { it.value.owner === l }.map { it.key }
            dropped.forEach { key ->
                active.remove(key)?.let { recent[key] = it.sbn }
                cachedPhotos.remove(key)
                cachedTexts.remove(key)
            }
        }
    }

    fun snooze(
        key: String,
        ms: Long,
    ): Boolean {
        val s = service ?: return false
        return runCatching {
            s.snoozeNotification(key, ms)
            true
        }.onFailure { Log.w("PILL", "snooze failed", it) }
            .getOrDefault(false)
    }

    fun cancelAll(): Boolean {
        val s = service ?: return false
        return runCatching {
            s.cancelAllNotifications()
            true
        }.onFailure { Log.w("PILL", "cancelAll failed", it) }
            .getOrDefault(false)
    }

    fun onConnected(service: NotificationListenerService) {
        this.service = service
        _connected.value = true
        val list = runCatching { service.activeNotifications.orEmpty() }.getOrDefault(emptyArray())
        val ranking = runCatching { service.currentRanking }.getOrNull()
        val liveKeys = list.mapTo(HashSet()) { it.key }
        for (sbn in list) dispatch(sbn, ranking, replay = true)
        for (key in active.keys.toList()) {
            if (key !in liveKeys) removeClaimed(key)
        }
    }

    fun onDisconnected() {
        service = null
        _connected.value = false
        for (key in active.keys.toList()) removeClaimed(key)
    }

    fun onPosted(
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.RankingMap,
    ) {
        dispatch(sbn, ranking, replay = false)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        removeClaimed(sbn.key)
    }

    internal fun send(
        key: String,
        target: NoteTarget,
        reply: String?,
    ): Boolean {
        val sbn =
            sbnFor(key) ?: run {
                Log.w("PILL", "send failed: no notification for $key")
                return false
            }
        val n = sbn.notification
        val ex = n.extras
        var fill: Intent? = null
        val pi: PendingIntent? =
            when (target) {
                NoteTarget.Content -> n.contentIntent
                NoteTarget.Answer -> ex.getParcelable("android.answerIntent", PendingIntent::class.java)
                NoteTarget.Decline -> ex.getParcelable("android.declineIntent", PendingIntent::class.java)
                NoteTarget.HangUp -> ex.getParcelable("android.hangUpIntent", PendingIntent::class.java)
                is NoteTarget.Action -> {
                    val a = n.actions?.getOrNull(target.index)
                    val ri = a?.remoteInputs?.firstOrNull()
                    if (ri != null && reply != null) {
                        fill =
                            Intent().also {
                                RemoteInput.addResultsToIntent(arrayOf(ri), it, Bundle().apply { putCharSequence(ri.resultKey, reply) })
                            }
                    }
                    a?.actionIntent
                }
            }
        if (pi == null) {
            Log.w("PILL", "send failed: missing intent for $target ($key)")
            return false
        }
        val options =
            ActivityOptions
                .makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        return runCatching {
            pi.send(appContext, 0, fill, null, null, null, options.toBundle())
            true
        }.onFailure {
            Log.w("PILL", "send failed", it)
        }.getOrDefault(false)
    }

    internal fun callerPhoto(key: String): Bitmap? {
        if (cachedPhotos.containsKey(key)) return cachedPhotos[key]
        val sbn = sbnFor(key) ?: return null
        val n = sbn.notification
        val person = n.extras.getParcelable("android.callPerson", Person::class.java)
        val bmp =
            runCatching {
                val icon = person?.icon ?: n.getLargeIcon()
                icon?.loadDrawable(appContext)?.let { d ->
                    createBitmap(PHOTO_PX, PHOTO_PX).also {
                        d.setBounds(0, 0, PHOTO_PX, PHOTO_PX)
                        d.draw(Canvas(it))
                    }
                }
            }.getOrNull()
        cachedPhotos[key] = bmp
        return bmp
    }

    internal fun picture(key: String): Bitmap? {
        val n = sbnFor(key)?.notification ?: return null
        return runCatching {
            val icon = n.getLargeIcon() ?: n.smallIcon
            icon?.loadDrawable(appContext)?.let { d ->
                createBitmap(PHOTO_PX, PHOTO_PX).also {
                    d.setBounds(0, 0, PHOTO_PX, PHOTO_PX)
                    d.draw(Canvas(it))
                }
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    internal fun customTexts(key: String): List<String> {
        cachedTexts[key]?.let { return it }
        val sbn = sbnFor(key) ?: return emptyList()
        val n = sbn.notification
        val rv = n.contentView ?: n.bigContentView ?: return emptyList()
        val out = mutableListOf<String>()

        fun walk(v: View) {
            if (v is TextView && v.text.isNotBlank()) out += v.text.toString()
            if (v is ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        runCatching { walk(rv.apply(appContext, FrameLayout(appContext))) }
        val result = out.distinct()
        cachedTexts[key] = result
        return result
    }

    private fun sbnFor(key: String): StatusBarNotification? = inFlight?.takeIf { it.key == key } ?: active[key]?.sbn ?: recent[key]

    private fun dispatch(
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.RankingMap?,
        replay: Boolean,
    ) {
        val note = toNote(sbn, ranking, replay)
        val handle = NoteHandle(sbn.key, this)
        inFlight = sbn
        try {
            for (l in listeners.toList()) {
                if (l.posted(note, handle)) {
                    recent.remove(sbn.key)
                    val existing = active[sbn.key]
                    if (existing != null) {
                        existing.sbn = sbn
                        existing.owner = l
                    } else {
                        active[sbn.key] = Claimed(sbn, l)
                    }
                    return
                }
            }
        } finally {
            inFlight = null
        }
        if (sbn.key in active) removeClaimed(sbn.key)
    }

    private fun offerTo(
        l: NoteListener,
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.RankingMap?,
        replay: Boolean,
    ) {
        val note = toNote(sbn, ranking, replay)
        val handle = NoteHandle(sbn.key, this)
        inFlight = sbn
        try {
            if (l.posted(note, handle)) {
                recent.remove(sbn.key)
                active[sbn.key] = Claimed(sbn, l)
            }
        } finally {
            inFlight = null
        }
    }

    private fun removeClaimed(key: String) {
        val claimed = active.remove(key) ?: return
        cachedPhotos.remove(key)
        cachedTexts.remove(key)
        recent[key] = claimed.sbn
        claimed.owner.removed(key)
    }

    private fun toNote(
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.RankingMap?,
        replay: Boolean,
    ): Note {
        val n = sbn.notification
        val ex = n.extras
        val r = NotificationListenerService.Ranking()
        val hasRanking = ranking != null && ranking.getRanking(sbn.key, r)
        val person = ex.getParcelable("android.callPerson", Person::class.java)
        val callIntents =
            buildSet {
                if (ex.getParcelable("android.answerIntent", PendingIntent::class.java) != null) add(NoteTarget.Answer)
                if (ex.getParcelable("android.declineIntent", PendingIntent::class.java) != null) add(NoteTarget.Decline)
                if (ex.getParcelable("android.hangUpIntent", PendingIntent::class.java) != null) add(NoteTarget.HangUp)
            }
        val actions =
            n.actions.orEmpty().mapIndexedNotNull { idx, a ->
                if (a.actionIntent == null) {
                    null
                } else {
                    NoteAction(idx, a.title?.toString().orEmpty(), a.semanticAction, !a.remoteInputs.isNullOrEmpty())
                }
            }
        return Note(
            key = sbn.key,
            pkg = sbn.packageName,
            category = n.category,
            style = ex.getString(Notification.EXTRA_TEMPLATE).orEmpty(),
            title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() },
            text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            bigText = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            whenMs = n.`when`,
            chronometer = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            foregroundService = n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            insistent = n.flags and Notification.FLAG_INSISTENT != 0,
            fullScreen = n.fullScreenIntent != null,
            importance = if (hasRanking) r.importance else NotificationManager.IMPORTANCE_NONE,
            passesDnd = hasRanking && r.matchesInterruptionFilter(),
            callType = ex.getInt("android.callType", 0),
            callerName = person?.name?.toString()?.takeIf { it.isNotBlank() },
            callIntents = callIntents,
            actions = actions,
            replay = replay,
            subText = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            progress = ex.getInt(Notification.EXTRA_PROGRESS, 0),
            progressMax =
                if (ex.getBoolean(
                        Notification.EXTRA_PROGRESS_INDETERMINATE,
                        false,
                    )
                ) {
                    0
                } else {
                    ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                },
        )
    }
}
