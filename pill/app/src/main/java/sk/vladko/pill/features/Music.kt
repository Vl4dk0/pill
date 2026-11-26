package sk.vladko.pill.features

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sk.vladko.pill.PillNotificationListener
import sk.vladko.pill.engine.Host
import sk.vladko.pill.engine.Source
import sk.vladko.pill.overlay.accentOf
import sk.vladko.pill.rules.Accent
import sk.vladko.pill.rules.PillActivity
import sk.vladko.pill.system.Apps

/** One music player with an active media session that has a title. [id] is the player's package. Position is read live, not kept here. */
data class Music(
    override val id: String,
    override val since: Long,
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val coverAccentArgb: Int,
    val playing: Boolean,
    val durationMs: Long,
    /** The player accepts ACTION_SEEK_TO and the track has a length. */
    val canSeek: Boolean,
    private val player: PlayerHandle,
) : PillActivity {
    override val kind get() = "music"
    override val priority get() = 1
    override val label get() = "$title - $artist ${if (playing) "playing" else "paused"}"
    override val accent get() = Accent.App(id)

    /** Position now, moving on while playing. */
    fun position(): Long = player.position(durationMs)

    /** Changes each time the player reports a new playback state. */
    fun stateStamp(): Long = player.stateStamp()

    fun playPause() = player.playPause(playing)

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(ms: Long) = player.seekTo(ms, durationMs)

    override fun open() = player.open()
}

/** The live media session of one player. Compares by session token. */
class PlayerHandle(
    private val controller: MediaController,
    private val context: Context,
    private val apps: Apps,
) {
    fun position(durationMs: Long): Long {
        val st = controller.playbackState ?: return 0
        if (st.state != PlaybackState.STATE_PLAYING) return st.position
        val moved = ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        return (st.position + moved).coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
    }

    fun stateStamp(): Long = controller.playbackState?.lastPositionUpdateTime ?: 0

    fun playPause(playing: Boolean) = if (playing) controller.transportControls.pause() else controller.transportControls.play()

    fun next() = controller.transportControls.skipToNext()

    fun previous() = controller.transportControls.skipToPrevious()

    fun seekTo(
        ms: Long,
        durationMs: Long,
    ) {
        if (durationMs > 0) controller.transportControls.seekTo(ms.coerceIn(0, durationMs))
    }

    /** The player's own activity; Pill asks Android to let it start from the background. Else its launcher activity. */
    fun open() {
        val own =
            controller.sessionActivity ?: run {
                apps.launch(controller.packageName)
                return
            }
        val options =
            ActivityOptions
                .makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        runCatching { own.send(context, 0, null, null, null, null, options.toBundle()) }
            .onFailure { Log.w("PILL", "send failed", it) }
    }

    override fun equals(other: Any?): Boolean = other is PlayerHandle && other.controller.sessionToken == controller.sessionToken

    override fun hashCode(): Int = controller.sessionToken.hashCode()
}

/** Follows every media session that the notification listener may see. */
val MusicSource = Source { host -> MusicWatch(host) }

