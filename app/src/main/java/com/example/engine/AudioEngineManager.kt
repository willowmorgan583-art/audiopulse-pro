package com.example.engine

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioEngineState
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.model.StudioEqPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Manages the high-fidelity DSP audio effects pipeline:
 * - 10-band Adaptive Graphic Equalizer with hardware HAL interpolation
 * - Pre-Amp Gain trim (-6.0 dB to +6.0 dB) for ReplayGain normalization
 * - Deep Bass Boost (0 - 1000‰)
 * - 3D Virtualizer / Spatial stage (0 - 1000‰)
 * - Pitch Control & Shift (-12 to +12 semitones, 0.5x to 2.0x pitch bend)
 * - Independent Playback Speed / Tempo (0.5x to 2.0x)
 * - User preset creation, persistence, and deletion
 */
class AudioEngineManager private constructor() {

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var hardwareEqualizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    // Physical hardware frequency bands in Hz: e.g. [60, 230, 910, 3600, 14000]
    private var physicalCenterFreqsHz = listOf<Int>()
    private var hardwareMinMilliBels: Short = -1500
    private var hardwareMaxMilliBels: Short = 1500

    /**
     * Binds the DSP effects to an active audio session ID from ExoPlayer.
     */
    @Synchronized
    fun attachAudioSession(sessionId: Int, player: ExoPlayer) {
        this.exoPlayer = player
        if (sessionId == 0 || (sessionId == _engineState.value.audioSessionId && hardwareEqualizer != null)) {
            return
        }

        releaseHardwareEffects()

        try {
            // 1. Equalizer initialization
            val eq = Equalizer(0, sessionId).apply {
                enabled = _engineState.value.isEqEnabled
            }
            hardwareEqualizer = eq

            val numBands = eq.numberOfBands.toInt()
            val bandRange = eq.bandLevelRange
            hardwareMinMilliBels = bandRange[0]
            hardwareMaxMilliBels = bandRange[1]

            val centerFreqs = mutableListOf<Int>()
            for (i in 0 until numBands) {
                // eq.getCenterFreq returns milliHertz
                val freqHz = eq.getCenterFreq(i.toShort()) / 1000
                centerFreqs.add(freqHz)
            }
            physicalCenterFreqsHz = centerFreqs

            // 2. BassBoost initialization
            val bb = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(_engineState.value.bassBoostStrength.toShort())
                    enabled = _engineState.value.bassBoostStrength > 0
                }
            }
            bassBoost = bb

