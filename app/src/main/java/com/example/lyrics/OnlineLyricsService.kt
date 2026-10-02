package com.example.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class OnlineLyricsResult(
    val title: String,
    val artist: String,
    val syncedLyrics: String?,
    val plainLyrics: String?
) {
    val bestLyrics: String?
        get() = if (!syncedLyrics.isNullOrBlank()) syncedLyrics else plainLyrics
}

object OnlineLyricsService {
    private const val TAG = "OnlineLyricsService"
    private const val BASE_URL = "https://lrclib.net/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    /**
     * Attempts to fetch lyrics from LRCLIB.
     * First queries the exact /get endpoint, and if not found, falls back to /search.
     */
    suspend fun fetchLyrics(rawTitle: String, rawArtist: String, durationMs: Long = 0): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        var cleanTitle = cleanSearchTerm(rawTitle)
        var cleanArtist = cleanSearchTerm(rawArtist)

        // If artist is part of title (e.g. "Artist - Song Title" or "Song Title - Artist")
        if (isGenericArtist(cleanArtist) && cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                cleanArtist = parts[0].trim()
                cleanTitle = parts[1].trim()
            }
        }

        val durationSec = (durationMs / 1000).toInt()
        Log.d(TAG, "Searching online lyrics for '$cleanTitle' by '$cleanArtist'")

        // 1. Try exact get endpoint if artist is known
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
                            val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                            val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                            if (synced != null || plain != null) {
                                return@withContext OnlineLyricsResult(
                                    title = json.optString("trackName", cleanTitle),
                                    artist = json.optString("artistName", cleanArtist),
                                    syncedLyrics = synced,
                                    plainLyrics = plain
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct /get lyrics lookup error: ${e.message}")
            }
        }

        // 2. Fallback to /search endpoint (try with title + artist, or just title if artist generic)
        val queriesToTry = mutableListOf<String>()
        if (!isGenericArtist(cleanArtist)) {
            queriesToTry.add("$cleanTitle $cleanArtist".trim())
        }
        queriesToTry.add(cleanTitle)

        for (q in queriesToTry) {
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
                            // Look for result with synced or plain lyrics
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                                val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                                if (synced != null || plain != null) {
                                    return@withContext OnlineLyricsResult(
                                        title = item.optString("trackName", cleanTitle),
                                        artist = item.optString("artistName", cleanArtist),
                                        syncedLyrics = synced,
                                        plainLyrics = plain
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search /search lyrics error for query '$q': ${e.message}")
            }
        }

        null
    }
}
