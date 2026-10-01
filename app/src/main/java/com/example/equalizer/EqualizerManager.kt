package com.example.equalizer

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SpeakerProfile(val displayName: String, val subtitle: String) {
    PHONE_SPEAKER("Loa điện thoại", "Chống rè, làm rõ lời thoại, tăng âm lượng loa an toàn"),
    BLUETOOTH_SPEAKER("Loa Bluetooth / Dàn âm thanh", "Âm trầm bùng nổ, âm trường rộng, bass căng"),
    HEADPHONES("Tai nghe / Earphones", "Chi tiết phòng thu, âm vòm không gian 3D"),
    CUSTOM("Tùy chỉnh chuyên sâu", "Thiết lập thủ công 10 dải tần độc lập")
}

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val levelMilliBels: Short, // e.g. -1500 to +1500 mB (-15dB to +15dB)
    val minLevelMilliBels: Short = -1500,
    val maxLevelMilliBels: Short = 1500
) {
    val levelDb: Float get() = levelMilliBels / 100f

    val centerFreqLabel: String
        get() = if (centerFreqHz >= 1000) {
            "${centerFreqHz / 1000}k"
        } else {
            "${centerFreqHz}Hz"
        }

    val frequencyRole: String
        get() = when (centerFreqHz) {
            31 -> "Siêu trầm"
            62 -> "Âm Bass"
            125 -> "Trầm ấm"
            250 -> "Đầy đặn"
            500 -> "Trung trầm"
            1000 -> "Giọng hát"
            2000 -> "Rõ nét"
            4000 -> "Chi tiết"
            8000 -> "Âm Treble"
            16000 -> "Trong trẻo"
            else -> "Dải tần"
        }
}

data class EqualizerState(
    val isEnabled: Boolean = true,
    val isSystemWide: Boolean = true,
    val isGlobalSessionSupported: Boolean = true,
    val currentSessionId: Int = 0,
    val bands: List<EqualizerBand> = defaultBands(),
    val bassBoostStrength: Int = 350, // 0 - 1000
    val virtualizerStrength: Int = 250, // 0 - 1000
    val loudnessEnhancerGainMb: Int = 400, // 0 - 1000 mB (0 to +10 dB target gain)
    val isAntiClippingEnabled: Boolean = true,
    val reverbPreset: Short = PresetReverb.PRESET_NONE,
    val activePresetName: String = "Tối ưu Loa ngoài",
    val speakerProfile: SpeakerProfile = SpeakerProfile.PHONE_SPEAKER,
    val currentOutputDeviceName: String = "Loa ngoài thiết bị",
    val isHiResDspEnabled: Boolean = true,
    val isReplayGainEnabled: Boolean = true,
    val audioFormatInfo: String = "24-bit / 96kHz Lossless"
) {
    companion object {
        fun defaultBands(): List<EqualizerBand> {
            val freqs = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
            // Default tuned for phone speaker: safe sub-bass, clean vocal clarity, crisp highs
            val defaultGains = listOf(-200, 100, 200, 100, 200, 350, 400, 300, 350, 250)
            return freqs.mapIndexed { idx, freq ->
                EqualizerBand(
                    index = idx.toShort(),
                    centerFreqHz = freq,
                    levelMilliBels = defaultGains.getOrElse(idx) { 0 }.toShort(),
                    minLevelMilliBels = -1500,
                    maxLevelMilliBels = 1500
                )
            }
        }
    }
}

class EqualizerManager(private val context: Context) {
    private val TAG = "EqualizerManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    // Active Audio Effects references
    private var activeEqualizer: Equalizer? = null
    private var activeBassBoost: BassBoost? = null
    private var activeVirtualizer: Virtualizer? = null
    private var activeLoudnessEnhancer: LoudnessEnhancer? = null
    private var activeReverb: PresetReverb? = null

    init {
        detectCurrentAudioDevice()
        // Standard non-intrusive priority = 0 to prevent conflicts with Dolby/OEM sound managers
        initAudioEffects(sessionId = 0, isSystemWide = true)
    }

