# Veyra TV: Privacy Subsystem & Filter Engine Specification

## 1. Overview

Veyra prioritizes a tiered, lightweight privacy design to protect users on Android TV without exhausting system RAM or slowing down page rendering.

---

## 2. Tiered Defense Strategy

1. **Tier 1 (Primary Foundation - Milestone 3)**: DNS-based blocking (filtering hostnames before connection establishment). Zero memory parsing overhead during page navigation.
2. **Tier 2 (Optional In-Engine Layer - Milestone 4)**: Browser-level network and cosmetic filtering via `WebViewClient.shouldInterceptRequest()` and CSS element hiding.

---

## 3. Evaluation of AdblockAndroid for Android TV

As specified in Phase 4 of the Master Specification, `https://github.com/Edsuns/AdblockAndroid` is an open-source filter candidate under evaluation.

### Technical Feasibility Criteria
- **Native ABI Footprint**: Must support `armeabi-v7a` and `arm64-v8a` without ballooning APK size beyond the 15 MB budget.
- **Memory Consumption**: Rule-matching trie/automata must not exceed 25 MB RAM on 1 GB / 2 GB Android TV devices.
- **Interception Coverage in WebView**:
  - `shouldInterceptRequest()` intercepts GET subresources, images, scripts, and stylesheets.
  - Limitations: Does not intercept WebSockets, certain streaming chunk protocols, or WebRTC by default.
  - Cosmetic element hiding: Injects declarative CSS rules into loaded documents without unrestricted JS bridges.

---

## 4. Internal Abstraction Interface

To prevent vendor lock-in and allow modular testing, filter engines will be implemented behind a minimal interface:
```kotlin
interface FilterEngine {
    fun shouldBlock(url: String, origin: String, resourceType: ResourceType): FilterResult
    fun getCosmeticSelectors(domain: String): List<String>
}
```
