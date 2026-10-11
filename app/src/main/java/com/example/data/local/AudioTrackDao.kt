package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local cached audio tracks.
 */
@Dao
interface AudioTrackDao {

    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): AudioTrackEntity?

    @Query("SELECT * FROM audio_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<AudioTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<AudioTrackEntity>)

    @Query("DELETE FROM audio_tracks WHERE id NOT IN (:validIds)")
    suspend fun deleteTracksNotIn(validIds: List<Long>)

    @Query("DELETE FROM audio_tracks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM audio_tracks")
    suspend fun getTrackCount(): Int
}
