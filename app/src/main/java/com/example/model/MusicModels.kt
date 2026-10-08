package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val albumArtUri: String? = null,
    val format: String = "FLAC", // FLAC, WAV, MP3, AAC, ALAC, OGG
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44100, // 44100, 48000, 96000, 192000
    val bitDepth: Int = 16, // 16-bit, 24-bit, 32-bit
    val isHiRes: Boolean = false,
    val isFavorite: Boolean = false,
    val lyrics: String? = null, // LRC formatted text
    val lrcOffsetMs: Long = 0,
    val playCount: Int = 0,
    val addedTimestamp: Long = System.currentTimeMillis()
)

val Song.sourceLabel: String
    get() = when {
        album.equals("YouTube Offline", ignoreCase = true) ||
            format.contains("YouTube Offline", ignoreCase = true) -> "YouTube Downloaded"
        album.equals("YouTube Online", ignoreCase = true) ||
            filePath.startsWith("yt://", ignoreCase = true) -> "YouTube Online"
        else -> "Thư viện"
    }

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverGradientStart: Long = 0xFF7C3AED,
    val coverGradientEnd: Long = 0xFF06B6D4,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_song_cross_ref", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val orderIndex: Int = 0
)

@Entity(tableName = "equalizer_presets")
data class EqualizerPreset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCustom: Boolean = false,
    val bandLevelsCsv: String, // e.g. "0,3,5,2,-1,0,3,4,2,1"
    val bassBoost: Int = 0, // 0 - 1000
    val virtualizer: Int = 0, // 0 - 1000
    val reverbPreset: Int = 0 // 0: None, 1: SmallRoom, 2: MediumRoom, 3: LargeHall, 4: Plate
)