    fun detectCurrentAudioDevice() {
        try {
            var deviceName = "Loa ngoài thiết bị"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (dev in devices) {
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dev.productName else "Loa Bluetooth"
                            deviceName = "Loa Bluetooth ($name)"
                            break
                        }
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                            deviceName = "Tai nghe cắm dây"
                            break
                        }
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                            deviceName = "Loa ngoài điện thoại"
                        }
                    }
                }
            }
            _state.update { it.copy(currentOutputDeviceName = deviceName) }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting audio device: ${e.message}")
        }
    }

    fun attachToSession(sessionId: Int) {
        val isSystem = _state.value.isSystemWide
        _state.update { it.copy(currentSessionId = sessionId) }
        val targetSession = if (isSystem && _state.value.isGlobalSessionSupported) 0 else sessionId
        initAudioEffects(sessionId = targetSession, isSystemWide = isSystem)
    }

    fun toggleSystemWide(enabled: Boolean) {
        _state.update { it.copy(isSystemWide = enabled) }
        val targetSession = if (enabled) 0 else _state.value.currentSessionId
        initAudioEffects(sessionId = targetSession, isSystemWide = enabled)
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }
        try {
            activeEqualizer?.enabled = enabled
            activeBassBoost?.enabled = enabled
            activeVirtualizer?.enabled = enabled
            activeLoudnessEnhancer?.enabled = enabled
            activeReverb?.enabled = enabled

            broadcastSystemAudioSession(
                open = enabled,
                sessionId = if (_state.value.isSystemWide) 0 else _state.value.currentSessionId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling effects enabled state: ${e.message}")
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
                activePresetName = "Tùy chỉnh (Custom)",
                speakerProfile = SpeakerProfile.CUSTOM
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
            Log.w(TAG, "Safe notice - BassBoost update: ${e.message}")
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
            Log.w(TAG, "Safe notice - Virtualizer update: ${e.message}")
        }
    }

    fun setLoudnessEnhancerGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1200) // 0 to +12 dB
        _state.update { it.copy(loudnessEnhancerGainMb = clamped) }
        try {
            activeLoudnessEnhancer?.setTargetGain(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Safe notice - LoudnessEnhancer update: ${e.message}")
        }
    }

    fun toggleAntiClipping(enabled: Boolean) {
        _state.update { it.copy(isAntiClippingEnabled = enabled) }
        applyBandLevels(_state.value.bands)
    }

    fun setSpeakerProfile(profile: SpeakerProfile) {
        detectCurrentAudioDevice()
        when (profile) {
            SpeakerProfile.PHONE_SPEAKER -> {
                // Cut buzzing sub-bass (<60Hz) to prevent phone speaker crackle, boost vocal clarity & presence
                val gains = listOf(-300, 50, 150, 100, 200, 400, 450, 350, 400, 300)
                applyPreset("Tối ưu Loa Ngoài Điện Thoại", gains, bass = 200, virtual = 150)
                setLoudnessEnhancerGain(450)
            }
            SpeakerProfile.BLUETOOTH_SPEAKER -> {
                // Rich punchy bass, wide treble sparkle, high loudness
                val gains = listOf(500, 600, 450, 200, 0, 100, 200, 400, 550, 650)
                applyPreset("Loa Bluetooth Siêu Trầm", gains, bass = 700, virtual = 350)
                setLoudnessEnhancerGain(350)
            }
            SpeakerProfile.HEADPHONES -> {
                // Audiophile Harman-style curve with 3D Spatial Audio
                val gains = listOf(400, 300, 150, 0, 100, 250, 350, 400, 500, 600)
                applyPreset("Tai Nghe Hi-Res Studio", gains, bass = 450, virtual = 500)
                setLoudnessEnhancerGain(200)
            }
            SpeakerProfile.CUSTOM -> {
                // Keep current settings
            }
        }
        _state.update { it.copy(speakerProfile = profile) }
    }

    fun setReverbPreset(preset: Short) {
        _state.update { it.copy(reverbPreset = preset) }
        try {
            activeReverb?.preset = preset
        } catch (e: Exception) {
            Log.w(TAG, "Safe notice - Reverb update: ${e.message}")
        }
    }

    fun applyPreset(presetName: String, gains: List<Int>, bass: Int = 300, virtual: Int = 200) {
        val updatedBands = _state.value.bands.mapIndexed { index, band ->
            val gain = gains.getOrElse(index) { 0 }.toShort()
            band.copy(levelMilliBels = gain)
        }
        _state.update {
            it.copy(
                bands = updatedBands,
                activePresetName = presetName,
                bassBoostStrength = bass,
                virtualizerStrength = virtual
            )
        }
        applyBandLevels(updatedBands)
        setBassBoost(bass)
        setVirtualizer(virtual)
    }

    fun toggleHiResDsp(enabled: Boolean) {
        _state.update { it.copy(isHiResDspEnabled = enabled) }
    }

    fun toggleReplayGain(enabled: Boolean) {
        _state.update { it.copy(isReplayGainEnabled = enabled) }
    }

    private fun broadcastSystemAudioSession(open: Boolean, sessionId: Int) {
        try {
            val action = if (open) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
            } else {
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
            }
            val intent = Intent(action).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                if (open) {
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Safe notice - Broadcast session: ${e.message}")
        }
    }

    /**
     * Resilient initialization:
     * 1. Uses priority = 0 (standard respectful priority to prevent conflicts with Dolby/Dirac/OneUI).
     * 2. Isolates each audio effect in its own try-catch so one unsupported effect on a specific OEM
     *    device does not kill the Equalizer or LoudnessEnhancer.
     * 3. Falls back gracefully to the app session if session 0 is restricted by the OEM.
     */
    private fun initAudioEffects(sessionId: Int, isSystemWide: Boolean) {
        releaseEffects()

        var effectiveSessionId = sessionId
        var isGlobalSupported = true

        // 1. Equalizer (Priority = 0)
        try {
            val eq = Equalizer(0, effectiveSessionId).apply {
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
            Log.w(TAG, "Session $effectiveSessionId Equalizer not supported or restricted by OEM: ${e.message}")
            if (effectiveSessionId == 0) {
                isGlobalSupported = false
                // Auto fallback to player session to guarantee functionality without conflict
                effectiveSessionId = _state.value.currentSessionId
                try {
                    val eq = Equalizer(0, effectiveSessionId).apply {
                        enabled = _state.value.isEnabled
                    }
                    activeEqualizer = eq
                    applyBandLevels(_state.value.bands)
                } catch (fallbackEx: Exception) {
                    Log.w(TAG, "Player session Equalizer error: ${fallbackEx.message}")
                }
            }
        }

        _state.update { it.copy(isGlobalSessionSupported = isGlobalSupported) }

        // 2. LoudnessEnhancer (Hardware Speaker Booster)
        try {
            val loud = LoudnessEnhancer(effectiveSessionId).apply {
                setTargetGain(_state.value.loudnessEnhancerGainMb)
                enabled = _state.value.isEnabled
            }
            activeLoudnessEnhancer = loud
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer not supported on session $effectiveSessionId: ${e.message}")
        }

        // 3. BassBoost
        try {
            val bass = BassBoost(0, effectiveSessionId).apply {
                if (strengthSupported) setStrength(_state.value.bassBoostStrength.toShort())
                enabled = _state.value.isEnabled
            }
            activeBassBoost = bass
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost not supported on session $effectiveSessionId: ${e.message}")
        }

        // 4. Virtualizer (3D Audio)
        try {
            val virt = Virtualizer(0, effectiveSessionId).apply {
                if (strengthSupported) setStrength(_state.value.virtualizerStrength.toShort())
                enabled = _state.value.isEnabled
            }
            activeVirtualizer = virt
        } catch (e: Exception) {
            Log.w(TAG, "Virtualizer not supported on session $effectiveSessionId: ${e.message}")
        }

        // 5. PresetReverb (Environment)
        try {
            val rev = PresetReverb(0, effectiveSessionId).apply {
                preset = _state.value.reverbPreset
                enabled = _state.value.isEnabled
            }
            activeReverb = rev
        } catch (e: Exception) {
            Log.w(TAG, "PresetReverb not supported on session $effectiveSessionId: ${e.message}")
        }

        broadcastSystemAudioSession(open = _state.value.isEnabled, sessionId = effectiveSessionId)
        Log.d(TAG, "Audio effects initialized on session $effectiveSessionId (systemWide=$isSystemWide, globalSupported=$isGlobalSupported)")
    }

    private fun applyBandLevels(bands: List<EqualizerBand>) {
        try {
            activeEqualizer?.let { eq ->
                val count = minOf(bands.size, eq.numberOfBands.toInt())
                val maxBoost = bands.maxOfOrNull { it.levelMilliBels } ?: 0
                val attenuation = if (_state.value.isAntiClippingEnabled && maxBoost > 500) {
                    ((maxBoost - 500) * 0.35f).toInt().toShort()
                } else {
                    0.toShort()
                }

                for (i in 0 until count) {
                    val band = bands[i]
                    val compensatedLevel = (band.levelMilliBels - attenuation).coerceIn(
                        band.minLevelMilliBels.toInt(),
                        band.maxLevelMilliBels.toInt()
                    ).toShort()
                    eq.setBandLevel(band.index, compensatedLevel)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying band level to hardware EQ: ${e.message}")
        }
    }

    fun releaseEffects() {
        try {
            broadcastSystemAudioSession(open = false, sessionId = 0)

            activeEqualizer?.run { enabled = false; release() }
            activeBassBoost?.run { enabled = false; release() }
            activeVirtualizer?.run { enabled = false; release() }
            activeLoudnessEnhancer?.run { enabled = false; release() }
            activeReverb?.run { enabled = false; release() }

            activeEqualizer = null
            activeBassBoost = null
            activeVirtualizer = null
            activeLoudnessEnhancer = null
            activeReverb = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio effects: ${e.message}")
        }
    }

    companion object {
        val PRESET_FLAT = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val PRESET_BASS_BOOST = listOf(800, 600, 450, 200, 0, 0, 100, 200, 300, 350)
        val PRESET_ROCK = listOf(500, 400, 250, -100, -200, 0, 250, 450, 600, 650)
        val PRESET_POP = listOf(-100, 150, 350, 450, 300, 0, -100, 150, 300, 400)
        val PRESET_JAZZ = listOf(300, 200, 100, 200, -150, -150, 0, 150, 300, 400)
        val PRESET_CLASSICAL = listOf(450, 350, 250, 150, -100, -100, 0, 200, 350, 450)
        val PRESET_EDM = listOf(700, 600, 200, 0, -200, 200, 400, 600, 700, 800)
        val PRESET_VOCAL = listOf(-300, -200, 0, 250, 500, 600, 500, 300, 100, 0)
        val PRESET_HIP_HOP = listOf(650, 550, 300, 100, -100, 0, 200, 350, 450, 500)
        val PRESET_ACOUSTIC = listOf(350, 300, 150, 100, 200, 250, 350, 400, 450, 350)
    }
}
