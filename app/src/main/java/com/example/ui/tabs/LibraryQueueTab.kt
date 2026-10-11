package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QueueState
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import com.example.ui.theme.AmberAnalog
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardSurface
import com.example.ui.theme.StudioDeckSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LibraryQueueTab(
    allTracks: List<AudioTrack>,
    queueState: QueueState,
    playbackState: PlaybackState,
    onTrackClick: (AudioTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onEnqueueNext: (AudioTrack) -> Unit,
    onEnqueueTail: (AudioTrack) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onAddToPlaylistClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Library, 1 = Queue

    val filteredTracks = remember(allTracks, searchQuery) {
        if (searchQuery.isBlank()) allTracks
        else allTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, albums...", fontSize = 13.sp, color = TextMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = StudioBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = StudioDeckSurface,
                unfocusedContainerColor = StudioDeckSurface
            ),
            modifier = Modifier.fillMaxWidth().testTag("library_search_input")
        )

        // Sub-Tab Switcher: All Tracks vs Up Next Queue
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = StudioDeckSurface,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = NeonCyan
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("ALL SONGS (${filteredTracks.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("QUEUE (${queueState.queue.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        if (selectedSubTab == 0) {
            // Action Buttons: Play All & Shuffle All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).testTag("play_all_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PLAY ALL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onShuffleAll,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAnalog),
                    modifier = Modifier.weight(1f).testTag("shuffle_all_button")
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHUFFLE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // All Songs List
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("library_songs_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredTracks) { _, track ->
                    val isCurrent = playbackState.currentTrack?.id == track.id
                    TrackRowItem(
                        track = track,
                        isCurrentPlaying = isCurrent,
                        onClick = { onTrackClick(track) },
                        onEnqueueNext = { onEnqueueNext(track) },
                        onEnqueueTail = { onEnqueueTail(track) },
                        onAddToPlaylist = { onAddToPlaylistClick(track) }
                    )
                }
            }
        } else {
            // Queue View Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPCOMING QUEUE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                if (queueState.queue.isNotEmpty()) {
                    Button(
                        onClick = onClearQueue,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCardSurface, contentColor = PeakCoral),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp).testTag("clear_queue_button")
                    ) {
                        Text("CLEAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Queue Items
            if (queueState.queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Playback queue is empty",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("active_queue_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queueState.queue) { index, track ->
                        val isPlaying = index == queueState.currentIndex
                        QueueRowItem(
                            index = index,
                            track = track,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) },
                            onRemove = { onRemoveFromQueue(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRowItem(
    track: AudioTrack,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit,
    onEnqueueNext: () -> Unit,
    onEnqueueTail: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("track_row_${track.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlaying) StudioCardSurface else StudioDeckSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrentPlaying) NeonCyan else StudioCardSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrentPlaying) ObsidianBg else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentPlaying) NeonCyan else TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${track.artist} • ${track.durationFormatted}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = track.audioQualityBadge,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AmberAnalog
                    )
                }
            }

            // Quick Enqueue Next
            IconButton(
                onClick = onEnqueueNext,
                modifier = Modifier.size(32.dp).testTag("enqueue_next_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Play Next",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Add to Playlist
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(32.dp).testTag("add_to_playlist_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to Playlist",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun QueueRowItem(
    index: Int,
    track: AudioTrack,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("queue_row_$index"),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) StudioCardSurface else StudioDeckSurface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isPlaying) NeonCyan else TextMuted,
                modifier = Modifier.width(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 13.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPlaying) NeonCyan else TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${track.artist} • ${track.durationFormatted}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp).testTag("remove_queue_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
