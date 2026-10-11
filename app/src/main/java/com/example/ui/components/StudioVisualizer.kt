package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioEngineState
import com.example.model.PlaybackState
import com.example.ui.theme.AmberAnalog
import com.example.ui.theme.LedActive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated 32-Band Studio Frequency Spectrum Visualizer with Peak Hold LEDs.
 * Dynamically modulates bar heights based on playback state, audio time, and active EQ bands.
 */
@Composable
fun StudioSpectrumVisualizer(
    playbackState: PlaybackState,
    engineState: AudioEngineState,
    modifier: Modifier = Modifier
) {
    val barCount = 32

    val infiniteTransition = rememberInfiniteTransition(label = "SpectrumPulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseAnimation"
    )

    // Base pre-computed pseudo-random noise table for organic visualizer dancing
    val randomNoise = remember {
        List(barCount) { i -> (sin(i * 1.7) * 0.5 + 0.5).toFloat() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StudioCardSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("studio_spectrum_visualizer")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar with Frequency Scale Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME SPECTRUM RTA",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (playbackState.isPlaying) "60 FPS • PEAK HOLD" else "IDLE",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (playbackState.isPlaying) NeonCyan else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 32-Band Canvas Rendering
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalBars = barCount
                val gap = 3.dp.toPx()
                val barWidth = (canvasWidth - (totalBars - 1) * gap) / totalBars

                val isPlaying = playbackState.isPlaying
                val eqGains = engineState.bands.map { it.gainDb }
                val bassBoostNorm = engineState.bassBoostStrength / 1000f

                for (i in 0 until totalBars) {
                    val progressRatio = i.toFloat() / (totalBars - 1)

                    // Map 32 spectrum bars to the 10 EQ band influences
                    val eqIndex = (progressRatio * 9).toInt().coerceIn(0, 9)
                    val eqBoost = (eqGains.getOrElse(eqIndex) { 0f } + 15f) / 30f // 0.0 to 1.0

                    // Dynamic wave animation when playing
                    val dynamicEnergy = if (isPlaying) {
                        val wave1 = (sin(phase * 2f + i * 0.45f) * 0.35f + 0.5f)
                        val wave2 = (cos(phase * 3f + i * 0.25f) * 0.25f + 0.35f)
                        val noise = randomNoise[i] * 0.25f

                        // Extra bass energy in low bands (bars 0 to 8)
                        val bassWeight = if (i < 8) bassBoostNorm * 0.4f else 0f

                        ((wave1 + wave2 + noise + bassWeight) * (0.4f + eqBoost * 0.6f)).coerceIn(0.08f, 0.96f)
                    } else {
                        0.04f
                    }

                    val barHeight = canvasHeight * dynamicEnergy
                    val left = i * (barWidth + gap)
                    val top = canvasHeight - barHeight

                    // 1. Draw Spectrum Bar with Vertical Neon Gradient
                    val barBrush = Brush.verticalGradient(
                        colors = listOf(
                            PeakCoral,
                            AmberAnalog,
                            NeonCyan
                        ),
                        startY = top,
                        endY = canvasHeight
                    )

                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // 2. Draw Peak Hold LED Floating Indicator
                    val peakTop = (top - 5.dp.toPx()).coerceAtLeast(0f)
                    val peakColor = if (dynamicEnergy > 0.82f) PeakCoral else AmberAnalog

                    drawRoundRect(
                        color = peakColor,
                        topLeft = Offset(left, peakTop),
                        size = Size(barWidth, 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Frequency Grid Ticks
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "30Hz", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "250Hz", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "1kHz", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "4kHz", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "16kHz", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
            }
        }
    }
}
