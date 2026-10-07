package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

class YouTubeDownloadNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tải nhạc YouTube", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Tiến trình tải bài hát từ YouTube trong Music EQ"
                    setShowBadge(false)
                }
            )
        }
    }

    fun progress(videoId: String, title: String, downloaded: Long, total: Long) {
        val downloadedText = formatBytes(downloaded)
        val hasTotal = total > 0L
        val percent = if (hasTotal) ((downloaded * 100L) / total).toInt().coerceIn(0, 99) else 0
        val text = if (hasTotal) "$downloadedText / ${formatBytes(total)} · $percent%" else "$downloadedText đã tải · đang nhận dữ liệu"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Đang tải: $title")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(if (hasTotal) 100 else 0, percent, !hasTotal)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        notify(videoId, notification)
    }

    fun finished(videoId: String, title: String) = notify(videoId, NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Đã tải xong")
        .setContentText(title)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build())

    fun failed(videoId: String, title: String, message: String) = notify(videoId, NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Tải nhạc thất bại")
        .setContentText("$title · $message")
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build())

    fun cancel(videoId: String) = manager.cancel(notificationId(videoId))

    private fun notify(videoId: String, notification: android.app.Notification) {
        try { NotificationManagerCompat.from(appContext).notify(notificationId(videoId), notification) }
        catch (_: SecurityException) { /* Runtime notification permission is optional; in-app progress remains available. */ }
    }

    private fun notificationId(videoId: String) = videoId.hashCode()
    private fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / (1024.0 * 1024.0))

    companion object { private const val CHANNEL_ID = "youtube_downloads" }
}
