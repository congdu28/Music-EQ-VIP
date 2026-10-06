package com.example.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import androidx.room.Room
import com.example.equalizer.EqualizerManager
import com.example.model.EqualizerPreset
import com.example.model.Playlist
import com.example.model.PlaylistSongCrossRef
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository(private val context: Context) {
    private val TAG = "MusicRepository"

    val db: MusicDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            MusicDatabase::class.java,
            "nhip_dieu_music.db"
        ).fallbackToDestructiveMigration().build()
    }

    private val prefs = context.getSharedPreferences("nhip_dieu_settings", Context.MODE_PRIVATE)

    fun isDarkThemeEnabled(): Boolean = prefs.getBoolean("appearance_dark_theme", false)

    fun getAppearanceMode(): String = prefs.getString("appearance_mode", null)
        ?: if (isDarkThemeEnabled()) "DARK" else "LIGHT"

    fun setAppearanceMode(mode: String) {
        val normalized = mode.takeIf { it in setOf("LIGHT", "DARK", "SYSTEM") } ?: "LIGHT"
        prefs.edit()
            .putString("appearance_mode", normalized)
            .putBoolean("appearance_dark_theme", normalized == "DARK")
            .apply()
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("appearance_dark_theme", enabled).apply()
    }

    fun getAccentColor(): Int = prefs.getInt("appearance_accent_color", 0xFF3399FF.toInt())

    fun setAccentColor(color: Int) {
        prefs.edit().putInt("appearance_accent_color", color).apply()
    }

    fun getPlayerAmbientMode(): String = prefs.getString("player_ambient_mode", "OFF") ?: "OFF"

    fun setPlayerAmbientMode(mode: String) {
        prefs.edit().putString("player_ambient_mode", mode).apply()
    }

    fun getGeminiApiKey(): String {
        val def = com.example.lyrics.OnlineLyricsService.getDefaultGeminiApiKey()
        return getCustomGeminiApiKey() ?: def
    }

    /** Returns only a user-provided key; the embedded app key is never exposed to the settings UI. */
    fun getCustomGeminiApiKey(): String? {
        val saved = prefs.getString("gemini_api_key", null)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val defaultKey = com.example.lyrics.OnlineLyricsService.getDefaultGeminiApiKey()
        return saved.takeUnless { it == defaultKey }
    }

    fun setGeminiApiKey(key: String) {
        val cleaned = key.trim()
        val defaultKey = com.example.lyrics.OnlineLyricsService.getDefaultGeminiApiKey()
        if (cleaned.isBlank() || cleaned == defaultKey) {
            prefs.edit().remove("gemini_api_key").apply()
        } else {
            prefs.edit().putString("gemini_api_key", cleaned).apply()
        }
    }

    fun getGeminiModel(): String {
        return prefs.getString("gemini_model", "gemini-3.7-flash") ?: "gemini-3.7-flash"
    }

    fun setGeminiModel(model: String) {
        prefs.edit().putString("gemini_model", model.trim()).apply()
    }

    fun shouldRecommendInitialLibraryScan(): Boolean =
        !prefs.getBoolean("has_seen_initial_scan_recommendation", false)

    fun markInitialLibraryScanRecommendationSeen() {
        prefs.edit().putBoolean("has_seen_initial_scan_recommendation", true).apply()
    }

    fun hasCompletedLibraryScan(): Boolean = prefs.getBoolean("has_completed_library_scan", false)

    fun markLibraryScanCompleted() {
        prefs.edit().putBoolean("has_completed_library_scan", true).apply()
    }

    fun getRecentlyPlayedSongs(): List<Song> {
        val saved = prefs.getString("recently_played_songs_v1", null) ?: return emptyList()
        return runCatching {
            val items = JSONArray(saved)
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val filePath = item.optString("filePath").takeIf(String::isNotBlank) ?: continue
                    add(
                        Song(
                            id = item.optLong("id"),
                            title = item.optString("title", "Bài hát không tên"),
                            artist = item.optString("artist", "Không rõ nghệ sĩ"),
                            album = item.optString("album", ""),
                            durationMs = item.optLong("durationMs"),
                            filePath = filePath,
                            albumArtUri = item.optString("albumArtUri").takeIf(String::isNotBlank),
                            format = item.optString("format", "FLAC"),
                            bitrateKbps = item.optInt("bitrateKbps", 320),
                            sampleRateHz = item.optInt("sampleRateHz", 44100),
                            bitDepth = item.optInt("bitDepth", 16),
                            isHiRes = item.optBoolean("isHiRes"),
                            isFavorite = item.optBoolean("isFavorite"),
                            lyrics = item.optString("lyrics").takeIf(String::isNotBlank),
                            lrcOffsetMs = item.optLong("lrcOffsetMs"),
                            playCount = item.optInt("playCount"),
                            addedTimestamp = item.optLong("addedTimestamp", System.currentTimeMillis())
                        )
                    )
                }
            }.take(20)
        }.getOrDefault(emptyList())
    }

    fun recordRecentlyPlayed(song: Song): List<Song> {
        val updated = (listOf(song) + getRecentlyPlayedSongs())
            .distinctBy { if (it.id != 0L) it.id else it.filePath }
            .take(20)
        replaceRecentlyPlayedSongs(updated)
        return updated
    }

    fun replaceRecentlyPlayedSongs(songs: List<Song>) {
        val items = JSONArray()
        songs.take(20).forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("album", item.album)
                    .put("durationMs", item.durationMs)
                    .put("filePath", item.filePath)
                    .put("albumArtUri", item.albumArtUri)
                    .put("format", item.format)
                    .put("bitrateKbps", item.bitrateKbps)
                    .put("sampleRateHz", item.sampleRateHz)
                    .put("bitDepth", item.bitDepth)
                    .put("isHiRes", item.isHiRes)
                    .put("isFavorite", item.isFavorite)
                    .put("lyrics", item.lyrics)
                    .put("lrcOffsetMs", item.lrcOffsetMs)
                    .put("playCount", item.playCount)
                    .put("addedTimestamp", item.addedTimestamp)
            )
        }
        prefs.edit().putString("recently_played_songs_v1", items.toString()).apply()
    }

    val allSongs: Flow<List<Song>> = db.songDao().getAllSongs()
    val favoriteSongs: Flow<List<Song>> = db.songDao().getFavoriteSongs()
    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().getAllPlaylists()
    val allPresets: Flow<List<EqualizerPreset>> = db.equalizerDao().getAllPresets()

    suspend fun initializeDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        removeDuplicateSongs()

        // Older builds generated six demo WAV files in cache. Remove only those exact app-owned
        // paths and rows when upgrading, then seed one short, truthful WAV preview if no songs remain.
        val legacySampleNames = listOf(
            "viet_ballad_nhip_tim.wav",
            "chill_lofi_ha_noi_mua.wav",
            "piano_sonata_dem_sai_gon.wav",
            "edm_cyber_neon_bass.wav",
            "acoustic_chieu_tay_ho.wav",
            "synthwave_audiophile_96k.wav"
        )
        val legacySampleFiles = legacySampleNames.map { File(context.cacheDir, it) }
        val legacyPaths = legacySampleFiles.map { it.absolutePath }
        val currentSongs = db.songDao().getAllSongs().firstOrNull().orEmpty()
        val legacyRows = currentSongs.filter { it.filePath in legacyPaths }
        if (legacyRows.isNotEmpty()) {
            db.playlistDao().removeSongReferences(legacyRows.map { it.id })
            db.songDao().deleteSongsByPaths(legacyRows.map { it.filePath })
            db.playlistDao().deletePlaylistsByNames(
                listOf(
                    "Nhạc Việt Tuyệt Đỉnh 2026",
                    "Audiophile Hi-Res FLAC & WAV",
                    "Chill Buổi Tối & Lofi Beat",
                    "EDM Siêu Bass Cực Căng"
                )
            )
        }
        legacySampleFiles.forEach { it.delete() }

        if (db.songDao().getAllSongs().firstOrNull().isNullOrEmpty()) {
            db.songDao().insertSong(createSampleSong())
        }
        if (db.equalizerDao().getAllPresetsList().isEmpty()) {
            val defaultPresets = listOf(
                EqualizerPreset(name = "Mặc định (Flat)", bandLevelsCsv = "0,0,0,0,0,0,0,0,0,0", bassBoost = 0, virtualizer = 0, reverbPreset = 0),
                EqualizerPreset(name = "Tăng âm trầm (Bass Boost)", bandLevelsCsv = "800,600,450,200,0,0,100,200,300,350", bassBoost = 650, virtualizer = 300, reverbPreset = 1),
                EqualizerPreset(name = "Nhạc Pop (Pop Vocal)", bandLevelsCsv = "-100,150,350,450,300,0,-100,150,300,400", bassBoost = 250, virtualizer = 300, reverbPreset = 2),
                EqualizerPreset(name = "Nhạc Rock (Rock Energy)", bandLevelsCsv = "500,400,250,-100,-200,0,250,450,600,650", bassBoost = 400, virtualizer = 200, reverbPreset = 3),
                EqualizerPreset(name = "Nhạc Điện Tử EDM (Dance)", bandLevelsCsv = "700,600,200,0,-200,200,400,600,700,800", bassBoost = 750, virtualizer = 400, reverbPreset = 4),
                EqualizerPreset(name = "Giọng Hát Trong Trẻo (Acoustic)", bandLevelsCsv = "-300,-200,0,250,500,600,500,300,100,0", bassBoost = 150, virtualizer = 250, reverbPreset = 1),
                EqualizerPreset(name = "Nhạc Jazz (Smooth Jazz)", bandLevelsCsv = "300,200,100,200,-150,-150,0,150,300,400", bassBoost = 300, virtualizer = 200, reverbPreset = 2),
                EqualizerPreset(name = "Nhạc Cổ Điển (Classical)", bandLevelsCsv = "450,350,250,150,-100,-100,0,200,350,450", bassBoost = 200, virtualizer = 400, reverbPreset = 3)
            )
            db.equalizerDao().insertPresets(defaultPresets)
        }
    }

    private fun createSampleSong(): Song {
        val file = AudioSynthesizer.generateSampleAudioFile(
            context,
            fileName = "music_eq_demo_preview.wav",
            durationSeconds = 30,
            style = AudioSynthesizer.TrackStyle.VIET_BALLAD
        )
        return Song(
            title = "Bản nhạc thử",
            artist = "Music EQ",
            album = "Bài nghe thử",
            durationMs = 30_000,
            filePath = file.absolutePath,
            format = "WAV",
            bitrateKbps = 1411,
            sampleRateHz = 44_100,
            bitDepth = 16,
            isHiRes = false,
            isFavorite = false
        )
    }

    suspend fun scanDeviceAudioFiles(): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                val scannedSongs = mutableListOf<Song>()
                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Bài hát không tên"
                    val artist = it.getString(artistCol) ?: "Nghệ sĩ chưa rõ"
                    val album = it.getString(albumCol) ?: "Album không tên"
                    val duration = it.getLong(durationCol)
                    val path = it.getString(dataCol) ?: ""
                    val mime = it.getString(mimeCol) ?: ""

                    val format = when {
                        path.endsWith(".flac", ignoreCase = true) || mime.contains("flac") -> "FLAC"
                        path.endsWith(".wav", ignoreCase = true) || mime.contains("wav") -> "WAV"
                        path.endsWith(".aac", ignoreCase = true) || mime.contains("aac") -> "AAC"
                        path.endsWith(".m4a", ignoreCase = true) || path.endsWith(".alac", ignoreCase = true) -> "ALAC"
                        path.endsWith(".ogg", ignoreCase = true) || path.endsWith(".opus", ignoreCase = true) -> "OGG"
                        else -> "MP3"
                    }

                    val isHiRes = format in listOf("FLAC", "WAV", "ALAC")

                    // Check if a local .lrc file exists alongside the audio file
                    var localLyrics: String? = null
                    if (path.isNotEmpty()) {
                        try {
                            val dotIndex = path.lastIndexOf('.')
                            if (dotIndex > 0) {
                                val lrcPath = path.substring(0, dotIndex) + ".lrc"
                                val lrcFile = File(lrcPath)
                                if (lrcFile.exists() && lrcFile.isFile && lrcFile.canRead()) {
                                    val text = lrcFile.readText().trim()
                                    if (text.isNotEmpty()) {
                                        localLyrics = text
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore file reading errors
                        }
                    }

                    scannedSongs.add(
                        Song(
                            title = title,
                            artist = if (artist == "<unknown>") "Nghệ sĩ Việt" else artist,
                            album = album,
                            durationMs = duration,
                            filePath = path,
                            format = format,
                            bitrateKbps = if (isHiRes) 1411 else 320,
                            sampleRateHz = if (isHiRes) 96000 else 44100,
                            bitDepth = if (isHiRes) 24 else 16,
                            isHiRes = isHiRes,
                            lyrics = localLyrics
                        )
                    )
                }
                val existingPaths = db.songDao().getAllSongPaths().filter { it.isNotEmpty() }.toSet()
                val uniqueScannedSongs = scannedSongs
                    .filter { it.filePath.isNotEmpty() }
                    .distinctBy { it.filePath }
                    .filter { !existingPaths.contains(it.filePath) }

                if (uniqueScannedSongs.isNotEmpty()) {
                    db.songDao().insertSongs(uniqueScannedSongs)
                    count = uniqueScannedSongs.size
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning MediaStore: ${e.message}")
        }
        count
    }

    /** Imports audio documents below a user-selected SAF folder. The persisted tree grant
     * lets MediaPlayer reopen these content URIs after the app or device restarts. */
    suspend fun scanAudioFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val scannedSongs = mutableListOf<Song>()
        val visitedDirectories = mutableSetOf<String>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "alac")

        fun scanDirectory(documentId: String, depth: Int) {
            if (depth > 12 || !visitedDirectories.add(documentId)) return

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = context.contentResolver.query(childrenUri, projection, null, null, null) ?: return
            cursor.use { children ->
                val idIndex = children.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = children.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = children.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (children.moveToNext()) {
                    val childId = children.getString(idIndex) ?: continue
                    val displayName = children.getString(nameIndex).orEmpty()
                    val mimeType = children.getString(mimeIndex).orEmpty()

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanDirectory(childId, depth + 1)
                        continue
                    }

                    val extension = displayName.substringAfterLast('.', "").lowercase()
                    if (!mimeType.startsWith("audio/") && extension !in audioExtensions) continue

                    val audioUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val retriever = MediaMetadataRetriever()
                    val duration: Long
                    val title: String
                    val artist: String
                    val album: String
                    val sampleRate: Int
                    val bitrate: Int
                    try {
                        retriever.setDataSource(context, audioUri)
                        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.', displayName)
                        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.takeIf { it.isNotBlank() } ?: "Nghệ sĩ chưa rõ"
                        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            ?.takeIf { it.isNotBlank() } ?: "Album không tên"
                        sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 44_100
                        bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000) ?: 320
                    } catch (e: Exception) {
                        Log.d(TAG, "Skipping unreadable audio document $displayName: ${e.message}")
                        continue
                    } finally {
                        try { retriever.release() } catch (_: Exception) { }
                    }

                    if (duration <= 10_000L) continue
                    val format = when (extension) {
                        "flac" -> "FLAC"
                        "wav" -> "WAV"
                        "aac" -> "AAC"
                        "m4a" -> "M4A"
                        "alac" -> "ALAC"
                        "ogg", "opus" -> "OGG"
                        "wma" -> "WMA"
                        else -> "MP3"
                    }
                    val hiRes = format in setOf("FLAC", "WAV", "ALAC") && (sampleRate > 48_000 || bitrate > 1000)
                    scannedSongs.add(
                        Song(
                            title = title,
                            artist = if (artist == "<unknown>") "Nghệ sĩ Việt" else artist,
                            album = album,
                            durationMs = duration,
                            filePath = audioUri.toString(),
                            format = format,
                            bitrateKbps = bitrate,
                            sampleRateHz = sampleRate,
                            bitDepth = if (hiRes) 24 else 16,
                            isHiRes = hiRes
                        )
                    )
                }
            }
        }

        try {
            scanDirectory(DocumentsContract.getTreeDocumentId(treeUri), 0)
            val existingPaths = db.songDao().getAllSongPaths().toSet()
            val newSongs = scannedSongs.distinctBy { it.filePath }.filterNot { it.filePath in existingPaths }
            if (newSongs.isNotEmpty()) db.songDao().insertSongs(newSongs)
            newSongs.size
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning selected audio folder: ${e.message}")
            0
        }
    }

    suspend fun removeDuplicateSongs() = withContext(Dispatchers.IO) {
        try {
            val allSongs = db.songDao().getAllSongsList()
            val seenKeys = mutableSetOf<String>()
            val duplicatesToDelete = mutableListOf<Long>()
            for (song in allSongs) {
                val key = song.filePath.ifEmpty { "${song.title.trim().lowercase()}__${song.artist.trim().lowercase()}" }
                if (seenKeys.contains(key)) {
                    duplicatesToDelete.add(song.id)
                } else {
                    seenKeys.add(key)
                }
            }
            for (id in duplicatesToDelete) {
                db.songDao().deleteSongById(id)
            }
            if (duplicatesToDelete.isNotEmpty()) {
                Log.d(TAG, "Removed ${duplicatesToDelete.size} duplicate songs from database")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing duplicates: ${e.message}")
        }
    }

    suspend fun toggleFavorite(song: Song): Boolean = withContext(Dispatchers.IO) {
        val dao = db.songDao()
        val updatedRows = dao.toggleFavorite(song.id)
        if (updatedRows == 0) {
            // Online YouTube songs are not part of the local scan. Store them on first favorite
            // action so the same favorites flow and screen can manage them reliably.
            dao.insertSong(song.copy(isFavorite = true))
            true
        } else {
            dao.getSongById(song.id)?.isFavorite ?: false
        }
    }

    suspend fun updateSongLyrics(songId: Long, lyrics: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        db.songDao().updateLyrics(songId, lyrics, offsetMs)
        try {
            val song = db.songDao().getSongById(songId)
            if (song != null && song.filePath.isNotEmpty()) {
                val dotIndex = song.filePath.lastIndexOf('.')
                if (dotIndex > 0) {
                    val lrcFile = File(song.filePath.substring(0, dotIndex) + ".lrc")
                    if (!lrcFile.exists() || lrcFile.canWrite()) {
                        lrcFile.writeText(lyrics)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore filesystem permissions issues on external storage
        }
    }

    suspend fun updateSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newFormat: String,
        newBitrate: Int = 320
    ): Song? = withContext(Dispatchers.IO) {
        try {
            val song = db.songDao().getSongById(songId) ?: return@withContext null
            val updated = song.copy(
                title = newTitle.trim(),
                artist = newArtist.trim(),
                album = newAlbum.trim(),
                format = newFormat.trim().uppercase(),
                bitrateKbps = newBitrate,
                isHiRes = newFormat.trim().uppercase() in listOf("FLAC", "WAV", "ALAC") || song.isHiRes
            )
            db.songDao().updateSong(updated)
            updated
        } catch (e: Exception) {
            Log.e(TAG, "Error updating song metadata: ${e.message}")
            null
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        val p = Playlist(name = name, description = description)
        db.playlistDao().insertPlaylist(p)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        db.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return db.playlistDao().getSongsForPlaylist(playlistId)
    }

    suspend fun saveEqualizerPreset(preset: EqualizerPreset) = withContext(Dispatchers.IO) {
        db.equalizerDao().insertPreset(preset)
    }
}
