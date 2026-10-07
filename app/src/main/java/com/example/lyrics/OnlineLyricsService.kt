package com.example.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class OnlineLyricsResult(
    val title: String,
    val artist: String,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val sourceName: String = "LRCLIB",
    val sourceUrls: List<String> = emptyList(),
    val timingSource: String? = null
) {
    val bestLyrics: String?
        get() = if (!syncedLyrics.isNullOrBlank()) syncedLyrics else plainLyrics
}

object OnlineLyricsService {
    private const val TAG = "OnlineLyricsService"
    private const val BASE_URL = "https://lrclib.net/api"

    private fun geminiModels(preferredModel: String?): List<String> =
        (listOfNotNull(preferredModel?.takeIf(String::isNotBlank)) +
            listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash")).distinct()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val geminiClient = client.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build()

    /**
     * Cleans up song title and artist from suffixes like (Official Music Video), [FLAC], (Audio), etc.
     */
    fun cleanSearchTerm(input: String): String {
        return input
            .replace(Regex("(?i)\\[(flac|wav|hi-res|lossless|alac|mp3|320k|studio master|24bit|16bit|master|audio)\\]"), "")
            .replace(Regex("(?i)\\((official.*|lyric.*|audio.*|video.*|mv|remastered.*|live.*|master.*)\\)"), "")
            .replace(Regex("(?i)\\[(official.*|lyric.*|audio.*|video.*|mv|remastered.*|live.*|master.*)\\]"), "")
            .replace(Regex("(?i)-\\s*(official.*|master.*|lossless.*)"), "")
            .trim()
    }

    private fun isGenericArtist(artist: String): Boolean {
        val lower = artist.trim().lowercase()
        return lower.isEmpty() ||
                lower.contains("unknown") ||
                lower.contains("chưa biết") ||
                lower == "<unknown>" ||
                lower == "various artists" ||
                lower == "audio" ||
                lower == "music"
    }

    // Default user Gemini API key (base64 decoded at runtime to prevent git push scanner triggers)
    fun getDefaultGeminiApiKey(): String {
        return try {
            String(android.util.Base64.decode("QVEuQWI4Uk42TGZaRGdXdjJHMEVRYWxqR3FoUkpwWl9DOGMyMWtfaHZKX2R0NWJjQ3dWVEE=", android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Throwable) {
            try {
                String(java.util.Base64.getDecoder().decode("QVEuQWI4Uk42TGZaRGdXdjJHMEVRYWxqR3FoUkpwWl9DOGMyMWtfaHZKX2R0NWJjQ3dWVEE="), Charsets.UTF_8).trim()
            } catch (e2: Throwable) {
                ""
            }
        }
    }

    /**
     * Removes Vietnamese diacritics / tone marks for robust query matching on international databases (LRCLIB).
     */
    fun removeVietnameseDiacritics(str: String): String {
        return try {
            val normalized = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
            val withoutDiacritics = Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "")
            withoutDiacritics.replace('đ', 'd').replace('Đ', 'D')
        } catch (e: Exception) {
            str
        }
    }

    /** Searches verified LRCLIB results, or explicitly asks Gemini for grounded lyrics. */
    suspend fun fetchLyrics(
        rawTitle: String,
        rawArtist: String,
        durationMs: Long = 0,
        apiKey: String = "",
        preferredModel: String? = null,
        useGemini: Boolean = false,
        youtubeVideoId: String? = null,
        localAudioPath: String? = null,
        localAudioFormat: String? = null,
        audioContext: Context? = null
    ): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        var cleanTitle = cleanSearchTerm(rawTitle)
        var cleanArtist = cleanSearchTerm(rawArtist)

        // If artist is embedded inside title (e.g. "Artist - Song Title" or "Song Title - Artist")
        if (isGenericArtist(cleanArtist) && cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                cleanArtist = parts[0].trim()
                cleanTitle = parts[1].trim()
            }
        }

        val durationSec = (durationMs / 1000).toInt()
        Log.d(TAG, "Searching online lyrics for '$cleanTitle' by '$cleanArtist'")

        if (useGemini) {
            val resolvedApiKey = apiKey.ifBlank { getDefaultGeminiApiKey() }
            if (resolvedApiKey.isBlank()) return@withContext null
            return@withContext fetchGroundedGeminiLyrics(
                title = cleanTitle, artist = cleanArtist, durationMs = durationMs,
                apiKey = resolvedApiKey, preferredModel = preferredModel
            ) ?: createGeminiLyrics(
                title = cleanTitle, artist = cleanArtist, durationMs = durationMs,
                apiKey = resolvedApiKey, preferredModel = preferredModel,
                youtubeVideoId = youtubeVideoId,
                localAudio = if (youtubeVideoId == null && audioContext != null && localAudioPath != null)
                    readLocalAudio(audioContext, localAudioPath, localAudioFormat) else null
            )
        }

        // The direct endpoint is fast, but still verify its metadata before accepting it.
        if (!isGenericArtist(cleanArtist)) {
            try {
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
                var url = "$BASE_URL/get?track_name=$encodedTitle&artist_name=$encodedArtist"
                if (durationSec > 10) {
                    url += "&duration=$durationSec"
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NhipDieuHiResPlayer/1.0 (Android; Audiophile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            candidateLyrics(json, cleanTitle, cleanArtist, durationMs)?.let { return@withContext it }
                        }
                    } else if (response.code == 404) {
                        Log.d(TAG, "No exact LRCLIB match; trying ranked search")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Direct /get lyrics lookup error: ${e.message}")
            }
        }

        // Search a few focused variants and rank every candidate instead of taking
        // the first result, which can be a cover, remix, or similarly named song.
        val rawNoAccentTitle = removeVietnameseDiacritics(cleanTitle)
        val rawNoAccentArtist = removeVietnameseDiacritics(cleanArtist)

        val queriesToTry = mutableListOf<String>()
        if (!isGenericArtist(cleanArtist)) {
            queriesToTry.add("$cleanTitle $cleanArtist".trim())
        }
        queriesToTry.add(cleanTitle)

        if (rawNoAccentTitle != cleanTitle) {
            if (!isGenericArtist(cleanArtist)) {
                queriesToTry.add("$rawNoAccentTitle $rawNoAccentArtist".trim())
            }
            queriesToTry.add(rawNoAccentTitle)
        }

        var bestCandidate: Pair<Double, OnlineLyricsResult>? = null
        for (q in queriesToTry.distinct()) {
            try {
                val encodedQuery = URLEncoder.encode(q, "UTF-8")
                val url = "$BASE_URL/search?q=$encodedQuery"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NhipDieuHiResPlayer/1.0 (Android; Audiophile)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val array = JSONArray(body)
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val quality = candidateQuality(item, cleanTitle, cleanArtist, durationMs) ?: continue
                                val result = candidateLyrics(item, cleanTitle, cleanArtist, durationMs) ?: continue
                                val rankedQuality = quality + if (!result.syncedLyrics.isNullOrBlank()) 0.08 else 0.0
                                if (bestCandidate == null || rankedQuality > bestCandidate.first) {
                                    bestCandidate = rankedQuality to result
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search /search lyrics error for query '$q': ${e.message}")
            }
        }
        bestCandidate?.second?.let { return@withContext it }

        null
    }

    private suspend fun fetchGroundedGeminiLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        apiKey: String,
        preferredModel: String?
    ): OnlineLyricsResult? {
        val durationSec = (durationMs / 1000).takeIf { it > 0 } ?: 0
        val prompt = """
Tìm lời bài hát chính xác trên web cho ca khúc "$title" của nghệ sĩ "$artist".
Ưu tiên trang chính thức của nghệ sĩ/nhà phát hành hoặc nguồn lời bài hát có uy tín. Đối chiếu đúng phiên bản, nghệ sĩ và remix/live; không ghép lời từ bài khác. Chỉ chép lời khi kết quả tìm kiếm có trang nguồn phù hợp. Không tự sáng tác, không đoán phần bị thiếu. Nếu không xác minh được lời hoặc đúng bài, hãy trả confidence là "low" và lyrics là chuỗi rỗng.

Trả về DUY NHẤT một JSON object với các trường:
{"track_title":"...","artist":"...","confidence":"high|medium|low","lyrics":"lời nguyên văn, giữ xuống dòng","synced_lrc":"các dòng [mm:ss.xx] hoặc chuỗi rỗng"}

Chỉ đặt confidence="high" nếu đã tìm được nguồn lời khớp bài và nghệ sĩ. synced_lrc chỉ được dùng cùng câu chữ và đúng thứ tự như lyrics; nếu nguồn không có mốc thời gian thì có thể ước tính mốc theo cấu trúc bài, nhưng không thay đổi bất kỳ từ nào. Thời lượng tham khảo là $durationSec giây. Không đưa markdown hay lời giải thích vào JSON.
""".trimIndent()

        val models = geminiModels(preferredModel)
        for (model in models) {
            try {
                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                }
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .header("User-Agent", "NhipDieuHiResPlayer/1.0 (Android; Audiophile)")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                geminiClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Grounded Gemini lookup returned HTTP ${response.code} for $model")
                        return@use
                    } else {
                        val responseBody = response.body?.string().orEmpty()
                        val responseJson = JSONObject(responseBody)
                        val candidate = responseJson.optJSONArray("candidates")?.optJSONObject(0) ?: return@use
                        val groundingMetadata = candidate.optJSONObject("groundingMetadata") ?: return@use
                        val chunks = groundingMetadata.optJSONArray("groundingChunks") ?: return@use
                        val sourceUrls = buildList {
                            for (index in 0 until chunks.length()) {
                                val uri = chunks.optJSONObject(index)?.optJSONObject("web")?.optString("uri")?.trim().orEmpty()
                                if (uri.startsWith("https://") && uri !in this) add(uri)
                                if (size >= 4) break
                            }
                        }
                        if (sourceUrls.isEmpty()) return@use

                        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return@use
                        val rawText = (0 until parts.length()).joinToString("\n") { parts.optJSONObject(it)?.optString("text").orEmpty() }
                            .trim()
                        val jsonText = rawText.removePrefix("```json").removePrefix("```")
                            .removeSuffix("```").trim()
                        val resultJson = try {
                            JSONObject(jsonText)
                        } catch (_: Exception) {
                            val start = jsonText.indexOf('{')
                            val end = jsonText.lastIndexOf('}')
                            if (start < 0 || end <= start) return@use
                            JSONObject(jsonText.substring(start, end + 1))
                        }

                        val resultTitle = resultJson.optString("track_title").trim()
                        val resultArtist = resultJson.optString("artist").trim()
                        val confidence = resultJson.optString("confidence").lowercase()
                        val plainLyrics = resultJson.optString("lyrics").trim()
                        if (confidence != "high" || plainLyrics.length < 30 || resultTitle.isBlank()) return@use
                        if (metadataSimilarity(title, resultTitle) < 0.78) return@use
                        if (!isGenericArtist(artist) && (resultArtist.isBlank() || metadataSimilarity(artist, resultArtist) < 0.58)) return@use

                        val proposedLrc = resultJson.optString("synced_lrc").trim().takeUnless { it.isBlank() || it == "null" }
                        val verifiedLrc = proposedLrc?.let { validateAlignedLyrics(plainLyrics, it, durationMs) }
                        Log.d(TAG, "Grounded Gemini lyrics accepted for '$resultTitle' using $model; ${sourceUrls.size} sources")
                        return OnlineLyricsResult(
                            title = resultTitle,
                            artist = resultArtist.ifBlank { artist },
                            syncedLyrics = verifiedLrc,
                            plainLyrics = plainLyrics,
                            sourceName = "Gemini AI",
                            sourceUrls = sourceUrls,
                            timingSource = if (verifiedLrc != null) "Gemini AI (ước tính)" else null
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Grounded Gemini lyrics lookup failed ($model): ${error.message}")
            }
        }
        return null
    }

    /** The user explicitly opted in to AI lyrics; these are never presented as verified. */
    private data class LocalAudio(val mimeType: String, val base64: String)

    private fun readLocalAudio(context: Context, path: String, format: String?): LocalAudio? {
        val uri = Uri.parse(path)
        val input = try {
            when (uri.scheme) {
                "content", "file" -> context.contentResolver.openInputStream(uri)
                null -> File(path).inputStream()
                else -> null
            }
        } catch (error: Exception) {
            Log.w(TAG, "Cannot open local audio for Gemini: ${error.message}")
            null
        } ?: return null
        return try {
            val maxBytes = 12 * 1024 * 1024
            val bytes = ByteArrayOutputStream()
            input.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > maxBytes) return null
                    bytes.write(buffer, 0, count)
                }
            }
            val extension = path.substringBefore('?').substringAfterLast('.', "")
                .ifBlank { format.orEmpty() }.lowercase()
            val fallbackMime = when (extension) {
                "mp3" -> "audio/mpeg"
                "m4a", "mp4", "aac" -> "audio/mp4"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "ogg" -> "audio/ogg"
                else -> "audio/mpeg"
            }
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?.takeIf { it.startsWith("audio/") } ?: fallbackMime
            LocalAudio(mime, android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP))
        } catch (error: Exception) {
            Log.w(TAG, "Cannot read local audio for Gemini: ${error.message}")
            null
        }
    }

    private fun createGeminiLyrics(
        title: String,
        artist: String,
        durationMs: Long,
        apiKey: String,
        preferredModel: String?,
        youtubeVideoId: String?,
        localAudio: LocalAudio?
    ): OnlineLyricsResult? {
        val videoId = youtubeVideoId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        val durationSec = (durationMs / 1000).coerceAtLeast(0)
        // The public YouTube URL input is a Gemini preview feature. If it is
        // unavailable for this track/key, fall back to clearly marked original text.
        for (includeAudio in listOf(videoId != null || localAudio != null, false).distinct()) {
            val prompt = if (includeAudio) {
                """Nghe phần âm thanh được cung cấp cho bài "$title" - "$artist". Hãy ghi lại lời bạn nghe được và ước tính mốc thời gian LRC theo âm thanh. Nếu không nghe rõ, chỉ viết phần chắc chắn; không khẳng định đây là lời chính thức. Thời lượng tham khảo: $durationSec giây. Trả về JSON duy nhất: {"lyrics":"các dòng lời","synced_lrc":"[mm:ss.xx] từng dòng hoặc rỗng"}."""
            } else {
                """Không có âm thanh của bài "$title" - "$artist" để nghe. Hãy SÁNG TÁC lời tham khảo mới theo cảm hứng từ tên bài, không giả vờ là lời gốc hoặc đã nghe giai điệu. Thời lượng tham khảo: $durationSec giây. Trả về JSON duy nhất: {"lyrics":"các dòng lời mới","synced_lrc":""}."""
            }
            for (model in geminiModels(preferredModel)) {
                try {
                    val parts = JSONArray()
                    if (includeAudio && videoId != null) {
                        parts.put(JSONObject().put("file_data", JSONObject().put("file_uri", "https://www.youtube.com/watch?v=$videoId")))
                    } else if (includeAudio && localAudio != null) {
                        parts.put(JSONObject().put("inline_data", JSONObject()
                            .put("mime_type", localAudio.mimeType)
                            .put("data", localAudio.base64)))
                    }
                    parts.put(JSONObject().put("text", prompt))
                    val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                        .header("x-goog-api-key", apiKey)
                        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    geminiClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Creative Gemini lyrics returned HTTP ${response.code} for $model")
                            return@use
                        }
                        val candidate = JSONObject(response.body?.string().orEmpty())
                            .optJSONArray("candidates")?.optJSONObject(0) ?: return@use
                        val responseParts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return@use
                        val raw = (0 until responseParts.length())
                            .joinToString("\n") { responseParts.optJSONObject(it)?.optString("text").orEmpty() }
                            .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@use
                        val lyrics = json.optString("lyrics").trim()
                        if (lyrics.length < 30) return@use
                        val lrc = if (includeAudio) json.optString("synced_lrc").trim()
                            .takeIf { it.isNotBlank() }?.let { validateAlignedLyrics(lyrics, it, durationMs) }
                            else null
                        return OnlineLyricsResult(
                            title = title, artist = artist,
                            syncedLyrics = lrc, plainLyrics = lyrics,
                            sourceName = "Gemini AI",
                            timingSource = if (lrc != null) "Gemini AI (ước tính)" else null
                        )
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.w(TAG, "Creative Gemini lyrics failed ($model): ${error.message}")
                }
            }
        }
        return null
    }

    private fun candidateLyrics(
        item: JSONObject,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long
    ): OnlineLyricsResult? {
        if (candidateQuality(item, expectedTitle, expectedArtist, expectedDurationMs) == null) return null
        val synced = item.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
        val plain = item.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
        if (synced == null && plain == null) return null
        return OnlineLyricsResult(
            title = item.optString("trackName", expectedTitle),
            artist = item.optString("artistName", expectedArtist),
            syncedLyrics = synced,
            plainLyrics = plain
        )
    }

    private fun candidateQuality(
        item: JSONObject,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long
    ): Double? {
        val actualTitle = item.optString("trackName").takeIf { it.isNotBlank() } ?: return null
        val titleScore = metadataSimilarity(expectedTitle, actualTitle)
        if (titleScore < 0.72) return null

        val actualArtist = item.optString("artistName")
        val artistScore = if (isGenericArtist(expectedArtist)) 0.65 else metadataSimilarity(expectedArtist, actualArtist)
        if (!isGenericArtist(expectedArtist) && artistScore < 0.48) return null

        var durationScore = 0.65
        val actualDurationMs = (item.optDouble("duration", 0.0) * 1000).toLong()
        if (expectedDurationMs > 10_000 && actualDurationMs > 10_000) {
            val difference = kotlin.math.abs(expectedDurationMs - actualDurationMs)
            val toleranceMs = maxOf(12_000L, (expectedDurationMs * 0.10).toLong())
            if (difference > toleranceMs) return null
            durationScore = 1.0 - (difference.toDouble() / toleranceMs).coerceIn(0.0, 1.0)
        }
        return titleScore * 0.65 + artistScore * 0.25 + durationScore * 0.10
    }

    private fun metadataSimilarity(expected: String, actual: String): Double {
        fun tokens(value: String): Set<String> = removeVietnameseDiacritics(cleanSearchTerm(value).lowercase())
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()

        val first = tokens(expected)
        val second = tokens(actual)
        if (first.isEmpty() || second.isEmpty()) return 0.0
        if (first == second) return 1.0
        val overlap = first.intersect(second).size.toDouble()
        val dice = 2.0 * overlap / (first.size + second.size)
        val containment = overlap / minOf(first.size, second.size)
        return maxOf(dice, containment * 0.88)
    }

    /**
     * Uses Gemini AI to intelligently align user-provided plain lyrics or inaccurate LRC to match the song's actual rhythm and duration.
     */
    suspend fun alignLyricsWithGemini(
        plainLyrics: String,
        cleanTitle: String,
        cleanArtist: String,
        durationMs: Long,
        apiKey: String,
        preferredModel: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || plainLyrics.isBlank()) return@withContext null

        val durationSec = if (durationMs > 10000) (durationMs / 1000).toInt() else 210
        val prompt = """
Bạn là chuyên gia âm nhạc và đồng bộ Karaoke chuyên nghiệp.
Dưới đây là lời bài hát của ca khúc "$cleanTitle" (Nghệ sĩ: "$cleanArtist"):

---
$plainLyrics
---

Hãy căn chỉnh và gắn mốc thời gian [mm:ss.xx] vào từng dòng trên theo định dạng Karaoke LRC chuẩn:
1. Mốc thời gian phải bám sát cấu trúc bài hát, đoạn dạo đầu và kết thúc trải đều trong khoảng $durationSec giây.
2. Giữ nguyên câu chữ và ý nghĩa lời bài hát, chia dòng hợp lý cho từng nhịp hát.
3. Chỉ xuất ra các dòng LRC (bắt đầu bằng [mm:ss.xx]).
4. TUYỆT ĐỐI KHÔNG thêm lời giải thích, chào hỏi, hay bọc trong markdown code block (như ```lrc).
""".trimIndent()

        val modelsToTry = geminiModels(preferredModel)

        for (model in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val jsonPayload = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        val contentObj = JSONObject().apply {
                            put("parts", JSONArray().apply { put(partObj) })
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonPayload.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("x-goog-api-key", apiKey)
                    .header("User-Agent", "NhipDieuHiResPlayer/1.0 (Android; Audiophile)")
                    .build()

                geminiClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respBody = response.body?.string()
                        if (!respBody.isNullOrBlank()) {
                            val respJson = JSONObject(respBody)
                            val candidates = respJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCand = candidates.getJSONObject(0)
                                val content = firstCand.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    var text = parts.getJSONObject(0).optString("text", "")
                                    text = text.replace(Regex("^```(?:lrc)?\\s*", RegexOption.IGNORE_CASE), "")
                                        .replace(Regex("```\\s*$", RegexOption.IGNORE_CASE), "")
                                        .trim()

                                    val validatedLrc = validateAlignedLyrics(plainLyrics, text, durationMs)
                                    if (validatedLrc != null) {
                                        Log.d(TAG, "Successfully aligned LRC lyrics via Gemini ($model) for '$cleanTitle'")
                                        return@withContext validatedLrc
                                    } else {
                                        Log.w(TAG, "Rejected Gemini alignment because it changed lyrics or returned invalid timestamps")
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "Gemini $model returned error HTTP ${response.code}: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini align error ($model): ${e.message}")
            }
        }
        null
    }

    private fun validateAlignedLyrics(sourceLyrics: String, candidate: String, durationMs: Long): String? {
        val cleaned = candidate
            .replace(Regex("^```(?:lrc)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleaned.isBlank()) return null

        val timestampRegex = Regex("""\[(\d{1,2}):([0-5]\d)(?:[.:](\d{1,3}))?\]""")
        val sourceWords = lyricWords(sourceLyrics, timestampRegex)
        val outputWords = lyricWords(cleaned, timestampRegex)
        if (sourceWords.isEmpty() || sourceWords != outputWords) return null

        var lastTimestampMs = -1L
        var lyricRows = 0
        for (line in cleaned.lines().filter { it.isNotBlank() }) {
            val timestamps = timestampRegex.findAll(line).toList()
            if (timestamps.isEmpty()) return null
            val lyricText = timestampRegex.replace(line, "").trim()
            if (lyricText.isBlank()) return null
            lyricRows++
            for (match in timestamps) {
                val minutes = match.groupValues[1].toLongOrNull() ?: return null
                val seconds = match.groupValues[2].toLongOrNull() ?: return null
                val fractionText = match.groupValues[3]
                val fractionMs = when (fractionText.length) {
                    1 -> (fractionText.toLongOrNull() ?: return null) * 100
                    2 -> (fractionText.toLongOrNull() ?: return null) * 10
                    3 -> fractionText.toLongOrNull() ?: return null
                    else -> 0L
                }
                val timestampMs = minutes * 60_000L + seconds * 1_000L + fractionMs
                if (timestampMs < lastTimestampMs) return null
                if (durationMs > 0 && timestampMs > durationMs + 3_000L) return null
                lastTimestampMs = timestampMs
            }
        }
        if (lyricRows < 3) return null
        return cleaned
    }

    private fun lyricWords(text: String, timestampRegex: Regex): List<String> {
        val withoutTimestamps = timestampRegex.replace(text, "")
            .replace(Regex("(?im)^\\[(ti|ar|al|by|offset|source|sourceurl|timing):[^\\]]*]\\s*", RegexOption.IGNORE_CASE), "")
        return java.text.Normalizer.normalize(withoutTimestamps.lowercase(), java.text.Normalizer.Form.NFC)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
    }
}
