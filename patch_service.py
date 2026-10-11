with open("app/src/main/java/com/example/service/AudioService.kt", "r") as f:
    content = f.read()

# Add import
old_import = "import androidx.media3.common.Player"
new_import = "import androidx.media3.common.Player\nimport androidx.media3.common.PlaybackException"

content = content.replace(old_import, new_import, 1)

# Add onPlayerError in Player.Listener
old_listener = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                _playbackState.update {
                    it.copy(
                        durationMs = exo.duration.coerceAtLeast(0L),
                        currentPositionMs = 0L
                    )
                }
                updateNotification()
            }
        })"""

new_listener = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
        })"""

assert old_listener in content, "old_listener not found"
content = content.replace(old_listener, new_listener, 1)

# Reset errorMessage in playTrackInternal
old_play_track = """        val syntheticAmplitudes = generateWaveformSample(track.durationMs)
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                waveformAmplitudes = syntheticAmplitudes
            )
        }"""

new_play_track = """        val syntheticAmplitudes = generateWaveformSample(track.durationMs)
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                waveformAmplitudes = syntheticAmplitudes,
                errorMessage = null
            )
        }"""

assert old_play_track in content, "old_play_track not found"
content = content.replace(old_play_track, new_play_track, 1)

with open("app/src/main/java/com/example/service/AudioService.kt", "w") as f:
    f.write(content)

print("AudioService.kt successfully patched!")