private class MusicWatch(
    private val host: Host,
) : AutoCloseable {
    private class Watched(
        val controller: MediaController,
        val player: PlayerHandle,
        val callback: MediaController.Callback,
        var metadata: MediaMetadata?,
        var state: PlaybackState?,
        var cover: Bitmap?,
        var coverAccent: Int,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val msm = host.context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(host.context, PillNotificationListener::class.java)
    private var listening = false
    private val watched = mutableMapOf<String, Watched>()
    private val since = mutableMapOf<String, Long>()
    private val last = mutableMapOf<String, Music>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sync(it.orEmpty()) }

    init {
        // Media sessions are visible only while the notification listener is connected.
        scope.launch {
            host.notes.connected.collect { on ->
                if (on) {
                    msm.removeOnActiveSessionsChangedListener(sessionsChanged)
                    msm.addOnActiveSessionsChangedListener(sessionsChanged, component)
                    listening = true
                    sync(activeSessions())
                } else {
                    stopListening()
                    sync(emptyList())
                }
            }
        }
        scope.launch {
            host.settings.value
                .map { it.playersOff }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (listening) sync(activeSessions()) }
        }
    }

    override fun close() {
        scope.cancel()
        stopListening()
        watched.values.forEach { it.controller.unregisterCallback(it.callback) }
        watched.clear()
        since.clear()
        last.clear()
    }

    private fun stopListening() {
        if (listening) msm.removeOnActiveSessionsChangedListener(sessionsChanged)
        listening = false
    }

    private fun activeSessions(): List<MediaController> =
        if (!listening) emptyList() else runCatching { msm.getActiveSessions(component) }.getOrDefault(emptyList())

    private fun sync(all: List<MediaController>) {
        val settings = host.settings
        val unseen = all.map { it.packageName }.distinct().filter { it !in settings.value.value.playersSeen }
        if (unseen.isNotEmpty()) settings.update { s -> s.copy(playersSeen = s.playersSeen + unseen) }
        val off = settings.value.value.playersOff
        // A player the user switched off in Pill's settings never becomes an activity.
        val sessions = all.filter { it.packageName !in off }
        val ids = sessions.map { it.packageName }.toSet()
        (watched.keys - ids).toList().forEach { end(it) }
        sessions.forEach { c ->
            val id = c.packageName
            val existing = watched[id]
            val entry =
                if (existing == null || existing.controller.sessionToken != c.sessionToken) {
                    existing?.let { it.controller.unregisterCallback(it.callback) }
                    lateinit var created: Watched
                    val cb =
                        object : MediaController.Callback() {
                            override fun onPlaybackStateChanged(state: PlaybackState?) {
                                if (watched[id] !== created) return
                                created.state = state
                                publish(created)
                            }

                            override fun onMetadataChanged(metadata: MediaMetadata?) {
                                if (watched[id] !== created) return
                                created.metadata = metadata
                                created.cover = scaledCover(metadata)
                                created.coverAccent = accentOf(created.cover).toArgb()
                                publish(created)
                            }

                            override fun onSessionDestroyed() = sync(activeSessions())
                        }
                    val cover = scaledCover(c.metadata)
                    created =
                        Watched(
                            c,
                            PlayerHandle(c, host.context, host.apps),
                            cb,
                            c.metadata,
                            c.playbackState,
                            cover,
                            accentOf(cover).toArgb(),
                        )
                    watched[id] = created
                    c.registerCallback(cb, handler)
                    if (id !in since) since[id] = host.now()
                    created
                } else {
                    existing
                }
            publish(entry)
        }
    }

    private fun end(id: String) {
        watched.remove(id)?.let { it.controller.unregisterCallback(it.callback) }
        since.remove(id)
        last.remove(id)
        host.sink.ended(id)
    }

    private fun scaledCover(md: MediaMetadata?): Bitmap? {
        val raw =
            md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: return null
        val max = maxOf(raw.width, raw.height)
        if (max <= 192) return raw
        val scale = 192f / max
        return raw.scale(
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
        )
    }

    /** Sends the player only when what it shows changed; position moves are read live by the view. */
    private fun publish(w: Watched) {
        val id = w.controller.packageName
        val md = w.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) {
            last.remove(id)
            host.sink.ended(id)
            return
        }
        val st = w.state
        val playing = st?.state == PlaybackState.STATE_PLAYING
        // A player that starts playing becomes the newest.
        if (playing && last[id]?.playing != true) since[id] = host.now()
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val music =
            Music(
                id = id,
                since = since[id] ?: 0,
                title = title,
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                cover = w.cover,
                coverAccentArgb = w.coverAccent,
                playing = playing,
                durationMs = duration,
                canSeek = duration > 0 && ((st?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L,
                player = w.player,
            )
        if (music == last[id]) return
        last[id] = music
        host.sink.activity(music)
    }
}
