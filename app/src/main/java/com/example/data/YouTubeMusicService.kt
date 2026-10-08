package com.example.data

import android.util.Log
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class YouTubeSearchPage(
    val songs: List<Song>,
    val continuation: String?
)

data class YouTubeAudioStream(val url: String, val bitrateKbps: Int, val audioOnly: Boolean)

object YouTubeMusicService {
    private const val TAG = "YouTubeMusicService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val suggestionClient = client.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    // Playback metadata responses are small; bound each network attempt so one slow
    // response cannot hold the player in its loading state for tens of seconds.
    private val playbackClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    const val AUDIO_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    fun videoIdFor(song: Song): String? {
        val fromPath = song.filePath.takeIf { it.startsWith("yt://") }?.removePrefix("yt://")
        val fromResolvedStream = streamVideoIdsByUrl[song.filePath]
        val fromThumbnail = song.albumArtUri
            ?.substringAfter("/vi/", "")
            ?.substringBefore('/')
        return (fromPath ?: fromResolvedStream ?: fromThumbnail)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
    }

    fun bitrateKbpsForStream(url: String): Int? = streamCache.values
        .asSequence()
        .mapNotNull { cache -> cache.streams.firstOrNull { it.url == url }?.bitrateKbps }
        .firstOrNull { it > 0 }

    @Volatile
    private var cachedVisitorData: String? = null
    @Volatile
    private var visitorDataExpiryTimestamp: Long = 0L

    private val visitorDataMutex = Mutex()
    private data class CachedStream(val streams: List<YouTubeAudioStream>, val activeIndex: Int, val expiresAtMs: Long) {
        val urls: List<String> get() = streams.map { it.url }
        val url: String get() = streams[activeIndex].url
        fun selectedIndex(targetKbps: Int?): Int {
            if (targetKbps == null) return activeIndex
            val audioIndices = streams.indices.filter { streams[it].audioOnly && streams[it].bitrateKbps > 0 }
            if (audioIndices.isEmpty()) return activeIndex
            return audioIndices.minByOrNull { index ->
                val bitrate = streams[index].bitrateKbps
                if (bitrate <= targetKbps) targetKbps - bitrate else 100_000 + bitrate - targetKbps
            } ?: activeIndex
        }
    }
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val streamVideoIdsByUrl = ConcurrentHashMap<String, String>()
    private val streamResolutionGates = ConcurrentHashMap<String, Mutex>()
    private val suggestionCache = ConcurrentHashMap<String, List<String>>()

    /** Returns a still-valid visitor token without waiting for the token endpoint. */
    private fun peekVisitorData(): String {
        val token = cachedVisitorData
        return if (!token.isNullOrBlank() && System.currentTimeMillis() < visitorDataExpiryTimestamp) token else ""
    }

    /** Returns YouTube-scoped autocomplete terms for the search field. */
    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        suggestionCache[trimmed.lowercase()]?.let { return@withContext it }

