# Veyra TV: Architecture & System Design

## 1. Design Overview & Core Principles

Veyra is an original, lightweight web browser built from the ground up for Android TV and Google TV.
Unlike conventional desktop or mobile browsers ported to TV, Veyra is designed around the unique constraints of television hardware:
1. **Strict Hardware Constraints**: Android TV hardware frequently operates with limited RAM (1.5 GB - 2 GB) and entry-level quad-core ARM chipsets. Keeping multiple live WebViews in memory quickly triggers low-memory kills (OOM).
2. **Remote-First Interaction**: No mouse pointer or direct touch interaction can be assumed. Focus traversal must be deterministic, visually prominent, and predictable.
3. **No Background Footprint**: No persistent background services, local proxies, or sync daemons. When the user exits, background resource consumption drops to zero.

---

## 2. System Architecture

```
+--------------------------------------------------------------------------+
|                               MainActivity                               |
|   - TV Focus Engine       - D-pad Key Interceptor     - Intent Handler   |
+------------------------------------+-------------------------------------+
                                     |
         +---------------------------+---------------------------+
         |                                                       |
         v                                                       v
+-----------------------+                             +--------------------+
|     Home Launcher     |                             |  Browser Container |
| - URL & Search Bar    |                             | - Top Nav Toolbar  |
| - OTT Quick Tiles     |                             | - Progress Bar     |
| - Focus Rings & D-pad |                             | - Fullscreen Video |
+-----------------------+                             +---------+----------+
                                                                |
                                                                v
                                              +----------------------------------+
                                              |      VeyraWebViewController      |
                                              |   (Single Active WebView Host)   |
                                              +--------+----------------+--------+
                                                       |                |
                       +-------------------------------+                +------------------------------+
                       v                                                                               v
+----------------------------------------------+                             +-----------------------------------+
|             VeyraWebViewClient               |                             |       VeyraWebChromeClient        |
| - Scheme Security Filter (HTTPS/HTTP)        |                             | - HTML5 Video Fullscreen Overlay  |
| - Strict TLS / SSL Validation                |                             | - Page Progress & Title Tracking  |
| - Renderer Crash Recovery (RenderProcessGone)|                             | - Custom View Transitions         |
+----------------------------------------------+                             +-----------------------------------+
                       |                                                                               |
                       +-------------------------------+-----------------------------------------------+
                                                       v
                                     +-----------------------------------+
                                     |       SecurityPolicy & State      |
                                     | - Disabled File Access            |
                                     | - Mixed Content: Blocked          |
                                     | - Persistent CookieManager Flush  |
                                     | - DOM & IndexedDB Enabled         |
                                     +-----------------------------------+
```

---

## 3. Package Structure

- `dev.veyra.tv.browser.ui`: Android TV Activity, layouts, TV focus state selectors, and UI controllers.
- `dev.veyra.tv.browser.engine`: WebView controller, custom clients (`WebChromeClient`, `WebViewClient`), HTML5 fullscreen handling, and crash recovery.
- `dev.veyra.tv.browser.navigation`: URL resolution, schema verification, and search fallback.
- `dev.veyra.tv.browser.security`: WebView security configuration, cookie flush synchronization, TLS error policies.

---

## 4. Key Architectural Decisions

### Single Active WebView
Maintaining a single active WebView avoids high RSS/PSS memory overhead from multiple Blink/Chromium renderers. Logical tab restoration (Milestone 2) will store lightweight metadata (URL, title, timestamp) and reconstruct DOM state on-demand rather than holding dormant renderers in memory.

### Persistent Sessions Without Credential Storage
To satisfy OTT website login persistence (YouTube, Netflix, Prime Video, Hotstar), Veyra enables DOM storage and configures `CookieManager` to accept first- and third-party cookies (necessary for single-sign-on flows), explicitly flushing cookies on page finish and lifecycle pause. Credentials and passwords are never extracted, inspected, or stored in app memory.

### HTML5 Video Fullscreen Architecture
Web video on TV requires full-screen presentation. `VeyraWebChromeClient` captures `onShowCustomView`, swaps out the browser UI for a full-screen FrameLayout, and passes key focus directly to the media player view. Pressing remote BACK gracefully returns to the web viewport via `onHideCustomView`.
