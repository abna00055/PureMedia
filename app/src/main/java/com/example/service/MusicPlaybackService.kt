package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import coil.Coil
import coil.request.ImageRequest

class MusicPlaybackService : Service() {

    private var isReceiverRegistered = false
    private var mediaSession: MediaSession? = null

    // Headphone unplugged broadcaster
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                com.example.viewmodel.MusicViewModel.activeInstance?.pausePlayback()
                updateNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Register action audio becoming noisy
        try {
            val filter = IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val activeVM = com.example.viewmodel.MusicViewModel.activeInstance

        when (action) {
            ACTION_PLAY -> {
                activeVM?.playPlayback()
                updateNotification()
            }
            ACTION_PAUSE -> {
                activeVM?.pausePlayback()
                updateNotification()
            }
            ACTION_PREVIOUS -> {
                activeVM?.previousTrack()
                updateNotification()
            }
            ACTION_NEXT -> {
                activeVM?.nextTrack()
                updateNotification()
            }
            ACTION_STOP -> {
                activeVM?.pausePlayback()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateNotification()
        return START_NOT_STICKY
    }

    private fun updateNotification(loadedAlbumArt: Bitmap? = null) {
        val activeVM = com.example.viewmodel.MusicViewModel.activeInstance
        val track = activeVM?.currentTrack?.value
        val isPlaying = activeVM?.isPlaying?.value ?: false

        if (track == null) {
            val fallbackNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PureMedia Player")
                .setContentText("إستعد لتشغيل الموسيقى (Ready to play)")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
            startForeground(NOTIFICATION_ID, fallbackNotif)
            return
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val prevIntent = PendingIntent.getService(this, 1, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS }, flag)
        val playToggleAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playIntent = PendingIntent.getService(this, 2, Intent(this, MusicPlaybackService::class.java).apply { action = playToggleAction }, flag)
        val nextIntent = PendingIntent.getService(this, 3, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }, flag)
        val stopIntent = PendingIntent.getService(this, 4, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }, flag)

        // Bind Media3 player session
        val activePlayer = activeVM.exoPlayer
        if (activePlayer != null) {
            try {
                if (mediaSession == null || mediaSession?.player != activePlayer) {
                    mediaSession?.release()
                    mediaSession = MediaSession.Builder(this, activePlayer).build()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .setDeleteIntent(stopIntent)

        val session = mediaSession
        if (session != null) {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        if (loadedAlbumArt != null) {
            builder.setLargeIcon(loadedAlbumArt)
        } else {
            val context = this
            track.albumArtUri?.let { uri ->
                try {
                    val loader = Coil.imageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .target(
                            onSuccess = { drawable ->
                                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                                if (bitmap != null) {
                                    updateNotification(bitmap)
                                }
                            }
                        )
                        .build()
                    loader.enqueue(request)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
        val playIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playActionTitle = if (isPlaying) "Pause" else "Play"
        builder.addAction(playIconRes, playActionTitle, playIntent)
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)

        val notification = builder.build()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PureMedia Music Foreground Active Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pure_media_music_playback_channel_v3"
        const val NOTIFICATION_ID = 8888

        const val ACTION_PLAY = "com.puremedia.action.play"
        const val ACTION_PAUSE = "com.puremedia.action.pause"
        const val ACTION_PREVIOUS = "com.puremedia.action.previous"
        const val ACTION_NEXT = "com.puremedia.action.next"
        const val ACTION_STOP = "com.puremedia.action.stop"
    }
}
