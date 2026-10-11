package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.R
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans local storage for audio files using Android's MediaStore API,
 * with fallbacks to bundled high-fidelity demo stems when no audio files are present.
 */
class MediaStoreAudioScanner(private val context: Context) {

    suspend fun scanLocalAudioTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()

        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Track"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val duration = c.getLong(durationCol)
                    val albumId = c.getLong(albumIdCol)
                    val mime = c.getString(mimeCol) ?: "audio/mpeg"
                    val size = c.getLong(sizeCol)
                    val year = c.getInt(yearCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    tracks.add(
                        AudioTrack(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            mimeType = mime,
                            sizeBytes = size,
                            year = year
                        )
                    )
                }
            }
            Log.d(TAG, "Scanned ${tracks.size} tracks from MediaStore.")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore audio tracks", e)
        }

        // If no local tracks exist yet on the clean device / emulator environment,
        // provide high-fidelity bundled audio stems so the player and DSP engine can be immediately tested.
        if (tracks.isEmpty()) {
            tracks.addAll(getBundledAudiophileTracks())
        }

        tracks
    }

    private fun getBundledAudiophileTracks(): List<AudioTrack> {
        val packageName = context.packageName
        val cyberPulseUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.cyber_pulse}")
        val subBassUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.sub_bass_resonance}")
        val midnightVelocityUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.midnight_velocity}")

        return listOf(
            AudioTrack(
                id = -1L,
                title = "Cyber Pulse (Studio Master)",
                artist = "Neon Odyssey",
                album = "Synthesize 2099",
                durationMs = 18000L,
                contentUri = cyberPulseUri,
                albumArtUri = null,
                mimeType = "audio/wav",
                sizeBytes = 3175244L,
                sampleRateHz = 44100,
                bitRateKbps = 1411
            ),
            AudioTrack(
                id = -2L,
                title = "Sub-Bass Resonance 45Hz",
                artist = "Frequency Lab",
                album = "Audiophile Acoustic Calibration",
                durationMs = 18000L,
                contentUri = subBassUri,
                albumArtUri = null,
                mimeType = "audio/wav",
                sizeBytes = 3175244L,
                sampleRateHz = 44100,
                bitRateKbps = 1411
            ),
            AudioTrack(
                id = -3L,
                title = "Midnight Velocity (Acoustic Hi-Res)",
                artist = "Aura Quartet",
                album = "Analog Sessions Vol. 4",
                durationMs = 18000L,
                contentUri = midnightVelocityUri,
                albumArtUri = null,
                mimeType = "audio/wav",
                sizeBytes = 3175244L,
                sampleRateHz = 44100,
                bitRateKbps = 1411
            )
        )
    }

    companion object {
        private const val TAG = "MediaStoreAudioScanner"
    }
}
