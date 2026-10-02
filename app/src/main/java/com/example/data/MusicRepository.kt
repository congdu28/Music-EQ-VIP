package com.example.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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

    fun getGeminiApiKey(): String {
        return prefs.getString("gemini_api_key", com.example.lyrics.OnlineLyricsService.DEFAULT_GEMINI_API_KEY)
            ?: com.example.lyrics.OnlineLyricsService.DEFAULT_GEMINI_API_KEY
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    val allSongs: Flow<List<Song>> = db.songDao().getAllSongs()
    val favoriteSongs: Flow<List<Song>> = db.songDao().getFavoriteSongs()
    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().getAllPlaylists()
    val allPresets: Flow<List<EqualizerPreset>> = db.equalizerDao().getAllPresets()

    suspend fun initializeDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        removeDuplicateSongs()
        val existingSongs = db.songDao().getAllSongs().firstOrNull()
        if (existingSongs.isNullOrEmpty()) {
            val sampleSongs = createSampleSongs()
            db.songDao().insertSongs(sampleSongs)

            val samplePlaylists = listOf(
                Playlist(
                    name = "Nhạc Việt Tuyệt Đỉnh 2026",
                    description = "Những giai điệu acoustic ballad và pop thịnh hành",
                    coverGradientStart = 0xFFEC4899,
                    coverGradientEnd = 0xFF8B5CF6
                ),
                Playlist(
                    name = "Audiophile Hi-Res FLAC & WAV",
                    description = "Âm thanh chuẩn phòng thu 24-bit/96kHz cực chi tiết",
                    coverGradientStart = 0xFF06B6D4,
                    coverGradientEnd = 0xFF3B82F6
                ),
                Playlist(
                    name = "Chill Buổi Tối & Lofi Beat",
                    description = "Giai điệu thư giãn giảm căng thẳng sau giờ làm việc",
                    coverGradientStart = 0xFF10B981,
                    coverGradientEnd = 0xFF6366F1
                ),
                Playlist(
                    name = "EDM Siêu Bass Cực Căng",
                    description = "Âm trầm bùng nổ, thử nghiệm hiệu ứng Bass Boost 1000mB",
                    coverGradientStart = 0xFFF59E0B,
                    coverGradientEnd = 0xFFEF4444
                )
            )

            for (p in samplePlaylists) {
                val pId = db.playlistDao().insertPlaylist(p)
                // Add some songs to each playlist
                val insertedSongs = db.songDao().getAllSongs().firstOrNull() ?: emptyList()
                insertedSongs.take(3).forEachIndexed { idx, s ->
                    db.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(pId, s.id, idx))
                }
            }

            // Seed equalizer presets
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

    private fun createSampleSongs(): List<Song> {
        val file1 = AudioSynthesizer.generateSampleAudioFile(context, "viet_ballad_nhip_tim.wav", 185, AudioSynthesizer.TrackStyle.VIET_BALLAD)
        val file2 = AudioSynthesizer.generateSampleAudioFile(context, "chill_lofi_ha_noi_mua.wav", 195, AudioSynthesizer.TrackStyle.CHILL_LOFI)
        val file3 = AudioSynthesizer.generateSampleAudioFile(context, "piano_sonata_dem_sai_gon.wav", 210, AudioSynthesizer.TrackStyle.PIANO_BALLAD)
        val file4 = AudioSynthesizer.generateSampleAudioFile(context, "edm_cyber_neon_bass.wav", 175, AudioSynthesizer.TrackStyle.EDM_BASS)
        val file5 = AudioSynthesizer.generateSampleAudioFile(context, "acoustic_chieu_tay_ho.wav", 168, AudioSynthesizer.TrackStyle.ACOUSTIC_FOLK)
        val file6 = AudioSynthesizer.generateSampleAudioFile(context, "synthwave_audiophile_96k.wav", 225, AudioSynthesizer.TrackStyle.SYNTHWAVE)

        return listOf(
            Song(
                title = "Nhịp Tim Em Và Nắng Mùa Thu",
                artist = "Vũ & Hà Anh Tuấn (HQ Remaster)",
                album = "Thành Phố Sương Mù FLAC",
                durationMs = 185000,
                filePath = file1.absolutePath,
                format = "FLAC",
                bitrateKbps = 1411,
                sampleRateHz = 96000,
                bitDepth = 24,
                isHiRes = true,
                isFavorite = true,
                lyrics = """
[ti:Nhịp Tim Em Và Nắng Mùa Thu]
[ar:Vũ & Hà Anh Tuấn]
[al:Thành Phố Sương Mù FLAC]
[00:00.00]Nhịp Tim Em Và Nắng Mùa Thu - Hi-Res Audio 24-bit/96kHz
[00:04.50]Gió heo may thổi nhẹ qua từng góc phố quen
[00:09.80]Từng chiếc lá vàng rơi nghiêng mình bên hiên vắng
[00:15.20]Anh nhớ tiếng cười trong veo của em ngày nắng hạ
[00:21.00]Như khúc ca dịu êm vỗ về những giấc mơ xưa
[00:28.40]Có những chiều ta cùng dạo bước trên phố dài
[00:34.10]Tay nắm chặt bàn tay ngỡ ngàng trước hoàng hôn
[00:40.50]Thời gian ơi trôi chậm lại để ta còn giữ lấy
[00:47.20]Từng nhịp đập yêu thương gửi trọn vào tiếng dương cầm
[00:54.80]Dẫu mai này đường đời chia đôi ngả sóng gió
[01:01.30]Thì tình anh trao em vẫn vẹn nguyên như thuở ban đầu
[01:08.50]Nghe tiếng thu sang ngập tràn bao nỗi nhớ
[01:15.90]Nguyện yêu em đến muôn đời sau mãi mãi không phai...
[01:25.00]♪ Giai điệu Solo Acoustic Guitar & Bass Hi-Res ♪
[01:38.20]Hãy nhắm mắt lại lắng nghe từng nốt nhạc
[01:44.60]Từng rung động ngân vang khắp không gian tĩnh lặng
[01:52.00]Trái tim anh thuộc về em từ muôn kiếp trước
[02:00.00]Nhịp Điệu tình yêu ngân vang mãi không bao giờ tắt.
                """.trimIndent()
            ),
            Song(
                title = "Hà Nội Mưa & Khúc Lofi Đêm",
                artist = "Chillies & Suni Hạ Linh",
                album = "Góc Phố Mưa Bay Vol.1",
                durationMs = 195000,
                filePath = file2.absolutePath,
                format = "WAV",
                bitrateKbps = 2304,
                sampleRateHz = 48000,
                bitDepth = 24,
                isHiRes = true,
                isFavorite = true,
                lyrics = """
[ti:Hà Nội Mưa & Khúc Lofi Đêm]
[ar:Chillies & Suni Hạ Linh]
[al:Góc Phố Mưa Bay Vol.1]
[00:00.00]Hà Nội Mưa & Khúc Lofi Đêm - Master Lossless
[00:05.10]Tiếng mưa rào rơi tí tách trên mái tôn
[00:11.20]Ngồi bên tách cà phê ấm nghe điệu lofi dìu dịu
[00:18.00]Phố phường thưa người chỉ còn ánh đèn vàng le lói
[00:25.30]Tâm tư trôi theo những hạt mưa đêm dài
[00:33.40]Gửi một chút bình yên đến người phương xa
[00:41.20]Liệu em có đang nghe bản nhạc này cùng anh không?
[00:49.00]Giai điệu lofi vỗ về bao mỏi mệt ngày qua
[00:57.30]Để lòng nhẹ tênh tìm lại những nụ cười...
[01:06.00]Mưa ơi xin đừng làm em buốt giá
[01:14.20]Hãy mang ấm áp đến từng giấc mơ đêm nay
[01:22.50]Nhịp Điệu lofi êm đềm ru êm mọi giác quan...
                """.trimIndent()
            ),
            Song(
                title = "Đêm Sài Gòn & Khúc Sonata Tình Nhân",
                artist = "Đức Trí & Nguyên Hà (Studio Master)",
                album = "Hòa Âm Không Gian 3D",
                durationMs = 210000,
                filePath = file3.absolutePath,
                format = "ALAC",
                bitrateKbps = 1411,
                sampleRateHz = 96000,
                bitDepth = 24,
                isHiRes = true,
                isFavorite = false,
                lyrics = """
[ti:Đêm Sài Gòn & Khúc Sonata Tình Nhân]
[ar:Đức Trí & Nguyên Hà]
[al:Hòa Âm Không Gian 3D]
[00:00.00]Đêm Sài Gòn & Khúc Sonata Tình Nhân (Spatial Audio 3D)
[00:06.00]Dưới ánh đèn neon rực rỡ phố Sài Gòn
[00:13.50]Tiếng piano vang lên tha thiết từng hồi
[00:21.00]Em bước qua như giấc mộng giữa đời thực
[00:29.20]Để lại mùi hương hoa sữa vương trên áo anh
[00:38.00]Bản Sonata đưa ta qua bao miền ký ức
[00:46.50]Những nốt thăng trầm như chính cuộc đời này
[00:55.00]Dù năm tháng đổi thay tình ta vẫn thế
[01:04.00]Trong trẻo như giọt sương mai trên đầu cành...
[01:14.00]♪ Điệp khúc hòa tấu Piano & Cello Dịu Êm ♪
[01:30.00]Hãy để âm thanh này bao bọc lấy tâm hồn bạn.
                """.trimIndent()
            ),
            Song(
                title = "Cyberpunk Neon & Sub-Bass Extreme",
                artist = "Hoaprox & K-ICM (Hi-Res EDM)",
                album = "Future Bass Universe 2026",
                durationMs = 175000,
                filePath = file4.absolutePath,
                format = "FLAC",
                bitrateKbps = 3072,
                sampleRateHz = 192000,
                bitDepth = 32,
                isHiRes = true,
                isFavorite = true,
                lyrics = """
[ti:Cyberpunk Neon & Sub-Bass Extreme]
[ar:Hoaprox & K-ICM]
[al:Future Bass Universe 2026]
[00:00.00]Cyberpunk Neon & Sub-Bass Extreme [Ultra Hi-Res 192kHz/32-bit]
[00:03.50]Kích hoạt hệ thống Equalizer 10 dải tần...
[00:07.20]Tăng cường Bass Boost lên cực đại 1000mB!
[00:12.00]3... 2... 1... DROP THE BASS!
[00:16.80]Cảm nhận từng đợt sóng âm trầm rung chuyển không gian
[00:24.00]Âm thanh vòm 3D Virtualizer lan tỏa đa chiều
[00:32.50]Sức mạnh âm nhạc bùng nổ mọi giới hạn!
[00:41.00]Đắm chìm trong thế giới tương lai của ánh sáng và nhịp đập!
[00:50.00]Cùng nhảy theo điệu nhạc EDM cuồng nhiệt nhất!
[01:05.00]BASS BOOST SYSTEM IS ACTIVATED!
                """.trimIndent()
            ),
            Song(
                title = "Chiều Tây Hồ Gió Lộng",
                artist = "Trần Tiến & Tùng Dương",
                album = "Sắc Màu Quê Hương",
                durationMs = 168000,
                filePath = file5.absolutePath,
                format = "AAC",
                bitrateKbps = 320,
                sampleRateHz = 44100,
                bitDepth = 16,
                isHiRes = false,
                isFavorite = false,
                lyrics = """
[ti:Chiều Tây Hồ Gió Lộng]
[ar:Trần Tiến & Tùng Dương]
[al:Sắc Màu Quê Hương]
[00:00.00]Chiều Tây Hồ Gió Lộng - Acoustic Studio
[00:04.80]Mặt nước Tây Hồ mênh mông sóng biếc
[00:11.00]Tiếng sáo diều vi vu giữa bầu trời cao
[00:18.20]Hoa sen tỏa ngát hương thơm mùa hạ
[00:25.50]Gửi gắm nỗi lòng người lữ khách phương xa
[00:33.40]Yêu biết bao quê hương Việt Nam mình
[00:41.00]Từng góc phố, mái chùa, dòng sông ngàn năm...
                """.trimIndent()
            ),
            Song(
                title = "Chuyến Tàu Đêm & Hoàng Hôn Synthwave",
                artist = "The Midnight & Vũ Cát Tường",
                album = "Retro Wave Master Edition",
                durationMs = 225000,
                filePath = file6.absolutePath,
                format = "FLAC",
                bitrateKbps = 2400,
                sampleRateHz = 96000,
                bitDepth = 24,
                isHiRes = true,
                isFavorite = true,
                lyrics = """
[ti:Chuyến Tàu Đêm & Hoàng Hôn Synthwave]
[ar:The Midnight & Vũ Cát Tường]
[al:Retro Wave Master Edition]
[00:00.00]Chuyến Tàu Đêm & Hoàng Hôn Synthwave (24-bit Hi-Res)
[00:05.00]Chuyến tàu đêm lăn bánh qua miền hoàng hôn tím
[00:12.30]Tia nắng cuối ngày tắt dần nơi chân trời xa
[00:19.80]Giai điệu Synthwave cổ điển pha lẫn hiện đại
[00:27.50]Đưa ta trở về những giấc mơ ngọt ngào năm xưa
[00:36.00]Cùng đắm mình trong bản hòa tấu bất tận
[00:44.20]Nơi âm nhạc kết nối mọi tâm hồn đồng điệu...
                """.trimIndent()
            )
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

                    val isHiRes = format in listOf("FLAC", "WAV", "ALAC") || duration > 0

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

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        db.songDao().updateFavorite(song.id, !song.isFavorite)
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
