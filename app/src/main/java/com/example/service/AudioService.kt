package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
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
import kotlin.math.pow

/**
 * Control interface provided to clients via [AudioServiceBinder]
 * to inspect and control playback and attach DSP audio effects.
 */
interface AudioControlInterface {
    val playbackState: StateFlow<PlaybackState>
    val player: ExoPlayer?
    fun getAudioSessionId(): Int
    fun attachAudioEffects(equalizer: Equalizer? = null, bassBoost: BassBoost? = null, virtualizer: Virtualizer? = null)
    fun getAudioEngineManager(): AudioEngineManager
    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int = 0)
    fun playTrack(track: AudioTrack)
    fun togglePlayPause()
    fun playNextTrack()
    fun playPreviousTrack()
    fun seekTo(positionMs: Long)
    fun setSpeedAndPitch(speed: Float, pitch: Float)
    fun setPitch(pitch: Float)
    fun setPitchSemitones(semitones: Int)
    fun setPlaybackSpeed(speed: Float)
    fun resetPitch()
    fun resetPlaybackSpeed()
    fun getPitch(): Float
    fun getPlaybackSpeed(): Float
    fun getPlaylistManager(): com.example.data.PlaylistManager
    fun enqueueNext(track: AudioTrack)
    fun enqueueTail(track: AudioTrack)
    fun toggleShuffle()
    fun toggleRepeat()
    fun setPreAmpGain(gainDb: Float)
    fun getPreAmpGain(): Float
    fun resetPreAmpGain()
}

/**
 * Core background Audio Service inheriting from AndroidX Media3 [MediaSessionService].
 * Initializes ExoPlayer with high-fidelity audio attributes (music content type, media usage),
 * maintains media session, and exposes [AudioControlInterface] via local Binder.
 */
@UnstableApi
open class AudioService : MediaSessionService(), AudioControlInterface {

    override var player: ExoPlayer? = null
    protected var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackingJob: Job? = null

    private val audioEngineManager = AudioEngineManager.getInstance()

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playlist = mutableListOf<AudioTrack>()
    private var currentTrackIndex = -1

    /**
     * Binder implementation exposing [AudioControlInterface].
     */
    inner class AudioServiceBinder : Binder() {
        fun getService(): AudioService = this@AudioService
        fun getController(): AudioControlInterface = this@AudioService
    }

