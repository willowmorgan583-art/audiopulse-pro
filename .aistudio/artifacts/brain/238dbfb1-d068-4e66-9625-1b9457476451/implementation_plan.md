# AudioPulse Pro — Advanced Audio Fine-Tuning & Studio Visualizer

This plan details the addition of three studio-grade fine-tuning features to **AudioPulse Pro**: ReplayGain volume normalization with pre-amp gain trim, a real-time animated frequency spectrum visualizer with peak-hold LEDs, and custom equalizer preset persistence with user naming and management.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The fine-tuning enhancements confirmed by your selections:

- **Confirmed Decision 1: Pre-Amp Gain & ReplayGain Normalization**:
  - Integrate a dedicated pre-amp trim slider (-6.0 dB to +6.0 dB) in `AudioEngineManager`.
  - Normalizes playback volume across disparate master levels, applying pre-gain before the 10-band graphic EQ stage to prevent digital clipping/distortion when large positive EQ boosts are applied.
- **Confirmed Decision 2: Frequency Spectrum Visualizer with Peak Hold LEDs**:
  - Live 32-bar frequency visualizer in the studio deck rendered on Jetpack Compose `Canvas`.
  - Driven by active playback frequency bands and waveform metrics with realistic ballistics: smooth rise, gravity decay, and floating peak-hold indicator LEDs that slowly fall.
- **Confirmed Decision 3: Custom Preset Management & Persistence**:
  - Provide a "Save Current EQ as Preset" modal dialog where users can input custom names (e.g., "Sennheiser HD600 Harmon Curve", "Car Sub Bass").
  - Persistent storage in local JSON/SharedPreferences, allowing users to select, apply, and delete custom user presets alongside built-in factory presets.

---

### 1. Overview & Core Concept

- **What It Delivers**: Transforms the player from a standard audio engine into a complete studio mixing deck with volume-leveling control, real-time spectral feedback, and custom acoustic sound profiles.
- **Target Audience**: Audiophiles and music lovers who fine-tune specific headphone frequency responses or car stereo acoustic curves and desire studio visualizer feedback.

---

### 2. User Experience & Visual Design

#### Visual Identity & Theme
- **Theme Archetype**: Cyberpunk Studio Rack / Pro Audio Hardware.
- **Palette**: Deep Obsidian (`#070A10`), Studio Slate (`#0F172A`), Neon Cyan (`#00F0FF`), Warm Amber (`#FF9F1C`), and Peak Coral (`#FF3366`).
- **Spectrum Visualizer Styling**:
  - 32 vertical spectrum columns grouped from Sub-Bass (left) to Air Treble (right).
  - Vertical neon gradient bars (Cyan to Amber to Coral clip).
  - Peak-hold horizontal ticks floating at recent crests before falling under gravity.
- **Pre-Amp Section**:
  - Studio master fader with numeric decibel readout (`+2.5 dB`, `0.0 dB`, `-3.0 dB`) and soft-clipping warning indicator.
- **Preset Manager UI**:
  - "Save Preset" action button with modal text field.
  - User presets tagged with a `USER` chip, editable and deletable.

---

### 3. Key Technical Decisions & Trade-Offs

- **Decision 1: Pre-Amp Implementation in Media3 / AudioEngineManager**
  - *Chosen Approach*: Apply gain adjustments in `AudioEngineManager` by factoring `preAmpGainDb` into the interpolated band levels and adjusting ExoPlayer volume scaling smoothly.
  - *Why*: Allows zero-latency headroom adjustments without needing external third-party C++ libraries while guaranteeing clipping protection.
- **Decision 2: 60FPS Hardware-Accelerated Canvas Spectrum Visualizer**
  - *Chosen Approach*: Implement a custom Jetpack Compose `Canvas` with frame-driven physics (`withFrameMillis` or `Animatable`) rather than heavy view recompositions.
  - *Why*: Delivers fluid, buttery 60 FPS motion with zero audio thread interference.
- **Decision 3: Local Preset Storage via SharedPreferences / JSON Serialization**
  - *Chosen Approach*: Simple, robust JSON serialization stored in private app storage.
  - *Why*: Zero database overhead, instant synchronous lookup on launch, easily extensible for export/import.

---

### 4. Technical Architecture & Component Structure

```
┌────────────────────────────────────────────────────────────────────────┐
│                          AudioPulse Pro UI                             │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │         Live Frequency Spectrum Visualizer (32 Canvas Bars)      │  │
│  │          - Bar Ballistics (Rise / Decay) + Peak Hold LEDs        │  │
│  └─────────────────────────────────┬────────────────────────────────┘  │
│                                    │                                   │
│  ┌────────────────────────┐        │        ┌───────────────────────┐  │
│  │ Pre-Amp Gain Trim      │        │        │ Custom Preset Manager │  │
│  │ (-6dB to +6dB Fader)   │        │        │ (Save / Load / Delete)│  │
│  └───────────┬────────────┘        │        └───────────┬───────────┘  │
│              └─────────────────┐   │   ┌────────────────┘              │
│                                ▼   ▼   ▼                               │
│                     ┌─────────────────────────────┐                    │
│                     │     AudioEngineManager      │                    │
│                     │ - preAmpGainDb              │                    │
│                     │ - userPresets Flow          │                    │
│                     │ - 10-Band EQ Interpolation  │                    │
│                     └──────────────┬──────────────┘                    │
│                                    │                                   │
│                                    ▼                                   │
│                     ┌─────────────────────────────┐                    │
│                     │   AudioService (Media3)     │                    │
│                     │ - ExoPlayer Audio Attributes│                    │
│                     │ - Hardware AudioFx Pipeline │                    │
│                     └─────────────────────────────┘                    │
└────────────────────────────────────────────────────────────────────────┘
```

#### Detailed File Updates:
1. `model/AudioModels.kt`:
   - Add `isUserPreset: Boolean` to `EqualizerPreset`.
   - Add `preAmpGainDb: Float` to `AudioEngineState`.
   - Add `spectrumBands: List<Float>` to `PlaybackState`.
2. `engine/AudioEngineManager.kt`:
   - Add `setPreAmpGain(gainDb: Float)`.
   - Add `saveUserPreset(name: String, gains: List<Float>)`, `deleteUserPreset(name: String)`.
   - Load saved custom presets from persistent storage on startup.
3. `ui/components/StudioVisualizer.kt`:
   - Canvas-based 32-band real-time visualizer with smooth spring decay and amber peak-hold dots.
4. `MainActivity.kt`:
   - Add Pre-Amp Gain slider deck with dB readouts.
   - Add Spectrum Visualizer below the transport controls.
   - Add "Save Current EQ" button with dialog and user preset chips.

---

### Verification Plan

- Run `compile_applet` to ensure error-free compilation.
- Run `testDebugUnitTest` to test preset persistence and pre-amp gain clamping.
- Validate fluid 60FPS rendering of spectrum visualizer in the emulator.
