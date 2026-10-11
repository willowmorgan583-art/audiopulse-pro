package com.example

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import com.example.service.AudioControlInterface
import com.example.service.AudioService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioServiceTest {

    @Test
    fun `service binds and exposes AudioControlInterface`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        try {
            val service = controller.get()
            val binder = service.onBind(Intent()) as? AudioService.AudioServiceBinder
            assertNotNull("Binder should not be null", binder)

            val controlInterface: AudioControlInterface = binder!!.getController()
            assertNotNull("AudioControlInterface controller should not be null", controlInterface)
            assertNotNull("AudioEngineManager should be accessible", controlInterface.getAudioEngineManager())
            assertNotNull("PlaybackState flow should not be null", controlInterface.playbackState)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `independent pitch shifting does not alter playback tempo`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        try {
            val service = controller.get()
            val binder = service.onBind(Intent()) as AudioService.AudioServiceBinder
            val controlInterface = binder.getController()

            // 1. Initial defaults
            assertEquals(1.0f, controlInterface.getPlaybackSpeed(), 0.01f)
            assertEquals(1.0f, controlInterface.getPitch(), 0.01f)

            // 2. Set tempo to 1.5x, pitch should remain 1.0x
            controlInterface.setPlaybackSpeed(1.5f)
            assertEquals(1.5f, controlInterface.getPlaybackSpeed(), 0.01f)
            assertEquals(1.0f, controlInterface.getPitch(), 0.01f)

            // 3. Shift pitch up by 12 semitones (1 octave up, 2.0x ratio), tempo MUST stay 1.5x
            controlInterface.setPitchSemitones(12)
            assertEquals(2.0f, controlInterface.getPitch(), 0.01f)
            assertEquals(1.5f, controlInterface.getPlaybackSpeed(), 0.01f)

            // 4. Shift pitch down by 12 semitones (1 octave down, 0.5x ratio), tempo MUST stay 1.5x
            controlInterface.setPitchSemitones(-12)
            assertEquals(0.5f, controlInterface.getPitch(), 0.01f)
            assertEquals(1.5f, controlInterface.getPlaybackSpeed(), 0.01f)

            // 5. Reset pitch, tempo MUST remain 1.5x
            controlInterface.resetPitch()
            assertEquals(1.0f, controlInterface.getPitch(), 0.01f)
            assertEquals(1.5f, controlInterface.getPlaybackSpeed(), 0.01f)

            // 6. Reset tempo, pitch MUST remain 1.0x
            controlInterface.resetPlaybackSpeed()
            assertEquals(1.0f, controlInterface.getPlaybackSpeed(), 0.01f)
            assertEquals(1.0f, controlInterface.getPitch(), 0.01f)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `replay gain pre-amp gain controls adjust audio levels and reset`() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        try {
            val service = controller.get()
            val binder = service.onBind(Intent()) as AudioService.AudioServiceBinder
            val controlInterface = binder.getController()

            // Default is 0.0 dB
            assertEquals(0.0f, controlInterface.getPreAmpGain(), 0.01f)

            // Set to +4.5 dB
            controlInterface.setPreAmpGain(4.5f)
            assertEquals(4.5f, controlInterface.getPreAmpGain(), 0.01f)

            // Reset back to unity gain
            controlInterface.resetPreAmpGain()
            assertEquals(0.0f, controlInterface.getPreAmpGain(), 0.01f)
        } finally {
            controller.destroy()
        }
    }
}
