package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaRepository
import com.example.data.MediaStoreAudioScanner
import com.example.data.local.AppDatabase
import com.example.data.local.AudioTrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: MediaRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scanner = MediaStoreAudioScanner(context)
        repository = MediaRepository(database.audioTrackDao(), database.playlistDao(), scanner)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `syncLocalMedia populates Room database from MediaStore scanner`() = runBlocking {
        val result = repository.syncLocalMedia()
        assertTrue("Sync should succeed", result.isSuccess)

        val tracks = repository.tracksFlow.first()
        assertTrue("Tracks should be cached in database", tracks.isNotEmpty())

        val count = repository.getTrackCount()
        assertEquals(tracks.size, count)
    }

    @Test
    fun `searchTracks filters cached tracks by title or artist`() = runBlocking {
        val testEntity = AudioTrackEntity(
            id = 9999L,
            title = "Synthetic Skyline",
            artist = "Neon Producer",
            album = "Cyber Dreams",
            durationMs = 210000L,
            contentUriString = "content://media/external/audio/media/9999",
            albumArtUriString = null,
            mimeType = "audio/flac",
            sizeBytes = 12000000L,
            sampleRateHz = 48000,
            bitRateKbps = 1411,
            year = 2026
        )
        database.audioTrackDao().insertTracks(listOf(testEntity))

        val searchResults = repository.searchTracks("Skyline").first()
        assertEquals(1, searchResults.size)
        assertEquals("Synthetic Skyline", searchResults[0].title)
        assertEquals("Neon Producer", searchResults[0].artist)

        val artistResults = repository.searchTracks("Producer").first()
        assertEquals(1, artistResults.size)
    }

    @Test
    fun `getTrackById retrieves exact track from Room`() = runBlocking {
        val testEntity = AudioTrackEntity(
            id = 7777L,
            title = "Acoustic Resonance",
            artist = "String Master",
            album = "Unplugged 2026",
            durationMs = 180000L,
            contentUriString = "content://media/external/audio/media/7777",
            albumArtUriString = null,
            mimeType = "audio/wav",
            sizeBytes = 25000000L,
            sampleRateHz = 96000,
            bitRateKbps = 2304,
            year = 2026
        )
        database.audioTrackDao().insertTracks(listOf(testEntity))

        val track = repository.getTrackById(7777L)
        assertNotNull(track)
        assertEquals("Acoustic Resonance", track?.title)
        assertEquals("WAV • 24-bit", track?.audioQualityBadge)
    }
}
