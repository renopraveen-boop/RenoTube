# RenoTube TV 📺✨

> **High-Performance, Lightweight Android TV YouTube Client with HyperHDR-Grade Ambilight, Mobile Web Remote, and Official YouTube TV UI.**

---

## 🌟 Key Highlights

### 🎨 Authentic Official YouTube TV UI & DPAD Flow
* **Collapsible Navigation Rail & Drawer**: 72dp collapsed rail expands smoothly to 240dp on focus/left-press, featuring authenticated user profile header, Search, Home, Subscriptions, Library ("You"), and Settings pinned to the bottom.
* **Top Filter Chips Bar**: High-contrast horizontal pill navigation ("All", "Music", "Gaming", "Live", "Recently uploaded", etc.) with seamless DPAD boundary traversal.
* **Redesigned Video Cards**: 16:9 thumbnail ratio, 12dp rounded corners, channel initial badge, 2-line bold title, watch progress bar, and 1.06x focus elevation with crisp 2.5dp white border.
* **Smart History & Recommendations**:
  * **Signed-in Mode**: Directly connects to YouTube cloud watch history, recommendations, and search tags.
  * **Guest Mode**: Custom local algorithm ("Jump Back In", "Most Played", local search completions).

### 💡 HyperHDR-Grade Ambilight Engine
* **Frame-Exact Zero-Lag Edge Extraction**: Real-time 228-LED edge analysis using exact HyperHDR bounding coordinates.
* **Direct Network Transmission**: DDP (Distributed Display Protocol, port 4048) and WLED DRGB (port 21324) over UDP.
* **WS2812B Hardware Calibration**: Low-PWM diode floor gates, shadow chroma gates, and sub-12ms micro-pacing drift locking.
* **Player Quick-Toggle**: Dedicated "Ambient Mode" action button in the video player controls with live status accent.

### 📱 Integrated Mobile Web Remote & Live Browser Tester
* **Web Remote (Port 8080)**: Control TV playback, DPAD navigation, search input, and LED calibration from any phone or PC on your local Wi-Fi at `http://<tv-ip>:8080`.
* **Live Browser Tester (Port 8500)**: Interactive desktop browser viewer with live screen mirror and full keyboard DPAD bindings (<kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd>, <kbd>Enter</kbd>, <kbd>Esc</kbd>, <kbd>Space</kbd>).

---

## 🚀 Performance & Memory Benchmarks

Tested and verified on Sony Bravia Android TV (MediaTek Quad-Core SoC with 2GB RAM):

| Metric | Before Optimization | After Optimization | Gain |
| :--- | :--- | :--- | :--- |
| **DEX Code Size** | 52.4 MB (5 DEX files) | **11.5 MB (2 DEX files)** | **78% smaller** |
| **Target APK Size (ARM v7a)** | 28.2 MB | **16.7 MB** | **40.7% smaller** |
| **Java Heap (Idle)** | ~64 – 99 MB | **23.5 MB** | **63–76% reduction** |
| **Java Heap (4K + Ambilight)** | ~110 – 140 MB | **44.9 MB** | **59–68% reduction** |
| **Per-Thumbnail Memory** | ~8.3 MB | **0.29 MB** | **96.3% reduction** |
| **GC Pause Time** | Periodic LOS stalls | **164 microseconds (0.16 ms)** | **Imperceptible** |

---

## 🛠️ Building & Installation

### Prerequisites
* JDK 17
* Android SDK (API 34, Build Tools 34.0.0)

### Build Commands
```bash
# Build Universal and Architecture-Specific Release APKs
./gradlew assembleRelease
```

Generated APKs will be located in:
`app/build/outputs/apk/release/`
* `app-universal-release.apk`: Multi-architecture standalone APK.
* `app-armeabi-v7a-release.apk`: Optimized build for Sony Bravia / 32-bit userland Android TVs.
* `app-arm64-v8a-release.apk`: Build for 64-bit Google TV boxes.

### Install via ADB
```bash
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/release/app-universal-release.apk
```

---

## 📄 License
This project is licensed under the Apache License 2.0.
