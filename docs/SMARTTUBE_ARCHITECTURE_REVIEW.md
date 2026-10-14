# Veyra TV: SmartTube-Informed Architecture Review & Hardening Report

## Executive Summary
This document records an evidence-based architectural comparison between **SmartTube** (`yuliskov/SmartTube`, release tree) and **Veyra TV** (an original, lightweight Android TV browser built on Android System WebView).

The objective is to identify mature Android TV engineering patterns from SmartTube—specifically around TV input normalization, focus management, lifecycle transitions, state persistence, and resource efficiency—and evaluate their applicability to a general-purpose web browser.

---

## 1. SmartTube Source Findings: Directly Verified Patterns

The SmartTube repository source code and its submodules (`MediaServiceCore`, `SharedModules`) were cloned, inspected, and cross-referenced against the actual files on disk. Below are the directly verified architectural patterns:

| Area | SmartTube Source File & Class | Architectural Responsibility | Why Design Exists in SmartTube | Browser Applicability & Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **TV Key Input Normalization** | `common/.../misc/GlobalKeyTranslator.java`<br>`KeyTranslator.java` | Translates raw hardware keycodes (`BUTTON_B`, `ESCAPE`, `BUTTON_A`, `BUTTON_C`) into standard TV action events. | Resolves vendor remote quirks (e.g. Nvidia Shield remote emitting gamepad B instead of Back; Chinese air mice emitting Escape). | **ADAPT**: General Android TV remotes have identical quirks when navigating web views. Implemented as `TvKeyTranslator.kt`. |
| **Double-Back Exit Protection** | `smarttubetv/.../keyhandler/DoubleBackManager2.java` | Intercepts back keypresses at the root view and requires two presses within 2000 ms to exit. | Prevents accidental app exit when browsing dense media catalogs on TV. | **KEEP CURRENT**: In Veyra, the back key unwinds WebView history first, then returns to Home screen. An exit confirmation toast on Home is sufficient. |
| **Lightweight Typed Persistence** | `common/.../prefs/AppPrefs.java`<br>`GeneralData.java` | Strongly typed SharedPreferences abstraction without Room, SQLite, or heavy ORM dependencies. | Persists UI states, playback parameters, and toggles without startup overhead or disk schema migrations. | **ADOPT**: Implemented as `BrowserPreferences.kt` for Desktop Mode, Content Blocker toggles, and user allowlists. |
| **Focus Loss Prevention** | `smarttubetv/.../focus/FocusFixBrowseFrameLayout.java` | Custom `BrowseFrameLayout` overriding `focusSearch()` to retain directional focus during grid animations. | Android TV Leanback rows frequently lose focus to root decor views during fast D-pad navigation. | **ADAPT**: Veyra's `VirtualPointerController.kt` and `MainActivity.kt` constrain D-pad traversal and restore focus between toolbar and page. |
| **Focus Scale Highlights** | `leanback-1.0.0/.../widget/FocusHighlightHelper.java` | `TimeAnimator`-driven scale (1.05x–1.1x) and elevation for TV card focus. | Provides clear 10-foot visual feedback for the currently selected item. | **ADAPT**: Veyra implements this via lightweight `TvMotionHelper.kt` and XML state-list animators (`res/animator/tv_card_elevation.xml`). |
| **ExoPlayer Media Stack** | `common/.../controller/PlayerView.java`<br>`EmbedPlayerView.java`<br>`MediaServiceCore/...` | Dedicated multi-surface ExoPlayer 2.10.6 pipeline with custom DASH parser and SponsorBlock integration. | Direct media decoding, hardware surface rendering, and stream switching for YouTube media URLs. | **REJECT**: Veyra is a general-purpose web browser relying on System WebView's HTML5 `<video>` engine and Widevine CDM. Replacing WebView with ExoPlayer is architecturally incompatible with general web standards. |
| **Multi-Activity Leanback Stack** | `smarttubetv/.../BrowseActivity.java`<br>`PlaybackActivity.java` | Separates browsing catalog from fullscreen video playback into distinct Android Activities. | Leanback player activity manages dedicated audio focus, media session, and overlays. | **REJECT**: Passing an active WebView across Activities causes `WindowManager$BadTokenException` and context leaks. Recreating WebView destroys page state and inflates memory. Single-Activity container swapping is retained. |
| **Forked AndroidX Leanback** | `SharedModules/leanback-1.0.0/` | Custom in-tree patch of AndroidX Leanback UI components to bypass Google TV UI limitations. | Workaround for Leanback header styling and navigation bugs. | **REJECT**: Heavy maintenance burden; Veyra utilizes standard AndroidX views and constraint layouts. |

