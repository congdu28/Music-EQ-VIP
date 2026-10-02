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

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val levelMilliBels: Short, // e.g. -1200 to +1200 mB (-12dB to +12dB)
    val minLevelMilliBels: Short = -1200,
    val maxLevelMilliBels: Short = 1200
) {
    val levelDb: Float get() = levelMilliBels / 100f

    val centerFreqLabel: String
        get() = if (centerFreqHz >= 1000) {
            "${centerFreqHz / 1000}kHz"
        } else {
            "${centerFreqHz}Hz"
        }
}

data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentSessionId: Int = 0,
    val bands: List<EqualizerBand> = defaultBands(),
    val bassBoostStrength: Int = 300, // 0 - 1000
    val virtualizerStrength: Int = 200, // 0 - 1000
    val activePresetName: String = "Mặc định (Flat)",
    val isCustomPreset: Boolean = false
) {
    companion object {
        fun defaultBands(): List<EqualizerBand> {
            val freqs = listOf(60, 230, 910, 3600, 14000)
            return freqs.mapIndexed { idx, freq ->
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

    private val _state = MutableStateFlow(EqualizerState())
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

    private fun applyBandLevels(bands: List<EqualizerBand>) {
        val eq = activeEqualizer ?: return
        try {
            if (!eq.enabled && _state.value.isEnabled) {
                eq.enabled = true
            }
            bands.forEach { band ->
                if (band.index < eq.numberOfBands) {
                    eq.setBandLevel(band.index, band.levelMilliBels)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band levels: ${e.message}")
        }
    }

    private fun initAudioEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId <= 0) return

        // 1. Hardware Equalizer
        try {
            val eq = Equalizer(0, sessionId).apply {
                enabled = _state.value.isEnabled
            }
            activeEqualizer = eq

            val numBands = eq.numberOfBands.toInt()
            if (numBands > 0) {
                val minRange = eq.bandLevelRange[0]
                val maxRange = eq.bandLevelRange[1]
                val detectedBands = (0 until numBands).map { i ->
                    val bandIdx = i.toShort()
                    val centerFreq = eq.getCenterFreq(bandIdx) / 1000 // mHz to Hz
                    val currentVal = _state.value.bands.getOrNull(i)?.levelMilliBels ?: 0
                    EqualizerBand(
                        index = bandIdx,
                        centerFreqHz = centerFreq,
                        levelMilliBels = currentVal.coerceIn(minRange, maxRange),
                        minLevelMilliBels = minRange,
                        maxLevelMilliBels = maxRange
                    )
                }
                _state.update { it.copy(bands = detectedBands) }
            }
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
                enabled = _state.value.isEnabled
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
                enabled = _state.value.isEnabled
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
