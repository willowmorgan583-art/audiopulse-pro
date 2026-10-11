package com.example.model

import android.net.Uri

/**
 * Represents a local audio track scanned from Android MediaStore or loaded from bundled stems.
 */
data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri? = null,
    val mimeType: String = "audio/mpeg",
    val sizeBytes: Long = 0L,
    val sampleRateHz: Int = 44100,
    val bitRateKbps: Int = 320,
    val year: Int = 0
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val audioQualityBadge: String
        get() = when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC • Hi-Res"
            mimeType.contains("wav", ignoreCase = true) -> "WAV • 24-bit"
            mimeType.contains("ogg", ignoreCase = true) -> "OGG • 320k"
            mimeType.contains("aac", ignoreCase = true) -> "AAC • HD"
            else -> "MP3 • 320kbps"
        }
}

/**
 * Single equalizer frequency band descriptor.
 */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val gainDb: Float,
    val minDb: Float = -15f,
    val maxDb: Float = 15f
)

/**
 * Saved equalizer preset profile.
 */
data class EqualizerPreset(
    val name: String,
    val gains: List<Float>,
    val isUserPreset: Boolean = false
)

/**
 * Built-in studio equalizer presets calibrated for 10-band octave equalization.
 */
object StudioEqPresets {
    val FLAT = EqualizerPreset("Flat", List(10) { 0f }, isUserPreset = false)
    val BASS_BOOST = EqualizerPreset("Bass Boost", listOf(6.5f, 5.0f, 3.5f, 2.0f, 0f, 0f, 0f, 0f, 0.5f, 1.0f), isUserPreset = false)
    val ELECTRONIC = EqualizerPreset("Electronic", listOf(5.0f, 4.0f, 1.5f, 0f, -1.5f, 2.0f, 1.0f, 2.5f, 4.0f, 5.5f), isUserPreset = false)
    val ROCK = EqualizerPreset("Rock", listOf(4.5f, 3.0f, -1.0f, -1.5f, 0f, 1.5f, 3.0f, 4.0f, 4.5f, 5.0f), isUserPreset = false)
    val JAZZ = EqualizerPreset("Jazz", listOf(3.0f, 2.0f, 1.0f, 1.5f, -1.0f, -1.0f, 0f, 1.5f, 3.0f, 3.5f), isUserPreset = false)
    val VOCAL = EqualizerPreset("Vocal Boost", listOf(-2.0f, -1.0f, 0f, 2.5f, 4.5f, 4.0f, 2.5f, 1.0f, 0f, -1.0f), isUserPreset = false)
    val ACOUSTIC = EqualizerPreset("Acoustic", listOf(3.5f, 3.0f, 1.5f, 1.0f, 1.5f, 1.5f, 2.5f, 3.0f, 3.5f, 3.0f), isUserPreset = false)
    val HIP_HOP = EqualizerPreset("Hip Hop", listOf(6.0f, 5.5f, 2.5f, 0.5f, -1.0f, -1.0f, 1.0f, 0f, 2.5f, 3.5f), isUserPreset = false)

    val PRESETS = listOf(FLAT, BASS_BOOST, ELECTRONIC, ROCK, JAZZ, VOCAL, ACOUSTIC, HIP_HOP)
}

/**
 * Reactive snapshot of DSP engine parameters.
 */
data class AudioEngineState(
    val isEqEnabled: Boolean = true,
    val bands: List<EqualizerBand> = createDefaultBands(),
    val bassBoostStrength: Int = 0,         // 0 - 1000 millibels
    val virtualizerStrength: Int = 0,       // 0 - 1000 millibels
    val activePresetName: String = "Flat",
    val userPresets: List<EqualizerPreset> = emptyList(),
    val pitch: Float = 1.0f,                // 0.5x - 2.0x frequency multiplier
    val pitchSemitones: Int = 0,            // -12 to +12 semitones
    val tempo: Float = 1.0f,                // 0.5x - 2.0x playback speed
    val preAmpGainDb: Float = 0.0f,         // -12.0 dB to +12.0 dB ReplayGain trim
    val isHardwareEqAttached: Boolean = false,
    val audioSessionId: Int = 0,
    val physicalBandCount: Int = 0
) {
    val allPresets: List<EqualizerPreset>
        get() = userPresets + StudioEqPresets.PRESETS

    companion object {
        val ISO_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val ISO_LABELS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

        fun createDefaultBands(): List<EqualizerBand> {
            return ISO_FREQUENCIES.mapIndexed { index, freq ->
                val label = ISO_LABELS.getOrElse(index) { "${freq}Hz" }
                EqualizerBand(
                    index = index,
                    centerFreqHz = freq,
                    label = label,
                    gainDb = 0f,
                    minDb = -15f,
                    maxDb = 15f
                )
            }
        }
    }
}

/**
 * Real-time playback status emitted to the UI layer.
 */
data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val repeatMode: Int = 0,               // 0 = Off, 1 = Repeat All, 2 = Repeat One
    val isShuffleActive: Boolean = false,
    val waveformAmplitudes: List<Float> = emptyList(),
    val spectrumAmplitudes: List<Float> = emptyList(),
    val errorMessage: String? = null
)