---

## 2. Veyra TV Architecture Audit Across 9 Engineering Areas

### A. Activity & Controller Responsibilities
- **Current Architecture**: `MainActivity` serves as the central coordination host. It manages top-level container visibility (`homeContainer`, `browserContainer`, `fullscreenContainer`), passes key events to `VirtualPointerController`, and delegates WebView lifecycle to `VeyraWebViewController`.
- **Findings**: `MainActivity` has clean separation. It does not manipulate DOM, execute ad blocking, or decode video directly.
- **Improvement**: Normalized key handling via `TvKeyTranslator` before passing to pointer or views. Back key release (`ACTION_UP`) is cleanly consumed when browsing to prevent framework back leaks.

### B. Browser Engine Boundary
- **Current Architecture**: `VeyraWebViewController` cleanly encapsulates the single active `WebView`. It manages WebSettings, user agent switching, viewport scripts, and renderer crash recovery.
- **Findings**: Single-active-WebView invariant is strictly preserved. In-memory views are reused rather than destroyed and re-instantiated.
- **Improvement**: Desktop Mode state is backed by `BrowserPreferences`, persisting across application restarts. Cached fallback handlers are retained during `recoverRenderer()`.

### C. Lifecycle and Resource Management
- **Current Architecture**: Explicit lifecycle hooks in `MainActivity`:
  - `onPause()` calls `WebView.onPause()` and `pauseTimers()`.
  - `onResume()` calls `WebView.onResume()` and `resumeTimers()`.
  - **Cookie Persistence**: Android's `CookieManager.flush()` executes synchronously on the calling thread and performs disk I/O. Calling it inside `VeyraWebViewController.onPause()` is an intentional lifecycle trade-off: it accepts potential transition-time disk I/O on the main thread when navigating away or backgrounding the app in order to ensure in-memory cookies are committed to persistent storage before background process termination. It is strictly excluded from `onPageFinished()` to avoid blocking the main thread during active web page loading.
  - `onTrimMemory(level)`:
    - `level >= TRIM_MEMORY_UI_HIDDEN`: When the UI is hidden or backgrounded, `onPause()` has already flushed cookies; ensures controller timers/state are paused if `browserContainer` was hidden.
    - `level == TRIM_MEMORY_RUNNING_CRITICAL`: Commits cookies in case the low-memory killer terminates the running process under critical memory pressure.
    - No deprecated `freeMemory()` call. No destruction of active WebView or page state.
  - Renderer crash (`onRenderProcessGone`) is trapped cleanly in `VeyraWebViewClient`, notifying the user and calling `controller.recoverRenderer()`.

### D. TV Navigation and Input
- **Current Architecture**: Dual navigation model:
  1. Native D-pad spatial focus on Home screen and Navigation Toolbar.
  2. Virtual pointer mode with smooth analog acceleration (`VirtualPointerController`) for free-form web interaction.
- **Hardware Quirk Normalization**: `TvKeyTranslator` intercepts raw events and maps vendor remote keycodes (`BUTTON_B`, `ESCAPE`, `BUTTON_A`, `BUTTON_C`) to standard Android TV navigation primitives (`KEYCODE_BACK`, `KEYCODE_DPAD_CENTER`).

### E. Media & Fullscreen Architecture
- **Current Architecture**: `VeyraWebChromeClient` handles HTML5 video fullscreen transitions via `onShowCustomView` and `onHideCustomView`. Container swapping moves the video view into `binding.fullscreenContainer`, hiding the browser UI and enabling hardware acceleration.
- **Comparison with SmartTube**: SmartTube's ExoPlayer pipeline is inappropriate for a web browser. Veyra's WebView-native HTML5 media architecture is preserved.

### F. Content Filtering & Privacy
- **Current Architecture**: `ContentBlocker` intercepts subresource requests via `WebViewClient.shouldInterceptRequest(view, request)`:
  - Exact domain and subdomain suffix matching against ~350 curated ad, tracker, and telemetry hosts.
  - Strict dot-boundary checking (preventing false-positive collisions like `notdoubleclick.net`).
  - Blocked requests return HTTP `204 No Content` with empty byte stream and CORS allow headers (`Access-Control-Allow-Origin: *`).
  - Thread-safe user allowlist persisted via `BrowserPreferences`.
