package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.example.engine.AudioEngineManager
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class AudioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackingJob: Job? = null

    private val audioEngineManager = AudioEngineManager.getInstance()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playlist = mutableListOf<AudioTrack>()
    private var currentTrackIndex = -1

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        initializePlayer()
        initializeMediaSession()
        startProgressTracking()
        Log.d(TAG, "AudioPlaybackService created successfully.")
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                Log.d(TAG, "onAudioSessionIdChanged: $audioSessionId")
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    audioEngineManager.attachAudioSession(audioSessionId, exo)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackStateInt: Int) {
                super.onPlaybackStateChanged(playbackStateInt)
                val isBuffering = playbackStateInt == Player.STATE_BUFFERING
                val isEnded = playbackStateInt == Player.STATE_ENDED

                _playbackState.update {
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = exo.duration.coerceAtLeast(0L)
                    )
                }

                if (isEnded) {
                    playNextTrack()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                _playbackState.update {
                    it.copy(
                        durationMs = exo.duration.coerceAtLeast(0L),
                        currentPositionMs = 0L
                    )
                }
                updateNotification()
            }
        })

        this.player = exo
    }

    private fun initializeMediaSession() {
        val currentExo = player ?: return
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, currentExo)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    /**
     * Loads a playlist and begins playing at the specified track index.
     */
    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        playlist.clear()
        playlist.addAll(tracks)
        playTrackAtIndex(startIndex.coerceIn(0, playlist.lastIndex))
    }

    fun playTrack(track: AudioTrack) {
        val existingIndex = playlist.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) {
            playTrackAtIndex(existingIndex)
        } else {
            playlist.add(0, track)
            playTrackAtIndex(0)
        }
    }

    fun playTrackAtIndex(index: Int) {
        val exo = player ?: return
        if (index !in playlist.indices) return

        currentTrackIndex = index
        val track = playlist[index]

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(track.contentUri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()

        // Generate synthetic waveform bars for visualizer based on track duration
        val syntheticAmplitudes = generateWaveformSample(track.durationMs)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                waveformAmplitudes = syntheticAmplitudes
            )
        }

        updateNotification()
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
    }

    fun playNextTrack() {
        if (playlist.isEmpty()) return
        val nextIndex = if (_playbackState.value.isShuffleActive) {
            playlist.indices.random()
        } else {
            (currentTrackIndex + 1) % playlist.size
        }
        playTrackAtIndex(nextIndex)
    }

    fun playPreviousTrack() {
        if (playlist.isEmpty()) return
        val exo = player ?: return
        if (exo.currentPosition > 3000L) {
            exo.seekTo(0L)
            return
        }
        val prevIndex = if (currentTrackIndex - 1 < 0) playlist.lastIndex else currentTrackIndex - 1
        playTrackAtIndex(prevIndex)
    }

    fun seekTo(positionMs: Long) {
        val exo = player ?: return
        exo.seekTo(positionMs.coerceIn(0L, exo.duration.coerceAtLeast(0L)))
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffleActive = !it.isShuffleActive) }
    }

    fun toggleRepeat() {
        val exo = player ?: return
        val nextRepeat = (_playbackState.value.repeatMode + 1) % 3
        _playbackState.update { it.copy(repeatMode = nextRepeat) }
        when (nextRepeat) {
            0 -> exo.repeatMode = Player.REPEAT_MODE_OFF
            1 -> exo.repeatMode = Player.REPEAT_MODE_ALL
            2 -> exo.repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = serviceScope.launch {
            while (isActive) {
                player?.let { exo ->
                    if (exo.isPlaying) {
                        _playbackState.update {
                            it.copy(
                                currentPositionMs = exo.currentPosition.coerceAtLeast(0L),
                                durationMs = exo.duration.coerceAtLeast(0L)
                            )
                        }
                    }
                }
                delay(200L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback controls and media notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val track = _playbackState.value.currentTrack ?: return
        val isPlaying = _playbackState.value.isPlaying

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setSubText(track.audioQualityBadge)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun generateWaveformSample(durationMs: Long): List<Float> {
        val bars = 64
        val seed = (durationMs % 1000).toInt()
        return List(bars) { i ->
            val angle = (i.toFloat() / bars) * 4 * Math.PI
            val norm = (Math.sin(angle + seed) * 0.4 + Math.cos(angle * 1.5) * 0.3 + 0.5).toFloat()
            norm.coerceIn(0.15f, 0.95f)
        }
    }

    override fun onDestroy() {
        progressTrackingJob?.cancel()
        audioEngineManager.detachAudioSession()
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        instance = null
        super.onDestroy()
        Log.d(TAG, "AudioPlaybackService destroyed.")
    }

    companion object {
        private const val TAG = "AudioPlaybackService"
        private const val NOTIFICATION_CHANNEL_ID = "audio_playback_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: AudioPlaybackService? = null
            private set
    }
}
