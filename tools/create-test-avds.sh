#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

HOST_ARCH="$(uname -m)"
if [ "$HOST_ARCH" = "arm64" ] || [ "$HOST_ARCH" = "aarch64" ]; then
    ABI="arm64-v8a"
else
    ABI="x86_64"
fi

echo "Detecting compile SDK from app/build.gradle.kts..."
COMPILE_SDK=$(grep -E "release\([0-9]+\)" app/build.gradle.kts | grep -oE "[0-9]+" || echo "36")
echo "Recorded Gradle compile SDK: $COMPILE_SDK"

AVDMANAGER=""
if command -v avdmanager >/dev/null 2>&1; then
    AVDMANAGER="avdmanager"
elif [ -f "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager" ]; then
    AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
elif [ -f "$ANDROID_SDK_ROOT/tools/bin/avdmanager" ]; then
    AVDMANAGER="$ANDROID_SDK_ROOT/tools/bin/avdmanager"
fi

SYSIMG_23="$ANDROID_SDK_ROOT/system-images/android-23/google_apis/$ABI"
SYSIMG_36="$ANDROID_SDK_ROOT/system-images/android-36/google_apis/$ABI"

MISSING_IMAGES=()
if [ ! -d "$SYSIMG_23" ]; then
    MISSING_IMAGES+=("system-images;android-23;google_apis;$ABI")
fi
if [ ! -d "$SYSIMG_36" ]; then
    MISSING_IMAGES+=("system-images;android-36;google_apis;$ABI")
fi

if [ ${#MISSING_IMAGES[@]} -gt 0 ] || [ -z "$AVDMANAGER" ]; then
    echo "ERROR: Required Android test system images or avdmanager tool are missing:" >&2
    if [ -z "$AVDMANAGER" ]; then
        echo " - avdmanager command line tool not found in $ANDROID_SDK_ROOT" >&2
    fi
    for img in "${MISSING_IMAGES[@]}"; do
        echo " - $img" >&2
    done
    echo "Failing rather than selecting another API, ABI, or ambient device." >&2
    exit 1
fi

echo "Creating AVD mobile-pos-api23..."
"$AVDMANAGER" create avd --name "mobile-pos-api23" --package "system-images;android-23;google_apis;$ABI" --force

echo "Creating AVD mobile-pos-api36..."
"$AVDMANAGER" create avd --name "mobile-pos-api36" --package "system-images;android-36;google_apis;$ABI" --force

AVD_DIR_23="$HOME/.android/avd/mobile-pos-api23.avd"
if [ -d "$AVD_DIR_23" ]; then
    cat <<EOF >> "$AVD_DIR_23/config.ini"
hw.cpu.ncore=2
hw.ramSize=1024MB
hw.lcd.width=720
hw.lcd.height=1280
hw.lcd.density=320
EOF
fi

AVD_DIR_36="$HOME/.android/avd/mobile-pos-api36.avd"
if [ -d "$AVD_DIR_36" ]; then
    cat <<EOF >> "$AVD_DIR_36/config.ini"
hw.cpu.ncore=4
hw.ramSize=2048MB
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
EOF
fi

echo "Successfully created test AVDs mobile-pos-api23 and mobile-pos-api36."