        try {
            val encodedQuery = java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=vi&gl=VN&q=$encodedQuery")
                .header("User-Agent", AUDIO_USER_AGENT)
                .build()

            suggestionClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val payload = JSONArray(response.body?.string().orEmpty())
                val suggestions = payload.optJSONArray(1) ?: return@withContext emptyList()
                val results = buildList {
                    for (index in 0 until suggestions.length()) {
                        val suggestion = suggestions.optString(index).trim()
                        if (suggestion.isNotEmpty() && suggestion !in this) add(suggestion)
                        if (size == 6) break
                    }
                }
                suggestionCache[trimmed.lowercase()] = results
                results
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.d(TAG, "YouTube autocomplete unavailable: ${error.message}")
            emptyList()
        }
    }

    /**
     * Obtains or refreshes the visitorData token from YouTube's visitor_id endpoint.
     */
    suspend fun getVisitorData(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val current = cachedVisitorData
        if (!forceRefresh && current != null && now < visitorDataExpiryTimestamp && current.isNotBlank()) {
            return@withContext current
        }

        visitorDataMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            val lockedCurrent = cachedVisitorData
            if (!forceRefresh && !lockedCurrent.isNullOrBlank() && lockedNow < visitorDataExpiryTimestamp) {
                return@withLock lockedCurrent
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
                    .header("User-Agent", AUDIO_USER_AGENT)
                    .header("X-Goog-Api-Format-Version", "2")
                    .build()

                playbackClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseStr = response.body?.string() ?: ""
                        val json = JSONObject(responseStr)
                        val visitorData = json.optJSONObject("responseContext")?.optString("visitorData")
                        if (!visitorData.isNullOrBlank()) {
                            cachedVisitorData = visitorData
                            visitorDataExpiryTimestamp = lockedNow + (2 * 3600 * 1000) // valid for 2 hours
                            Log.d(TAG, "Successfully acquired fresh visitorData token")
                            return@withLock visitorData
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Error fetching visitorData: ${e.message}")
            }
            cachedVisitorData?.takeIf {
                it.isNotBlank() && System.currentTimeMillis() < visitorDataExpiryTimestamp
            } ?: ""
        }
    }

    /**
     * Resolves a direct audio stream URL for a given YouTube videoId.
     */
    suspend fun resolveStreamUrl(videoId: String, preferredBitrateKbps: Int? = null): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        val gate = streamResolutionGates.computeIfAbsent(videoId) { Mutex() }
        gate.withLock {
            // Another prefetch or tap may have resolved this video while we waited.
            streamCache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
                Log.d(TAG, "Using cached stream URL for $videoId")
                return@withLock it.streams[it.selectedIndex(preferredBitrateKbps)].url
            }

            val startedAt = System.nanoTime()
            val cached = streamCache[videoId]
            if (cached != null && streamCache.remove(videoId, cached)) {
                cached.urls.forEach { streamVideoIdsByUrl.remove(it, videoId) }
            }

            fun logResolved(source: String, url: String): String {
                Log.d(TAG, "Resolved $videoId via $source in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
                return url
            }

            // Fast path: use visitorData warmed while the YouTube tab/search was loading.
            var streamUrl = queryVisionOsStream(videoId, forceFreshVisitorData = false, preferredBitrateKbps = preferredBitrateKbps)
            if (streamUrl != null) {
                return@withLock logResolved("VISIONOS", streamUrl)
            }

        // Try the second supported client with the same token before making another
        // visitor-token request. This avoids an unnecessary round trip on fallback.
            try {
            val visitorData = getVisitorData(forceRefresh = false)
            val vrPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.56.21")
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

            val vrRequest = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(vrPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", AUDIO_USER_AGENT)
                .header("X-Goog-Api-Format-Version", "2")
                .build()

            playbackClient.newCall(vrRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val streams = extractDirectAudioStreams(json)
                    if (streams.isNotEmpty()) {
                        val url = streams[selectStreamIndex(streams, preferredBitrateKbps)].url
                        cacheStreamUrl(videoId, streams, json)
                        Log.d(TAG, "Resolved stream via ANDROID_VR fallback for $videoId")
                        return@withLock logResolved("ANDROID_VR", url)
                    }
                    Log.w(
                        TAG,
                        "ANDROID_VR returned no direct audio URL for $videoId; status=${json.optJSONObject("playabilityStatus")?.optString("status") ?: "unknown"}"
                    )
                } else {
                    Log.w(TAG, "ANDROID_VR player request failed for $videoId: HTTP ${response.code}")
                }
            }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "ANDROID_VR fallback stream resolution failed for $videoId: ${e.message}")
            }

        // Refresh the token only after both clients have failed with the cached token.
            Log.d(TAG, "Both playback clients failed for $videoId; refreshing visitorData once")
            streamUrl = queryVisionOsStream(videoId, forceFreshVisitorData = true, preferredBitrateKbps = preferredBitrateKbps)
            if (streamUrl != null) {
                return@withLock logResolved("VISIONOS refreshed session", streamUrl)
            }

            Log.e(TAG, "All stream resolution passes failed for $videoId in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
            null
        }
    }

    fun invalidateStreamUrl(url: String) {
        val videoId = streamVideoIdsByUrl.remove(url)
        if (videoId != null) {
            streamCache.computeIfPresent(videoId) { _, cached -> if (url in cached.urls) null else cached }
        } else {
            streamCache.entries.removeAll { url in it.value.urls }
        }
    }

    /** Try another compatible format from the same player response before another network request. */
    suspend fun resolveFreshStreamUrl(failedUrl: String, fallbackVideoId: String? = null): String? {
        val videoId = streamVideoIdsByUrl[failedUrl] ?: fallbackVideoId ?: return null
        val gate = streamResolutionGates.computeIfAbsent(videoId) { Mutex() }
        val nextUrl = gate.withLock {
            val cached = streamCache[videoId] ?: return@withLock null
            if (cached.expiresAtMs <= System.currentTimeMillis()) return@withLock null
            val failedIndex = cached.urls.indexOf(failedUrl)
            if (failedIndex < 0) return@withLock null
            val nextIndex = failedIndex + 1
            if (nextIndex >= cached.urls.size) return@withLock null
            streamCache[videoId] = cached.copy(activeIndex = nextIndex)
            cached.urls[nextIndex]
        }
        if (nextUrl != null) {
            Log.i(TAG, "Switching $videoId to another stream format")
            return nextUrl
        }
        invalidateStreamUrl(failedUrl)
        return resolveStreamUrl(videoId)
    }

    private fun cacheStreamUrl(videoId: String, streams: List<YouTubeAudioStream>, playerResponse: JSONObject) {
        val expiresInSeconds = playerResponse.optJSONObject("streamingData")
            ?.optLong("expiresInSeconds", 240L)
            ?: 240L
        val safeLifetimeMs = (expiresInSeconds - 60L).coerceAtLeast(0L) * 1000L
        streams.forEach { streamVideoIdsByUrl[it.url] = videoId }
        if (safeLifetimeMs == 0L) return
        streamCache[videoId] = CachedStream(streams, 0, System.currentTimeMillis() + safeLifetimeMs)
    }

    private suspend fun queryVisionOsStream(videoId: String, forceFreshVisitorData: Boolean, preferredBitrateKbps: Int? = null): String? = withContext(Dispatchers.IO) {
        try {
            // The first playback attempt can work without visitorData. Don't add a separate
            // network round trip before asking for the stream; request a token only on retry.
            val visitorData = if (forceFreshVisitorData) getVisitorData(forceRefresh = true) else peekVisitorData()
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
                .header("User-Agent", AUDIO_USER_AGENT)
                .header("X-Goog-Api-Format-Version", "2")
                .build()

            playbackClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val playability = json.optJSONObject("playabilityStatus")?.optString("status", "")
                    if (playability != null && (playability == "LOGIN_REQUIRED" || playability == "UNPLAYABLE")) {
                        Log.w(TAG, "VISIONOS returned $playability for $videoId")
                        return@withContext null
                    }
                    val streams = extractDirectAudioStreams(json)
                    if (streams.isNotEmpty()) {
                        val url = streams[selectStreamIndex(streams, preferredBitrateKbps)].url
                        cacheStreamUrl(videoId, streams, json)
                        Log.d(TAG, "Successfully resolved VISIONOS audio stream for $videoId")
                        return@withContext url
                    }
                    Log.w(
                        TAG,
                        "VISIONOS returned no direct audio URL for $videoId; status=${playability ?: "unknown"}"
                    )
                } else {
                    Log.w(TAG, "VISIONOS player request failed for $videoId: HTTP ${response.code}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "VISIONOS query error for $videoId: ${e.message}")
        }
        return@withContext null
    }

    private fun extractDirectAudioStreams(playerResponseJson: JSONObject): List<YouTubeAudioStream> {
        val streamingData = playerResponseJson.optJSONObject("streamingData") ?: return emptyList()
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
        val formats = streamingData.optJSONArray("formats")
        val aac = mutableListOf<YouTubeAudioStream>()
        val alternativeAudio = mutableListOf<YouTubeAudioStream>()
        val muxedMp4 = mutableListOf<YouTubeAudioStream>()
        if (adaptiveFormats != null) {
            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType", "")
                val url = format.optString("url", "")
                if (!mimeType.startsWith("audio/") || url.isBlank()) continue
                val entry = YouTubeAudioStream(url, format.optInt("bitrate", 0) / 1000, audioOnly = true)
                if (mimeType.contains("mp4")) aac += entry else alternativeAudio += entry
            }
        }
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val url = format.optString("url", "")
                if (url.isNotBlank() && format.optString("mimeType").startsWith("video/mp4"))
                    muxedMp4 += YouTubeAudioStream(url, format.optInt("bitrate", 0) / 1000, audioOnly = false)
            }
        }
        // Prefer the highest-bitrate audio-only stream available, regardless of codec.
        // Keep remaining audio formats as fallbacks for devices with limited decoder support.
        val audioOnly = (aac + alternativeAudio)
            .sortedByDescending { it.bitrateKbps }
            .distinctBy { it.url }
        val muxedFallback = muxedMp4
            .sortedByDescending { it.bitrateKbps }
            .take(1)
        return (audioOnly + muxedFallback).distinctBy { it.url }
    }

    private fun selectStreamIndex(streams: List<YouTubeAudioStream>, targetKbps: Int?): Int {
        if (targetKbps == null) return 0
        val audioIndices = streams.indices.filter { streams[it].audioOnly && streams[it].bitrateKbps > 0 }
        if (audioIndices.isEmpty()) return 0
        return audioIndices.minByOrNull { index ->
            val bitrate = streams[index].bitrateKbps
            if (bitrate <= targetKbps) targetKbps - bitrate else 100_000 + bitrate - targetKbps
        } ?: 0
    }

    /**
     * Searches YouTube Music using the official InnerTube WEB_REMIX client.
     * Returns rich Song objects with thumbnails, artist, title, and duration.
     */
    suspend fun searchSongs(query: String): List<Song> = searchSongsPage(query).songs

    suspend fun searchSongsPage(query: String): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext YouTubeSearchPage(emptyList(), null)

        try {
            val page = requestSearchPage(searchPayload(query = trimmed, songFilter = true))
            if (page.songs.isNotEmpty() || page.continuation != null) return@withContext page
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Targeted song search failed for '$query': ${e.message}")
        }

        // Fallback: search without the song filter if targeted search gave no results.
        try {
            requestSearchPage(searchPayload(query = trimmed, songFilter = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Fallback search failed for '$query': ${e.message}")
            YouTubeSearchPage(emptyList(), null)
        }
    }

    suspend fun loadMoreSongs(continuation: String): YouTubeSearchPage = withContext(Dispatchers.IO) {
        if (continuation.isBlank()) return@withContext YouTubeSearchPage(emptyList(), null)
        try {
            requestSearchPage(searchPayload(continuation = continuation))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "YouTube continuation request failed: ${e.message}")
            YouTubeSearchPage(emptyList(), continuation)
        }
    }

    private fun searchPayload(
        query: String? = null,
        continuation: String? = null,
        songFilter: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("context", JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20231215.01.00")
                put("hl", "vi")
                put("gl", "VN")
            })
            cachedVisitorData?.takeIf { System.currentTimeMillis() < visitorDataExpiryTimestamp }
                ?.let { put("visitorData", it) }
        })
        if (query != null) put("query", query)
        if (continuation != null) put("continuation", continuation)
        if (songFilter) put("params", "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D")
    }

    private fun requestSearchPage(payload: JSONObject): YouTubeSearchPage {
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return YouTubeSearchPage(emptyList(), null)
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return YouTubeSearchPage(emptyList(), null)
            val root = JSONObject(body)
            cacheVisitorDataFromResponse(root)
            return YouTubeSearchPage(parseSearchResults(root), extractContinuation(root))
        }
    }

    private fun cacheVisitorDataFromResponse(root: JSONObject) {
        val visitorData = root.optJSONObject("responseContext")?.optString("visitorData")
        if (!visitorData.isNullOrBlank()) {
            cachedVisitorData = visitorData
            visitorDataExpiryTimestamp = System.currentTimeMillis() + (2 * 3600 * 1000)
        }
    }

    private fun extractContinuation(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                value.optJSONObject("nextContinuationData")?.optString("continuation")
                    ?.takeIf { it.isNotBlank() }?.let { return it }
                value.optJSONObject("continuationCommand")?.optString("token")
                    ?.takeIf { it.isNotBlank() }?.let { return it }
                val keys = value.keys()
                while (keys.hasNext()) {
                    extractContinuation(value.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    extractContinuation(value.opt(index))?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseSearchResults(root: JSONObject): List<Song> {
        val results = mutableListOf<Song>()
        val seenVideoIds = mutableSetOf<String>()

        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")

            val contents = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents != null) for (cIdx in 0 until contents.length()) {
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

        // Subsequent InnerTube pages wrap results in continuationContents or an
        // appendContinuationItemsAction instead of the initial tab/shelf layout.
        collectContinuationSongs(root.opt("continuationContents"), results, seenVideoIds)
        collectContinuationSongs(root.opt("onResponseReceivedCommands"), results, seenVideoIds)
        collectContinuationSongs(root.opt("onResponseReceivedActions"), results, seenVideoIds)

        return results
    }

    private fun collectContinuationSongs(value: Any?, results: MutableList<Song>, seenVideoIds: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                val renderer = value.optJSONObject("musicResponsiveListItemRenderer")
                    ?: value.optJSONObject("compactVideoRenderer")
                if (renderer != null) {
                    val song = parseMusicItem(renderer)
                    if (song != null && seenVideoIds.add(song.filePath)) results.add(song)
                    return
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    collectContinuationSongs(value.opt(keys.next()), results, seenVideoIds)
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectContinuationSongs(value.opt(index), results, seenVideoIds)
                }
            }
        }
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
            val idHash = videoId.hashCode().toLong()
            val stableId = -kotlin.math.abs(idHash).coerceAtLeast(1L)

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
        "🏆 Top Hits",
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
