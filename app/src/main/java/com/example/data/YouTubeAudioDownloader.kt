package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class DownloadedYouTubeAudio(val file: File, val format: String)
data class YouTubeAudioTransferProgress(val downloadedBytes: Long, val totalBytes: Long)

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
        onProgress: (YouTubeAudioTransferProgress) -> Unit
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
        tryDownloadParallelRanges(streamUrl, videoId, downloadDir, onProgress)?.let { return@withContext it }
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
                    var lastProgressAt = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloadedBytes += count
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastProgressAt >= 300L || totalBytes > 0 && downloadedBytes == totalBytes) {
                                    onProgress(YouTubeAudioTransferProgress(downloadedBytes, totalBytes))
                                    lastProgressAt = now
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
                    onProgress(YouTubeAudioTransferProgress(downloadedBytes, totalBytes.takeIf { it > 0 } ?: downloadedBytes))
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

    /** Uses parallel byte ranges only after a 206 probe confirms that this signed CDN URL supports them. */
    private suspend fun tryDownloadParallelRanges(
        url: String,
        videoId: String,
        directory: File,
        onProgress: (YouTubeAudioTransferProgress) -> Unit
    ): DownloadedYouTubeAudio? = withContext(Dispatchers.IO) {
        val probeRequest = Request.Builder().url(url)
            .header("User-Agent", YouTubeMusicService.AUDIO_USER_AGENT)
            .header("Referer", "https://www.youtube.com/")
            .header("Range", "bytes=0-0")
            .build()
        val probe = try { client.newCall(probeRequest).execute() } catch (_: Exception) { return@withContext null }
        val totalBytes: Long
        val mime: String
        try {
            if (probe.code != 206) return@withContext null
            val contentRange = probe.header("Content-Range") ?: return@withContext null
            totalBytes = contentRange.substringAfterLast('/').toLongOrNull() ?: return@withContext null
            if (totalBytes < 2L * 1024 * 1024) return@withContext null
            val headerMime = probe.body?.contentType()?.toString()?.substringBefore(';')?.lowercase().orEmpty()
            val urlMime = Uri.parse(url).getQueryParameter("mime")?.substringBefore(';')?.lowercase().orEmpty()
            mime = headerMime.takeIf { it.startsWith("audio/") }
                ?: urlMime.takeIf { it.startsWith("audio/") }
                ?: return@withContext null
        } finally { probe.close() }

        val extension = when {
            mime.contains("webm") -> "webm"
            mime.contains("ogg") -> "ogg"
            mime.contains("mpeg") -> "mp3"
            else -> "m4a"
        }
        val finalFile = File(directory, "$videoId.$extension")
        val tempFile = File(directory, "$videoId.$extension.parallel.part")
        val partCount = 4
        val partSize = (totalBytes + partCount - 1) / partCount
        val received = AtomicLong(0L)
        val lastReportAt = AtomicLong(0L)
        val partFiles = (0 until partCount).map { File(directory, "$videoId.$extension.range$it.part") }
        try {
            val completed = coroutineScope {
                (0 until partCount).map { index ->
                    async(Dispatchers.IO) {
                        val start = index * partSize
                        val end = minOf(totalBytes - 1, start + partSize - 1)
                        if (start > end) return@async true
                        val request = Request.Builder().url(url)
                            .header("User-Agent", YouTubeMusicService.AUDIO_USER_AGENT)
                            .header("Referer", "https://www.youtube.com/")
                            .header("Range", "bytes=$start-$end")
                            .build()
                        val response = try { client.newCall(request).execute() } catch (_: Exception) { return@async false }
                        response.use { result ->
                            if (result.code != 206) return@async false
                            val body = result.body ?: return@async false
                            var partBytes = 0L
                            body.byteStream().use { input ->
                                FileOutputStream(partFiles[index]).buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                        partBytes += count
                                        val now = android.os.SystemClock.elapsedRealtime()
                                        if (now - lastReportAt.get() >= 300L) {
                                            val totalReceived = received.addAndGet(count.toLong())
                                            onProgress(YouTubeAudioTransferProgress(totalReceived, totalBytes))
                                            lastReportAt.set(now)
                                        } else received.addAndGet(count.toLong())
                                    }
                                }
                            }
                            partBytes == end - start + 1
                        }
                    }
                }.awaitAll().all { it }
            }
            if (!completed) return@withContext null
            FileOutputStream(tempFile).buffered().use { output ->
                partFiles.forEach { part -> part.inputStream().buffered().use { it.copyTo(output) } }
            }
            if (tempFile.length() != totalBytes) return@withContext null
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) tempFile.copyTo(finalFile, overwrite = true).also { tempFile.delete() }
            onProgress(YouTubeAudioTransferProgress(totalBytes, totalBytes))
            DownloadedYouTubeAudio(finalFile, extension.uppercase())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            partFiles.forEach(File::delete)
            tempFile.delete()
        }
    }
}