- **Technical Scope Boundary**:
  - Covers HTTP/HTTPS subresources (scripts, images, stylesheets, iframes, fetch/XHR, media).
  - Does NOT cover WebSockets (`wss://`), Service Worker background fetch, or top-level URL navigations.
  - Does NOT claim to be a system-wide VPN or DNS proxy.

### G. Browser State & Persistence
- **Current Architecture**: Lightweight typed persistence via `BrowserPreferences` backed by Android `SharedPreferences`:
  - `desktop_mode_enabled` (boolean)
  - `content_blocker_enabled` (boolean)
  - `content_blocker_allowlist` (StringSet)
- **Design Decision**: Avoided heavy frameworks (Room, SQLite, DataStore) to keep APK size at 4.60 MB and cold start under 400 ms.

### H. Error Handling & Diagnostics
- **Current Architecture**:
  - TLS/SSL errors: Strict certificate validation in `VeyraWebViewClient.onReceivedSslError()`. Displays warning dialog with SSL error details.
  - Renderer crash: `onRenderProcessGone()` returns `true` (preventing app termination) and calls `recoverRenderer()`.
  - HTTP errors: Logged locally with sanitized URLs (query parameters stripped) to prevent leaking auth tokens.

### I. Dependencies & Modularity
- **Current Footprint**: Zero third-party dependencies outside standard Google AndroidX libraries (`core-ktx`, `appcompat`, `webkit`, `material`).
- **APK Size**: 4.60 MB (Release APK: 4,820,619 bytes).

---

## 3. Prioritized Architectural Recommendations Matrix

| Recommendation | Category | Area | SmartTube Pattern Verified | Veyra Implementation | Action | Rationale |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TV Key Normalization** | Necessary Correctness | Input | `GlobalKeyTranslator.java` | `TvKeyTranslator.kt` | **Adopt** | Fixes Shield remote and gamepad Back / Select button drops. |
| **Allowlist & Prefs Persistence** | Low-risk Structural | State | `AppPrefs.java` | `BrowserPreferences.kt` | **Adopt** | Preserves user Desktop Mode and custom allowlists across app restarts without Room/SQLite overhead. |
| **Single-Activity Architecture** | Low-risk Structural | Architecture | `LeanbackActivity.java` | `MainActivity.kt` | **Keep Current** | Preserves single active WebView and prevents window manager token leaks. |
| **In-App Subresource Filter** | Necessary Correctness | Security | N/A (DNS/SponsorBlock) | `ContentBlocker.kt` | **Keep Current** | Lightweight, predictable in-app subresource filtering without VPN/VpnService overhead. Returns HTTP 204 No Content. |
| **Separate Media Service / ExoPlayer** | Unnecessary Complexity | Media | `MediaServiceCore` / `PlayerView` | System WebView HTML5 Video | **Reject** | WebView is required for general web pages; ExoPlayer cannot parse arbitrary web DOM or DRM-protected web portals. |
| **Forked AndroidX Leanback** | Unnecessary Complexity | Dependencies | `SharedModules/leanback-1.0.0` | Standard Android Views | **Reject** | High maintenance overhead; standard Android Views and ConstraintLayout provide higher flexibility and lower memory footprint. |

---

## 4. Controlled Benchmark Results

All measurements were captured on an active Android TV emulator running Android 14 (API 34, `sdk_google_atv64_amati_arm64`, Google WebView 113.0.5672.136) using `scripts/measure_performance.sh`.

### A. APK Footprint
- **Release APK**: 4,820,619 bytes (**4.60 MB**) at `app/build/outputs/apk/release/app-release.apk`
- **Debug APK**: 6,989,873 bytes (**6.67 MB**) at `app/build/outputs/apk/debug/app-debug.apk`

### B. Startup Performance (Reported by `adb shell am start -W`)
Timings are raw metrics reported by the Android ActivityManager `am start -W` tool:
- **First Observed Launch After Boot**: TotalTime: **2,401 ms** (WaitTime: 2,466 ms)
- **Subsequent Process-Cold Launches (Process Killed via `force-stop`, 3 runs)**:
  - Run 1: TotalTime: 360 ms (WaitTime: 369 ms, LaunchState: COLD)
  - Run 2: TotalTime: 291 ms (WaitTime: 296 ms, LaunchState: COLD)
  - Run 3: TotalTime: 270 ms (WaitTime: 275 ms, LaunchState: COLD)
  - **Mean Cold Startup**: **307.0 ms**
- **Hot Resume (App in background, brought to front)**: TotalTime: **24 ms** (WaitTime: 26 ms, LaunchState: HOT)

