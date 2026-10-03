package com.example.data

import android.util.Log
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YouTubeMusicService {
    private const val TAG = "YouTubeMusicService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private const val VISION_OS_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    @Volatile
    private var cachedVisitorData: String? = null
    @Volatile
    private var visitorDataExpiryTimestamp: Long = 0L

    /**
     * Obtains or refreshes the visitorData token from YouTube's visitor_id endpoint.
     */
    suspend fun getVisitorData(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val current = cachedVisitorData
        if (current != null && now < visitorDataExpiryTimestamp) {
            return@withContext current
        }

        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("hl", "vi")
                        put("gl", "VN")
                    })
                })
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", VISION_OS_UA)
                .header("X-Goog-Api-Format-Version", "2")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val json = JSONObject(responseStr)
                    val visitorData = json.optJSONObject("responseContext")?.optString("visitorData")
                    if (!visitorData.isNullOrBlank()) {
                        cachedVisitorData = visitorData
                        visitorDataExpiryTimestamp = now + (2 * 3600 * 1000) // valid for 2 hours
                        return@withContext visitorData
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error fetching visitorData: ${e.message}")
        }
        return@withContext cachedVisitorData ?: ""
    }

    /**
     * Resolves a direct audio stream URL for a given YouTube videoId.
     */
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        // Strategy 1: VISIONOS client with visitorData (returns unthrottled direct audio URLs)
        try {
            val visitorData = getVisitorData()
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("hl", "vi")
                        put("gl", "VN")
                        if (visitorData.isNotBlank()) {
                            put("visitorData", visitorData)
                        }
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", VISION_OS_UA)
                .header("X-Goog-Api-Format-Version", "2")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val url = extractDirectAudioUrl(json)
                    if (url != null) {
                        return@withContext url
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "VISIONOS stream resolution failed for $videoId: ${e.message}")
        }

        // Strategy 2: Fallback to ANDROID_VR client
        try {
            val vrPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.56.21")
                        put("hl", "vi")
                        put("gl", "VN")
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val vrRequest = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(vrPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Quest 2) AppleWebKit/537.36")
                .build()

            client.newCall(vrRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val url = extractDirectAudioUrl(json)
                    if (url != null) {
                        return@withContext url
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ANDROID_VR fallback stream resolution failed for $videoId: ${e.message}")
        }

        return@withContext null
    }

    private fun extractDirectAudioUrl(playerResponseJson: JSONObject): String? {
        val streamingData = playerResponseJson.optJSONObject("streamingData") ?: return null
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

        var bestAacUrl: String? = null
        var bestAacBitrate = 0
        var bestAlternativeAudioUrl: String? = null
        var maxAltBitrate = 0

        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType", "")
            if (mimeType.contains("audio")) {
                val url = format.optString("url", "")
                if (url.isNotBlank()) {
                    val itag = format.optInt("itag", 0)
                    val bitrate = format.optInt("bitrate", 0)
                    val isAac = mimeType.contains("mp4") || itag == 140 || itag == 139
                    if (isAac && bitrate >= bestAacBitrate) {
                        bestAacBitrate = bitrate
                        bestAacUrl = url
                    } else if (bitrate > maxAltBitrate) {
                        maxAltBitrate = bitrate
                        bestAlternativeAudioUrl = url
                    }
                }
            }
        }

        return bestAacUrl ?: bestAlternativeAudioUrl
    }

    /**
     * Searches YouTube Music using the official InnerTube WEB_REMIX client.
     * Returns rich Song objects with thumbnails, artist, title, and duration.
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            // Include song filter param (EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D) for targeted song results
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20231215.01.00")
                        put("hl", "vi")
                        put("gl", "VN")
                    })
                })
                put("query", trimmed)
                put("params", "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D")
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(bodyStr)
                val results = parseSearchResults(root)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Targeted song search failed for '$query': ${e.message}")
        }

        // Fallback: search without filter param if targeted search gave no results
        try {
            val fallbackPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20231215.01.00")
                        put("hl", "vi")
                        put("gl", "VN")
                    })
                })
                put("query", trimmed)
            }

            val fallbackRequest = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(fallbackPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .build()

            client.newCall(fallbackRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(bodyStr)
                return@withContext parseSearchResults(root)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Fallback search failed for '$query': ${e.message}")
            return@withContext emptyList()
        }
    }

    private fun parseSearchResults(root: JSONObject): List<Song> {
        val results = mutableListOf<Song>()
        val seenVideoIds = mutableSetOf<String>()

        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?: return emptyList()

            val contents = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: return emptyList()

            for (cIdx in 0 until contents.length()) {
                val section = contents.optJSONObject(cIdx) ?: continue

                // Check musicShelfRenderer
                val shelfRenderer = section.optJSONObject("musicShelfRenderer")
                if (shelfRenderer != null) {
                    val items = shelfRenderer.optJSONArray("contents")
                    if (items != null) {
                        for (iIdx in 0 until items.length()) {
                            val item = items.optJSONObject(iIdx) ?: continue
                            val listItem = item.optJSONObject("musicResponsiveListItemRenderer")
                                ?: item.optJSONObject("compactVideoRenderer")
                                ?: continue

                            val song = parseMusicItem(listItem)
                            if (song != null && !seenVideoIds.contains(song.filePath)) {
                                seenVideoIds.add(song.filePath)
                                results.add(song)
                            }
                        }
                    }
                }

                // Check itemSectionRenderer
                val itemSectionRenderer = section.optJSONObject("itemSectionRenderer")
                if (itemSectionRenderer != null) {
                    val subContents = itemSectionRenderer.optJSONArray("contents")
                    if (subContents != null) {
                        for (sIdx in 0 until subContents.length()) {
                            val subItem = subContents.optJSONObject(sIdx) ?: continue

                            // Nested shelf inside itemSectionRenderer
                            val nestedShelf = subItem.optJSONObject("musicShelfRenderer")
                            if (nestedShelf != null) {
                                val nItems = nestedShelf.optJSONArray("contents")
                                if (nItems != null) {
                                    for (iIdx in 0 until nItems.length()) {
                                        val item = nItems.optJSONObject(iIdx) ?: continue
                                        val listItem = item.optJSONObject("musicResponsiveListItemRenderer")
                                            ?: item.optJSONObject("compactVideoRenderer")
                                            ?: continue
                                        val song = parseMusicItem(listItem)
                                        if (song != null && !seenVideoIds.contains(song.filePath)) {
                                            seenVideoIds.add(song.filePath)
                                            results.add(song)
                                        }
                                    }
                                }
                            }

                            // Direct listItem inside itemSectionRenderer
                            val listItem = subItem.optJSONObject("musicResponsiveListItemRenderer")
                                ?: subItem.optJSONObject("compactVideoRenderer")
                            if (listItem != null) {
                                val song = parseMusicItem(listItem)
                                if (song != null && !seenVideoIds.contains(song.filePath)) {
                                    seenVideoIds.add(song.filePath)
                                    results.add(song)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error traversing search results: ${e.message}")
        }

        return results
    }

    private fun parseMusicItem(item: JSONObject): Song? {
        try {
            // Find videoId
            var videoId = ""
            val overlay = item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
            if (overlay != null) {
                videoId = overlay.optString("videoId", "")
            }

            if (videoId.isBlank()) {
                val nav = item.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                if (nav != null) {
                    videoId = nav.optString("videoId", "")
                }
            }

            val flexColumns = item.optJSONArray("flexColumns")
            if (flexColumns == null || flexColumns.length() == 0) return null

            // Column 0: Title & possible videoId
            val col0 = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            val title = col0?.optJSONObject(0)?.optString("text", "") ?: ""
            if (title.isBlank()) return null

            if (videoId.isBlank() && col0 != null) {
                val watchEndpoint = col0.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                if (watchEndpoint != null) {
                    videoId = watchEndpoint.optString("videoId", "")
                }
            }

            if (videoId.isBlank()) return null

            // Column 1: Artist, Album, Duration
            var artist = "YouTube Music"
            var durationMs = 180000L // default 3:00

            val col1 = if (flexColumns.length() > 1) {
                flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
            } else null

            if (col1 != null && col1.length() > 0) {
                // Find artist from runs (filter out generic headers like "Bài hát", "Video", " • ", separators)
                for (r in 0 until col1.length()) {
                    val runObj = col1.optJSONObject(r) ?: continue
                    val runText = runObj.optString("text", "").trim()
                    if (runText.isBlank() || runText == "•" || runText == "," || runText == "&" || runText == "và") continue
                    if (runText.equals("Bài hát", ignoreCase = true) || runText.equals("Video", ignoreCase = true) || runText.equals("Song", ignoreCase = true)) continue
                    if (runText.matches(Regex("\\d{1,2}:\\d{2}"))) {
                        durationMs = parseDurationMs(runText)
                    } else if (artist == "YouTube Music" && !runText.contains("lượt xem") && !runText.contains("views")) {
                        artist = runText
                    }
                }
            }

            // Thumbnail
            var thumbUrl: String? = null
            val thumbnails = item.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")

            if (thumbnails != null && thumbnails.length() > 0) {
                val bestThumb = thumbnails.optJSONObject(thumbnails.length() - 1)
                thumbUrl = bestThumb?.optString("url")
            }
            if (thumbUrl == null) {
                thumbUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            }

            // Negative stable ID based on videoId to distinguish from device MediaStore IDs
            val stableId = -Math.abs(videoId.hashCode().toLong()).coerceAtMost(-1L)

            return Song(
                id = stableId,
                title = title,
                artist = artist,
                album = "YouTube Online",
                durationMs = durationMs,
                filePath = "yt://$videoId",
                albumArtUri = thumbUrl,
                format = "YouTube Online",
                bitrateKbps = 128,
                sampleRateHz = 44100,
                bitDepth = 16,
                isHiRes = false,
                isFavorite = false
            )
        } catch (e: Throwable) {
            return null
        }
    }

    private fun parseDurationMs(durationStr: String): Long {
        return try {
            val parts = durationStr.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                (minutes * 60 + seconds) * 1000L
            } else if (parts.size == 3) {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].toLong()
                (hours * 3600 + minutes * 60 + seconds) * 1000L
            } else {
                180000L
            }
        } catch (e: Exception) {
            180000L
        }
    }

    /**
     * Curated category presets for instant discovery without typing.
     */
    val CATEGORIES = listOf(
        "🔥 Hot V-Pop",
        "⚡ Remix TikTok",
        "☕ Acoustic Chill",
        "🌙 Lofi Thư Giãn",
        "🏆 Top Hits"
    )

    fun getCategoryQuery(category: String): String {
        return when (category) {
            "🔥 Hot V-Pop" -> "top nhac tre viet nam hot nhat hien nay"
            "⚡ Remix TikTok" -> "nhac tre remix hot tiktok bay phong"
            "☕ Acoustic Chill" -> "acoustic viet nam nhe nhang chill"
            "🌙 Lofi Thư Giãn" -> "nhac lofi viet nam chill thu gian dem khuya"
            "🏆 Top Hits" -> "top hits global billboard"
            else -> category
        }
    }
}
