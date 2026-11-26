package sk.vladko.fakeplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Test chat app. `am broadcast -n <pkg>/sk.vladko.fakeplayer.ChatReceiver -a sk.vladko.fakeplayer.CHAT
 * --es chat <name> --es text <text>` posts a high-importance notification with a reply field and a
 * "Mark read" button; `--es actions read,mute,delete,archive,custom` picks the extra buttons. One notification per chat name, so the same chat updates the same notification.
 */
class ChatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chat = intent.getStringExtra("chat") ?: "Chat"
        when (intent.action) {
            "sk.vladko.fakeplayer.CHAT" -> post(context, chat, intent.getStringExtra("text").orEmpty(), intent.getStringExtra("actions") ?: "read")
            "sk.vladko.fakeplayer.ACT" -> Log.i("FAKECHAT", "action ${intent.getStringExtra("name")} chat=$chat")
            "sk.vladko.fakeplayer.REPLY" -> {
                val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply")?.toString().orEmpty()
                Log.i("FAKECHAT", "reply chat=$chat text=$reply")
                post(context, chat, "You: $reply")
            }
            "sk.vladko.fakeplayer.ALARM" -> postAlarm(context, chat, intent.getBooleanExtra("ring", true))
            "sk.vladko.fakeplayer.ALARM_STOP" -> {
                Log.i("FAKECHAT", "alarm button ${intent.getStringExtra("button")}")
                context.getSystemService(NotificationManager::class.java).cancel(ALARM_ID)
            }
            "sk.vladko.fakeplayer.NAV" -> postNavigation(context, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("text"), intent.getStringExtra("sub"))
            "sk.vladko.fakeplayer.NAV_STOP" -> {
                Log.i("FAKECHAT", "navigation button exit")
                context.getSystemService(NotificationManager::class.java).cancel(NAV_ID)
            }
            "sk.vladko.fakeplayer.VOIP" -> {
                val mode = intent.getStringExtra("mode") ?: "style"
                // dialing / connected: an outgoing CallStyle call without and with a running chronometer.
                if (mode == "dialing" || mode == "connected") postCall(context, chat, "style", ongoing = true, chronometer = mode == "connected")
                else postCall(context, chat, mode, ongoing = false)
            }
            "sk.vladko.fakeplayer.VOIPBTN" -> {
                val button = intent.getStringExtra("button")
                Log.i("FAKECHAT", "call button $button")
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(CALL_ID)
                if (button == "answer") postCall(context, chat, intent.getStringExtra("mode") ?: "style", ongoing = true)
            }
            "sk.vladko.fakeplayer.READ" -> {
                Log.i("FAKECHAT", "read chat=$chat")
                context.getSystemService(NotificationManager::class.java).cancel(chat.hashCode())
            }
        }
    }

    private fun post(context: Context, chat: String, text: String, actions: String = "read") {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("chat", "Chat", NotificationManager.IMPORTANCE_HIGH))
        fun broadcast(action: String, mutable: Boolean) = PendingIntent.getBroadcast(
            context, chat.hashCode(),
            Intent(context, ChatReceiver::class.java).setAction(action).putExtra("chat", chat),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, chat.hashCode(),
            Intent(context, ScreenActivity::class.java).putExtra("title", chat).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val tokens = actions.split(",").filter { it.isNotBlank() }
        val includeReply = "noreply" !in tokens
        val replyAction = Notification.Action.Builder(null, "Reply", broadcast("sk.vladko.fakeplayer.REPLY", true))
            .addRemoteInput(RemoteInput.Builder("reply").setLabel("Reply").build()).build()
        // Extra buttons by token: read, mute, delete carry a semantic; archive has only its title; custom is unknown to Pill; noreply omits Reply.
        val extra = tokens.filter { it != "noreply" }.map { token ->
            when (token) {
                "read" -> Notification.Action.Builder(null, "Mark read", broadcast("sk.vladko.fakeplayer.READ", false)).setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ).build()
                "mute" -> Notification.Action.Builder(null, "Mute", actBroadcast(context, chat, "mute")).setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE).build()
                "delete" -> Notification.Action.Builder(null, "Delete", actBroadcast(context, chat, "delete")).setSemanticAction(Notification.Action.SEMANTIC_ACTION_DELETE).build()
                "archive" -> Notification.Action.Builder(null, "Archive", actBroadcast(context, chat, "archive")).build()
                else -> Notification.Action.Builder(null, "Frobnicate", actBroadcast(context, chat, "custom")).build()
            }
        }
        nm.notify(
            chat.hashCode(),
            Notification.Builder(context, "chat")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(chat)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .apply {
                    if (includeReply) addAction(replyAction)
                    extra.forEach { addAction(it) }
                }
                .build(),
        )
    }

    private fun actBroadcast(context: Context, chat: String, name: String) = PendingIntent.getBroadcast(
        context, (chat + name).hashCode(),
        Intent(context, ChatReceiver::class.java).setAction("sk.vladko.fakeplayer.ACT").putExtra("chat", chat).putExtra("name", name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val ALARM_ID = 4242

    /** Alarm-category notification like the Clock app's: ringing = full-screen intent + ongoing, silent = a plain "upcoming alarm". */
    private fun postAlarm(context: Context, label: String, ring: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("alarm", "Alarm", NotificationManager.IMPORTANCE_HIGH))
        fun button(name: String) = PendingIntent.getBroadcast(
            context, name.hashCode(),
            Intent(context, ChatReceiver::class.java).setAction("sk.vladko.fakeplayer.ALARM_STOP").putExtra("button", name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, ScreenActivity::class.java).putExtra("title", label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText(if (ring) "7:30" else "Next alarm 7:30")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Snooze", button("snooze")).build())
            .addAction(Notification.Action.Builder(null, "Dismiss", button("dismiss")).build())
        if (ring) b.setOngoing(true).setFullScreenIntent(open, true)
        nm.notify(ALARM_ID, b.build())
    }

    private val NAV_ID = 4444

    /** Navigation like Google Maps: ongoing, category navigation, a black turn arrow as large icon, a trip progress bar and one "Exit navigation" action. */
    private fun postNavigation(context: Context, title: String, text: String?, sub: String?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nav", "Navigation", NotificationManager.IMPORTANCE_LOW))
        val arrow = android.graphics.Bitmap.createBitmap(126, 126, android.graphics.Bitmap.Config.ARGB_8888)
        val path = android.graphics.Path().apply {
            moveTo(63f, 14f); lineTo(105f, 70f); lineTo(77f, 70f); lineTo(77f, 112f); lineTo(49f, 112f); lineTo(49f, 70f); lineTo(21f, 70f); close()
        }
        android.graphics.Canvas(arrow).drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK })
        val exit = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ChatReceiver::class.java).setAction("sk.vladko.fakeplayer.NAV_STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, ScreenActivity::class.java).putExtra("title", "Navigation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NAV_ID,
            Notification.Builder(context, "nav")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setLargeIcon(arrow)
                .setContentTitle(title)
                .apply { if (text != null) setContentText(text); if (sub != null) setSubText(sub) }
                .setProgress(470946, 150000, false)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, "Exit navigation", exit).build())
                .build(),
        )
    }

    private val CALL_ID = 4343

    /**
     * A VoIP call notification. mode "style": a real CallStyle (extras carry the intents, button titles are the system's).
     * mode "plain": call category with Slovak button titles and no CallStyle, so only the word list can find the buttons;
     * answering it posts a plain ongoing notification with no call category (a Telegram-like continuation).
     */
    private fun postCall(context: Context, name: String, mode: String, ongoing: Boolean, chronometer: Boolean = true) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("voip", "Calls", NotificationManager.IMPORTANCE_HIGH))
        fun button(b: String) = PendingIntent.getBroadcast(
            context, b.hashCode(),
            Intent(context, ChatReceiver::class.java).setAction("sk.vladko.fakeplayer.VOIPBTN").putExtra("button", b).putExtra("chat", name).putExtra("mode", mode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, ScreenActivity::class.java).putExtra("title", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, "voip").setSmallIcon(android.R.drawable.sym_action_call).setContentIntent(open)
        if (mode == "style") {
            val person = android.app.Person.Builder().setName(name).build()
            b.setStyle(if (ongoing) Notification.CallStyle.forOngoingCall(person, button("hangup")) else Notification.CallStyle.forIncomingCall(person, button("decline"), button("answer")))
                .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setFullScreenIntent(open, true)
            if (ongoing && chronometer) b.setWhen(System.currentTimeMillis()).setUsesChronometer(true)
        } else if (!ongoing) {
            b.setContentTitle(name).setContentText("Prichádzajúci hovor").setCategory(Notification.CATEGORY_CALL).setOngoing(true).setFullScreenIntent(open, true)
                .addAction(Notification.Action.Builder(null, "Odmietnuť", button("decline")).build())
                .addAction(Notification.Action.Builder(null, "Prijať", button("answer")).build())
        } else {
            b.setContentTitle(name).setContentText("Prebieha hovor").setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Ukončiť hovor", button("hangup")).build())
        }
        nm.notify(CALL_ID, b.build())
    }
}