### C. Multi-Workload Memory Footprint (PSS)
Raw measurements captured directly via `dumpsys meminfo`. Binary units are calculated as $\text{kB} / 1{,}024$ and labeled $\text{MiB}$. Decimal units are calculated as $\text{kB} / 1{,}000$ and labeled $\text{MB}$.

| Workload | Raw Host PSS | Raw Renderer PSS | Raw Combined PSS | Binary Combined PSS (MiB = kB/1024) | Decimal Combined PSS (MB = kB/1000) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Home Screen Idle** | 78,906 kB | 6,506 kB | 85,412 kB | **83.41 MiB** (Host: 77.06, Rend: 6.35) | **85.41 MB** (Host: 78.91, Rend: 6.51) |
| **Wikipedia Mobile** | 91,710 kB | 128,002 kB | 219,712 kB | **214.56 MiB** (Host: 89.56, Rend: 125.00) | **219.71 MB** (Host: 91.71, Rend: 128.00) |
| **Wikipedia Desktop** | 88,814 kB | 149,612 kB | 238,426 kB | **232.84 MiB** (Host: 86.73, Rend: 146.11) | **238.43 MB** (Host: 88.81, Rend: 149.61) |

*Important Finding on Previous Benchmark Inconsistency*: The earlier reported figure of ~283 MiB was captured on a complex desktop page with deep DOM and unconstrained media. The subsequent figure of 95.6 MiB was captured on a lightweight mobile portal. As demonstrated by the controlled benchmark above, mobile vs desktop Wikipedia pages consume 125.00 MiB vs 146.11 MiB renderer PSS respectively:
- **Renderer PSS Difference**: $149{,}612 - 128{,}002 = 21{,}610\ \text{kB} = \mathbf{21.10\ \text{MiB}}$ (binary) or $\mathbf{21.61\ \text{MB}}$ (decimal).
- **Combined Total Difference**: $238{,}426 - 219{,}712 = 18{,}714\ \text{kB} = \mathbf{18.28\ \text{MiB}}$ (binary) or $\mathbf{18.71\ \text{MB}}$ (decimal).
This difference strictly compares the measured mobile and desktop Wikipedia workloads under identical conditions, not different versions of Veyra. Comparing mobile to desktop was an invalid comparison; the previously claimed reduction was an artifact of testing different workloads.

---

## 5. Technical Boundaries, ContentBlocker Nuances & DRM Realities

### A. ContentBlocker Response Behavior & JSON Parsing Limitations
- `ContentBlocker.createBlockedResponse()` returns HTTP `204 No Content` with an empty byte stream and CORS allow-all headers.
- **Scripts and Stylesheets**: Parses cleanly without throwing JS or CSS syntax errors.
- **Fetch / XHR & `res.json()` Limitation**: If a client web script executes `fetch(blockedUrl).then(res => res.json())` without checking `res.status === 204` or handling exceptions, the JavaScript engine will throw a `SyntaxError: Unexpected end of JSON input`. Returning HTTP 204 does **not** eliminate all client-side exceptions or browser console warnings on sites expecting structured JSON payloads from telemetry endpoints.
- **Interception Boundary**: Subresources (scripts, images, stylesheets, iframes, media, XHR) are intercepted. WebSockets (`wss://`), Service Worker background fetch, and top-level URL navigations are not.

### B. OTT Compatibility & Widevine DRM Realities
- **Website Loading & UI**: Supported. Popular streaming sites (YouTube, Prime Video, Netflix, Disney+, Hotstar) load and render their web portals.
- **Authentication & QR Login**: Supported. Dedicated TV quick-access tiles facilitate 8-digit code or QR code sign-in via smartphone, completely avoiding remote-control password entry.
- **Session Persistence**: Supported. Cookies and DOM storage persist across app restarts.
- **DRM-Protected Playback**:
  - *YouTube (HTML5)*: Uses standard VP9 / AVC without Widevine; playback is verified and works natively.
  - *Widevine DRM (L1 vs L3)*: In standard System WebView, Widevine EME typically operates at **Level 3 (software DRM)**, which caps streaming resolution to 480p/720p or may be rejected entirely by strict licensing services (e.g. Netflix Desktop Web). Certified Level 1 (hardware-secure video path) playback is OEM- and device-dependent in WebView and remains **unverified** on arbitrary Android TV hardware.
  - *Commercial OTT Streaming Services*: Full-resolution playback on Netflix, Amazon Prime Video, Disney+, and Hotstar in WebView is **not guaranteed** and must be tested per physical device model.
