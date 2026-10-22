#!/bin/bash
set -euo pipefail

# StreamSurf TV - Comprehensive, Repeatable Performance Measurement Benchmark
# Measures:
# 1. APK Size (bytes and MB) for Release and Debug builds
# 2. Cold Startup Time (TotalTime from am start -W across multiple iterations)
# 3. Multi-workload Memory Usage (Host App PSS and Sandboxed Chromium Renderer PSS)
#    - Workload A: Home Screen Idle
#    - Workload B: Wikipedia Mobile (https://en.m.wikipedia.org/wiki/Television)
#    - Workload C: Wikipedia Desktop (https://en.wikipedia.org/wiki/Television)

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
MAIN_ACTIVITY="dev.veyra.tv.browser.ui.MainActivity"

echo "=================================================="
echo " STREAMSURF TV: PERFORMANCE MEASUREMENT BENCHMARK"
echo "=================================================="

# 1. APK Size Metrics
echo "1. APK Metrics:"
if [ -f "$RELEASE_APK" ]; then
    BYTES=$(stat -f%z "$RELEASE_APK" 2>/dev/null || stat -c%s "$RELEASE_APK")
    MB=$(python3 -c "print(f'{$BYTES / (1024 * 1024):.2f}')")
    echo "   - Release APK: $BYTES bytes (${MB} MB)"
    echo "   - Path: $RELEASE_APK"
fi

if [ -f "$DEBUG_APK" ]; then
    D_BYTES=$(stat -f%z "$DEBUG_APK" 2>/dev/null || stat -c%s "$DEBUG_APK")
    D_MB=$(python3 -c "print(f'{$D_BYTES / (1024 * 1024):.2f}')")
    echo "   - Debug APK:   $D_BYTES bytes (${D_MB} MB)"
    echo "   - Path: $DEBUG_APK"
fi

# 2. Device Connectivity Check
ADB_BIN="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if ! command -v "$ADB_BIN" &> /dev/null && [ ! -f "$ADB_BIN" ]; then
    if command -v adb &> /dev/null; then
        ADB_BIN="adb"
    else
        echo "Notice: adb not found. Skipping on-device metrics."
        exit 0
    fi
fi

DEVICE_COUNT=$("$ADB_BIN" devices 2>/dev/null | grep -E "\bdevice$" | wc -l | tr -d '[:space:]' || true)
if [ -z "$DEVICE_COUNT" ] || [ "$DEVICE_COUNT" = "0" ]; then
    echo ""
    echo "2. Device Metrics: No physical Android TV or active emulator connected."
    echo "   Skipping on-device cold start and PSS memory measurement."
    exit 0
fi

DEVICE_ID=$("$ADB_BIN" devices | grep -v "List" | grep "device$" | head -n1 | awk '{print $1}')
DEV_MODEL=$("$ADB_BIN" shell getprop ro.product.model | tr -d '\r')
DEV_ANDROID=$("$ADB_BIN" shell getprop ro.build.version.release | tr -d '\r')
DEV_SDK=$("$ADB_BIN" shell getprop ro.build.version.sdk | tr -d '\r')
WEBVIEW_VER=$("$ADB_BIN" shell dumpsys webviewupdate | tr -d '\r' | grep "Current WebView package" | head -n1 || echo "N/A")

echo ""
echo "Connected Device Diagnostics:"
echo "   - Device ID:        $DEVICE_ID"
echo "   - Model:            $DEV_MODEL"
echo "   - Android Version:  $DEV_ANDROID (API $DEV_SDK)"
echo "   - WebView Provider: $WEBVIEW_VER"

# Detect target package
PACKAGE_NAME="dev.veyra.tv.browser"
if ! "$ADB_BIN" shell pm list packages | grep -q "package:dev.veyra.tv.browser$"; then
    if "$ADB_BIN" shell pm list packages | grep -q "package:dev.veyra.tv.browser.debug"; then
        PACKAGE_NAME="dev.veyra.tv.browser.debug"
    fi
fi
echo "   - Target Package:   $PACKAGE_NAME"

