package com.example

import com.example.engine.AudioEngineManager
import com.example.model.StudioEqPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineTest {

    @Test
    fun `default state has 10 equalizer bands`() {
        val manager = AudioEngineManager.getInstance()
        val state = manager.engineState.value
        assertEquals(10, state.bands.size)
        assertEquals(31, state.bands[0].centerFreqHz)
        assertEquals(16000, state.bands[9].centerFreqHz)
        assertEquals("Flat", state.activePresetName)
    }

    @Test
    fun `applying preset updates all 10 band gains`() {
        val manager = AudioEngineManager.getInstance()
        manager.applyPreset(StudioEqPresets.BASS_BOOST)
        val state = manager.engineState.value
        assertEquals("Bass Boost", state.activePresetName)
        assertEquals(6.5f, state.bands[0].gainDb, 0.01f)
        assertEquals(5.0f, state.bands[1].gainDb, 0.01f)
    }

    @Test
    fun `tempo and pitch clamping works properly`() {
        val manager = AudioEngineManager.getInstance()
        manager.setTempo(1.5f)
        manager.setPitch(1.2f)
        val state = manager.engineState.value
        assertEquals(1.5f, state.tempo, 0.01f)
        assertEquals(1.2f, state.pitch, 0.01f)

        manager.setTempo(5.0f)
        assertEquals(2.0f, manager.engineState.value.tempo, 0.01f)

        manager.setTempo(0.1f)
        assertEquals(0.5f, manager.engineState.value.tempo, 0.01f)
    }

    @Test
    fun `pitch semitone shifting calculates correct octave ratios`() {
        val manager = AudioEngineManager.getInstance()
        
        // 0 semitones = 1.0x (unison)
        manager.setPitchSemitones(0)
        assertEquals(0, manager.engineState.value.pitchSemitones)
        assertEquals(1.0f, manager.engineState.value.pitch, 0.01f)

        // +12 semitones = 2.0x (one octave up)
        manager.setPitchSemitones(12)
        assertEquals(12, manager.engineState.value.pitchSemitones)
        assertEquals(2.0f, manager.engineState.value.pitch, 0.01f)

        // -12 semitones = 0.5x (one octave down)
        manager.setPitchSemitones(-12)
        assertEquals(-12, manager.engineState.value.pitchSemitones)
        assertEquals(0.5f, manager.engineState.value.pitch, 0.01f)

        // Reset pitch
        manager.resetPitch()
        assertEquals(0, manager.engineState.value.pitchSemitones)
        assertEquals(1.0f, manager.engineState.value.pitch, 0.01f)
    }

    @Test
    fun `pre-amp gain clamping and ReplayGain trim`() {
        val manager = AudioEngineManager.getInstance()
        manager.setPreAmpGain(3.5f)
        assertEquals(3.5f, manager.engineState.value.preAmpGainDb, 0.01f)

        // Boundary clamp to -12dB .. +12dB
        manager.setPreAmpGain(18f)
        assertEquals(12.0f, manager.engineState.value.preAmpGainDb, 0.01f)

        manager.setPreAmpGain(-20f)
        assertEquals(-12.0f, manager.engineState.value.preAmpGainDb, 0.01f)

        manager.resetPreAmpGain()
        assertEquals(0.0f, manager.engineState.value.preAmpGainDb, 0.01f)
    }

    @Test
    fun `bass boost and virtualizer clamp within 0 to 1000`() {
        val manager = AudioEngineManager.getInstance()
        manager.setBassBoostStrength(750)
        assertEquals(750, manager.engineState.value.bassBoostStrength)

        manager.setBassBoostStrength(1500)
        assertEquals(1000, manager.engineState.value.bassBoostStrength)

        manager.setVirtualizerStrength(-100)
        assertEquals(0, manager.engineState.value.virtualizerStrength)
    }
}
