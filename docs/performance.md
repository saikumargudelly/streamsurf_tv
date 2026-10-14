# Veyra TV: Performance Benchmark & Resource Metrics

## 1. Architectural Performance Targets

On resource-constrained Android TV streaming sticks and televisions:
- **APK Package Size**: Aim for < 15 MB.
- **Cold Startup Latency**: Aim for < 3000 ms to interactive Home screen.
- **Idle Memory Footprint (App Process PSS)**: Aim for < 40 MB before page loading.
- **Background Tasks**: 0 persistent background tasks or services while idle.
- **Logical Tab Scalability**: At least 3 logical tabs without retaining 3 concurrent live WebViews.

---

## 2. Measurement Methodology

Run the benchmark script:
```bash
./scripts/measure_performance.sh
```

### Metrics Definitions
- **APK Size**: Measured directly on `app/build/outputs/apk/debug/app-debug.apk`.
- **Cold Startup Time**: Measured using `adb shell am start -W -n dev.veyra.tv.browser.debug/dev.veyra.tv.browser.ui.MainActivity`. Captures `TotalTime` (kernel launch to first frame rendered).
- **Process Memory (PSS)**: Measured using `adb shell dumpsys meminfo dev.veyra.tv.browser.debug`. Distinguishes between browser application PSS and isolated WebView renderer PSS (`org.chromium.webview_zygote` / `com.android.webview`).

---

## 3. Baseline Verification Log

| Milestone | Target | Measured Result | Status |
|---|---|---|---|
| **M1: APK Size (Debug)** | < 15.0 MB | Pending build output measurement | Evaluating |
| **M1: Cold Startup** | < 3000 ms | Hardware dependent (requires connected TV / AVD) | Script ready |
| **M1: Idle App PSS** | < 40.0 MB | Hardware dependent (requires connected TV / AVD) | Script ready |
| **M1: Idle Background Tasks** | 0 tasks | 0 background services configured | PASS |
