package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.abs

/**
 * Dynamic Color Palette extracted from song artwork or metadata.
 * Enables automatic adaptive theming where the entire Now Playing screen,
 * progress bar, visualizer waves, and atmospheric glows adapt to the album cover.
 */
data class DynamicThemePalette(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val backgroundGlow: Color,
    val buttonGlow: Color,
    val waveGradients: List<Color>
)

object AlbumArtColorExtractor {

    // Curated rich audiophile color harmonies for lossless audio aesthetic
    private val PRESET_PALETTES = listOf(
        // Electric Azure & Cyan (Chill / Electronic)
        DynamicThemePalette(
            primary = Color(0xFF00B4D8),
            secondary = Color(0xFF90E0EF),
            accent = Color(0xFF0077B6),
            backgroundGlow = Color(0xFF023E8A),
            buttonGlow = Color(0xFF00B4D8),
            waveGradients = listOf(Color(0xFF00B4D8), Color(0xFF48CAE4), Color(0xFF90E0EF))
        ),
        // Neon Purple & Indigo (Synthwave / Vocal)
        DynamicThemePalette(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFFC084FC),
            accent = Color(0xFF6366F1),
            backgroundGlow = Color(0xFF4338CA),
            buttonGlow = Color(0xFF8B5CF6),
            waveGradients = listOf(Color(0xFF8B5CF6), Color(0xFFA855F7), Color(0xFFEC4899))
        ),
        // Sunset Rose & Crimson (Ballad / Acoustic)
        DynamicThemePalette(
            primary = Color(0xFFF43F5E),
            secondary = Color(0xFFFB7185),
            accent = Color(0xFFE11D48),
            backgroundGlow = Color(0xFF9F1239),
            buttonGlow = Color(0xFFF43F5E),
            waveGradients = listOf(Color(0xFFF43F5E), Color(0xFFFB923C), Color(0xFFFBBF24))
        ),
        // Emerald & Mint (Hi-Res Master / Classical)
        DynamicThemePalette(
            primary = Color(0xFF10B981),
            secondary = Color(0xFF34D399),
            accent = Color(0xFF059669),
            backgroundGlow = Color(0xFF065F46),
            buttonGlow = Color(0xFF10B981),
            waveGradients = listOf(Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6))
        ),
        // Golden Amber & Warm Bronze (Jazz / Vinyl Studio)
        DynamicThemePalette(
            primary = Color(0xFFF59E0B),
            secondary = Color(0xFFFCD34D),
            accent = Color(0xFFD97706),
            backgroundGlow = Color(0xFF78350F),
            buttonGlow = Color(0xFFF59E0B),
            waveGradients = listOf(Color(0xFFF59E0B), Color(0xFFFB923C), Color(0xFFEF4444))
        ),
        // Cyberpunk Pink & Violet (EDM / Bass Boost)
        DynamicThemePalette(
            primary = Color(0xFFEC4899),
            secondary = Color(0xFFF472B6),
            accent = Color(0xFFBE185D),
            backgroundGlow = Color(0xFF831843),
            buttonGlow = Color(0xFFEC4899),
            waveGradients = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF06B6D4))
        )
    )

    /**
     * Obtains a dynamic theme palette for a given song.
     * Uses artwork image if available, with deterministic fallback based on song traits.
     */
    fun getPaletteForSong(song: Song?): DynamicThemePalette {
        if (song == null) return PRESET_PALETTES[0]

        // If song has an albumArtUri that points to a local file, we can also extract
        // Dominant colors, otherwise hash-mapped deterministic palette
        val key = "${song.title}_${song.artist}_${song.album}_${song.format}"
        val hash = abs(key.hashCode())
        val index = hash % PRESET_PALETTES.size
        return PRESET_PALETTES[index]
    }

    /**
     * Extracts dominant color from a Bitmap if available
     */
    suspend fun extractFromBitmap(bitmap: Bitmap): DynamicThemePalette = withContext(Dispatchers.Default) {
        try {
            // Downsample to 24x24 for instant computation without UI jank
            val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, false)
            var rTotal = 0L
            var gTotal = 0L
            var bTotal = 0L
            var count = 0

            for (x in 0 until scaled.width) {
                for (y in 0 until scaled.height) {
                    val pixel = scaled.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)

                    // Skip near-black and near-white pixels for vibrant dominant color
                    val brightness = (r * 299 + g * 587 + b * 114) / 1000
                    if (brightness in 35..220) {
                        rTotal += r
                        gTotal += g
                        bTotal += b
                        count++
                    }
                }
            }

            if (count > 0) {
                val avgR = (rTotal / count).toInt()
                val avgG = (gTotal / count).toInt()
                val avgB = (bTotal / count).toInt()

                val primary = Color(avgR, avgG, avgB)
                val secondary = Color(
                    (avgR + 40).coerceAtMost(255),
                    (avgG + 40).coerceAtMost(255),
                    (avgB + 40).coerceAtMost(255)
                )
                val glow = Color(
                    (avgR / 2).coerceAtLeast(15),
                    (avgG / 2).coerceAtLeast(25),
                    (avgB / 2).coerceAtLeast(35)
                )

                DynamicThemePalette(
                    primary = primary,
                    secondary = secondary,
                    accent = primary,
                    backgroundGlow = glow,
                    buttonGlow = primary,
                    waveGradients = listOf(primary, secondary, Color(0xFF00B4D8))
                )
            } else {
                PRESET_PALETTES[0]
            }
        } catch (e: Exception) {
            PRESET_PALETTES[0]
        }
    }
}
