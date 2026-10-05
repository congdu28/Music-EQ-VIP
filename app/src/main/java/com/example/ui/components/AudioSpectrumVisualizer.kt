package com.example.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisualizerStyle(val displayName: String) {
    WAVE("Sóng nhạc uốn lượn"),
    SPECTRUM("Cột tần số Neon")
}

/** Draws samples captured from the currently playing MediaPlayer audio session. */
@Composable
fun AudioSpectrumVisualizer(
    isPlaying: Boolean,
    audioSessionId: Int,
    hasAudioCapturePermission: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth().height(90.dp),
    customColors: List<Color>? = null,
    style: VisualizerStyle = VisualizerStyle.WAVE,
    onToggleStyle: (() -> Unit)? = null
) {
    var waveform by remember(audioSessionId) { mutableStateOf(FloatArray(0)) }
    var fft by remember(audioSessionId) { mutableStateOf(FloatArray(0)) }
    val hasCurrentSignal = if (style == VisualizerStyle.WAVE) {
        waveform.maxOfOrNull { abs(it) }?.let { it > 0.015f } == true
    } else {
        fft.maxOrNull()?.let { it > 0.015f } == true
    }
    val useFallback = isPlaying && !hasCurrentSignal
    val fallbackTransition = rememberInfiniteTransition(label = "fallback_visualizer")
    val fallbackPhase by fallbackTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = if (useFallback) {
            infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart)
        } else {
            tween(0)
        },
        label = "fallback_phase"
    )

    DisposableEffect(audioSessionId, isPlaying, hasAudioCapturePermission) {
        var visualizer: Visualizer? = null
        if (audioSessionId > 0 && hasAudioCapturePermission) {
            try {
                val instance = Visualizer(audioSessionId)
                val captureSize = Visualizer.getCaptureSizeRange()[1]
                    .coerceAtMost(1024)
                    .let { Integer.highestOneBit(it) }
                instance.captureSize = captureSize
                instance.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveformBytes: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveformBytes?.let { samples ->
                            waveform = FloatArray(samples.size) { index ->
                                ((samples[index].toInt() and 0xFF) - 128) / 128f
                            }
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fftBytes: ByteArray?,
                        samplingRate: Int
                    ) {
                        fftBytes?.let { samples ->
                            val bins = samples.size / 2
                            fft = FloatArray(bins) { index ->
                                val real = samples[index * 2].toInt()
                                val imaginary = samples[index * 2 + 1].toInt()
                                (sqrt((real * real + imaginary * imaginary).toFloat()) / 180f)
                                    .coerceIn(0f, 1f)
                            }
                        }
                    }
                }, (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(4_000), true, true)
                instance.enabled = isPlaying
                visualizer = instance
            } catch (_: Exception) {
                // Some audio effects implementations do not expose Visualizer; keep the UI quiet.
            }
        }
        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (_: Exception) {
                // The player may release its audio session at the same time.
            }
        }
    }

    val waveColors = customColors ?: listOf(Color(0xFF3399FF), Color(0xFF818CF8), Color(0xFFEC4899))
    val gradientBrush = Brush.horizontalGradient(colors = waveColors)
    val verticalBrush = Brush.verticalGradient(
        colors = listOf(waveColors.first(), waveColors.getOrElse(1) { waveColors.first() }, waveColors.last().copy(alpha = 0.3f))
    )

    Column(modifier = modifier) {
        if (onToggleStyle != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggleStyle).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (style == VisualizerStyle.WAVE) Icons.Default.Waves else Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = waveColors.first(),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(style.displayName, color = TextSecondary, fontSize = 10.sp)
                }
                Text(
                    text = when {
                        !isPlaying -> "Tạm dừng"
                        !hasAudioCapturePermission -> "Cần quyền để đồng bộ sóng"
                        (style == VisualizerStyle.WAVE && waveform.isNotEmpty()) ||
                            (style == VisualizerStyle.SPECTRUM && fft.isNotEmpty()) -> "Tín hiệu âm thanh trực tiếp"
                        else -> "Sóng nhạc mô phỏng"
                    },
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }
        }

        Canvas(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .then(if (onToggleStyle != null) Modifier.clickable(onClick = onToggleStyle) else Modifier)
        ) {
            val centerY = size.height / 2f
            if (style == VisualizerStyle.WAVE) {
                val path = Path()
                val samples = waveform
                if (samples.size > 1 && hasCurrentSignal) {
                    val stride = (samples.size / size.width.toInt().coerceAtLeast(1)).coerceAtLeast(1)
                    var point = 0
                    while (point < size.width.toInt()) {
                        val sampleIndex = (point * samples.size / size.width.toInt().coerceAtLeast(1)).coerceIn(0, samples.lastIndex)
                        val x = point.toFloat()
                        val y = centerY - samples[sampleIndex] * (size.height * 0.46f)
                        if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        point += stride
                    }
                    drawPath(path, brush = gradientBrush, style = Stroke(width = 2.dp.toPx()))
                } else if (isPlaying) {
                    val fallbackPath = Path()
                    val points = size.width.toInt().coerceAtLeast(2)
                    for (x in 0 until points) {
                        val progress = x.toFloat() / (points - 1)
                        val wave = sin(progress * Math.PI * 4 + fallbackPhase) * 0.55 +
                            sin(progress * Math.PI * 9 - fallbackPhase * 0.7f) * 0.25
                        val y = centerY + wave.toFloat() * size.height * 0.4f
                        if (x == 0) fallbackPath.moveTo(x.toFloat(), y) else fallbackPath.lineTo(x.toFloat(), y)
                    }
                    drawPath(fallbackPath, brush = gradientBrush, style = Stroke(width = 2.dp.toPx()))
                } else {
                    drawLine(waveColors.first().copy(alpha = 0.35f), Offset(0f, centerY), Offset(size.width, centerY), 1.dp.toPx())
                }
            } else {
                val spectrum = fft
                val totalBars = 28
                val spacing = 2.dp.toPx()
                val barWidth = (size.width - spacing * (totalBars - 1)) / totalBars
                for (i in 0 until totalBars) {
                    val heightFraction = if (spectrum.isNotEmpty() && hasCurrentSignal) {
                        // Log-like bin spacing gives visible bass and treble detail in a small widget.
                        val start = (spectrum.size * (i.toFloat() / totalBars) * (i + 2) / totalBars).toInt().coerceIn(0, spectrum.lastIndex)
                        val end = (start + (spectrum.size / totalBars).coerceAtLeast(1)).coerceAtMost(spectrum.size)
                        var energy = 0f
                        for (bin in start until end) energy = maxOf(energy, spectrum[bin])
                        (energy * 2.4f).coerceIn(0.025f, 1f)
                    } else if (isPlaying) {
                        (0.12f + (sin(fallbackPhase + i * 0.53f).toFloat() + 1f) * 0.28f)
                            .coerceIn(0.06f, 0.72f)
                    } else 0.025f
                    val barHeight = size.height * heightFraction
                    val x = i * (barWidth + spacing)
                    drawRoundRect(
                        brush = verticalBrush,
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}
