#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Usage: %s [--check]\n' "${0##*/}" >&2
}

MODE="create"
if [ "$#" -gt 1 ]; then
    usage
    exit 2
fi
if [ "$#" -eq 1 ]; then
    if [ "$1" = "--check" ]; then
        MODE="check"
    else
        usage
        exit 2
    fi
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
    arm64|aarch64) ABI="arm64-v8a" ;;
    *) ABI="x86_64" ;;
esac

GRADLE_FILE="$ROOT_DIR/app/build.gradle.kts"
if [ ! -f "$GRADLE_FILE" ]; then
    printf 'ERROR: Gradle file not found: %s\n' "$GRADLE_FILE" >&2
    exit 1
fi

COMPILE_RELEASE="$(grep -E '^[[:space:]]*version[[:space:]]*=[[:space:]]*release\([0-9]+\)[[:space:]]*\{' "$GRADLE_FILE" \
    | sed -n 's/.*release(\([0-9][0-9]*\)).*/\1/p' | head -n 1)"
COMPILE_MINOR="$(grep -E '^[[:space:]]*minorApiLevel[[:space:]]*=[[:space:]]*[0-9]+[[:space:]]*$' "$GRADLE_FILE" \
    | sed -n 's/.*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n 1)"
if [ -z "$COMPILE_RELEASE" ] || [ -z "$COMPILE_MINOR" ]; then
    printf 'ERROR: Could not determine compile SDK release and minorApiLevel from %s\n' "$GRADLE_FILE" >&2
    exit 1
fi
COMPILE_PLATFORM="$COMPILE_RELEASE.$COMPILE_MINOR"
PLATFORM_DIR="$ANDROID_SDK_ROOT/platforms/android-$COMPILE_PLATFORM"
if [ ! -d "$PLATFORM_DIR" ]; then
    printf 'ERROR: Required compile platform is missing: platforms;android-%s\n' "$COMPILE_PLATFORM" >&2
    exit 1
fi

resolve_tool() {
    local sdk_path="$1"
    local tool_name="$2"
    local candidate
    for candidate in "$sdk_path" "$sdk_path/emulator" "$sdk_path/cmdline-tools/latest/bin" "$sdk_path/tools/bin"; do
        if [ -f "$candidate/$tool_name" ] && [ -x "$candidate/$tool_name" ]; then
            printf '%s\n' "$candidate/$tool_name"
            return 0
        fi
    done
    if command -v "$tool_name" >/dev/null 2>&1; then
        command -v "$tool_name"
        return 0
    fi
    return 1
}

if ! AVDMANAGER="$(resolve_tool "$ANDROID_SDK_ROOT" avdmanager)"; then
    printf 'ERROR: avdmanager not found under %s or PATH\n' "$ANDROID_SDK_ROOT" >&2
    exit 1
fi
if ! EMULATOR="$(resolve_tool "$ANDROID_SDK_ROOT" emulator)"; then
    printf 'ERROR: emulator not found under %s or PATH\n' "$ANDROID_SDK_ROOT" >&2
    exit 1
fi
EMULATOR_VERSION="$($EMULATOR -version 2>&1 | awk 'NR == 1 { print; exit }')"
if [ -z "$EMULATOR_VERSION" ]; then
    printf 'ERROR: Could not determine emulator version from %s\n' "$EMULATOR" >&2
    exit 1
fi

validate_image() {
    local api="$1"
    local image_dir="$ANDROID_SDK_ROOT/system-images/android-$api/google_apis/$ABI"
    local source_properties="$image_dir/source.properties"
    local package_id="system-images;android-$api;google_apis;$ABI"
    local actual_api actual_tag actual_abi revision
    if [ ! -d "$image_dir" ] || [ ! -f "$source_properties" ]; then
        printf 'ERROR: Required system image is missing: %s\n' "$package_id" >&2
        return 1
    fi
    actual_api="$(sed -n 's/^AndroidVersion.ApiLevel=//p' "$source_properties" | head -n 1)"
    actual_tag="$(sed -n 's/^SystemImage.TagId=//p' "$source_properties" | head -n 1)"
    actual_abi="$(sed -n 's/^SystemImage.Abi=//p' "$source_properties" | head -n 1)"
    revision="$(sed -n 's/^Pkg.Revision=//p' "$source_properties" | head -n 1)"
    if [ "$actual_api" != "$api" ] || [ "$actual_tag" != "google_apis" ] || [ "$actual_abi" != "$ABI" ] || [ -z "$revision" ]; then
        printf 'ERROR: Metadata mismatch for %s (api=%s tag=%s abi=%s revision=%s)\n' \
            "$package_id" "${actual_api:-missing}" "${actual_tag:-missing}" "${actual_abi:-missing}" "${revision:-missing}" >&2
        return 1
    fi
    printf '%s\n' "$revision"
}

