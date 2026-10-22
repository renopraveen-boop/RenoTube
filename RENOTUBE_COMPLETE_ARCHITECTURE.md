# RenoTube: Complete Architectural Breakdown & Engineering Specification

> **Target Platform:** Android TV / Google TV (Optimized for Sony BRAVIA 4K UHD, Nvidia Shield TV, Chromecast with Google TV)  
> **Package Name:** `com.renotube.tv`  
> **Language:** Kotlin 1.9.22 / Java 17  
> **UI Toolkit:** Jetpack Compose for TV (`androidx.tv:tv-material:1.0.0-alpha10`, `androidx.tv:tv-foundation:1.0.0-alpha10`)  
> **Media Engine:** AndroidX Media3 / ExoPlayer `1.2.1`  
> **Dependency Injection:** Dagger Hilt `2.51`  

---

## Table of Contents
1. [Executive Overview & Design Philosophy](#1-executive-overview--design-philosophy)
2. [Project & Module Structure](#2-project--module-structure)
3. [UI Layouts, TV Focus & Navigation System](#3-ui-layouts-tv-focus--navigation-system)
4. [Video Playback Engine (4K 60fps Zero-Dropped-Frames)](#4-video-playback-engine-4k-60fps-zero-dropped-frames)
5. [InnerTube API, PoToken & Stream Extraction Layer](#5-innertube-api-potoken--stream-extraction-layer)
6. [Ambilight & WLED Realtime Synchronization Engine](#6-ambilight--wled-realtime-synchronization-engine)
7. [SponsorBlock & Ad Elimination Pipeline](#7-sponsorblock--ad-elimination-pipeline)
8. [Watch History & YouTube Account Synchronization](#8-watch-history--youtube-account-synchronization)
9. [Embedded Web Remote Server & QR Companion Control](#9-embedded-web-remote-server--qr-companion-control)
10. [Step-by-Step Blueprint to Recreate the App](#10-step-by-step-blueprint-to-recreate-the-app)

---

## 1. Executive Overview & Design Philosophy

RenoTube is an open-source, cinema-grade YouTube client built from the ground up specifically for high-end television viewing. Standard YouTube TV apps and generic mobile ports suffer from aggressive dynamic downscaling, ad interruptions, forced telemetry, lack of ambient lighting integration, and sluggish D-Pad navigation. 

RenoTube solves this with four core engineering principles:
1. **Uncompromised 4K 60fps Performance:** Direct hardware decoding with custom PCM audio buffers and initial 50 Mbps bandwidth estimation to eliminate dropped frames and buffering delays.
2. **Cinema-Grade Ambilight:** Realtime double-buffered frame analysis streaming to local WLED / ESP32 light strips over DDP (Port 4048) or DRGB (Port 21324) matching HyperHDR physical mappings.
3. **Zero Distractions:** Complete native ad elimination plus SponsorBlock segment skipping with on-screen visual tags.
4. **Leanback Focus Engine:** Native Android TV D-Pad navigation using directional boundaries, physical long-press key release filtering, and an integrated mobile Web Remote.

---

## 2. Project & Module Structure

The project is structured as a multi-module Gradle project:

```
renotube/
├── app/                                 # Main Android TV application module
│   ├── src/main/java/com/renotube/tv/
│   │   ├── MainActivity.kt              # App entry point, TV shell, key dispatching
│   │   ├── RenoTubeApp.kt               # Application class (@HiltAndroidApp)
│   │   ├── ambilight/                   # WLED & HyperHDR ambilight processing
│   │   │   ├── AmbilightConfig.kt       # Config data class (LED counts, IP, port, physics)
│   │   │   ├── AmbilightProcessor.kt    # Black border detector, optical gamma, physics filter
│   │   │   ├── AmbilightRepository.kt   # Preferences, WLED HTTP auto-detect, watchdog
│   │   │   ├── HyperHdrBoxes.kt         # Exact 228-LED precomputed coordinate boxes
│   │   │   └── WledUdpSender.kt         # High-speed binary UDP sender (DDP & DRGB)
│   │   ├── crash/                       # Uncaught exception handler & crash reporter
│   │   ├── data/
│   │   │   ├── model/UiModels.kt        # Data classes (VideoCard, SectionRow, Metadata)
│   │   │   └── repository/
│   │   │       ├── ContentRepository.kt # Feeds (Home, Trending, Subs, Channel, History)
│   │   │       ├── MediaRepository.kt   # Stream format resolution, caching, SponsorBlock
│   │   │       └── UserHistoryRepository.kt # Local watch history, playlists, blocking
│   │   ├── di/
│   │   │   └── ServiceModule.kt         # Hilt DI provider for SmartTube services
│   │   ├── image/                       # Coil OkHttp fetcher configuration
│   │   ├── perf/                        # Realtime FPS & API latency performance tracker
│   │   ├── player/
│   │   │   ├── PlaybackQueueManager.kt  # Queue handling (Play Next, Auto-play)
│   │   │   └── RenoTubePlayer.kt        # Media3/ExoPlayer engine, audio sinks, subtitles
│   │   ├── remote/
│   │   │   ├── RemoteCommandDispatcher.kt # WeakRef bridge from HTTP to Activity key events
│   │   │   └── WebRemoteServer.kt       # Multi-threaded HTTP server (port 8080)
│   │   └── ui/
│   │       ├── components/              # VideoCard, SectionRow, HeroBanner, QrCodeView, ActionModal
│   │       ├── screens/
│   │       │   ├── home/                # HomeScreen & HomeViewModel
│   │       │   ├── player/              # PlayerScreen & PlayerViewModel
│   │       │   ├── search/              # SearchScreen & SearchViewModel
│   │       │   └── settings/            # SettingsScreen & SettingsViewModel
│   │       └── theme/                   # TV Material3 color palette & typography
│   └── build.gradle                     # App-level dependencies & build rules
├── mediaserviceinterfaces/              # Shared data contracts & service interfaces
├── youtubeapi/                          # Core YouTube InnerTube API & cipher decryption
├── sharedutils/                         # Shared utilities, OkHttp helpers, RxJava utils
├── commons-io-2.8.0/                    # Apache Commons IO
├── j2v8/                                # V8 JavaScript runtime for deciphering signatures
└── build.gradle                         # Root Gradle build script
```

### Key Version Matrix (`build.gradle`):
- `compileSdkVersion`: **34**
- `targetSdkVersion`: **34**
- `minSdkVersion`: **21** (Android 5.0 Lollipop+)
- `media3Version`: **1.2.1**
- `hiltVersion`: **2.51**
- `composeBomVersion`: **2024.02.00**
- `coilVersion`: **2.5.0**
- `kotlinVersion`: **1.9.22**
- `jvmTarget`: **17**

---

## 3. UI Layouts, TV Focus & Navigation System

The UI is built entirely in **Jetpack Compose for TV** using a two-column shell design that preserves scroll state and cursor position when navigating between menus and playback.

```
┌────────────────────────────────────────────────────────────────────────┐
│ MainTvShell (Root Box, Background: #0F0F0F)                             │
│ ┌───────────────┬────────────────────────────────────────────────────┐ │
│ │ Navigation    │ Content Viewport (FocusGroup with boundary links)  │ │
│ │ Sidebar       │                                                    │ │
│ │ (Collapsed/   │  • HomeScreen: Hero Banner + Category Video Rows   │ │
│ │  Expanded)    │  • SearchScreen: Virtual Keyboard / Voice + Grid   │ │
│ │               │  • SettingsScreen: Ambilight, Audio, SponsorBlock  │ │
│ └───────────────┴────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Fullscreen Player Overlay (Conditional: activeVideoId != null)     │ │
│ │  • SurfaceView / TextureView (Hardware Video Plane)                │ │
│ │  • Player OSD (Seekbar, Controls, Badges, Quick Settings Sheet)    │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.1. D-Pad Focus Engine & Boundary Traversal
- **Problem on TV:** Android TV Compose has a known focus loss bug when traversing between lazy rows and sidebars.
- **Solution:** In `MainActivity.kt`, the sidebar exposes a `FocusRequester` (`sidebarActiveItemRequester`). The main content viewport sets `.focusProperties { left = sidebarActiveItemRequester }`.
- When the user presses `DPAD_LEFT` on the leftmost card of any row, focus immediately jumps to the active item in the sidebar instead of getting trapped or dropped.
- Pressing `DPAD_RIGHT` on the rightmost card clamps within the row (`return@onKeyEvent true`), preventing focus from jumping unexpectedly to unrelated off-screen views.

### 3.2. Long-Press vs. Click Disambiguation Algorithm
In standard Android TV implementations, listening for long clicks often causes race conditions where releasing the button sends an `ACTION_UP` that immediately clicks the first item in the newly opened modal dialog.
RenoTube implements an explicit state machine in `VideoCard.kt`:

```kotlin
// 1. On KeyDown (repeatCount == 0):
keyDownTimeMs = native.downTime
isLongPressTriggered = false
longPressJob = coroutineScope.launch {
    delay(450) // Threshold for long press
    isLongPressTriggered = true
}

// 2. On KeyDown (repeatCount > 0):
// Hardware auto-repeats while held
val duration = System.currentTimeMillis() - keyDownTimeMs
if (duration >= 400 || native.repeatCount > 0) {
    isLongPressTriggered = true
}

// 3. On KeyUp (Button released):
longPressJob?.cancel()
val wasLong = isLongPressTriggered || (duration >= 400)
isLongPressTriggered = false

if (wasLong && onLongClick != null) {
    // ONLY invoke modal on release!
    onLongClick.invoke()
} else {
    // Short click: play video immediately
    onClick.invoke()
}
```

### 3.3. Video Action Modal
Triggered by long-pressing any video card:
- **Play Next:** Inserts video at index 0 of `PlaybackQueueManager`.
- **Add to Watch Later:** Appends to local JSON playlist.
- **Don't Recommend This Video:** Adds `videoId` to `user_blocked_content.json`.
- **Don't Recommend Channel:** Adds channel name to blocked list and hides all matching cards instantly.

### 3.4. Player Scrubbing Seekbar
The seekbar in `PlayerScreen.kt` supports both D-Pad stepping and physical cursor dragging:
- **Tap Gestures:** `detectTapGestures` calculates touch fraction `offset.x / fullWidth` and seeks immediately.
- **Drag Gestures:** `detectDragGestures` with a 90ms throttling window (`lastLiveSeekTime`) updates the red scrubber handle in realtime and updates playback position smoothly without flooding ExoPlayer seek queues.
- **SponsorBlock Bands:** Canvas paints color-coded intervals directly over the track:
  - Green (`#00D166`): Sponsor segment
  - Cyan (`#00A8FF`): Self-promo / interaction
  - Yellow (`#FFCC00`): Intro / recap / music offtopic

---

## 4. Video Playback Engine (4K 60fps Zero-Dropped-Frames)

The media engine in `RenoTubePlayer.kt` uses **AndroidX Media3 ExoPlayer 1.2.1** with custom renderers, load controls, and audio sinks designed for Sony BRAVIA televisions.

### 4.1. Unconstrained 4K Pipeline Configuration
```kotlin
// 1. Instant 50 Mbps bandwidth estimation (no starting in 360p/720p)
val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
    .setInitialBitrateEstimate(50_000_000L)
    .build()

// 2. Custom AudioSink for Bluetooth A2DP & TV speakers
val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioSink(...): AudioSink? {
        return DefaultAudioSink.Builder(context)
            .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
            .setEnableFloatOutput(false) // 16-bit PCM for universal TV/Bluetooth compatibility
            .setAudioTrackBufferSizeProvider { minBufferSize, _, _, pcmFrameSize, sampleRate, _, _ ->
                // Ensure at least 300ms buffer headroom to prevent Bluetooth audio underruns
                val targetBytes = ((sampleRate.toLong() * pcmFrameSize * 300_000L) / 1_000_000L).toInt()
                maxOf(minBufferSize * 2, targetBytes)
            }
            .build()
    }
}.apply {
    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    setEnableDecoderFallback(true)
}

// 3. Low-latency LoadControl
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 15_000,
        /* maxBufferMs = */ 30_000,
        /* bufferForPlaybackMs = */ 2_500,
        /* bufferForPlaybackAfterRebufferMs = */ 4_000
    )
    .setBackBuffer(5_000, /* retainBackBufferFromKeyframe = */ true)
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()
```

### 4.2. Stream Selection & Playback Strategy
Playback executes across three fallback mechanisms in order:
1. **DASH MPD (Adaptive Multi-Rate):**
   - InnerTube delivers raw audio and video streams (VP9 / AV1 / Opus).
   - `formatInfo.createMpdStream()` constructs a standard dynamic XML MPD.
   - The MPD is written to `context.cacheDir/manifest_<videoId>.mpd` and passed to `DashMediaSource.Factory`.
   - Allows ExoPlayer to seamlessly select the highest 4K 60fps video stream independently from the highest 5.1/Opus audio stream.
2. **HLS Manifest:**
   - Fallback for live streams or videos where DASH MPD is unavailable.
3. **Progressive MP4:**
   - Fallback for legacy streams or restricted devices.

### 4.3. Voice Clarity / Dialogue Boost Mode
When enabled, RenoTube hooks the active `audioSessionId` using `android.media.audiofx.LoudnessEnhancer`:
```kotlin
val enhancer = LoudnessEnhancer(exoPlayer.audioSessionId)
enhancer.setTargetGain(600) // +6 dB vocal clarity boost
enhancer.enabled = true
```

### 4.4. Audio Track Language Deduplication
YouTube audio tracks often contain redundant labels like `en-us (original)` and `en (dubbed)`. RenoTube parses language tags, ISO 639 codes, and display strings to present clean options like `English [Original] • Stereo` or `Spanish (Dubbed)` and defaults automatically to English.

---

## 5. InnerTube API, PoToken & Stream Extraction Layer

RenoTube interfaces with YouTube servers using the **SmartTube Core Service Architecture** (`mediaserviceinterfaces` and `youtubeapi`).

```
┌────────────────────────────────────────────────────────┐
│ RenoTube MediaRepository / ContentRepository           │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ YouTubeServiceManager / YouTubeMediaItemService        │
├──────────────────────────┬─────────────────────────────┤
│  Client Data Layer       │  Stream Decryption Layer    │
│  • TV Client (stbeta)    │  • J2V8 JavaScript Engine   │
│  • Android Client        │  • Signature Cipher Decrypt │
│  • Web / iOS Fallbacks   │  • n-parameter Descrambler  │
├──────────────────────────┴─────────────────────────────┤
│  PoToken Provider (Proof of Origin)                    │
│  • Generates session tokens to defeat bot detection    │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTPS (cronet / okhttp3)
┌──────────────────────────▼─────────────────────────────┐
│ YouTube InnerTube Endpoints:                           │
│  • /youtubei/v1/player                                 │
│  • /youtubei/v1/browse                                 │
│  • /youtubei/v1/next                                   │
│  • /youtubei/v1/search                                 │
│  • /youtubei/v1/feedback                               │
└────────────────────────────────────────────────────────┘
```

### 5.1. Proof of Origin (PoToken)
YouTube requires a `po_token` (Proof of Origin) for TV and mobile endpoints to verify that requests originate from a legitimate client environment. The `potokennp2` package computes the challenge response and attaches it as query parameters and HTTP headers (`X-YouTube-Po-Token`), preventing `HTTP 403 Forbidden` errors.

### 5.2. Cipher Decryption via V8 Runtime
When YouTube video streams contain scrambled signature ciphers (`s` and `sp` parameters):
1. InnerTube downloads the active base player JavaScript file (`base.js`).
2. Regex extracts the signature transform function (array reverse, slice, swap).
3. The embedded `J2V8` C++ runtime executes the transformation directly in-memory, returning the deciphered stream URL with negligible CPU overhead.

---

## 6. Ambilight & WLED Realtime Synchronization Engine

RenoTube features a fully integrated, zero-latency ambilight engine that replaces external capture cards by sampling frames directly from the hardware video surface and streaming to **WLED** or any **HyperHDR** compatible UDP receiver.

### 6.1. Hardware Frame Sampling Loop
In `PlayerScreen.kt`, a decoupled `HandlerThread("AmbilightSampler")` samples frames at ~20 Hz using double-buffered 64x36 bitmaps:
```kotlin
val bufferA = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
val bufferB = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
// On SurfaceView:
PixelCopy.request(surfaceView, activeBuffer, { result ->
    if (result == PixelCopy.SUCCESS) {
        processor.processFrame(activeBuffer, config)
    }
}, copyHandler)
// On TextureView:
textureView.getBitmap(activeBuffer)
processor.processFrame(activeBuffer, config)
```

### 6.2. Physical 228-LED Mapping (HyperHDR Standard)
The LED strip layout matches the physical installation exactly (**228 LEDs total**):
- **Left (40 LEDs):** LEDs `0..38` (bottom $\to$ top) and LED `227` (bottom-left corner).
- **Top (71 LEDs):** LEDs `39..109` (left $\to$ right).
- **Right (41 LEDs):** LEDs `110..150` (top $\to$ bottom).
- **Bottom (76 LEDs):** LEDs `151..226` (right $\to$ left).

Precomputed normalized coordinates in `HyperHdrBoxes.kt`:
```kotlin
data class LedBox(val hmin: Float, val hmax: Float, val vmin: Float, val vmax: Float)
```

### 6.3. Black Border Detection (`BlackBorderDetector`)
When playing 21:9 or letterboxed content, black bars would normally cause the top and bottom LEDs to turn off or sample dark grey. RenoTube scans the top/bottom 30% of pixels for luminance:
$$\text{Luma} = 0.2126 \cdot R + 0.7152 \cdot G + 0.0722 \cdot B$$
If border lines have $\text{Luma} < 8.0$ across 4 consecutive frames, the sampling coordinate box dynamically contracts inward to sample the actual video content.

### 6.4. Optical Gamma 1.5 & Backlight Cutoff
- **Optical Gamma Table:** Precomputed 256-element integer table matching HyperHDR:
  $$\text{Gamma}(x) = \text{round}\left( \left(\frac{x}{255}\right)^{1.5} \times 255 \right)$$
- **Backlight Threshold:** If $\text{Luma} < 3.5$ or all channels $< 4$, output snaps to pure $0$ (`#000000`). This ensures pitch-black stage curtains or letterbox bars turn the physical LEDs completely off.

### 6.5. Spring-Damper Physics Filter (`HybridRgbInterpolator`)
To eliminate LED flicker and provide smooth transitions between scene cuts:
- **Second-Order Damped Oscillator:**
  $$a = k \cdot (target - current) - d \cdot v$$
  Where $k = 150 \times \text{smoothMult}$ (spring stiffness) and $d = 26 \times (0.8 + 0.2 \cdot \text{smoothMult})$ (damping factor).
- **Integration:** 2 sub-steps of semi-implicit Euler integration per frame.
- **Deadband Filter:** If $|target - current| < 2.0$ and $|v| < 1.0$, $v = 0$ (eliminates quantization hum).
- **Fast Attack:** On sudden scene cuts ($|target - current| > 80$), damping is lowered to snap instantly.

### 6.6. Network Protocols: DDP vs. DRGB
`WledUdpSender.kt` formats binary UDP datagrams based on port:

#### DDP Protocol (Port 4048 - Recommended)
10-byte header:
```
Byte 0:    0x41 (Push flag set, Version 1)
Byte 1:    Sequence number (1..255)
Byte 2:    0x01 (Data type: 8-bit RGB)
Byte 3:    0x01 (Device ID)
Bytes 4-7: 0x00000000 (32-bit channel offset)
Bytes 8-9: Data length in bytes (228 * 3 = 684)
Bytes 10+: Raw RGB payload [R0, G0, B0, R1, G1, B1, ...]
```

#### DRGB Protocol (Port 21324 - Legacy)
2-byte header:
```
Byte 0:   0x02 (DRGB protocol identifier)
Byte 1:   0xFF (255s timeout lock)
Bytes 2+: Raw RGB payload [R0, G0, B0, ...]
```

### 6.7. Ambient Standby & Watchdog
- **Standby Warm White:** When paused, on home screen, or on app start, RenoTube sets LEDs to `#FFF5E6` (`RGB(255, 147, 41)`) at 20% brightness for a warm background glow.
- **Heartbeat Watchdog:** Every 15 seconds, probes `http://<ip>/json/state`. If unreachable for $> 3$ minutes, ambilight is automatically disabled to save power.

---

## 7. SponsorBlock & Ad Elimination Pipeline

### 7.1. Ad Elimination
RenoTube achieves 100% ad-free playback natively:
- InnerTube player requests specify client identities (`stbeta` / `tv`) and format preferences that exclude Google AdSense VAST/VMAP streams.
- The `DashMediaSource` parser filters out advertising interstitials from the MPD playlist entirely. No ads are buffered or decoded.

### 7.2. SponsorBlock Integration
- During video loading in `PlayerViewModel.kt`, an asynchronous task queries the SponsorBlock API:
  `GET https://sponsor.ajay.app/api/skipSegments?videoID=<id>&categories=["sponsor","intro","outro","interaction","selfpromo","preview"]`
- Segments are cached in memory as `SponsorSegmentModel(startMs, endMs, category)`.
- During the 250ms playback tick loop:
  ```kotlin
  if (sponsorBlockEnabled && !skippedKeys.contains(segmentKey)) {
      if (currentPos in segment.startMs..segment.endMs) {
          skippedKeys.add(segmentKey)
          renoTubePlayer.seekTo(segment.endMs + 50)
          showToast("Skipped ${segment.category}")
      }
  }
  ```

---

## 8. Watch History & YouTube Account Synchronization

RenoTube features a dual-path watch history system:

### 8.1. YouTube Cloud History Sync
To keep personalized home feed recommendations, subscriptions, and continue-watching rows active:
```kotlin
fun syncWatchHistoryToYouTube(posSec: Float, lengthSec: Float) {
    if (!format.eventId.isNullOrEmpty() && !format.ofParam.isNullOrEmpty()) {
        TrackingService.instance().updateWatchTime(
            videoId,
            posSec,
            lengthSec,
            format.eventId,
            format.visitorMonitoringData,
            format.ofParam
        )
    } else {
        mediaRepository.updateHistory(videoId, posSec)
    }
}
```
Triggered at regular playback intervals, when pausing, and when exiting playback.

### 8.2. Local Compressed Watch History
In `UserHistoryRepository.kt`:
- For guest sessions or offline playback, stores up to 100 recent entries in `user_watch_history.json`.
- When an account is connected, automatically compresses local records to `user_watch_history.json.gz` using GZIP to prevent NAND flash wear and eliminate duplicate history entries.

---

## 9. Embedded Web Remote Server & QR Companion Control

RenoTube includes an embedded multi-threaded HTTP server in `WebRemoteServer.kt` running on port `8080` (fallback `8888`).

### 9.1. Endpoints & API Contract

| Route | Method | Description |
| :--- | :--- | :--- |
| `/` or `/index.html` | `GET` | Mobile touch remote control web UI with tactile haptic feedback |
| `/api/status` | `GET` | Returns app status and port JSON: `{"status":"running","app":"RenoTube TV"}` |
| `/api/key?code=<cmd>` | `GET/POST` | Dispatches key commands: `UP`, `DOWN`, `LEFT`, `RIGHT`, `OK`, `LONG_OK`, `BACK`, `HOME`, `PLAY_PAUSE`, `NEXT`, `REWIND`, `FF`, `VOL_UP`, `VOL_DOWN`, `SEEK:<0.0-1.0>`, `QUALITY`, `SPEED`, `SUBTITLES`, `VOICE_BOOST` |
| `/api/play?videoId=<id>` | `GET/POST` | Immediately switches playback to the specified YouTube video ID |
| `/api/search?q=<query>` | `GET/POST` | Navigates to search screen and executes query |
| `/api/ambilight` | `GET/POST` | Updates config: `?enabled=true&ip=192.168.1.6&port=4048&top=71&bottom=76&left=40&right=41` |
| `/api/ambilight/dump` | `GET` | Dumps realtime RGB values, border offsets, and LED array in JSON |

### 9.2. Instant Mobile Pairing
Pressing the Remote icon in Player Settings displays `QrCodeView.kt`, which renders a ZXing QR code encoding `http://<tv-ip>:8080/`. Any phone on the same Wi-Fi network can scan it with its camera and instantly control the TV without installing an app.

---

## 10. Step-by-Step Blueprint to Recreate the App

Follow these exact steps to rebuild RenoTube from source:

### 10.1. Prerequisites
1. **JDK 17:** Ensure `JAVA_HOME` points to a valid Java 17 JDK.
2. **Android SDK:** Platforms `android-34`, build-tools `34.0.0`, NDK supporting `armeabi-v7a` and `arm64-v8a`.
3. **Gradle:** 8.2.2 (handled by `./gradlew`).

### 10.2. AndroidManifest.xml Requirements
Ensure `app/src/main/AndroidManifest.xml` contains:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<application
    android:name=".RenoTubeApp"
    android:banner="@drawable/banner"
    android:icon="@mipmap/ic_launcher"
    android:label="RenoTube"
    android:theme="@style/Theme.RenoTube"
    android:usesCleartextTraffic="true">
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
        android:screenOrientation="landscape">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

### 10.3. Building the Release APK
Execute from project root:
```powershell
set JAVA_HOME=C:\Users\renop\Downloads\renotube\jdk17
set PATH=%JAVA_HOME%\bin;%PATH%
./gradlew.bat assembleRelease
```
The output APKs will be generated in:
`app/build/outputs/apk/release/`
- `app-armeabi-v7a-release.apk` (Recommended for Sony BRAVIA TVs)
- `app-arm64-v8a-release.apk` (Recommended for Nvidia Shield TV)
- `app-universal-release.apk`

### 10.4. Installation & Verification
Install via ADB over Wi-Fi:
```powershell
adb connect 192.168.1.4:5555
adb install -r app/build/outputs/apk/release/app-armeabi-v7a-release.apk
adb shell am start -n com.renotube.tv/.MainActivity
```
Verify the live ambilight status via HTTP:
```bash
curl http://192.168.1.4:8080/api/ambilight
```
Expected output:
```json
{
  "enabled": true,
  "ip": "192.168.1.6",
  "port": 21324,
  "totalLeds": 228,
  "leftLeds": 40,
  "topLeds": 71,
  "rightLeds": 41,
  "bottomLeds": 76
}
```