    private val binder = AudioServiceBinder()

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
        initializeHighFidelityPlayer()
        initializeMediaSession()
        startProgressTracking()
        Log.d(TAG, "AudioService initialized with high-fidelity audio pipeline.")
    }

    /**
     * Configures ExoPlayer with high-fidelity audio attributes for optimal fidelity and low latency.
     */
    private fun initializeHighFidelityPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles audio focus internally
            .setHandleAudioBecomingNoisy(true)        // Pauses automatically on headphone disconnect
            .setWakeMode(C.WAKE_MODE_LOCAL)           // Prevents CPU sleep during playback
            .build()

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                Log.d(TAG, "AudioSessionId changed: $audioSessionId")
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
                        currentPositionMs = 0L,
                        errorMessage = null
                    )
                }
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e(TAG, "ExoPlayer playback error: ${error.message} (code: ${error.errorCode})", error)
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = "Playback error: ${error.localizedMessage ?: "Source unavailable"}"
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

        mediaSession?.release()
        val sessionId = "AudioPulseSession_${System.identityHashCode(this)}"
        mediaSession = MediaSession.Builder(this, currentExo)
            .setId(sessionId)
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

    // --- AudioControlInterface Implementations ---

    override fun getAudioSessionId(): Int {
        val current = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        return if (current != C.AUDIO_SESSION_ID_UNSET) current else 0
    }

    override fun attachAudioEffects(equalizer: Equalizer?, bassBoost: BassBoost?, virtualizer: Virtualizer?) {
        val sessionId = getAudioSessionId()
        val currentExo = player
        if (sessionId != 0 && currentExo != null) {
            audioEngineManager.attachAudioSession(sessionId, currentExo)
        }
    }

    override fun getAudioEngineManager(): AudioEngineManager = audioEngineManager

    private val playlistManager = com.example.data.PlaylistManager()

    override fun getPlaylistManager(): com.example.data.PlaylistManager = playlistManager

    override fun enqueueNext(track: AudioTrack) {
        playlistManager.enqueueNext(track)
    }

    override fun enqueueTail(track: AudioTrack) {
        playlistManager.enqueueTail(track)
    }

    override fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        playlistManager.setQueue(tracks, startIndex)
        val selected = tracks.getOrNull(startIndex) ?: tracks.first()
        playTrackInternal(selected)
    }

    override fun playTrack(track: AudioTrack) {
        playlistManager.playTrack(track)
        playTrackInternal(track)
    }

    private fun playTrackInternal(track: AudioTrack) {
        val exo = player ?: return

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

        val syntheticAmplitudes = generateWaveformSample(track.durationMs)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                waveformAmplitudes = syntheticAmplitudes,
                errorMessage = null
            )
        }

        updateNotification()
    }

    override fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
    }

    override fun playNextTrack() {
        val next = playlistManager.advanceToNext()
        if (next != null) {
            playTrackInternal(next)
        }
    }

    override fun playPreviousTrack() {
        val exo = player ?: return
        if (exo.currentPosition > 3000L) {
            exo.seekTo(0L)
            return
        }
        val prev = playlistManager.retreatToPrevious()
        if (prev != null) {
            playTrackInternal(prev)
        }
    }

    override fun seekTo(positionMs: Long) {
        val exo = player ?: return
        exo.seekTo(positionMs.coerceIn(0L, exo.duration.coerceAtLeast(0L)))
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    override fun setSpeedAndPitch(speed: Float, pitch: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        player?.playbackParameters = PlaybackParameters(clampedSpeed, clampedPitch)
        audioEngineManager.setTempo(clampedSpeed)
        audioEngineManager.setPitch(clampedPitch)
        _playbackState.update { it.copy(playbackSpeed = clampedSpeed) }
    }

    override fun setPitch(pitch: Float) {
        val exo = player ?: return
        val currentSpeed = exo.playbackParameters.speed
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        exo.playbackParameters = PlaybackParameters(currentSpeed, clampedPitch)
        audioEngineManager.setPitch(clampedPitch)
        Log.d(TAG, "Pitch shifted to $clampedPitch (tempo preserved at $currentSpeed)")
    }

    override fun setPitchSemitones(semitones: Int) {
        val exo = player ?: return
        val clampedSemitones = semitones.coerceIn(-12, 12)
        val multiplier = 2.0.pow(clampedSemitones.toDouble() / 12.0).toFloat().coerceIn(0.5f, 2.0f)
        val currentSpeed = exo.playbackParameters.speed
        exo.playbackParameters = PlaybackParameters(currentSpeed, multiplier)
        audioEngineManager.setPitchSemitones(clampedSemitones)
        Log.d(TAG, "Pitch shifted by $clampedSemitones semitones (ratio: $multiplier, tempo preserved at $currentSpeed)")
    }

    override fun setPlaybackSpeed(speed: Float) {
        val exo = player ?: return
        val currentPitch = exo.playbackParameters.pitch
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
        exo.playbackParameters = PlaybackParameters(clampedSpeed, currentPitch)
        audioEngineManager.setTempo(clampedSpeed)
        _playbackState.update { it.copy(playbackSpeed = clampedSpeed) }
        Log.d(TAG, "Playback speed updated to $clampedSpeed (pitch preserved at $currentPitch)")
    }

    override fun resetPitch() {
        setPitchSemitones(0)
    }

    override fun resetPlaybackSpeed() {
        setPlaybackSpeed(1.0f)
    }

    override fun getPitch(): Float {
        return player?.playbackParameters?.pitch ?: 1.0f
    }

    override fun getPlaybackSpeed(): Float {
        return player?.playbackParameters?.speed ?: 1.0f
    }

    override fun setPreAmpGain(gainDb: Float) {
        audioEngineManager.setPreAmpGain(gainDb)
    }

    override fun getPreAmpGain(): Float {
        return audioEngineManager.engineState.value.preAmpGainDb
    }

    override fun resetPreAmpGain() {
        audioEngineManager.resetPreAmpGain()
    }

    override fun toggleShuffle() {
        val isShuffle = playlistManager.toggleShuffle()
        _playbackState.update { it.copy(isShuffleActive = isShuffle) }
    }

    override fun toggleRepeat() {
        val exo = player ?: return
        val nextMode = playlistManager.toggleRepeat()
        when (nextMode) {
            com.example.data.RepeatMode.OFF -> exo.repeatMode = Player.REPEAT_MODE_OFF
            com.example.data.RepeatMode.ALL -> exo.repeatMode = Player.REPEAT_MODE_ALL
            com.example.data.RepeatMode.ONE -> exo.repeatMode = Player.REPEAT_MODE_ONE
        }
        _playbackState.update { it.copy(repeatMode = nextMode.ordinal) }
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
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
        Log.d(TAG, "AudioService destroyed.")
    }

    companion object {
        private const val TAG = "AudioService"
        private const val NOTIFICATION_CHANNEL_ID = "audio_playback_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var activeInstance: AudioService? = null
            private set
    }
}
