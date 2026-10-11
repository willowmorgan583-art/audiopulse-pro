package com.example.data

import com.example.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections

enum class RepeatMode {
    OFF, ALL, ONE
}

data class QueueState(
    val queue: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffledIndices: List<Int> = emptyList(),
    val history: List<AudioTrack> = emptyList()
) {
    val currentTrack: AudioTrack?
        get() = queue.getOrNull(currentIndex)

    val upcomingTracks: List<AudioTrack>
        get() {
            if (queue.isEmpty() || currentIndex !in queue.indices) return emptyList()
            return if (currentIndex < queue.lastIndex) {
                queue.subList(currentIndex + 1, queue.size)
            } else {
                emptyList()
            }
        }
}

/**
 * Manages playback queue, Fisher-Yates non-repeating shuffle, and repeat modes.
 */
class PlaylistManager {

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    /**
     * Initializes the queue with a collection of tracks and starts at startIndex.
     */
    fun setQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            clearQueue()
            return
        }
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val shuffled = generateShuffledIndices(tracks.size, safeIndex)

        _queueState.update { current ->
            current.copy(
                queue = tracks,
                currentIndex = safeIndex,
                shuffledIndices = shuffled
            )
        }
    }

    /**
     * Plays a specific track: if already in queue, jumps to it; otherwise inserts at head.
     */
    fun playTrack(track: AudioTrack) {
        val currentQueue = _queueState.value.queue.toMutableList()
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }

        if (existingIndex >= 0) {
            _queueState.update { it.copy(currentIndex = existingIndex) }
        } else {
            currentQueue.add(0, track)
            val shuffled = generateShuffledIndices(currentQueue.size, 0)
            _queueState.update {
                it.copy(
                    queue = currentQueue,
                    currentIndex = 0,
                    shuffledIndices = shuffled
                )
            }
        }
    }

    /**
     * Places track immediately after the currently playing song ("Play Next").
     */
    fun enqueueNext(track: AudioTrack) {
        _queueState.update { current ->
            val updated = current.queue.toMutableList()
            val insertPos = (current.currentIndex + 1).coerceIn(0, updated.size)
            updated.add(insertPos, track)
            val shuffled = generateShuffledIndices(updated.size, current.currentIndex)
            current.copy(queue = updated, shuffledIndices = shuffled)
        }
    }

    /**
     * Appends track to the very end of the playback queue.
     */
    fun enqueueTail(track: AudioTrack) {
        _queueState.update { current ->
            val updated = current.queue.toMutableList()
            updated.add(track)
            val shuffled = generateShuffledIndices(updated.size, current.currentIndex)
            current.copy(queue = updated, shuffledIndices = shuffled)
        }
    }

    /**
     * Removes an item from the queue at the specified index.
     */
    fun removeFromQueue(index: Int) {
        _queueState.update { current ->
            if (index !in current.queue.indices) return@update current
            val updated = current.queue.toMutableList()
            updated.removeAt(index)

            val newIndex = when {
                updated.isEmpty() -> -1
                index < current.currentIndex -> current.currentIndex - 1
                index == current.currentIndex -> index.coerceAtMost(updated.lastIndex)
                else -> current.currentIndex
            }
            val shuffled = generateShuffledIndices(updated.size, newIndex)
            current.copy(queue = updated, currentIndex = newIndex, shuffledIndices = shuffled)
        }
    }

    /**
     * Reorders an item within the queue.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _queueState.update { current ->
            if (fromIndex !in current.queue.indices || toIndex !in current.queue.indices) return@update current
            val updated = current.queue.toMutableList()
            val item = updated.removeAt(fromIndex)
            updated.add(toIndex, item)

            val newCurrentIndex = when (current.currentIndex) {
                fromIndex -> toIndex
                in (fromIndex + 1)..toIndex -> current.currentIndex - 1
                in toIndex until fromIndex -> current.currentIndex + 1
                else -> current.currentIndex
            }
            current.copy(queue = updated, currentIndex = newCurrentIndex)
        }
    }

    /**
     * Clears all tracks from queue.
     */
    fun clearQueue() {
        _queueState.value = QueueState()
    }

    /**
     * Advances to next track taking into account shuffle and repeat modes.
     */
    fun advanceToNext(): AudioTrack? {
        val state = _queueState.value
        val queue = state.queue
        if (queue.isEmpty() || state.currentIndex !in queue.indices) return null

        if (state.repeatMode == RepeatMode.ONE) {
            return state.currentTrack
        }

        val nextIndex = if (state.isShuffleEnabled && state.shuffledIndices.isNotEmpty()) {
            val currentShuffledPos = state.shuffledIndices.indexOf(state.currentIndex)
            if (currentShuffledPos >= 0 && currentShuffledPos < state.shuffledIndices.lastIndex) {
                state.shuffledIndices[currentShuffledPos + 1]
            } else if (state.repeatMode == RepeatMode.ALL) {
                state.shuffledIndices.first()
            } else {
                -1
            }
        } else {
            if (state.currentIndex < queue.lastIndex) {
                state.currentIndex + 1
            } else if (state.repeatMode == RepeatMode.ALL) {
                0
            } else {
                -1
            }
        }

        if (nextIndex in queue.indices) {
            val history = state.history.toMutableList()
            state.currentTrack?.let { history.add(it) }
            _queueState.update { it.copy(currentIndex = nextIndex, history = history) }
            return queue[nextIndex]
        }
        return null
    }

    /**
     * Steps back to previous track in queue or history.
     */
    fun retreatToPrevious(): AudioTrack? {
        val state = _queueState.value
        val queue = state.queue
        if (queue.isEmpty() || state.currentIndex !in queue.indices) return null

        val prevIndex = if (state.isShuffleEnabled && state.shuffledIndices.isNotEmpty()) {
            val currentShuffledPos = state.shuffledIndices.indexOf(state.currentIndex)
            if (currentShuffledPos > 0) {
                state.shuffledIndices[currentShuffledPos - 1]
            } else if (state.repeatMode == RepeatMode.ALL) {
                state.shuffledIndices.last()
            } else {
                0
            }
        } else {
            if (state.currentIndex > 0) {
                state.currentIndex - 1
            } else if (state.repeatMode == RepeatMode.ALL) {
                queue.lastIndex
            } else {
                0
            }
        }

        if (prevIndex in queue.indices) {
            _queueState.update { it.copy(currentIndex = prevIndex) }
            return queue[prevIndex]
        }
        return null
    }

    /**
     * Toggles shuffle mode on/off.
     */
    fun toggleShuffle(): Boolean {
        val newState = !_queueState.value.isShuffleEnabled
        _queueState.update { current ->
            val shuffled = if (newState) {
                generateShuffledIndices(current.queue.size, current.currentIndex)
            } else {
                emptyList()
            }
            current.copy(isShuffleEnabled = newState, shuffledIndices = shuffled)
        }
        return newState
    }

    /**
     * Cycles repeat mode: OFF -> ALL -> ONE -> OFF.
     */
    fun toggleRepeat(): RepeatMode {
        val nextMode = when (_queueState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _queueState.update { it.copy(repeatMode = nextMode) }
        return nextMode
    }

    private fun generateShuffledIndices(size: Int, currentPlayingIndex: Int): List<Int> {
        if (size <= 1) return if (size == 1) listOf(0) else emptyList()
        val list = (0 until size).toMutableList()
        // Ensure current playing track stays first in the shuffled sequence
        list.remove(currentPlayingIndex)
        list.shuffle()
        if (currentPlayingIndex in 0 until size) {
            list.add(0, currentPlayingIndex)
        }
        return list
    }
}
