# Veyra TV: Threat Model & Privacy Analysis

## 1. Security Objectives

Veyra is intended for personal use on Android TV and Google TV devices.
Because TV devices reside in living rooms and are connected to shared local home networks, the browser must defend against:
1. Malicious web content attempting to access local files, device storage, or hardware resources.
2. Man-in-the-Middle (MitM) attacks on local networks intercepting plaintext or invalid TLS traffic.
3. Cross-site tracking and cookie leakage across domains.
4. Credential interception or token leakage during OTT streaming authentication.

---

## 2. Threat Analysis & Mitigations

| Threat | Risk Level | Mitigation Strategy |
|---|---|---|
| **Local File Exfiltration (`file://`)** | High | `WebSettings.allowFileAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`. URL resolution strictly rejects non-HTTP(S) schemes. |
| **SSL / TLS Certificate Forgery** | High | `VeyraWebViewClient.onReceivedSslError()` unconditionally invokes `handler.cancel()`. Certificate errors are never silently bypassed or automatically accepted. |
| **Mixed Insecure Content (HTTP on HTTPS)** | Medium | `WebSettings.mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`. Insecure subresources on secure pages are blocked. |
| **Unrestricted JavaScript Bridges** | High | Zero JavaScript interfaces (`addJavascriptInterface`) exposed to arbitrary web content. Eliminates remote code execution via reflective Java bridge invocation. |
| **Renderer Crash Exploitation / OOM** | Medium | `onRenderProcessGone` intercepts renderer terminations, tears down the stale WebView, and cleanly recovers without leaking process memory or crashing the OS app container. |
| **Credential & Cookie Theft** | High | App never stores user passwords or extracts login tokens. Cookie persistence is delegated securely to Android's native `CookieManager`. Backups are disabled (`android:allowBackup="false"`). |
| **Telemetry & Cloud Leaks** | High | Zero remote telemetry SDKs, analytics endpoints, or cloud backends. All browsing state remains 100% on-device. |

---

## 3. Assumptions and Unresolved Issues

- **WebView Provider Security**: Veyra relies on the device's installed Android System WebView. Updating the System WebView via sideload or Google Play is recommended to maintain patch currency.
- **DRM & Widevine**: Protected OTT streams (e.g. Netflix 4K / Prime Video) rely on Widevine CDM support built into the platform's WebView implementation. Veyra does not circumvent or alter DRM pipelines.
