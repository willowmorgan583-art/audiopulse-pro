package com.example.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AudioTrack

/**
 * Room database entity storing indexed local audio metadata for high-speed offline access.
 */
@Entity(tableName = "audio_tracks")
data class AudioTrackEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUriString: String,
    val albumArtUriString: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    val bitRateKbps: Int,
    val year: Int,
    val lastScannedTimestamp: Long = System.currentTimeMillis()
) {
    fun toAudioTrack(): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            contentUri = Uri.parse(contentUriString),
            albumArtUri = albumArtUriString?.let { Uri.parse(it) },
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sampleRateHz = sampleRateHz,
            bitRateKbps = bitRateKbps,
            year = year
        )
    }

    companion object {
        fun fromAudioTrack(track: AudioTrack): AudioTrackEntity {
            return AudioTrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                contentUriString = track.contentUri.toString(),
                albumArtUriString = track.albumArtUri?.toString(),
                mimeType = track.mimeType,
                sizeBytes = track.sizeBytes,
                sampleRateHz = track.sampleRateHz,
                bitRateKbps = track.bitRateKbps,
                year = track.year
            )
        }
    }
}
