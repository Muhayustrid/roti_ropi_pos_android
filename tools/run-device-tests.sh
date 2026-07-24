#!/usr/bin/env bash
set -euo pipefail

TARGET_API="${1:-}"
if [ -z "$TARGET_API" ]; then
    echo "Usage: $0 <api23|api36> [--matrix]" >&2
    exit 1
fi

case "$TARGET_API" in
    api23)
        AVD_NAME="mobile-pos-api23"
        EXPECTED_API="23"
        ;;
    api36)
        AVD_NAME="mobile-pos-api36"
        EXPECTED_API="36"
        ;;
    *)
        echo "Error: Invalid target API '$TARGET_API'. Must be api23 or api36." >&2
        exit 1
        ;;
esac

ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

EMULATOR=""
if [ -f "$ANDROID_SDK_ROOT/emulator/emulator" ]; then
    EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
elif command -v emulator >/dev/null 2>&1; then
    EMULATOR="$(command -v emulator)"
fi

ADB=""
if [ -f "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
fi

if [ -z "$EMULATOR" ] || [ -z "$ADB" ]; then
    echo "ERROR: Android emulator or adb tool not found in $ANDROID_SDK_ROOT" >&2
    exit 1
fi

REPORT_DIR="app/build/reports/mobile-pos-devices/$TARGET_API"
mkdir -p "$REPORT_DIR"

AVD_EXISTS=false
if "$EMULATOR" -list-avds 2>/dev/null | grep -q "^${AVD_NAME}$"; then
    AVD_EXISTS=true
fi

if [ "$AVD_EXISTS" = "false" ]; then
    echo "ERROR: Required test AVD '$AVD_NAME' for $TARGET_API is not created or system image is unavailable." >&2
    echo "Run ./tools/create-test-avds.sh to verify and set up required AVDs." >&2
    exit 1
fi

echo "Running device tests on $AVD_NAME (API $EXPECTED_API)..."

# Check if already running
RUNNING_SERIAL=""
for SERIAL in $("$ADB" devices | grep "emulator-" | cut -f1); do
    CURRENT_AVD=$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null | head -n 1 | tr -d '\r' || true)
    if [ "$CURRENT_AVD" = "$AVD_NAME" ]; then
        RUNNING_SERIAL="$SERIAL"
        break
    fi
done

STARTED_EMULATOR=false
if [ -z "$RUNNING_SERIAL" ]; then
    echo "Starting emulator $AVD_NAME..."
    "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-snapshot -wipe-data > "$REPORT_DIR/emulator.log" 2>&1 &
    EMULATOR_PID=$!
    STARTED_EMULATOR=true
    
    # Wait for device to appear
    echo "Waiting for device to connect..."
    MAX_WAIT=120
    WAIT_COUNT=0
    while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
        RUNNING_SERIAL=$("$ADB" devices | grep "emulator-" | cut -f1 | head -n 1 || true)
        if [ -n "$RUNNING_SERIAL" ]; then
            BOOTED=$("$ADB" -s "$RUNNING_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
            if [ "$BOOTED" = "1" ]; then
                break
            fi
        fi
        sleep 2
        WAIT_COUNT=$((WAIT_COUNT + 2))
    done
fi

if [ -z "$RUNNING_SERIAL" ]; then
    echo "ERROR: Failed to start or connect to emulator $AVD_NAME within timeout." >&2
    exit 1
fi

# Record device properties
"$ADB" -s "$RUNNING_SERIAL" shell getprop > "$REPORT_DIR/device_properties.txt"

# Build APKs
echo "Building APKs..."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
./gradlew assembleDebug assembleDebugAndroidTest

# Install APKs
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

echo "Installing APKs on $RUNNING_SERIAL..."
"$ADB" -s "$RUNNING_SERIAL" install -r "$APP_APK"
"$ADB" -s "$RUNNING_SERIAL" install -r "$TEST_APK"

echo "Running instrumentation tests on $RUNNING_SERIAL..."
INSTRUMENTATION_OUTPUT="$REPORT_DIR/instrumentation.txt"
set +e
"$ADB" -s "$RUNNING_SERIAL" shell am instrument -w -r \
    com.rotiropi.pos_erpnext.test/androidx.test.runner.AndroidJUnitRunner > "$INSTRUMENTATION_OUTPUT" 2>&1
TEST_EXIT_CODE=$?
set -e

cat "$INSTRUMENTATION_OUTPUT"

if [ "$STARTED_EMULATOR" = "true" ]; then
    echo "Stopping emulator $AVD_NAME..."
    "$ADB" -s "$RUNNING_SERIAL" emu kill || true
fi

if [ $TEST_EXIT_CODE -ne 0 ] || grep -q "FAILURES!!!" "$INSTRUMENTATION_OUTPUT"; then
    echo "Device tests FAILED on $TARGET_API." >&2
    exit 1
fi

echo "Device tests PASSED on $TARGET_API."
