#!/bin/bash
set -euo pipefail

# Veyra TV: Local Emulator Setup & Launch Script
# Automates AVD creation, emulator startup, APK sideloading, and launch.

SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR_BIN="$SDK_DIR/emulator/emulator"
ADB_BIN="$SDK_DIR/platform-tools/adb"
AVD_NAME="Veyra_TV"
SYSTEM_IMAGE="system-images;android-34;google-tv;arm64-v8a"
APK_PATH="dist/veyra-tv-release.apk"

export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$SDK_DIR/platform-tools:/opt/homebrew/bin:$PATH"

echo "=================================================="
echo " VEYRA TV: LOCAL EMULATOR RUNNER"
echo "=================================================="

# 1. Check if AVD exists
AVD_EXISTS=$($EMULATOR_BIN -list-avds | grep "^${AVD_NAME}$" || true)

if [ -z "$AVD_EXISTS" ]; then
    echo "Creating Android TV AVD: $AVD_NAME..."
    echo "no" | avdmanager create avd \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE" \
        --device "tv_1080p" \
        --force
    echo "AVD created successfully."
else
    echo "AVD $AVD_NAME already exists."
fi

# 2. Check if emulator is already running
IS_RUNNING=$($ADB_BIN devices | grep "emulator-" | wc -l | tr -d '[:space:]' || true)

if [ "$IS_RUNNING" = "0" ] || [ -z "$IS_RUNNING" ]; then
    echo "Starting $AVD_NAME emulator in background..."
    "$EMULATOR_BIN" -avd "$AVD_NAME" -dns-server 8.8.8.8,1.1.1.1 -gpu host &
    EMULATOR_PID=$!
    echo "Emulator launched (PID: $EMULATOR_PID). Waiting for boot..."
else
    echo "An emulator is already running."
fi

# 3. Wait for device boot
echo "Waiting for device to finish booting..."
"$ADB_BIN" wait-for-device
while [ "$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done
echo "Emulator is fully booted!"

# 4. Install Veyra TV
if [ -f "$APK_PATH" ]; then
    echo "Installing $APK_PATH..."
    "$ADB_BIN" install -r "$APK_PATH"
else
    echo "Building APK first..."
    ./gradlew assembleRelease
    "$ADB_BIN" install -r "$APK_PATH"
fi

# 5. Launch Veyra TV
echo "Launching Veyra TV..."
"$ADB_BIN" shell am start -n dev.veyra.tv.browser/dev.veyra.tv.browser.ui.MainActivity

echo "=================================================="
echo " Veyra TV is running!"
echo " Remote navigation tips for Mac keyboard:"
echo " - Arrow Keys: D-pad Up / Down / Left / Right"
echo " - Enter / Return: D-pad Center / Select"
echo " - Escape / Backspace: Back button"
echo " - F2 / Menu: Menu button"
echo "=================================================="
