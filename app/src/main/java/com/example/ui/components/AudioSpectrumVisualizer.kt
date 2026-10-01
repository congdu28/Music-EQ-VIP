package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.equalizer.EqualizerBand
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import kotlin.math.sin

@Composable
fun AudioSpectrumVisualizer(
    isPlaying: Boolean,
    bands: List<EqualizerBand>,
    modifier: Modifier = Modifier.fillMaxWidth().height(90.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val barGradients = Brush.verticalGradient(
        colors = listOf(
            NeonPink,
            NeonPurple,
            NeonCyan
        )
    )

    Canvas(modifier = modifier) {
        val totalBars = 32
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (totalBars - 1)
        val barWidth = (size.width - totalSpacing) / totalBars
        val canvasHeight = size.height

        for (i in 0 until totalBars) {
            val bandIndex = (i.toFloat() / totalBars * bands.size).toInt().coerceIn(0, bands.size - 1)
            val bandLevelDb = bands.getOrNull(bandIndex)?.levelDb ?: 0f
            val eqMultiplier = (1.0f + (bandLevelDb / 15f) * 0.5f).coerceIn(0.2f, 2.0f)

            val wave1 = sin(phase + i * 0.45f)
            val wave2 = sin(phase * 1.8f + i * 0.3f)
            val waveCombined = (wave1 + wave2 + 2f) / 4f // 0.0 to 1.0

            val dynamicHeightFraction = if (isPlaying) {
                (0.15f + waveCombined * 0.85f) * eqMultiplier
            } else {
                0.08f + 0.05f * sin(i.toFloat())
            }.coerceIn(0.05f, 1.0f)

            val currentBarHeight = canvasHeight * dynamicHeightFraction
            val x = i * (barWidth + barSpacing)
            val y = canvasHeight - currentBarHeight

            drawRoundRect(
                brush = barGradients,
                topLeft = Offset(x, y),
                size = Size(barWidth, currentBarHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
