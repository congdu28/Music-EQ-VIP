package com.example.equalizer

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val levelMilliBels: Short, // -1200 to +1200 mB (-12dB to +12dB)
    val minLevelMilliBels: Short = -1200,
    val maxLevelMilliBels: Short = 1200
) {
    val levelDb: Float get() = levelMilliBels / 100f

    val centerFreqLabel: String
        get() = when {
            centerFreqHz >= 1000 && centerFreqHz % 1000 == 0 -> "${centerFreqHz / 1000}k"
            centerFreqHz >= 1000 -> String.format(java.util.Locale.US, "%.1fk", centerFreqHz / 1000f)
            else -> "${centerFreqHz}Hz"
        }
}

data class EqualizerState(
    val isEnabled: Boolean = false, // Mặc định là tắt theo yêu cầu
    val currentSessionId: Int = 0,
    val bands: List<EqualizerBand> = defaultBands(),
    val bassBoostStrength: Int = 0, // 0 - 1000 (mặc định 0 khi tắt)
    val virtualizerStrength: Int = 0, // 0 - 1000 (mặc định 0 khi tắt)
    val activePresetName: String = "Mặc định (Flat)",
    val isCustomPreset: Boolean = false
) {
    companion object {
        // Standard Audiophile 10-Band Graphic Equalizer Frequencies
        val FREQUENCIES_10_BAND = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        fun defaultBands(): List<EqualizerBand> {
            return FREQUENCIES_10_BAND.mapIndexed { idx, freq ->
                EqualizerBand(
                    index = idx.toShort(),
                    centerFreqHz = freq,
                    levelMilliBels = 0,
                    minLevelMilliBels = -1200,
                    maxLevelMilliBels = 1200
                )
            }
        }
    }
}

class EqualizerManager(private val context: Context) {
    private val TAG = "EqualizerManager"

    private val _state = MutableStateFlow(EqualizerState(isEnabled = false))
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var activeEqualizer: Equalizer? = null
    private var activeBassBoost: BassBoost? = null
    private var activeVirtualizer: Virtualizer? = null

    fun attachToSession(sessionId: Int) {
        if (sessionId <= 0) return
        _state.update { it.copy(currentSessionId = sessionId) }
        initAudioEffects(sessionId)
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }
        try {
            activeEqualizer?.enabled = enabled
            activeBassBoost?.enabled = enabled
            activeVirtualizer?.enabled = enabled
            if (enabled) {
                applyBandLevels(_state.value.bands)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling equalizer enabled: ${e.message}")
        }
    }

    fun updateBandLevel(bandIndex: Short, levelMilliBels: Short) {
        val updatedBands = _state.value.bands.map { band ->
            if (band.index == bandIndex) {
                band.copy(levelMilliBels = levelMilliBels)
            } else {
                band
            }
        }
        _state.update {
            it.copy(
                bands = updatedBands,
                activePresetName = "Tùy chỉnh",
                isCustomPreset = true
            )
        }
        applyBandLevels(updatedBands)
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.update { it.copy(bassBoostStrength = clamped) }
        try {
            activeBassBoost?.let {
                if (it.strengthSupported) it.setStrength(clamped.toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice setting BassBoost: ${e.message}")
        }
    }

    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.update { it.copy(virtualizerStrength = clamped) }
        try {
            activeVirtualizer?.let {
                if (it.strengthSupported) it.setStrength(clamped.toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice setting Virtualizer: ${e.message}")
        }
    }

    fun resetToFlat() {
        val flatBands = _state.value.bands.map { it.copy(levelMilliBels = 0) }
        _state.update {
            it.copy(
                bands = flatBands,
                bassBoostStrength = 0,
                virtualizerStrength = 0,
                activePresetName = "Mặc định (Flat)",
                isCustomPreset = false
            )
        }
        applyBandLevels(flatBands)
        setBassBoost(0)
        setVirtualizer(0)
    }

    fun applyPreset(presetName: String, gains: List<Int>, bass: Int = 0, virtual: Int = 0) {
        val updatedBands = _state.value.bands.mapIndexed { index, band ->
            val gain = gains.getOrElse(index) { 0 }.toShort()
            band.copy(levelMilliBels = gain)
        }
        _state.update {
            it.copy(
                bands = updatedBands,
                activePresetName = presetName,
                bassBoostStrength = bass,
                virtualizerStrength = virtual,
                isCustomPreset = false
            )
        }
        applyBandLevels(updatedBands)
        setBassBoost(bass)
        setVirtualizer(virtual)
    }

    /**
     * Maps our 10-band UI faders to the underlying hardware Equalizer.
     * If the hardware has 10 bands, it maps directly 1-to-1.
     * If the hardware has 5 bands, it maps each hardware band to the nearest frequency in our 10 bands.
     */
    private fun applyBandLevels(bands: List<EqualizerBand>) {
        val eq = activeEqualizer ?: return
        try {
            if (_state.value.isEnabled && !eq.enabled) {
                eq.enabled = true
            }

            val hwBandsCount = eq.numberOfBands.toInt()
            if (hwBandsCount <= 0) return

            val minRange = eq.bandLevelRange[0]
            val maxRange = eq.bandLevelRange[1]

            if (hwBandsCount >= bands.size) {
                // Direct 1-to-1 mapping
                bands.forEachIndexed { idx, band ->
                    if (idx < hwBandsCount) {
                        val level = band.levelMilliBels.coerceIn(minRange, maxRange)
                        eq.setBandLevel(idx.toShort(), level)
                    }
                }
            } else {
                // Map each hardware band to the closest frequency among our 10 bands
                for (h in 0 until hwBandsCount) {
                    val hBand = h.toShort()
                    val hwCenterHz = eq.getCenterFreq(hBand) / 1000 // mHz to Hz
                    val nearestUiBand = bands.minByOrNull { abs(it.centerFreqHz - hwCenterHz) }
                    if (nearestUiBand != null) {
                        val level = nearestUiBand.levelMilliBels.coerceIn(minRange, maxRange)
                        eq.setBandLevel(hBand, level)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band levels: ${e.message}")
        }
    }

    private fun initAudioEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId <= 0) return

        val isEqEnabled = _state.value.isEnabled

        // 1. Hardware Equalizer
        try {
            val eq = Equalizer(0, sessionId).apply {
                enabled = isEqEnabled
            }
            activeEqualizer = eq
            applyBandLevels(_state.value.bands)
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer initialization error for session $sessionId: ${e.message}")
        }

        // 2. BassBoost
        try {
            val bb = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(_state.value.bassBoostStrength.toShort())
                }
                enabled = isEqEnabled
            }
            activeBassBoost = bb
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost initialization error: ${e.message}")
        }

        // 3. Virtualizer (Spatial Audio)
        try {
            val virt = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(_state.value.virtualizerStrength.toShort())
                }
                enabled = isEqEnabled
            }
            activeVirtualizer = virt
        } catch (e: Exception) {
            Log.w(TAG, "Virtualizer initialization error: ${e.message}")
        }
    }

    fun release() {
        releaseEffects()
    }

    private fun releaseEffects() {
        try {
            activeEqualizer?.release()
        } catch (e: Exception) { }
        activeEqualizer = null

        try {
            activeBassBoost?.release()
        } catch (e: Exception) { }
        activeBassBoost = null

        try {
            activeVirtualizer?.release()
        } catch (e: Exception) { }
        activeVirtualizer = null
    }
}