API23_PACKAGE="system-images;android-23;google_apis;$ABI"
API36_PACKAGE="system-images;android-36;google_apis;$ABI"
API23_REVISION="$(validate_image 23)"
API36_REVISION="$(validate_image 36)"

if [ "$MODE" = "check" ]; then
    printf 'compile_platform=%s\n' "$COMPILE_PLATFORM"
    printf 'host_abi=%s\n' "$ABI"
    printf 'api23_package=%s\n' "$API23_PACKAGE"
    printf 'api36_package=%s\n' "$API36_PACKAGE"
    exit 0
fi

configure_avd() {
    local avd_name="$1"
    local config_file="$HOME/.android/avd/$avd_name.avd/config.ini"
    shift
    if [ ! -f "$config_file" ]; then
        printf 'ERROR: AVD config missing after creation: %s\n' "$config_file" >&2
        return 1
    fi
    local temp_file
    temp_file="$(mktemp "$config_file.tmp.XXXXXX")"
    awk '
        BEGIN {
            split("hw.cpu.ncore hw.ramSize hw.lcd.width hw.lcd.height hw.lcd.density hw.keyboard hw.locale timezone snapshot.present snapshot.autoSnapshot fastboot.forceColdBoot fastboot.forceFastBoot", keys)
            for (i in keys) managed[keys[i]] = 1
        }
        /^[[:space:]]*[^=]+=/ {
            key = $0
            sub(/^[[:space:]]*/, "", key)
            sub(/=.*/, "", key)
            if (managed[key]) next
        }
        { print }
    ' "$config_file" > "$temp_file"
    while [ "$#" -gt 1 ]; do
        printf '%s=%s\n' "$1" "$2" >> "$temp_file"
        shift 2
    done
    if [ "$#" -ne 0 ]; then
        rm -f "$temp_file"
        printf 'ERROR: Internal AVD configuration error\n' >&2
        return 1
    fi
    mv "$temp_file" "$config_file"
}

printf 'Creating AVD mobile-pos-api23...\n'
printf 'no\n' | "$AVDMANAGER" create avd --name mobile-pos-api23 --package "$API23_PACKAGE" --force >/dev/null
configure_avd mobile-pos-api23 \
    hw.cpu.ncore 2 hw.ramSize 1024 hw.lcd.width 720 hw.lcd.height 1280 hw.lcd.density 320 \
    hw.keyboard yes hw.locale en-US timezone UTC snapshot.present no snapshot.autoSnapshot no \
    fastboot.forceColdBoot yes fastboot.forceFastBoot no

printf 'Creating AVD mobile-pos-api36...\n'
printf 'no\n' | "$AVDMANAGER" create avd --name mobile-pos-api36 --package "$API36_PACKAGE" --force >/dev/null
configure_avd mobile-pos-api36 \
    hw.cpu.ncore 4 hw.ramSize 2048 hw.lcd.width 1080 hw.lcd.height 1920 hw.lcd.density 420 \
    hw.keyboard yes hw.locale en-US timezone UTC snapshot.present no snapshot.autoSnapshot no \
    fastboot.forceColdBoot yes fastboot.forceFastBoot no

REPORT_DIR="$ROOT_DIR/app/build/reports/mobile-pos-devices"
REPORT_FILE="$REPORT_DIR/avd-metadata.txt"
mkdir -p "$REPORT_DIR"
REPORT_TMP="$(mktemp "$REPORT_FILE.tmp.XXXXXX")"
cat > "$REPORT_TMP" <<EOF
compile_platform=$COMPILE_PLATFORM
host_abi=$ABI
avdmanager_path=$AVDMANAGER
emulator_version=$EMULATOR_VERSION
api23_package=$API23_PACKAGE
api23_revision=$API23_REVISION
api36_package=$API36_PACKAGE
api36_revision=$API36_REVISION
EOF
mv "$REPORT_TMP" "$REPORT_FILE"
printf 'Successfully configured test AVDs mobile-pos-api23 and mobile-pos-api36.\n'
