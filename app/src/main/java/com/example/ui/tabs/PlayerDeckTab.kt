package com.example.ui.tabs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioEngineState
import com.example.model.PlaybackState
import com.example.ui.components.StudioSpectrumVisualizer
import com.example.ui.theme.AmberAnalog
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardSurface
import com.example.ui.theme.StudioDeckSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlayerDeckTab(
    playbackState: PlaybackState,
    engineState: AudioEngineState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onPitchSemitonesChange: (Int) -> Unit,
    onResetPitch: () -> Unit,
    onTempoChange: (Float) -> Unit,
    onResetTempo: () -> Unit,
    onPreAmpChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack
    val scrollState = rememberScrollState()

    val rotationTransition = rememberInfiniteTransition(label = "VinylRotation")
    val rotationAngle by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Vinyl Turntable Platter Deck
        Card(
            modifier = Modifier.fillMaxWidth().testTag("turntable_card"),
            colors = CardDefaults.cardColors(containerColor = StudioDeckSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vinyl Disc Artwork
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg)
                        .border(3.dp, StudioBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Grooves
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .border(1.dp, StudioCardSurface, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .border(1.dp, StudioBorder, CircleShape)
                    )

                    // Spinning Center Label
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .rotate(if (playbackState.isPlaying) rotationAngle else 0f)
                            .clip(CircleShape)
                            .background(StudioCardSurface)
                            .border(2.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (playbackState.isPlaying) NeonCyan else AmberAnalog,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Track Info Header
                Text(
                    text = track?.title ?: "Select Track to Play",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${track?.artist ?: "AudioPulse"} • ${track?.album ?: "Studio Deck"}",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Quality Badge & DSP status
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = track?.audioQualityBadge ?: "HI-RES • 320k",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AmberAnalog,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioCardSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "10-BAND DSP",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioCardSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Track Progress Scrubber Slider
                val duration = playbackState.durationMs.coerceAtLeast(1L)
                val currentPos = playbackState.currentPositionMs.coerceIn(0L, duration)

                Slider(
                    value = currentPos.toFloat(),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = StudioCardSurface
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playback_scrubber")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeMs(currentPos),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = formatTimeMs(duration),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transport Controls: Shuffle, Prev, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.testTag("deck_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.isShuffleActive) NeonCyan else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.testTag("deck_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                            .testTag("deck_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = ObsidianBg,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.testTag("deck_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.testTag("deck_repeat_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (playbackState.repeatMode > 0) AmberAnalog else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ReplayGain Pre-Amp Gain Control Slider (Media Controller Level)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioCardSurface)
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("media_controller_replay_gain_card")
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "ReplayGain Pre-Amp",
                                    tint = AmberAnalog,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REPLAYGAIN PRE-AMP",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when {
                                        engineState.preAmpGainDb > 0.05f -> "+%.1f dB".format(engineState.preAmpGainDb)
                                        engineState.preAmpGainDb < -0.05f -> "%.1f dB".format(engineState.preAmpGainDb)
                                        else -> "0.0 dB (UNITY)"
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (engineState.preAmpGainDb != 0.0f) AmberAnalog else NeonCyan
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onPreAmpChange(0.0f) },
                                    modifier = Modifier.size(24.dp).testTag("deck_pre_amp_reset_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset Unity Gain",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Normalizes playback volume consistency across diverse masterings",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Slider(
                            value = engineState.preAmpGainDb,
                            onValueChange = onPreAmpChange,
                            valueRange = -12f..12f,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberAnalog,
                                activeTrackColor = AmberAnalog,
                                inactiveTrackColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("media_controller_pre_amp_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-12 dB (CUT)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                            Text("0 dB (NORMAL)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            Text("+12 dB (BOOST)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick ReplayGain Target Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(-6f, -3f, 0f, 3f, 6f).forEach { targetDb ->
                                val isSelected = kotlin.math.abs(engineState.preAmpGainDb - targetDb) < 0.2f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) AmberAnalog else ObsidianBg)
                                        .clickable { onPreAmpChange(targetDb) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("pre_amp_preset_${targetDb.toInt()}db"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            targetDb > 0 -> "+${targetDb.toInt()}dB"
                                            targetDb < 0 -> "${targetDb.toInt()}dB"
                                            else -> "0dB"
                                        },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ObsidianBg else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Real-Time 32-Band Frequency Spectrum Visualizer with Peak LEDs
        StudioSpectrumVisualizer(
            playbackState = playbackState,
            engineState = engineState
        )

        // 3. Studio Pitch Shifting & Tempo Controls Deck
        Card(
            modifier = Modifier.fillMaxWidth().testTag("pitch_tempo_deck"),
            colors = CardDefaults.cardColors(containerColor = StudioDeckSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PITCH CONTROL & PLAYBACK SPEED",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pitch Transpose
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PITCH SHIFT (KEY TRANSPOSE)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                engineState.pitchSemitones > 0 -> "+${engineState.pitchSemitones} st"
                                engineState.pitchSemitones < 0 -> "${engineState.pitchSemitones} st"
                                else -> "0 st (ORIGINAL KEY)"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (engineState.pitchSemitones != 0) AmberAnalog else NeonCyan
                        )
                        IconButton(
                            onClick = onResetPitch,
                            modifier = Modifier.size(28.dp).testTag("deck_reset_pitch")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Pitch",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Slider(
                    value = engineState.pitchSemitones.toFloat(),
                    onValueChange = { onPitchSemitonesChange(it.toInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = AmberAnalog,
                        activeTrackColor = AmberAnalog,
                        inactiveTrackColor = StudioCardSurface
                    ),
                    modifier = Modifier.testTag("deck_pitch_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tempo / Playback Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLAYBACK SPEED (TEMPO)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%.2fx".format(engineState.tempo),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        IconButton(
                            onClick = onResetTempo,
                            modifier = Modifier.size(28.dp).testTag("deck_reset_tempo")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Tempo",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Slider(
                    value = engineState.tempo,
                    onValueChange = onTempoChange,
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = StudioCardSurface
                    ),
                    modifier = Modifier.testTag("deck_tempo_slider")
                )
            }
        }

        // 4. Pre-Amp Gain Trim
        Card(
            modifier = Modifier.fillMaxWidth().testTag("pre_amp_deck"),
            colors = CardDefaults.cardColors(containerColor = StudioDeckSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AmberAnalog,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRE-AMP GAIN (REPLAYGAIN)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "%+.1f dB".format(engineState.preAmpGainDb),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (engineState.preAmpGainDb > 0) AmberAnalog else NeonCyan
                    )
                }

                Slider(
                    value = engineState.preAmpGainDb,
                    onValueChange = onPreAmpChange,
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(
                        thumbColor = AmberAnalog,
                        activeTrackColor = AmberAnalog,
                        inactiveTrackColor = StudioCardSurface
                    ),
                    modifier = Modifier.testTag("deck_pre_amp_slider")
                )
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
