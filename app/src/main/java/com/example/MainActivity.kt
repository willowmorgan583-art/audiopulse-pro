package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.data.MediaRepository
import com.example.data.QueueState
import com.example.data.local.PlaylistEntity
import com.example.engine.AudioEngineManager
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import com.example.service.AudioControlInterface
import com.example.service.AudioService
import com.example.ui.components.MiniPlayerBar
import com.example.ui.tabs.EqualizerTab
import com.example.ui.tabs.LibraryQueueTab
import com.example.ui.tabs.PlayerDeckTab
import com.example.ui.tabs.PlaylistsTab
import com.example.ui.theme.AmberAnalog
import com.example.ui.theme.LedActive
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardSurface
import com.example.ui.theme.StudioDeckSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {

    private var audioController: AudioControlInterface? = null
    private var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioService.AudioServiceBinder
            audioController = binder?.getController()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioController = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceIntent = Intent(this, AudioService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            MyApplicationTheme {
                AudioPulseMainScreen(
                    audioController = audioController,
                    isServiceBound = isBound
                )
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun AudioPulseMainScreen(
    audioController: AudioControlInterface?,
    isServiceBound: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val audioEngine = remember { AudioEngineManager.getInstance() }
    val engineState by audioEngine.engineState.collectAsState()
    val playbackState = audioController?.playbackState?.collectAsState()?.value ?: PlaybackState()

    val mediaRepository = remember { MediaRepository.getInstance(context) }
    val cachedTracks by mediaRepository.tracksFlow.collectAsState(initial = emptyList())
    val playlists by mediaRepository.playlistsFlow.collectAsState(initial = emptyList())
    val isSyncing by mediaRepository.isSyncing.collectAsState()

    val playlistManager = audioController?.getPlaylistManager() ?: mediaRepository.playlistManager
    val queueState by playlistManager.queueState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Deck, 1 = EQ, 2 = Library, 3 = Playlists

    // Dialog States
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    var showExportPresetDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var showImportPresetDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }

    LaunchedEffect(Unit) {
        audioEngine.loadUserPresets(context)
        mediaRepository.syncLocalMedia()
    }

    LaunchedEffect(isServiceBound, cachedTracks) {
        if (isServiceBound && audioController != null && playbackState.currentTrack == null && cachedTracks.isNotEmpty()) {
            audioController.setPlaylistAndPlay(cachedTracks, 0)
        }
    }

    LaunchedEffect(playbackState.errorMessage) {
        playbackState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(message = error)
        }
    }

    // Save Custom EQ Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("SAVE CUSTOM EQ PRESET", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g. Sennheiser Warmth") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("preset_input_dialog")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            audioEngine.saveUserPreset(context, presetNameInput.trim(), engineState.bands.map { it.gainDb })
                            presetNameInput = ""
                            showSavePresetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = StudioCardSurface
        )
    }

    // Export Presets Dialog
    if (showExportPresetDialog) {
        AlertDialog(
            onDismissRequest = { showExportPresetDialog = false },
            title = { Text("EXPORT USER EQ PRESETS", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column {
                    Text(
                        text = if (exportedJsonText == "[]" || exportedJsonText.isBlank()) {
                            "No custom user presets to export. Save a preset first!"
                        } else {
                            "Copy the JSON below or save it as a backup:"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("export_presets_json_text")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("EQ Presets", exportedJsonText)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Presets JSON copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        showExportPresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg)
                ) {
                    Text("Copy to Clipboard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPresetDialog = false }) { Text("Close", color = TextSecondary) }
            },
            containerColor = StudioCardSurface
        )
    }

    // Import Presets Dialog
    if (showImportPresetDialog) {
        AlertDialog(
            onDismissRequest = { showImportPresetDialog = false },
            title = { Text("IMPORT USER EQ PRESETS", color = AmberAnalog, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column {
                    Text(
                        text = "Paste your EQ Presets JSON array or object:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = {
                            importJsonInput = it
                            importStatusMessage = null
                        },
                        placeholder = { Text("[{\"name\": \"Bass Monster\", \"gains\": [8, 6, 4, 2, 0, 0, 1, 3, 5, 6]}]", fontSize = 10.sp) },
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberAnalog,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("import_presets_json_input")
                    )
                    importStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = msg, color = PeakCoral, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = audioEngine.importPresetsJson(context, importJsonInput)
                        if (result.isSuccess) {
                            val count = result.getOrNull() ?: 0
                            android.widget.Toast.makeText(context, "Successfully imported $count preset(s)!", android.widget.Toast.LENGTH_SHORT).show()
                            showImportPresetDialog = false
                        } else {
                            importStatusMessage = "Invalid format: ${result.exceptionOrNull()?.message ?: "Check syntax"}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAnalog, contentColor = ObsidianBg)
                ) {
                    Text("Import & Merge", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPresetDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = StudioCardSurface
        )
    }

    // Create New Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("CREATE NEW PLAYLIST", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. Gym Workout, Late Night") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            scope.launch { mediaRepository.createPlaylist(playlistNameInput.trim()) }
                            playlistNameInput = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = StudioCardSurface
        )
    }

    // Add Track to Playlist Picker Dialog
    trackToAddToPlaylist?.let { targetTrack ->
        AlertDialog(
            onDismissRequest = { trackToAddToPlaylist = null },
            title = { Text("ADD TO PLAYLIST", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column {
                    Text("Select destination playlist for '${targetTrack.title}':", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.size(10.dp))
                    if (playlists.isEmpty()) {
                        Text("No playlists found. Create one first!", color = TextMuted, fontSize = 12.sp)
                    } else {
                        playlists.forEach { p ->
                            Button(
                                onClick = {
                                    scope.launch {
                                        mediaRepository.addTrackToPlaylist(p.id, targetTrack.id)
                                        trackToAddToPlaylist = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioDeckSurface, contentColor = TextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(p.name, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { trackToAddToPlaylist = null }) { Text("Close", color = TextSecondary) }
            },
            containerColor = StudioCardSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (engineState.isHardwareEqAttached) LedActive else AmberAnalog)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUDIOPULSE PRO",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontSize = 17.sp,
                            color = NeonCyan
                        )
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isSyncing) "SYNCING..." else "${cachedTracks.size} TRACKS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSyncing) AmberAnalog else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StudioCardSurface)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        IconButton(
                            onClick = { scope.launch { mediaRepository.syncLocalMedia() } },
                            modifier = Modifier.size(36.dp).testTag("sync_media_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Resync", tint = NeonCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg)
            )
        },
        bottomBar = {
            Column {
                // Mini Player Bar (visible on all tabs when track is loaded, except Tab 0)
                if (selectedTab != 0 && playbackState.currentTrack != null) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        onBarClick = { selectedTab = 0 },
                        onPlayPause = { audioController?.togglePlayPause() },
                        onNext = { audioController?.playNextTrack() }
                    )
                }

                // Studio Navigation Bar
                NavigationBar(
                    containerColor = StudioDeckSurface,
                    contentColor = NeonCyan,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Deck") },
                        label = { Text("DECK", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_deck")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer") },
                        label = { Text("EQ STUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_eq")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("LIBRARY", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_library")
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Playlists") },
                        label = { Text("PLAYLISTS", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBg,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_playlists")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> PlayerDeckTab(
                    playbackState = playbackState,
                    engineState = engineState,
                    onPlayPause = { audioController?.togglePlayPause() },
                    onNext = { audioController?.playNextTrack() },
                    onPrev = { audioController?.playPreviousTrack() },
                    onSeekTo = { audioController?.seekTo(it) },
                    onToggleShuffle = { audioController?.toggleShuffle() },
                    onToggleRepeat = { audioController?.toggleRepeat() },
                    onPitchSemitonesChange = { audioController?.setPitchSemitones(it) ?: audioEngine.setPitchSemitones(it) },
                    onResetPitch = { audioController?.resetPitch() ?: audioEngine.resetPitch() },
                    onTempoChange = { audioController?.setPlaybackSpeed(it) ?: audioEngine.setTempo(it) },
                    onResetTempo = { audioController?.resetPlaybackSpeed() ?: audioEngine.setTempo(1.0f) },
                    onPreAmpChange = { audioEngine.setPreAmpGain(it) }
                )

                1 -> EqualizerTab(
                    engineState = engineState,
                    onToggleEq = { audioEngine.setEqualizerEnabled(it) },
                    onSelectPreset = { audioEngine.applyPreset(it) },
                    onBandChange = { index, gain -> audioEngine.setBandGain(index, gain) },
                    onReset = { audioEngine.resetToFlat() },
                    onOpenSavePreset = { showSavePresetDialog = true },
                    onExportPresets = {
                        exportedJsonText = audioEngine.exportPresetsJson()
                        showExportPresetDialog = true
                    },
                    onImportPresets = {
                        importJsonInput = ""
                        importStatusMessage = null
                        showImportPresetDialog = true
                    },
                    onDeleteUserPreset = { audioEngine.deleteUserPreset(context, it) },
                    onBassChange = { audioEngine.setBassBoostStrength(it) },
                    onVirtualizerChange = { audioEngine.setVirtualizerStrength(it) }
                )

                2 -> LibraryQueueTab(
                    allTracks = cachedTracks,
                    queueState = queueState,
                    playbackState = playbackState,
                    onTrackClick = { audioController?.playTrack(it) },
                    onPlayAll = { if (cachedTracks.isNotEmpty()) audioController?.setPlaylistAndPlay(cachedTracks, 0) },
                    onShuffleAll = {
                        if (cachedTracks.isNotEmpty()) {
                            audioController?.setPlaylistAndPlay(cachedTracks, 0)
                            audioController?.toggleShuffle()
                        }
                    },
                    onEnqueueNext = { audioController?.enqueueNext(it) },
                    onEnqueueTail = { audioController?.enqueueTail(it) },
                    onRemoveFromQueue = { playlistManager.removeFromQueue(it) },
                    onClearQueue = { playlistManager.clearQueue() },
                    onAddToPlaylistClick = { trackToAddToPlaylist = it }
                )

                3 -> PlaylistsTab(
                    playlists = playlists,
                    onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                    onPlaylistClick = { playlist ->
                        scope.launch {
                            mediaRepository.getTracksForPlaylist(playlist.id).collect { tracks ->
                                if (tracks.isNotEmpty()) {
                                    audioController?.setPlaylistAndPlay(tracks, 0)
                                    selectedTab = 0
                                }
                            }
                        }
                    },
                    onDeletePlaylist = { scope.launch { mediaRepository.deletePlaylist(it) } }
                )
            }
        }
    }
}
