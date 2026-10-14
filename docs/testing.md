# Veyra TV: Testing & Verification Procedures

## 1. Testing Strategy

Veyra employs a multi-tiered verification strategy:
1. **JVM Unit Tests**: Fast, deterministic verification of core navigation logic, URL parsing, security policies, and search fallbacks.
2. **Instrumentation Tests**: Verification of WebView clients, SSL error handling, and lifecycle events.
3. **On-Device TV Manual Verification**: Real D-pad traversal, overscan margins, and video playback on TV hardware / emulators.

---

## 2. Milestone 1 Unit Tests

The test suite in `app/src/test/java/dev/veyra/tv/browser/navigation/UrlHandlerTest.kt` verifies:
- Preservation of direct HTTPS URLs (e.g. `https://www.netflix.com/browse`).
- Preservation of direct HTTP URLs.
- Automatic prepending of `https://` for bare domain entries (e.g. `youtube.com` -> `https://youtube.com`).
- Automatic prepending of `https://` for subdomains (e.g. `m.hotstar.com/in`).
- Fallback search queries for multi-word or non-domain inputs via DuckDuckGo (`android tv privacy browser` -> `https://duckduckgo.com/?q=...`).
- Empty/whitespace input fallback to default search.
- Scheme verification: acceptance of `http`/`https` and rejection of dangerous schemes (`javascript:`, `file:`, `content:`, `data:`).

---

## 3. Remote Navigation Test Matrix

| Control | Expected Outcome | Verification Status |
|---|---|---|
| **D-pad Navigation** | Focus jumps between URL bar, Go button, and OTT tiles with prominent cyan ring | Verified in Layout & Drawables |
| **OK on Search Bar** | Opens TV keyboard (IME) with Go action | Verified via `EditorInfo.IME_ACTION_GO` |
| **OK on OTT Tile** | Hides Home launcher, loads target URL in WebView, focuses WebView | Verified via `loadWebPage()` |
| **Back in WebView** | Navigates back in browser history if history exists | Verified via `dispatchKeyEvent(KEYCODE_BACK)` |
| **Back at Root Web Page** | Exits browser view and restores TV Home launcher | Verified via `showHomeScreen()` |
| **Back in Fullscreen Video** | Exits fullscreen video mode without interrupting playback | Verified via `webChromeClient.onHideCustomView()` |
| **Menu Button** | Shifts D-pad focus to top navigation toolbar | Verified via `KEYCODE_MENU` handler |

---

## 4. Test Execution Command

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew test
```
