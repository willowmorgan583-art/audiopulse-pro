package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioEngineState
import com.example.model.EqualizerBand
import com.example.model.EqualizerPreset
import com.example.ui.theme.AmberAnalog
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PeakCoral
import com.example.ui.theme.StudioCardSurface
import com.example.ui.theme.StudioDeckSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EqualizerTab(
    engineState: AudioEngineState,
    onToggleEq: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
    onOpenSavePreset: () -> Unit,
    onExportPresets: () -> Unit,
    onImportPresets: () -> Unit,
    onDeleteUserPreset: (String) -> Unit,
    onBassChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Equalizer Main Deck
        Card(
            modifier = Modifier.fillMaxWidth().testTag("eq_tab_card"),
            colors = CardDefaults.cardColors(containerColor = StudioDeckSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "10-BAND GRAPHIC EQ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenSavePreset,
                            modifier = Modifier.size(32.dp).testTag("eq_save_preset")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Save Preset",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onReset,
                            modifier = Modifier.size(32.dp).testTag("eq_reset_flat")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Flat",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = engineState.isEqEnabled,
                            onCheckedChange = onToggleEq,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = NeonCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = StudioCardSurface
                            ),
                            modifier = Modifier.testTag("eq_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preset Header with Export & Import Action Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE: ${engineState.activePresetName.uppercase()}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AmberAnalog
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StudioCardSurface)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            androidx.compose.foundation.text.ClickableText(
                                text = androidx.compose.ui.text.AnnotatedString("EXPORT"),
                                onClick = { onExportPresets() },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                ),
                                modifier = Modifier.testTag("eq_export_presets_btn")
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StudioCardSurface)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            androidx.compose.foundation.text.ClickableText(
                                text = androidx.compose.ui.text.AnnotatedString("IMPORT"),
                                onClick = { onImportPresets() },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAnalog
                                ),
                                modifier = Modifier.testTag("eq_import_presets_btn")
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(engineState.allPresets) { preset ->
                        val isSelected = engineState.activePresetName.equals(preset.name, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonCyan else StudioCardSurface)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (preset.isUserPreset) "★ ${preset.name}" else preset.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ObsidianBg else TextSecondary,
                                modifier = Modifier.testTag("eq_chip_${preset.name.lowercase().replace(" ", "_")}")
                            )
                            if (preset.isUserPreset) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onDeleteUserPreset(preset.name) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Preset",
                                        tint = if (isSelected) ObsidianBg else PeakCoral,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 10-Band Sliders
                Text(
                    text = "OCTAVE FREQUENCY SLIDERS (-15 dB to +15 dB)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                engineState.bands.forEach { band ->
                    BandRow(
                        band = band,
                        isEnabled = engineState.isEqEnabled,
                        onGainChange = { gain -> onBandChange(band.index, gain) }
                    )
                }
            }
        }

        // DSP Acoustic Knobs (Bass Boost & 3D Virtualizer)
        Card(
            modifier = Modifier.fillMaxWidth().testTag("eq_dsp_knobs"),
            colors = CardDefaults.cardColors(containerColor = StudioDeckSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DSP ACOUSTIC ENHANCEMENT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bass Boost
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "SUB BASS BOOST", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Text(
                        text = "${engineState.bassBoostStrength / 10}%",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = engineState.bassBoostStrength.toFloat(),
                    onValueChange = { onBassChange(it.toInt()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = StudioCardSurface
                    ),
                    modifier = Modifier.testTag("eq_bass_boost_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Virtualizer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "3D SOUNDSTAGE VIRTUALIZER", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    Text(
                        text = "${engineState.virtualizerStrength / 10}%",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricViolet,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = engineState.virtualizerStrength.toFloat(),
                    onValueChange = { onVirtualizerChange(it.toInt()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricViolet,
                        activeTrackColor = ElectricViolet,
                        inactiveTrackColor = StudioCardSurface
                    ),
                    modifier = Modifier.testTag("eq_virtualizer_slider")
                )
            }
        }
    }
}

@Composable
private fun BandRow(
    band: EqualizerBand,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = band.label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isEnabled) TextPrimary else TextMuted,
            modifier = Modifier.width(46.dp)
        )

        Slider(
            value = band.gainDb,
            onValueChange = onGainChange,
            valueRange = -15f..15f,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = StudioCardSurface,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = StudioCardSurface
            ),
            modifier = Modifier.weight(1f).testTag("slider_band_${band.index}")
        )

        Text(
            text = "%+.1fdB".format(band.gainDb),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = if (band.gainDb > 0) AmberAnalog else TextSecondary,
            modifier = Modifier.width(54.dp)
        )
    }
}