            // 3. Virtualizer initialization
            val virt = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(_engineState.value.virtualizerStrength.toShort())
                    enabled = _engineState.value.virtualizerStrength > 0
                }
            }
            virtualizer = virt

            _engineState.update { current ->
                current.copy(
                    audioSessionId = sessionId,
                    isHardwareEqAttached = true,
                    physicalBandCount = numBands
                )
            }

            // Sync current gains to the newly attached hardware
            applyGainsToHardware(_engineState.value.bands.map { it.gainDb })
            syncTempoAndPitch()
            syncPreAmpGain()

            Log.d(TAG, "AudioFx attached to session $sessionId: $numBands hardware bands found.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize hardware audio effects on session $sessionId", e)
            _engineState.update { it.copy(isHardwareEqAttached = false) }
        }
    }

    /**
     * Detaches and releases hardware DSP audio effects.
     */
    @Synchronized
    fun detachAudioSession() {
        releaseHardwareEffects()
        exoPlayer = null
        _engineState.update {
            it.copy(
                isHardwareEqAttached = false,
                audioSessionId = 0,
                physicalBandCount = 0
            )
        }
    }

    private fun releaseHardwareEffects() {
        try {
            hardwareEqualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Equalizer", e)
        }
        hardwareEqualizer = null

        try {
            bassBoost?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing BassBoost", e)
        }
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Virtualizer", e)
        }
        virtualizer = null
    }

    /**
     * Toggles equalizer on/off bypass.
     */
    fun setEqualizerEnabled(enabled: Boolean) {
        _engineState.update { it.copy(isEqEnabled = enabled) }
        try {
            hardwareEqualizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling Equalizer enabled state", e)
        }
    }

    /**
     * Adjusts the gain in decibels (-15dB to +15dB) for a specific virtual 10-band index.
     */
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val clampedGain = gainDb.coerceIn(-15f, 15f)
        _engineState.update { current ->
            val updatedBands = current.bands.toMutableList()
            if (bandIndex in updatedBands.indices) {
                updatedBands[bandIndex] = updatedBands[bandIndex].copy(gainDb = clampedGain)
            }
            current.copy(
                bands = updatedBands,
                activePresetName = "Custom"
            )
        }
        applyGainsToHardware(_engineState.value.bands.map { it.gainDb })
    }

    /**
     * Sets Pre-Amp gain trim (-12.0 dB to +12.0 dB) for ReplayGain volume normalization and consistency.
     */
    fun setPreAmpGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 12f)
        _engineState.update { it.copy(preAmpGainDb = clamped) }
        applyGainsToHardware(_engineState.value.bands.map { it.gainDb })
        syncPreAmpGain()
    }

    /**
     * Resets ReplayGain Pre-Amp trim back to Unity gain (0.0 dB).
     */
    fun resetPreAmpGain() {
        setPreAmpGain(0.0f)
    }

    /**
     * Scales ExoPlayer playback volume proportionally using the ReplayGain logarithmic factor: 10^(gainDb / 20).
     */
    private fun syncPreAmpGain() {
        val preAmpDb = _engineState.value.preAmpGainDb
        val linearFactor = 10.0.pow(preAmpDb.toDouble() / 20.0).toFloat()
        try {
            exoPlayer?.volume = (1.0f * linearFactor).coerceIn(0.05f, 2.0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying ReplayGain volume factor to ExoPlayer", e)
        }
    }

    /**
     * Applies a studio preset curve across the 10 bands.
     */
    fun applyPreset(preset: EqualizerPreset) {
        _engineState.update { current ->
            val updatedBands = current.bands.mapIndexed { index, band ->
                val gain = preset.gains.getOrElse(index) { 0f }.coerceIn(band.minDb, band.maxDb)
                band.copy(gainDb = gain)
            }
            current.copy(
                bands = updatedBands,
                activePresetName = preset.name
            )
        }
        applyGainsToHardware(_engineState.value.bands.map { it.gainDb })
    }

    /**
     * Sets Bass Boost strength (0 to 1000 per mille).
     */
    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _engineState.update { it.copy(bassBoostStrength = clamped) }
        try {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(clamped.toShort())
                    bb.enabled = clamped > 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting BassBoost strength", e)
        }
    }

    /**
     * Sets 3D Virtualizer / Spatial stage strength (0 to 1000 per mille).
     */
    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _engineState.update { it.copy(virtualizerStrength = clamped) }
        try {
            virtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    virt.setStrength(clamped.toShort())
                    virt.enabled = clamped > 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Virtualizer strength", e)
        }
    }

    /**
     * Sets playback speed/tempo (0.5x to 2.0x) without altering pitch.
     */
    fun setTempo(tempo: Float) {
        val clamped = tempo.coerceIn(0.5f, 2.0f)
        _engineState.update { it.copy(tempo = clamped) }
        syncTempoAndPitch()
    }

    /**
     * Sets musical pitch shift in semitones (-12 to +12, spanning 1 octave down to 1 octave up).
     * Calculates the exact frequency multiplier: 2^(semitones / 12).
     */
    fun setPitchSemitones(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        val multiplier = 2.0.pow(clamped.toDouble() / 12.0).toFloat().coerceIn(0.5f, 2.0f)
        _engineState.update {
            it.copy(
                pitchSemitones = clamped,
                pitch = multiplier
            )
        }
        syncTempoAndPitch()
    }

    /**
     * Sets pitch multiplier directly (0.5x to 2.0x).
     */
    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        val semitones = (12.0 * ln(clamped.toDouble()) / ln(2.0)).roundToInt().coerceIn(-12, 12)
        _engineState.update {
            it.copy(
                pitch = clamped,
                pitchSemitones = semitones
            )
        }
        syncTempoAndPitch()
    }

    /**
     * Resets pitch back to original key (0 semitones, 1.0x).
     */
    fun resetPitch() {
        setPitchSemitones(0)
    }

    /**
     * Resets all 10 EQ bands to Flat (0dB).
     */
    fun resetToFlat() {
        applyPreset(StudioEqPresets.FLAT)
    }

    // --- User Preset Persistence ---

    fun loadUserPresets(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_USER_PRESETS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_PRESETS_DATA, null) ?: return
            val presets = parsePresets(raw)
            _engineState.update { it.copy(userPresets = presets) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user presets", e)
        }
    }

    fun saveUserPreset(context: Context, name: String, gains: List<Float>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val currentPresets = _engineState.value.userPresets.toMutableList()
        currentPresets.removeAll { it.name.equals(trimmed, ignoreCase = true) }
        val newPreset = EqualizerPreset(name = trimmed, gains = gains, isUserPreset = true)
        currentPresets.add(0, newPreset)

        _engineState.update {
            it.copy(
                userPresets = currentPresets,
                activePresetName = trimmed
            )
        }
        persistPresets(context, currentPresets)
    }

    fun deleteUserPreset(context: Context, name: String) {
        val currentPresets = _engineState.value.userPresets.toMutableList()
        currentPresets.removeAll { it.name.equals(name, ignoreCase = true) }
        _engineState.update {
            it.copy(
                userPresets = currentPresets,
                activePresetName = if (it.activePresetName == name) "Flat" else it.activePresetName
            )
        }
        persistPresets(context, currentPresets)
    }

    private fun persistPresets(context: Context, presets: List<EqualizerPreset>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_USER_PRESETS, Context.MODE_PRIVATE)
            val serialized = presets.joinToString(";") { p ->
                val gainsStr = p.gains.joinToString(",") { "%.1f".format(it) }
                "${p.name}::$gainsStr"
            }
            prefs.edit().putString(KEY_PRESETS_DATA, serialized).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user presets", e)
        }
    }

    private fun parsePresets(raw: String): List<EqualizerPreset> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size == 2) {
                val name = parts[0]
                val gains = parts[1].split(",").mapNotNull { it.toFloatOrNull() }
                if (gains.size == 10) EqualizerPreset(name, gains, isUserPreset = true) else null
            } else null
        }
    }

    /**
     * Exports all custom user equalizer presets as a formatted JSON string.
     */
    fun exportPresetsJson(): String {
        val array = org.json.JSONArray()
        val presetsToExport = _engineState.value.userPresets
        for (preset in presetsToExport) {
            val obj = org.json.JSONObject()
            obj.put("name", preset.name)
            val gainsArray = org.json.JSONArray()
            preset.gains.forEach { gainsArray.put(it.toDouble()) }
            obj.put("gains", gainsArray)
            array.put(obj)
        }
        return array.toString(2)
    }

    /**
     * Imports custom equalizer presets from a JSON string, merging them with existing presets.
     */
    fun importPresetsJson(context: Context, jsonStr: String): Result<Int> {
        return try {
            val trimmed = jsonStr.trim()
            val array = if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed)
            } else {
                // Support single preset object format: {"name": "...", "gains": [...]}
                val single = org.json.JSONObject(trimmed)
                org.json.JSONArray().put(single)
            }

            val imported = mutableListOf<EqualizerPreset>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "Imported Preset ${i + 1}").trim()
                val gainsArray = obj.getJSONArray("gains")
                val gains = mutableListOf<Float>()
                for (j in 0 until gainsArray.length()) {
                    gains.add(gainsArray.getDouble(j).toFloat().coerceIn(-15f, 15f))
                }
                // If fewer or more than 10 bands provided, pad or truncate to 10
                val normalizedGains = (gains + List(10) { 0f }).take(10)
                imported.add(EqualizerPreset(name = name, gains = normalizedGains, isUserPreset = true))
            }

            if (imported.isEmpty()) {
                return Result.failure(IllegalArgumentException("No presets parsed from input JSON"))
            }

            val currentPresets = _engineState.value.userPresets.toMutableList()
            imported.forEach { newPreset ->
                currentPresets.removeAll { it.name.equals(newPreset.name, ignoreCase = true) }
                currentPresets.add(0, newPreset)
            }

            _engineState.update { it.copy(userPresets = currentPresets) }
            persistPresets(context, currentPresets)
            Result.success(imported.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing presets JSON", e)
            Result.failure(e)
        }
    }

    private fun syncTempoAndPitch() {
        val currentTempo = _engineState.value.tempo
        val currentPitch = _engineState.value.pitch
        try {
            exoPlayer?.playbackParameters = PlaybackParameters(currentTempo, currentPitch)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ExoPlayer PlaybackParameters", e)
        }
    }

    /**
     * Adaptive interpolation algorithm:
     * Takes the 10 virtual user band gains, adds pre-amp trim, and maps onto physical hardware bands
     * using logarithmic frequency distance weighting.
     */
    private fun applyGainsToHardware(virtualGains: List<Float>) {
        val eq = hardwareEqualizer ?: return
        val physicalFreqs = physicalCenterFreqsHz
        if (physicalFreqs.isEmpty()) return

        val virtualFreqs = _engineState.value.bands.map { it.centerFreqHz }
        val preAmp = _engineState.value.preAmpGainDb

        try {
            for (pIndex in physicalFreqs.indices) {
                val pFreq = physicalFreqs[pIndex]

                var totalWeight = 0.0
                var weightedGainSum = 0.0

                for (vIndex in virtualFreqs.indices) {
                    val vFreq = virtualFreqs[vIndex]
                    val octaveDist = abs(ln(pFreq.toDouble() / vFreq.toDouble()))
                    val weight = 1.0 / (1.0 + 4.0 * octaveDist * octaveDist)
                    weightedGainSum += virtualGains[vIndex] * weight
                    totalWeight += weight
                }

                val interpolatedGainDb = if (totalWeight > 0.0) {
                    (weightedGainSum / totalWeight).toFloat()
                } else {
                    0f
                }

                // Add pre-amp gain trim with soft limit
                val finalGainDb = (interpolatedGainDb + preAmp).coerceIn(-15f, 15f)

                // Convert dB to milliBels: 1 dB = 100 milliBels
                val milliBels = (finalGainDb * 100).toInt().toShort()
                val clampedMilliBels = milliBels.coerceIn(hardwareMinMilliBels, hardwareMaxMilliBels)

                eq.setBandLevel(pIndex.toShort(), clampedMilliBels)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing gains to hardware Equalizer", e)
        }
    }

    companion object {
        private const val TAG = "AudioEngineManager"
        private const val PREFS_USER_PRESETS = "audio_user_presets"
        private const val KEY_PRESETS_DATA = "presets_data"

        @Volatile
        private var instance: AudioEngineManager? = null

        fun getInstance(): AudioEngineManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEngineManager().also { instance = it }
            }
        }
    }
}
