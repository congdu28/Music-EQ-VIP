package com.example.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object AudioSynthesizer {
    private const val SAMPLE_RATE = 44100
    private const val NUM_CHANNELS = 2 // Stereo
    private const val BITS_PER_SAMPLE = 16

    enum class TrackStyle {
        CHILL_LOFI,
        SYNTHWAVE,
        PIANO_BALLAD,
        ACOUSTIC_FOLK,
        EDM_BASS,
        VIET_BALLAD
    }

    fun generateSampleAudioFile(context: Context, fileName: String, durationSeconds: Int, style: TrackStyle): File {
        val file = File(context.cacheDir, fileName)
        if (file.exists() && file.length() > 1000) {
            return file
        }

        val totalSamples = SAMPLE_RATE * durationSeconds
        val dataSize = totalSamples * NUM_CHANNELS * (BITS_PER_SAMPLE / 8)
        val buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // Musical scales (Hz)
        val chordProgressions = when (style) {
            TrackStyle.VIET_BALLAD -> listOf(
                listOf(220.0, 261.63, 329.63), // Am
                listOf(174.61, 220.0, 261.63), // F
                listOf(261.63, 329.63, 392.0), // C
                listOf(196.0, 246.94, 293.66)  // G
            )
            TrackStyle.CHILL_LOFI -> listOf(
                listOf(293.66, 349.23, 440.0, 523.25), // Dm7
                listOf(196.0, 246.94, 293.66, 349.23), // G7
                listOf(261.63, 329.63, 392.0, 493.88), // Cmaj7
                listOf(220.0, 261.63, 329.63, 392.0)   // Am7
            )
            TrackStyle.SYNTHWAVE -> listOf(
                listOf(110.0, 164.81, 220.0), // A1
                listOf(130.81, 196.0, 261.63), // C2
                listOf(146.83, 220.0, 293.66), // D2
                listOf(98.0, 146.83, 196.0)    // G1
            )
            TrackStyle.PIANO_BALLAD -> listOf(
                listOf(261.63, 329.63, 392.0, 523.25), // C
                listOf(220.0, 261.63, 329.63, 440.0),  // Am
                listOf(174.61, 220.0, 261.63, 349.23), // F
                listOf(196.0, 246.94, 293.66, 392.0)   // G
            )
            TrackStyle.ACOUSTIC_FOLK -> listOf(
                listOf(196.0, 246.94, 293.66, 392.0), // G
                listOf(164.81, 196.0, 246.94, 329.63), // Em
                listOf(261.63, 329.63, 392.0), // C
                listOf(146.83, 220.0, 293.66) // D
            )
            TrackStyle.EDM_BASS -> listOf(
                listOf(55.0, 110.0, 220.0), // A0/A1
                listOf(73.42, 146.83, 293.66), // D1
                listOf(65.41, 130.81, 261.63), // C1
                listOf(82.41, 164.81, 329.63) // E1
            )
        }

        val tempoBpm = when (style) {
            TrackStyle.CHILL_LOFI -> 75
            TrackStyle.VIET_BALLAD -> 80
            TrackStyle.PIANO_BALLAD -> 72
            TrackStyle.ACOUSTIC_FOLK -> 95
            TrackStyle.SYNTHWAVE -> 118
            TrackStyle.EDM_BASS -> 128
        }
        val beatDurationSec = 60.0 / tempoBpm
        val chordDurationSec = beatDurationSec * 4.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val chordIndex = ((t / chordDurationSec).toInt()) % chordProgressions.size
            val currentChord = chordProgressions[chordIndex]

            var sampleLeft = 0.0
            var sampleRight = 0.0

            // Harmonic chord synthesis
            for ((freqIdx, freq) in currentChord.withIndex()) {
                val envelope = when (style) {
                    TrackStyle.PIANO_BALLAD, TrackStyle.CHILL_LOFI -> {
                        val beatPhase = (t % beatDurationSec) / beatDurationSec
                        Math.exp(-3.0 * beatPhase)
                    }
                    TrackStyle.SYNTHWAVE, TrackStyle.EDM_BASS -> {
                        val arpeggioIdx = ((t / (beatDurationSec / 4)).toInt()) % currentChord.size
                        if (freqIdx == arpeggioIdx) 1.0 else 0.2
                    }
                    else -> 0.6 + 0.4 * sin(2.0 * Math.PI * 0.5 * t)
                }

                val harmonic1 = sin(2.0 * Math.PI * freq * t)
                val harmonic2 = 0.3 * sin(2.0 * Math.PI * freq * 2.0 * t)
                val harmonic3 = 0.1 * sin(2.0 * Math.PI * freq * 3.0 * t)
                val voice = (harmonic1 + harmonic2 + harmonic3) * envelope * 0.25

                // Stereo spread
                val pan = if (freqIdx % 2 == 0) 0.7 else 0.3
                sampleLeft += voice * pan
                sampleRight += voice * (1.0 - pan)
            }

            // Bass pulse
            val bassFreq = currentChord.first() * 0.5
            val bassEnv = 0.3 * (1.0 + 0.5 * sin(2.0 * Math.PI * (1.0 / beatDurationSec) * t))
            val bassWave = sin(2.0 * Math.PI * bassFreq * t) * bassEnv
            sampleLeft += bassWave
            sampleRight += bassWave

            // Percussion / Hi-Hat rhythm tick
            val beatFrac = (t % beatDurationSec) / beatDurationSec
            if (beatFrac < 0.05) {
                val noise = (Math.random() - 0.5) * 0.15 * (1.0 - beatFrac / 0.05)
                sampleLeft += noise
                sampleRight += noise
            }

            // Master limiter & convert to 16-bit PCM
            val clampedL = (sampleLeft.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
            val clampedR = (sampleRight.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()

            buffer.putShort(clampedL)
            buffer.putShort(clampedR)
        }

        FileOutputStream(file).use { fos ->
            writeWavHeader(fos, dataSize, SAMPLE_RATE, NUM_CHANNELS, BITS_PER_SAMPLE)
            fos.write(buffer.array())
        }

        return file
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Audio format 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * (bitsPerSample / 8)).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