# 3. Cold Start Measurement (3 runs)
echo ""
echo "2. Cold Startup Benchmark (Home Screen, 3 runs):"
COLD_TIMES=()
for i in 1 2 3; do
    "$ADB_BIN" shell am force-stop "$PACKAGE_NAME" || true
    sleep 1
    STARTUP_OUTPUT=$("$ADB_BIN" shell am start -W -n "$PACKAGE_NAME/$MAIN_ACTIVITY" | tr -d '\r' || true)
    TOTAL_TIME=$(echo "$STARTUP_OUTPUT" | grep "TotalTime:" | awk '{print $2}' || echo "0")
    COLD_TIMES+=("$TOTAL_TIME")
    echo "   - Run $i: ${TOTAL_TIME} ms"
    sleep 1
done

python3 -c "
times = [int(x) for x in '${COLD_TIMES[*]}'.split() if int(x) > 0]
if times:
    mean = sum(times) / len(times)
    print(f'   -> Cold Start Mean: {mean:.1f} ms (Min: {min(times)} ms, Max: {max(times)} ms)')
"

# 4. Memory (PSS) Measurement Function
measure_memory() {
    local WORKLOAD_NAME="$1"
    local URL="${2:-}"
    local SETTLE_SECONDS="${3:-4}"

    if [ -n "$URL" ]; then
        "$ADB_BIN" shell am start -a android.intent.action.VIEW -d "$URL" "$PACKAGE_NAME" >/dev/null 2>&1 || true
    fi

    sleep "$SETTLE_SECONDS"

    local HOST_PSS=$("$ADB_BIN" shell dumpsys meminfo "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' | grep "TOTAL PSS:" | head -n 1 | awk '{print $3}' || echo "0")
    local RENDERER_PSS="0"
    local RENDERER_PID=$("$ADB_BIN" shell ps -ef 2>/dev/null | tr -d '\r' | grep "com.google.android.webview:sandboxed_process" | grep -v "grep" | awk '{print $2}' | head -n 1 || true)
    if [ -n "$RENDERER_PID" ]; then
        RENDERER_PSS=$("$ADB_BIN" shell dumpsys meminfo "$RENDERER_PID" 2>/dev/null | tr -d '\r' | grep "TOTAL PSS:" | head -n 1 | awk '{print $3}' || echo "0")
    fi

    echo ""
    echo "3. Workload: $WORKLOAD_NAME"
    if [ -n "$URL" ]; then
        echo "   - Target URL:        $URL"
    fi
    echo "   - Host App PSS:      ${HOST_PSS} kB ($(python3 -c "print(f'{float($HOST_PSS)/1024:.2f} MiB | {float($HOST_PSS)/1000:.2f} MB')"))"
    if [ "$RENDERER_PSS" != "0" ] && [ -n "$RENDERER_PSS" ]; then
        local COMBINED_PSS=$(( HOST_PSS + RENDERER_PSS ))
        echo "   - Renderer PSS:      ${RENDERER_PSS} kB ($(python3 -c "print(f'{float($RENDERER_PSS)/1024:.2f} MiB | {float($RENDERER_PSS)/1000:.2f} MB')")) [PID $RENDERER_PID]"
        echo "   - Combined Total:    ${COMBINED_PSS} kB ($(python3 -c "print(f'{float($COMBINED_PSS)/1024:.2f} MiB | {float($COMBINED_PSS)/1000:.2f} MB')"))"
    else
        echo "   - Renderer PSS:      Inactive / Not Spawned"
    fi
}

echo ""
echo "=== Memory Consumption Workload Benchmarks ==="
# Workload A: Home Screen Idle
"$ADB_BIN" shell am force-stop "$PACKAGE_NAME" || true
sleep 1
"$ADB_BIN" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" >/dev/null 2>&1 || true
measure_memory "Home Screen Idle" "" 3

# Workload B: Wikipedia Mobile
measure_memory "Wikipedia Mobile Portal" "https://en.m.wikipedia.org/wiki/Television" 5

# Workload C: Wikipedia Desktop (Complex Page with Media, Tables, and References)
measure_memory "Wikipedia Desktop Full Page" "https://en.wikipedia.org/wiki/Television" 6

echo ""
echo "=================================================="
