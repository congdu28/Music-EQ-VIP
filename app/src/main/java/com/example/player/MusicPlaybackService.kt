package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.example.MainActivity
import com.example.R
import com.example.model.Song

class MusicPlaybackService : Service() {
    private val binder = MusicBinder()
    private var mediaSession: MediaSession? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "MusicPlaybackServiceSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    MusicPlayerController.activeInstance?.resumePlayback()
                }

                override fun onPause() {
                    MusicPlayerController.activeInstance?.pausePlayback()
                }

                override fun onSkipToNext() {
                    MusicPlayerController.activeInstance?.playNext()
                }

                override fun onSkipToPrevious() {
                    MusicPlayerController.activeInstance?.playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    MusicPlayerController.activeInstance?.seekTo(pos)
                }

                override fun onStop() {
                    MusicPlayerController.activeInstance?.pausePlayback()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> MusicPlayerController.activeInstance?.resumePlayback()
            ACTION_PAUSE -> MusicPlayerController.activeInstance?.pausePlayback()
            ACTION_TOGGLE -> MusicPlayerController.activeInstance?.togglePlayPause()
            ACTION_NEXT -> MusicPlayerController.activeInstance?.playNext()
            ACTION_PREV -> MusicPlayerController.activeInstance?.playPrevious()
            ACTION_STOP -> {
                MusicPlayerController.activeInstance?.pausePlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nhịp Điệu Hi-Res"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Đang phát nhạc"
                val album = intent.getStringExtra(EXTRA_ALBUM) ?: ""
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val durationMs = intent.getLongExtra(EXTRA_DURATION, 0L)
                val positionMs = intent.getLongExtra(EXTRA_POSITION, 0L)

                updateMediaSession(title, artist, album, durationMs, positionMs, isPlaying)
                val notification = buildMediaNotification(title, artist, isPlaying)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    private fun updateMediaSession(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val session = mediaSession ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()
        session.setMetadata(metadata)

        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setState(state, positionMs.coerceAtLeast(0L), if (isPlaying) 1.0f else 0.0f, SystemClock.elapsedRealtime())
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .build()
        session.setPlaybackState(playbackState)
    }

    private fun buildMediaNotification(
        title: String,
        artist: String,
        isPlaying: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPending = PendingIntent.getService(
            this, 1,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val togglePending = PendingIntent.getService(
            this, 2,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextPending = PendingIntent.getService(
            this, 3,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val style = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.let {
            style.setMediaSession(it.sessionToken)
        }

        return notificationBuilder
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("Hi-Res 10-Band EQ")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setStyle(style)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Trước", prevPending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Tạm dừng" else "Phát",
                    togglePending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Kế tiếp", nextPending
                ).build()
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phát nhạc Nhịp Điệu Hi-Res",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Điều khiển âm nhạc và bộ chỉnh âm Equalizer trên màn hình khóa"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
    }

    companion object {
        const val CHANNEL_ID = "nhip_dieu_music_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_PLAY = "com.example.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.example.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_PREV = "com.example.ACTION_PREV"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_UPDATE = "com.example.ACTION_UPDATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ALBUM = "extra_album"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_POSITION = "extra_position"

        fun startOrUpdate(context: Context, song: Song, isPlaying: Boolean, positionMs: Long = 0L) {
            try {
                val intent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_TITLE, song.title)
                    putExtra(EXTRA_ARTIST, song.artist)
                    putExtra(EXTRA_ALBUM, song.album)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                    putExtra(EXTRA_DURATION, song.durationMs)
                    putExtra(EXTRA_POSITION, positionMs.coerceAtLeast(0L))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore background start exceptions
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
