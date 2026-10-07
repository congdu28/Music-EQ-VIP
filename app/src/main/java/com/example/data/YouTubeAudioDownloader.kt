package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class DownloadedYouTubeAudio(val file: File, val format: String)

/** Downloads an audio-only stream using the same in-app resolver used by online playback. */
object YouTubeAudioDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun download(
        context: Context,
        song: Song,
        onProgress: (Int) -> Unit
    ): DownloadedYouTubeAudio = withContext(Dispatchers.IO) {
        val videoId = song.filePath.removePrefix("yt://").takeIf {
            song.filePath.startsWith("yt://") && it.matches(Regex("[A-Za-z0-9_-]{6,20}"))
        } ?: song.albumArtUri
            ?.substringAfter("/vi/", "")
            ?.substringBefore('/')
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
            ?: throw IllegalArgumentException("Không xác định được mã video YouTube.")

        val externalMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val downloadDir = File(externalMusicDir ?: File(context.filesDir, "Music"), "Music EQ/YouTube")
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw IllegalStateException("Không tạo được thư mục lưu nhạc.")
        }

        var streamUrl = YouTubeMusicService.resolveStreamUrl(videoId)
            ?: throw IllegalStateException("Không lấy được luồng âm thanh cho bài này.")
        val attemptedUrls = mutableSetOf<String>()
        var lastError: String? = null

        try {
            repeat(4) {
                currentCoroutineContext().ensureActive()
                if (!attemptedUrls.add(streamUrl)) return@repeat

                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", YouTubeMusicService.AUDIO_USER_AGENT)
                    .header("Referer", "https://www.youtube.com/")
                    .build()

                val response = client.newCall(request).execute()
                try {
                    if (response.code == 403 || response.code == 410) {
                        lastError = "Luồng tải đã hết hạn, đang thử làm mới."
                        streamUrl = YouTubeMusicService.resolveFreshStreamUrl(streamUrl, videoId)
                            ?: throw IllegalStateException("YouTube đã từ chối luồng tải. Vui lòng thử lại.")
                        return@repeat
                    }
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Tải nhạc thất bại (HTTP ${response.code}).")
                    }

                    val headerMime = response.body?.contentType()?.toString()
                        ?.substringBefore(';')?.lowercase().orEmpty()
                    val urlMime = Uri.parse(streamUrl).getQueryParameter("mime")
                        ?.substringBefore(';')?.lowercase().orEmpty()
                    val mime = headerMime.takeIf { it.startsWith("audio/") }
                        ?: urlMime.takeIf { it.startsWith("audio/") }

                    if (headerMime.startsWith("video/") || (mime == null && urlMime.startsWith("video/"))) {
                        lastError = "Luồng hiện tại không phải âm thanh riêng, đang thử định dạng khác."
                        val nextUrl = YouTubeMusicService.resolveFreshStreamUrl(streamUrl, videoId)
                        if (nextUrl == null || nextUrl == streamUrl) return@repeat
                        streamUrl = nextUrl
                        return@repeat
                    }
                    if (mime == null && headerMime.isNotBlank() && headerMime != "application/octet-stream") {
                        throw IllegalStateException("Định dạng âm thanh YouTube không được hỗ trợ ($headerMime).")
                    }

                    val extension = when {
                        mime?.contains("webm") == true -> "webm"
                        mime?.contains("ogg") == true -> "ogg"
                        mime?.contains("mpeg") == true -> "mp3"
                        else -> "m4a"
                    }
                    val finalFile = File(downloadDir, "$videoId.$extension")
                    val tempFile = File(downloadDir, "$videoId.$extension.part")
                    tempFile.delete()
                    val body = response.body ?: throw IllegalStateException("YouTube không trả về dữ liệu âm thanh.")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloadedBytes += count
                                if (totalBytes > 0) {
                                    onProgress(((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99))
                                }
                            }
                        }
                    }
                    if (tempFile.length() == 0L) {
                        tempFile.delete()
                        throw IllegalStateException("Tệp tải về đang rỗng.")
                    }
                    if (finalFile.exists() && !finalFile.delete()) {
                        tempFile.delete()
                        throw IllegalStateException("Không thể thay thế bản tải cũ.")
                    }
                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }
                    onProgress(100)
                    return@withContext DownloadedYouTubeAudio(finalFile, extension.uppercase())
                } finally {
                    response.close()
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            downloadDir.listFiles()?.filter { it.name.startsWith("$videoId.") && it.name.endsWith(".part") }
                ?.forEach(File::delete)
            throw cancelled
        } catch (error: Throwable) {
            downloadDir.listFiles()?.filter { it.name.startsWith("$videoId.") && it.name.endsWith(".part") }
                ?.forEach(File::delete)
            throw error
        }

        throw IllegalStateException(lastError ?: "Không tìm được luồng âm thanh tải được cho bài này.")
    }
}
