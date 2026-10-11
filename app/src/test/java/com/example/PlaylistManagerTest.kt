package com.example

import android.net.Uri
import com.example.data.PlaylistManager
import com.example.data.RepeatMode
import com.example.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistManagerTest {

    private fun createTrack(id: Long, title: String) = AudioTrack(
        id = id,
        title = title,
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 180000L,
        contentUri = Uri.parse("content://media/external/audio/media/$id")
    )

    @Test
    fun `setQueue and navigation`() {
        val manager = PlaylistManager()
        val tracks = listOf(createTrack(1, "Track 1"), createTrack(2, "Track 2"), createTrack(3, "Track 3"))

        manager.setQueue(tracks, 0)
        assertEquals(3, manager.queueState.value.queue.size)
        assertEquals("Track 1", manager.queueState.value.currentTrack?.title)

        val next = manager.advanceToNext()
        assertEquals("Track 2", next?.title)

        val prev = manager.retreatToPrevious()
        assertEquals("Track 1", prev?.title)
    }

    @Test
    fun `enqueueNext places track immediately after current`() {
        val manager = PlaylistManager()
        val tracks = listOf(createTrack(1, "Song A"), createTrack(2, "Song B"))
        manager.setQueue(tracks, 0)

        val nextSong = createTrack(99, "Priority Next")
        manager.enqueueNext(nextSong)

        assertEquals(3, manager.queueState.value.queue.size)
        assertEquals("Song A", manager.queueState.value.queue[0].title)
        assertEquals("Priority Next", manager.queueState.value.queue[1].title)
        assertEquals("Song B", manager.queueState.value.queue[2].title)
    }

    @Test
    fun `repeat mode one replays current track`() {
        val manager = PlaylistManager()
        val tracks = listOf(createTrack(1, "Repeat Me"), createTrack(2, "Track 2"))
        manager.setQueue(tracks, 0)

        manager.toggleRepeat() // ALL
        manager.toggleRepeat() // ONE
        assertEquals(RepeatMode.ONE, manager.queueState.value.repeatMode)

        val advanced = manager.advanceToNext()
        assertEquals("Repeat Me", advanced?.title)
    }

    @Test
    fun `shuffle mode toggles and generates shuffled sequence`() {
        val manager = PlaylistManager()
        val tracks = (1..10).map { createTrack(it.toLong(), "Track $it") }
        manager.setQueue(tracks, 0)

        assertFalse(manager.queueState.value.isShuffleEnabled)
        val enabled = manager.toggleShuffle()
        assertTrue(enabled)
        assertTrue(manager.queueState.value.isShuffleEnabled)
        assertEquals(10, manager.queueState.value.shuffledIndices.size)
        // Current index must be first in shuffled sequence
        assertEquals(0, manager.queueState.value.shuffledIndices[0])
    }
}
