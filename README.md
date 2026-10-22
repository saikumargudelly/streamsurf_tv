# Veyra TV

> Fast, private, remote-first web browser engineered from the ground up for Android TV and Google TV.

[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV-blue?logo=android)](https://github.com/saikumargudelly/streamsuf_tv)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20%28API%2026%2B%29-3DDC84?logo=android)](https://github.com/saikumargudelly/streamsuf_tv)
[![Language](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-Single--WebView%20%7C%20Low--RAM-orange)](https://github.com/saikumargudelly/streamsuf_tv)

```
Topics: android-tv, google-tv, tv-browser, privacy-browser, adblocker, nextdns, dns-over-https, leanback, smart-tv, kotlin
```

---

## Overview

**Veyra TV** is an original Android TV browser crafted specifically for television hardware and remote controls. Unlike desktop or mobile ports, Veyra combines a clean 10-foot UI with a built-in content blocker, custom DNS-over-HTTPS (NextDNS), smart video streaming immunity, virtual cursor navigation, and crash-resilient memory management.

---

## Key Highlights

- 🛡️ **Veyra Shield Ad-Blocker**  
  Network-level ad/tracker interception with EasyList rule parsing, cosmetic CSS element hiding, anti-popup/clickjack neutralization, and intelligent video ad auto-skipping.
- 🌐 **Private DNS & Custom NextDNS**  
  Native DNS-over-HTTPS (DoH) engine with full manual **NextDNS profile ID** configuration, plus instant presets for NextDNS, Cloudflare, AdGuard, and Quad9.
- 🎯 **Remote-First TV UX & Virtual Mouse**  
  Fluid D-pad navigation with high-contrast focus rings, overscan-safe layout, TV status badges, and an integrated **Virtual Mouse Pointer Mode** toggled with a single remote press for desktop websites.
- 🎬 **Zero-Stall Streaming Media Engine**  
  Smart media immunity ensures streaming manifests and video segments (`.m3u8`, `.mpd`, `.ts`, `.m4s`, `.mp4`) bypass false-positive ad rules for smooth 4K/1080p playback.
- 📱 **Quick TV Sign-In Hub**  
  One-click code sign-in launchpads for YouTube TV, Prime Video, Disney+ Begin, Hotstar QR, and Netflix TV.
- 🪶 **Low-RAM Architecture & Crash Resiliency**  
  Single-active-WebView design keeping host memory under ~130 MB PSS. Compliant Android WebView dead-process handling and rate-limited renderer recovery eliminate crash loops and system LMK kills.
- 🔒 **Zero Telemetry & Local Session Persistence**  
  No telemetry, analytics, or cloud backends. Streaming logins and cookies persist locally across reboots with strict TLS certificate validation.

---

## Remote Control Guide

| Key | Navigation Function |
|---|---|
| **D-Pad (Up / Down / Left / Right)** | Moves focus across tiles, omnibar, and web page links (or moves cursor in Pointer mode) |
| **OK / Center** | Clicks focused element, enters video fullscreen, or clicks under virtual cursor |
| **BACK** | 1. Closes modals / Exits fullscreen video<br>2. Navigates back in page history<br>3. Returns to Veyra TV Home screen |
| **MENU / F2** | Jumps focus instantly to the top navigation toolbar |

---

## Build from Source

### Prerequisites
- JDK 17 (`export JAVA_HOME=...`)
- Android SDK (API 34/35 platform tools)

### Commands
```bash
# Clone the repository
git clone https://github.com/saikumargudelly/streamsuf_tv.git
cd streamsuf_tv

# Run unit tests
./gradlew test

# Build signed release APK
./gradlew assembleRelease copyReleaseApkToDist
```
The compiled APK will be output to `dist/veyra-tv-release.apk`.

---

## Sideloading onto Android TV

```bash
# 1. Connect to Android TV via ADB
adb connect <TV-IP-ADDRESS>:5555

# 2. Install release APK
adb install -r dist/veyra-tv-release.apk

# 3. Launch Veyra TV
adb shell am start -n dev.veyra.tv.browser/dev.veyra.tv.browser.ui.MainActivity
```

---

## License

Released under the [Apache License 2.0](LICENSE). Built purely with native AndroidX and Kotlin without proprietary tracking frameworks or third-party cloud SDKs.
