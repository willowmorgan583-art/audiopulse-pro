package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.AudioTrackDao
import com.example.data.local.AudioTrackEntity
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Unified Repository coordinating:
 * - MediaStore audio file scanner
 * - Room database cache for audio tracks
 * - Custom user playlists via Room PlaylistDao
 * - In-memory playback queue, shuffle, and repeat management via PlaylistManager
 */
class MediaRepository(
    private val audioTrackDao: AudioTrackDao,
    private val playlistDao: PlaylistDao,
    private val mediaStoreScanner: MediaStoreAudioScanner,
    val playlistManager: PlaylistManager = PlaylistManager()
) {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * Reactive stream of cached tracks from the Room database.
     */
    val tracksFlow: Flow<List<AudioTrack>> = audioTrackDao.getAllTracks().map { entities ->
        entities.map { it.toAudioTrack() }
    }

    /**
     * Reactive stream of all user-created playlists.
     */
    val playlistsFlow: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    /**
     * Scans device storage via Android MediaStore and synchronizes the internal Room database.
     */
    suspend fun syncLocalMedia(): Result<Int> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            Log.d(TAG, "Starting MediaStore to Room synchronization...")
            val scannedTracks = mediaStoreScanner.scanLocalAudioTracks()

            if (scannedTracks.isNotEmpty()) {
                val entities = scannedTracks.map { AudioTrackEntity.fromAudioTrack(it) }
                audioTrackDao.insertTracks(entities)

                val validIds = scannedTracks.map { it.id }
                audioTrackDao.deleteTracksNotIn(validIds)
            }

            val count = audioTrackDao.getTrackCount()
            Log.d(TAG, "Sync complete. Total cached tracks in Room: $count")
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing MediaStore to Room", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Searches cached tracks by title, artist, or album.
     */
    fun searchTracks(query: String): Flow<List<AudioTrack>> {
        return audioTrackDao.searchTracks(query.trim()).map { entities ->
            entities.map { it.toAudioTrack() }
        }
    }

    /**
     * Fetches a single track from the Room cache by ID.
     */
    suspend fun getTrackById(id: Long): AudioTrack? = withContext(Dispatchers.IO) {
        audioTrackDao.getTrackById(id)?.toAudioTrack()
    }

    suspend fun getTrackCount(): Int = withContext(Dispatchers.IO) {
        audioTrackDao.getTrackCount()
    }

    // --- Playlist CRUD Operations ---

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        val currentCount = playlistDao.getTrackCountForPlaylist(playlistId)
        playlistDao.addTrackToPlaylist(PlaylistTrackEntity(playlistId, trackId, sortOrder = currentCount))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<AudioTrack>> {
        return playlistDao.getTracksForPlaylist(playlistId).map { entities ->
            entities.map { it.toAudioTrack() }
        }
    }

    suspend fun getPlaylistTrackCount(playlistId: Long): Int = withContext(Dispatchers.IO) {
        playlistDao.getTrackCountForPlaylist(playlistId)
    }

    companion object {
        private const val TAG = "MediaRepository"

        @Volatile
        private var instance: MediaRepository? = null

        fun getInstance(context: Context): MediaRepository {
            return instance ?: synchronized(this) {
                val db = AppDatabase.getInstance(context.applicationContext)
                val scanner = MediaStoreAudioScanner(context.applicationContext)
                instance ?: MediaRepository(
                    db.audioTrackDao(),
                    db.playlistDao(),
                    scanner,
                    PlaylistManager()
                ).also { instance = it }
            }
        }
    }
}
