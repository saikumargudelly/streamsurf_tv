# Veyra TV: Lightweight, Privacy-First Android TV Browser

Veyra is an original, lightweight web browser built from scratch specifically for **Android TV** and **Google TV** devices. Distributed as a self-contained signed APK for sideloading, Veyra is designed with a strict remote-first interface, low memory footprint, persistent OTT sessions, and robust privacy defaults.

---

## Key Features (Milestone 1)

- **Remote-First Android TV Interface**: Optimized for 1080p and 4K displays with prominent high-contrast D-pad focus indicators and safe overscan margins (inspired by best practices from TV applications like SmartTube).
- **Single Active WebView Architecture**: Minimizes memory usage on RAM-constrained TV hardware, avoiding multiple background WebViews.
- **Persistent OTT Sessions**: Preserves authentication cookies and DOM storage across app restarts for services like Netflix, YouTube, Prime Video, and JioHotstar without storing credentials.
- **Fullscreen HTML5 Video**: Native `WebChromeClient` custom view integration with automatic full-screen display and remote control exit handling.
- **Strict TLS & Security Defaults**: Hardened WebView configuration with mandatory TLS certificate validation (no auto-bypass), disabled local file access, and blocked mixed content.
- **Renderer Crash Recovery**: Resilient `onRenderProcessGone` handler to recover cleanly without crashing the browser when Android terminates the renderer process under memory pressure.
- **Dual Launcher Support**: Native Leanback launcher banner and standard mobile launcher support for broad compatibility across TV and emulation environments.

---

## Requirements & Environment

- **Operating System**: macOS / Linux / Windows
- **JDK**: OpenJDK 17 or OpenJDK 21
- **Android SDK**: API Level 35 (Android 15) build tools, minimum API Level 26 (Android 8.0 Oreo)
- **Target Platform**: Android TV / Google TV / Android devices

---

## Build Instructions

1. Clone or navigate to the repository directory:
   ```bash
   cd "veyra TV"
   ```

2. Ensure `local.properties` specifies your Android SDK directory:
   ```properties
   sdk.dir=/Users/saikumargudelly/Library/Android/sdk
   ```

3. Build the debug APK:
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ./gradlew assembleDebug
   ```

4. Output APK location:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Running Unit Tests

Execute the unit test suite:
```bash
./gradlew test
```

---

## Installation & Sideloading onto Android TV

1. Enable **Developer Options** and **USB / Network Debugging** on your Android TV:
   - Go to **Settings > Device Preferences > About**.
   - Tap **Build** 7 times to enable Developer Options.
   - Go to **Settings > Developer Options > Network Debugging** and enable it.

2. Connect to your TV via ADB over Wi-Fi:
   ```bash
   adb connect <TV-IP-ADDRESS>:5555
   ```

3. Install the APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. Launch Veyra on the TV:
   ```bash
   adb shell am start -n dev.veyra.tv.browser.debug/dev.veyra.tv.browser.ui.MainActivity
   ```

---

## Remote Control Guide

| Remote Key | Function |
|---|---|
| **D-Pad Directional (Up/Down/Left/Right)** | Move focus between tiles, URL input, and web page elements |
| **OK / Center Button** | Select focused tile, submit URL search, click links, play/pause video |
| **BACK** | 1. Exit fullscreen video (if playing)<br>2. Navigate back in page history<br>3. Return to TV Home screen<br>4. Exit application |
| **MENU** | Jump focus directly to the top navigation toolbar |

---

## Performance Benchmark Procedure

Run the automated performance measurement script:
```bash
./scripts/measure_performance.sh
```
This measures:
- Output APK package size (Target: < 15 MB)
- Cold startup latency via `am start -W` (Target: < 3000 ms)
- Idle application PSS memory via `dumpsys meminfo` (Target: < 40 MB)

---

## License & Third-Party Notices

Developed independently without proprietary external dependencies or cloud backends. Utilizes Google AndroidX libraries under the Apache 2.0 license.
