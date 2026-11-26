package sk.vladko.fakeplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Test music player with a fixed 3-track playlist of silence. The media session it publishes carries
 * title, artist, album, cover art and duration, so other apps can read and control it.
 *
 * Control from adb: `am start-foreground-service -n <package>/sk.vladko.fakeplayer.FakePlayerService -a <ACTION>`
 * with ACTION one of PLAY, PAUSE, NEXT, PREV, STOP (see [handle]). STOP releases the session.
 */
class FakePlayerService : MediaSessionService() {
    private var session: MediaSession? = null

    private class Track(val letter: String, val durationMs: Long)

    private val tracks = listOf(
        Track("A", 60_000),
        Track("B", 45_000),
        Track("C", 30_000),
    )

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Audio focus off, so two fake players can play at the same time.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build(), false)
            .build()
        player.setMediaItems(tracks.map(::mediaItem))
        player.prepare()
        session = MediaSession.Builder(this, player).build()
    }

    private fun mediaItem(track: Track): MediaItem {
        val prefix = getString(R.string.player_prefix)
        val cover = coverPng(prefix, track.letter)
        val metadata = MediaMetadata.Builder()
            .setTitle("$prefix - Track ${track.letter}")
            .setArtist("$prefix Artist ${track.letter}")
            .setAlbumTitle("$prefix Album ${track.letter}")
            .setDurationMs(track.durationMs)
            .setArtworkData(cover, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        return MediaItem.Builder()
            .setMediaId("$prefix-${track.letter}")
            .setUri(Uri.parse("android.resource://$packageName/${R.raw.silence}"))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(track.durationMs).build(),
            )
            .setMediaMetadata(metadata)
            .build()
    }

    /** A 512 px cover: a colour gradient (one hue per player and track) with a big letter. */
    private fun coverPng(prefix: String, letter: String): ByteArray {
        val size = 512
        val hue = (if (prefix.contains("One", true)) 340f else 215f) + (letter[0] - 'A') * 38f
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            android.graphics.Color.HSVToColor(floatArrayOf(hue % 360, 0.65f, 1f)),
            android.graphics.Color.HSVToColor(floatArrayOf((hue + 50) % 360, 0.85f, 0.55f)),
            android.graphics.Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(235, 255, 255, 255)
            textSize = 330f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        c.drawText(letter, size / 2f, size / 2f - (text.ascent() + text.descent()) / 2f, text)
        return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    // Every `start-foreground-service` call must be answered with startForeground within seconds,
    // also when the player is paused. Media3's own notification is only posted while playing, so this
    // service owns one fixed notification and Media3 posts none.
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) = Unit

    private fun startForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        startForeground()
        intent?.action?.let(::handle)
        return result
    }

    private fun handle(action: String) {
        val player = session?.player ?: return
        when (action) {
            "PLAY" -> player.play()
            "PAUSE" -> player.pause()
            "NEXT" -> player.seekToNextMediaItem()
            "PREV" -> player.seekToPreviousMediaItem()
            "STOP" -> stopSelf()
        }
    }

    private companion object {
        const val CHANNEL = "player"
        const val NOTIFICATION_ID = 1
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
