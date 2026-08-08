#!/usr/bin/env bash
set -euo pipefail

TARGET_API=""
INSTRUMENTATION_TIMEOUT_SEC=300

while [[ $# -gt 0 ]]; do
    case "$1" in
        api23|api36)
            if [ -n "$TARGET_API" ]; then
                echo "Error: Multiple target APIs specified ('$TARGET_API' and '$1')." >&2
                exit 1
            fi
            TARGET_API="$1"
            shift
            ;;
        --matrix)
            echo "Error: --matrix mode is not defined for Task 1B." >&2
            exit 1
            ;;
        *)
            echo "Error: Unknown or invalid argument '$1'." >&2
            echo "Usage: $0 <api23|api36>" >&2
            exit 1
            ;;
    esac
done

if [ -z "$TARGET_API" ]; then
    echo "Usage: $0 <api23|api36>" >&2
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
rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"

COMMANDS_FILE="$REPORT_DIR/commands.txt"
VERSIONS_FILE="$REPORT_DIR/versions.txt"
DEVICE_PROP_FILE="$REPORT_DIR/device_properties.txt"
RUNTIME_STATE_FILE="$REPORT_DIR/runtime_state.txt"
INSTRUMENTATION_FILE="$REPORT_DIR/instrumentation.txt"
EMULATOR_LOG_FILE="$REPORT_DIR/emulator.log"

run_cmd_bounded() {
    local TIMEOUT_SEC="$1"
    shift
    local RC=0
    local OUT=""

    OUT=$(python3 -c "
import subprocess, sys, os, signal

timeout_sec = float(sys.argv[1])
cmd_argv = sys.argv[2:]

try:
    proc = subprocess.Popen(cmd_argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
    try:
        stdout, stderr = proc.communicate(timeout=timeout_sec)
        rc = proc.returncode
    except subprocess.TimeoutExpired:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except Exception:
            pass
        stdout, stderr = proc.communicate()
        rc = 124
except Exception as e:
    stdout, stderr = '', str(e)
    rc = 1

if stdout:
    sys.stdout.write(stdout)
if stderr:
    sys.stderr.write(stderr)

sys.exit(rc)
" "$TIMEOUT_SEC" "$@") || RC=$?

    # Quoted representation of command for evidence file
    local CMD_QUOTED=""
    for ARG in "$@"; do
        CMD_QUOTED="$CMD_QUOTED $(printf '%q' "$ARG")"
    done
    echo "[$RC]${CMD_QUOTED}" >> "$COMMANDS_FILE"

    if [ -n "$OUT" ]; then
        echo "$OUT"
    fi
    return $RC
}

get_serial_identity() {
    local SERIAL="$1"
    local AVD_NAME_OUT=""
    local API_LEVEL_OUT=""
    set +e
    AVD_NAME_OUT=$(run_cmd_bounded 5 "$ADB" -s "$SERIAL" emu avd name 2>/dev/null | head -n 1 | tr -d '\r')
    API_LEVEL_OUT=$(run_cmd_bounded 5 "$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | head -n 1 | tr -d '\r')
    set -e
    echo "${AVD_NAME_OUT}:${API_LEVEL_OUT}"
}

wait_for_serial_removal() {
    local SERIAL="$1"
    local DEADLINE
    DEADLINE=$(python3 -c 'import time; print(time.monotonic() + 30.0)')

    while true; do
        local DEVICES=""
        DEVICES=$(run_cmd_bounded 5 "$ADB" devices 2>/dev/null || true)
        if ! echo "$DEVICES" | grep -q "^${SERIAL}[[:space:]]"; then
            return 0
        fi

        local NOW
        NOW=$(python3 -c 'import time; print(time.monotonic())')
        if [ "$(python3 -c "print(1 if $NOW >= $DEADLINE else 0)")" -eq 1 ]; then
            return 1
        fi
        python3 -c 'import time; time.sleep(0.5)'
    done
}

AVD_EXISTS=false
if "$EMULATOR" -list-avds 2>/dev/null | grep -q "^${AVD_NAME}$"; then
    AVD_EXISTS=true
fi

if [ "$AVD_EXISTS" = "false" ]; then
    echo "ERROR: Required test AVD '$AVD_NAME' for $TARGET_API is not created or system image is unavailable." >&2
    echo "Run ./tools/create-test-avds.sh to verify and set up required AVDs." >&2
    exit 1
fi

STARTED_EMULATOR=false
RUNNING_SERIAL=""
EMULATOR_PID=""

cleanup() {
    local EXIT_CODE=$?
    if [ "$STARTED_EMULATOR" = "true" ]; then
        if [ -n "$RUNNING_SERIAL" ]; then
            echo "Cleaning up owned emulator $AVD_NAME on $RUNNING_SERIAL..."
            local IDENTS
            IDENTS=$(get_serial_identity "$RUNNING_SERIAL")
            local CUR_AVD="${IDENTS%%:*}"
            local CUR_API="${IDENTS##*:}"
            if [ "$CUR_AVD" = "$AVD_NAME" ] && [ "$CUR_API" = "$EXPECTED_API" ]; then
                echo "Stopping owned emulator $AVD_NAME on $RUNNING_SERIAL..."
                run_cmd_bounded 10 "$ADB" -s "$RUNNING_SERIAL" emu kill >/dev/null 2>&1 || true
                if ! wait_for_serial_removal "$RUNNING_SERIAL"; then
                    echo "WARNING: Serial $RUNNING_SERIAL remained registered after emulator shutdown." >&2
                fi
            else
                echo "WARNING: Serial $RUNNING_SERIAL identity mismatch ($CUR_AVD:$CUR_API). Terminating PID $EMULATOR_PID..."
                if [ -n "$EMULATOR_PID" ]; then
                    python3 -c "import os, signal, sys; os.killpg(os.getpgid(int(sys.argv[1])), signal.SIGKILL) if sys.argv[1] else None" "$EMULATOR_PID" 2>/dev/null || true
                fi
            fi
        elif [ -n "$EMULATOR_PID" ]; then
            echo "Terminating owned emulator process PID $EMULATOR_PID..."
            python3 -c "import os, signal, sys; os.killpg(os.getpgid(int(sys.argv[1])), signal.SIGKILL) if sys.argv[1] else None" "$EMULATOR_PID" 2>/dev/null || true
        fi
    fi
    exit $EXIT_CODE
}
trap cleanup EXIT INT TERM

echo "Scanning for existing running emulator for $AVD_NAME (API $EXPECTED_API)..."
INITIAL_DEVS=""
INITIAL_DEVS=$(run_cmd_bounded 10 "$ADB" devices 2>/dev/null || true)
INITIAL_SERIALS=$(echo "$INITIAL_DEVS" | grep "emulator-" | cut -f1 || true)

MATCHING_EXISTING=()
if [ -n "$INITIAL_SERIALS" ]; then
    for SERIAL in $INITIAL_SERIALS; do
        IDENTS=$(get_serial_identity "$SERIAL")
        CUR_AVD="${IDENTS%%:*}"
        CUR_API="${IDENTS##*:}"
        if [ "$CUR_AVD" = "$AVD_NAME" ] && { [ "$CUR_API" = "$EXPECTED_API" ] || [ -z "$CUR_API" ]; }; then
            MATCHING_EXISTING+=("$SERIAL")
        fi
    done
fi

if [ ${#MATCHING_EXISTING[@]} -gt 1 ]; then
    echo "ERROR: Ambiguous running emulators found for AVD '$AVD_NAME'." >&2
    exit 1
elif [ ${#MATCHING_EXISTING[@]} -eq 1 ]; then
    RUNNING_SERIAL="${MATCHING_EXISTING[0]}"
    echo "Reusing existing running emulator $RUNNING_SERIAL ($AVD_NAME, API $EXPECTED_API)"
else
    echo "Starting new emulator $AVD_NAME..."
    echo "[0] $EMULATOR -avd $AVD_NAME -no-window -no-audio -no-snapshot -wipe-data" >> "$COMMANDS_FILE"

    # Start process group for new emulator
    EMULATOR_PID=$(python3 -c "
import subprocess, sys
proc = subprocess.Popen([sys.argv[1], '-avd', sys.argv[2], '-no-window', '-no-audio', '-no-snapshot', '-wipe-data'], stdout=open(sys.argv[3], 'w'), stderr=subprocess.STDOUT, start_new_session=True)
print(proc.pid)
" "$EMULATOR" "$AVD_NAME" "$EMULATOR_LOG_FILE")
    STARTED_EMULATOR=true

    REG_START=$(python3 -c 'import time; print(time.monotonic())')
    REG_DEADLINE=$(python3 -c "import time; print($REG_START + 120.0)")
    CANDIDATE_SERIALS=()
    LAST_REGISTRATION_STATE="observed=[none], candidates=[none]"

    echo "Waiting for newly spawned emulator serial to register with ADB..."
    while true; do
        NOW=$(python3 -c 'import time; print(time.monotonic())')
        IS_PAST=$(python3 -c "print(1 if $NOW >= $REG_DEADLINE else 0)")
        if [ "$IS_PAST" -eq 1 ]; then
            break
        fi

        REMAINING=$(python3 -c "print(max(0.1, $REG_DEADLINE - $NOW))")
        PROBE_TIMEOUT=$(python3 -c "print(min(5.0, $REMAINING))")

        CURRENT_DEVS=""
        set +e
        CURRENT_DEVS=$(run_cmd_bounded "$PROBE_TIMEOUT" "$ADB" devices 2>/dev/null)
        set -e

        CURRENT_LIST=$(echo "$CURRENT_DEVS" | grep "emulator-" | cut -f1 || true)
        OBSERVED_ITEMS=()
        if [ -n "$CURRENT_LIST" ]; then
            for S in $CURRENT_LIST; do
                IDENTS=$(get_serial_identity "$S")
                CUR_AVD="${IDENTS%%:*}"
                CUR_API="${IDENTS##*:}"
                OBSERVED_ITEMS+=("$S(${CUR_AVD:-unknown}:${CUR_API:-unknown})")

                IS_OLD=false
                if [ -n "$INITIAL_SERIALS" ]; then
                    for OLD in $INITIAL_SERIALS; do
                        if [ "$S" = "$OLD" ]; then
                            IS_OLD=true
                            break
                        fi
                    done
                fi
                if [ "$IS_OLD" = "false" ]; then
                    if [ "$CUR_AVD" = "$AVD_NAME" ] && [ "$CUR_API" = "$EXPECTED_API" ]; then
                        ALREADY_CANDIDATE=false
                        if [ ${#CANDIDATE_SERIALS[@]} -gt 0 ]; then
                            for C in "${CANDIDATE_SERIALS[@]}"; do
                                if [ "$C" = "$S" ]; then
                                    ALREADY_CANDIDATE=true
                                    break
                                fi
                            done
                        fi
                        if [ "$ALREADY_CANDIDATE" = "false" ]; then
                            CANDIDATE_SERIALS+=("$S")
                        fi
                    fi
                fi
            done
        fi

        OBS_STR="none"
        if [ ${#OBSERVED_ITEMS[@]} -gt 0 ]; then
            OBS_STR="${OBSERVED_ITEMS[*]}"
        fi

        CAND_ITEMS=()
        if [ ${#CANDIDATE_SERIALS[@]} -gt 0 ]; then
            for C in "${CANDIDATE_SERIALS[@]}"; do
                C_IDENTS=$(get_serial_identity "$C")
                C_AVD="${C_IDENTS%%:*}"
                C_API="${C_IDENTS##*:}"
                CAND_ITEMS+=("$C(${C_AVD:-unknown}:${C_API:-unknown})")
            done
        fi

        CAND_STR="none"
        if [ ${#CAND_ITEMS[@]} -gt 0 ]; then
            CAND_STR="${CAND_ITEMS[*]}"
        fi

        LAST_REGISTRATION_STATE="observed=[$OBS_STR], candidates=[$CAND_STR]"

        if [ ${#CANDIDATE_SERIALS[@]} -eq 1 ]; then
            RUNNING_SERIAL="${CANDIDATE_SERIALS[0]}"
            break
        elif [ ${#CANDIDATE_SERIALS[@]} -gt 1 ]; then
            echo "ERROR: Multiple ambiguous new serials registered for $AVD_NAME." >&2
            exit 1
        fi

        SLEEP_TIME=$(python3 -c "import time; print(min(1.0, max(0.1, $REG_DEADLINE - time.monotonic())))")
        python3 -c "import time, sys; time.sleep(float(sys.argv[1]))" "$SLEEP_TIME"
    done

    if [ -z "$RUNNING_SERIAL" ]; then
        echo "ERROR: Target API '$TARGET_API' ($AVD_NAME) serial registration timed out after 120s (serial: ${RUNNING_SERIAL:-unknown}). Last observed state: '$LAST_REGISTRATION_STATE'. Emulator process PID: ${EMULATOR_PID:-unknown}, log: $EMULATOR_LOG_FILE" >&2
        exit 1
    fi
fi

echo "Ensuring root permissions on $RUNNING_SERIAL..."
set +e
run_cmd_bounded 10 "$ADB" -s "$RUNNING_SERIAL" root >/dev/null 2>&1
sleep 3
run_cmd_bounded 10 "$ADB" -s "$RUNNING_SERIAL" wait-for-device >/dev/null 2>&1
set -e

echo "Waiting for device readiness on $RUNNING_SERIAL..."
READ_START=$(python3 -c 'import time; print(time.monotonic())')
READ_DEADLINE=$(python3 -c "import time; print($READ_START + 120.0)")

# 1. State = device
READY_STATE=false
LAST_STATE="unknown"
while true; do
    NOW=$(python3 -c 'import time; print(time.monotonic())')
    IS_PAST=$(python3 -c "print(1 if $NOW >= $READ_DEADLINE else 0)")
    if [ "$IS_PAST" -eq 1 ]; then
        break
    fi

    REMAINING=$(python3 -c "print(max(0.1, $READ_DEADLINE - $NOW))")
    PROBE_TIMEOUT=$(python3 -c "print(min(5.0, $REMAINING))")

    set +e
    LAST_STATE=$(run_cmd_bounded "$PROBE_TIMEOUT" "$ADB" -s "$RUNNING_SERIAL" get-state 2>/dev/null | tr -d '\r')
    set -e
    if [ "$LAST_STATE" = "device" ]; then
        READY_STATE=true
        break
    fi
    SLEEP_TIME=$(python3 -c "import time; print(min(1.0, max(0.1, $READ_DEADLINE - time.monotonic())))")
    python3 -c "import time, sys; time.sleep(float(sys.argv[1]))" "$SLEEP_TIME"
done

if [ "$READY_STATE" = "false" ]; then
    echo "ERROR: Device state check timed out after 120s on target API '$TARGET_API' (serial: ${RUNNING_SERIAL:-unknown}). Last observed state: '$LAST_STATE'. Emulator log: $EMULATOR_LOG_FILE" >&2
    exit 1
fi

# 2. sys.boot_completed = 1
READY_BOOT=false
LAST_BOOT="0"
while true; do
    NOW=$(python3 -c 'import time; print(time.monotonic())')
    IS_PAST=$(python3 -c "print(1 if $NOW >= $READ_DEADLINE else 0)")
    if [ "$IS_PAST" -eq 1 ]; then
        break
    fi

    REMAINING=$(python3 -c "print(max(0.1, $READ_DEADLINE - $NOW))")
    PROBE_TIMEOUT=$(python3 -c "print(min(5.0, $REMAINING))")

    set +e
    LAST_BOOT=$(run_cmd_bounded "$PROBE_TIMEOUT" "$ADB" -s "$RUNNING_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    set -e
    if [ "$LAST_BOOT" = "1" ]; then
        READY_BOOT=true
        break
    fi
    SLEEP_TIME=$(python3 -c "import time; print(min(1.0, max(0.1, $READ_DEADLINE - time.monotonic())))")
    python3 -c "import time, sys; time.sleep(float(sys.argv[1]))" "$SLEEP_TIME"
done

if [ "$READY_BOOT" = "false" ]; then
    echo "ERROR: Boot completed check timed out after 120s on target API '$TARGET_API' (serial: ${RUNNING_SERIAL:-unknown}). Last observed state: 'sys.boot_completed=$LAST_BOOT'. Emulator log: $EMULATOR_LOG_FILE" >&2
    exit 1
fi

# 3. pm path android
READY_PM=false
LAST_PM=""
while true; do
    NOW=$(python3 -c 'import time; print(time.monotonic())')
    IS_PAST=$(python3 -c "print(1 if $NOW >= $READ_DEADLINE else 0)")
    if [ "$IS_PAST" -eq 1 ]; then
        break
    fi

    REMAINING=$(python3 -c "print(max(0.1, $READ_DEADLINE - $NOW))")
    PROBE_TIMEOUT=$(python3 -c "print(min(5.0, $REMAINING))")

    set +e
    LAST_PM=$(run_cmd_bounded "$PROBE_TIMEOUT" "$ADB" -s "$RUNNING_SERIAL" shell pm path android 2>/dev/null | tr -d '\r')
    set -e
    if echo "$LAST_PM" | grep -q "package:"; then
        READY_PM=true
        break
    fi
    SLEEP_TIME=$(python3 -c "import time; print(min(1.0, max(0.1, $READ_DEADLINE - time.monotonic())))")
    python3 -c "import time, sys; time.sleep(float(sys.argv[1]))" "$SLEEP_TIME"
done

if [ "$READY_PM" = "false" ]; then
    echo "ERROR: Package manager check timed out after 120s on target API '$TARGET_API' (serial: ${RUNNING_SERIAL:-unknown}). Last observed state: '$LAST_PM'. Emulator log: $EMULATOR_LOG_FILE" >&2
    exit 1
fi

# 4. home activity resolution (API23 dumpsys window non-null focus check with portable POSIX [[:space:]], API36 resolve-activity)
READY_HOME=false
LAST_HOME=""
while true; do
    NOW=$(python3 -c 'import time; print(time.monotonic())')
    IS_PAST=$(python3 -c "print(1 if $NOW >= $READ_DEADLINE else 0)")
    if [ "$IS_PAST" -eq 1 ]; then
        break
    fi

    REMAINING=$(python3 -c "print(max(0.1, $READ_DEADLINE - $NOW))")
    PROBE_TIMEOUT=$(python3 -c "print(min(5.0, $REMAINING))")

    set +e
    if [ "$EXPECTED_API" = "23" ]; then
        LAST_HOME=$(run_cmd_bounded "$PROBE_TIMEOUT" "$ADB" -s "$RUNNING_SERIAL" shell dumpsys window 2>/dev/null)
        if echo "$LAST_HOME" | grep -E "mCurrentFocus=Window\{[0-9a-fA-F]+[[:space:]]+u0[[:space:]]+[^/\}]+/[^[:space:]\}]+" | grep -v "null" >/dev/null; then
            READY_HOME=true
            break
        fi
    else
        LAST_HOME=$(run_cmd_bounded "$PROBE_TIMEOUT" "$ADB" -s "$RUNNING_SERIAL" shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r')
        if echo "$LAST_HOME" | grep -qE "name=|priority="; then
            READY_HOME=true
            break
        fi
    fi
    set -e
    SLEEP_TIME=$(python3 -c "import time; print(min(1.0, max(0.1, $READ_DEADLINE - time.monotonic())))")
    python3 -c "import time, sys; time.sleep(float(sys.argv[1]))" "$SLEEP_TIME"
done

if [ "$READY_HOME" = "false" ]; then
    echo "ERROR: Home activity resolution check timed out after 120s on target API '$TARGET_API' (serial: ${RUNNING_SERIAL:-unknown}). Last observed state: '${LAST_HOME:0:100}'. Emulator log: $EMULATOR_LOG_FILE" >&2
    exit 1
fi

# Verify AVD identity match
VER_IDENTS=$(get_serial_identity "$RUNNING_SERIAL")
VER_AVD="${VER_IDENTS%%:*}"
VER_API="${VER_IDENTS##*:}"
if [ "$VER_AVD" != "$AVD_NAME" ] || [ "$VER_API" != "$EXPECTED_API" ]; then
    echo "ERROR: Serial $RUNNING_SERIAL identity mismatch. Expected $AVD_NAME:$EXPECTED_API, got $VER_AVD:$VER_API" >&2
    exit 1
fi

echo "Recording device metadata and initial properties on $RUNNING_SERIAL..."
SYS_UNAME=$(uname -sr)
ADB_VER=$("$ADB" version | head -n 1)
EMU_VER=$("$EMULATOR" -version | head -n 1)
JAVA_VER=$(java -version 2>&1 | head -n 1)
GRADLE_VER=$(./gradlew --version | grep Gradle | head -n 1 || true)

{
    echo "Target API: $TARGET_API"
    echo "AVD Name: $AVD_NAME"
    echo "Serial: $RUNNING_SERIAL"
    echo "Host OS: $SYS_UNAME"
    echo "ADB Version: $ADB_VER"
    echo "Emulator Version: $EMU_VER"
    echo "Java Version: $JAVA_VER"
    echo "Gradle Version: $GRADLE_VER"
} > "$VERSIONS_FILE"

run_cmd_bounded 10 "$ADB" -s "$RUNNING_SERIAL" shell getprop > "$DEVICE_PROP_FILE"

get_runtime_state() {
    local WIN_ANIM TRANS_ANIM ANIM_DUR FONT_S TZ LOCALE LANG COUNTRY ROT_AUTO ROT_USER EFF_TZ EFF_LOCALE
    set +e
    WIN_ANIM=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings get global window_animation_scale | tr -d '\r')
    TRANS_ANIM=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings get global transition_animation_scale | tr -d '\r')
    ANIM_DUR=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings get global animator_duration_scale | tr -d '\r')
    FONT_S=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings get system font_scale | tr -d '\r')
    TZ=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell getprop persist.sys.timezone | tr -d '\r')
    LOCALE=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell getprop persist.sys.locale | tr -d '\r')
    LANG=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell getprop persist.sys.language | tr -d '\r')
    COUNTRY=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell getprop persist.sys.country | tr -d '\r')
    ROT_AUTO=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings get system accelerometer_rotation | tr -d '\r')
    ROT_USER=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings get system user_rotation | tr -d '\r')

    EFF_TZ=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell date +%Z | tr -d '\r')
    EFF_LOCALE="$LOCALE"
    if [ -z "$EFF_LOCALE" ] && [ -n "$LANG" ] && [ -n "$COUNTRY" ]; then
        EFF_LOCALE="${LANG}_${COUNTRY}"
    fi
    if [ -z "$EFF_LOCALE" ]; then
        EFF_LOCALE=$(run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell getprop ro.product.locale | tr -d '\r')
    fi
    set -e

    echo "window_animation_scale=$WIN_ANIM"
    echo "transition_animation_scale=$TRANS_ANIM"
    echo "animator_duration_scale=$ANIM_DUR"
    echo "font_scale=$FONT_S"
    echo "persist.sys.timezone=$TZ"
    echo "persist.sys.locale=$LOCALE"
    echo "persist.sys.language=$LANG"
    echo "persist.sys.country=$COUNTRY"
    echo "accelerometer_rotation=$ROT_AUTO"
    echo "user_rotation=$ROT_USER"
    echo "effective_timezone=$EFF_TZ"
    echo "effective_locale=$EFF_LOCALE"
}

{
    echo "=== INITIAL RUNTIME STATE ==="
    get_runtime_state
} > "$RUNTIME_STATE_FILE"

echo "Configuring deterministic runtime state on $RUNNING_SERIAL..."
set +e
run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings put global window_animation_scale 0
run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings put global transition_animation_scale 0
run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings put global animator_duration_scale 0
run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings put system font_scale 1.0

if [ "$EXPECTED_API" = "23" ]; then
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell setprop persist.sys.timezone UTC
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell service call alarm 3 s16 UTC
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell setprop persist.sys.language en
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell setprop persist.sys.country US
else
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell service call alarm 3 s16 UTC
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell setprop persist.sys.locale en-US
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell setprop persist.sys.language en
    run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell setprop persist.sys.country US
fi

run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings put system accelerometer_rotation 0
run_cmd_bounded 5 "$ADB" -s "$RUNNING_SERIAL" shell settings put system user_rotation 0
set -e

FINAL_STATE_STR=$(get_runtime_state)

{
    echo ""
    echo "=== FINAL RUNTIME STATE ==="
    echo "$FINAL_STATE_STR"
} >> "$RUNTIME_STATE_FILE"

# Assertion verification
WIN_ANIM=$(echo "$FINAL_STATE_STR" | grep "^window_animation_scale=" | cut -d= -f2)
TRANS_ANIM=$(echo "$FINAL_STATE_STR" | grep "^transition_animation_scale=" | cut -d= -f2)
ANIM_DUR=$(echo "$FINAL_STATE_STR" | grep "^animator_duration_scale=" | cut -d= -f2)
FONT_S=$(echo "$FINAL_STATE_STR" | grep "^font_scale=" | cut -d= -f2)
ROT_AUTO=$(echo "$FINAL_STATE_STR" | grep "^accelerometer_rotation=" | cut -d= -f2)
ROT_USER=$(echo "$FINAL_STATE_STR" | grep "^user_rotation=" | cut -d= -f2)
EFF_TZ=$(echo "$FINAL_STATE_STR" | grep "^effective_timezone=" | cut -d= -f2)
EFF_LOCALE=$(echo "$FINAL_STATE_STR" | grep "^effective_locale=" | cut -d= -f2)

STATE_PASS=true
FAILED_ASSERTS=()

if [ "$WIN_ANIM" != "0" ] && [ "$WIN_ANIM" != "0.0" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("window_animation_scale expected 0, got $WIN_ANIM")
fi
if [ "$TRANS_ANIM" != "0" ] && [ "$TRANS_ANIM" != "0.0" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("transition_animation_scale expected 0, got $TRANS_ANIM")
fi
if [ "$ANIM_DUR" != "0" ] && [ "$ANIM_DUR" != "0.0" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("animator_duration_scale expected 0, got $ANIM_DUR")
fi
if [ "$FONT_S" != "1.0" ] && [ "$FONT_S" != "1" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("font_scale expected 1.0, got $FONT_S")
fi
if [ "$ROT_AUTO" != "0" ] || [ "$ROT_USER" != "0" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("orientation expected portrait (0/0), got auto=$ROT_AUTO user=$ROT_USER")
fi
if [ "$EFF_TZ" != "UTC" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("effective_timezone expected UTC, got $EFF_TZ")
fi
if [ "$EFF_LOCALE" != "en-US" ] && [ "$EFF_LOCALE" != "en_US" ]; then
    STATE_PASS=false
    FAILED_ASSERTS+=("effective_locale expected en-US or en_US, got $EFF_LOCALE")
fi

{
    echo ""
    echo "DETERMINISTIC_STATE_PASS: $STATE_PASS"
    if [ ${#FAILED_ASSERTS[@]} -gt 0 ]; then
        echo "FAILED_ASSERTIONS:"
        for A in "${FAILED_ASSERTS[@]}"; do
            echo "  $A"
        done
    fi
} >> "$RUNTIME_STATE_FILE"

if [ "$STATE_PASS" = "false" ]; then
    echo "ERROR: Deterministic runtime state assertions failed." >&2
    for A in "${FAILED_ASSERTS[@]}"; do
        echo "  $A" >&2
    done
    exit 1
fi

echo "Building APKs..."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BUILD_RC=0
run_cmd_bounded 300 ./gradlew assembleDebug assembleDebugAndroidTest || BUILD_RC=$?
if [ $BUILD_RC -ne 0 ]; then
    echo "ERROR: Gradle build failed with status $BUILD_RC." >&2
    exit 1
fi

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [ ! -f "$APP_APK" ] || [ ! -f "$TEST_APK" ]; then
    echo "ERROR: Built APKs not found at $APP_APK or $TEST_APK" >&2
    exit 1
fi

APP_SHA=$(shasum -a 256 "$APP_APK" | cut -d' ' -f1)
TEST_SHA=$(shasum -a 256 "$TEST_APK" | cut -d' ' -f1)

{
    echo "APP_APK_SHA256: $APP_SHA"
    echo "TEST_APK_SHA256: $TEST_SHA"
} >> "$VERSIONS_FILE"

echo "Installing APKs on $RUNNING_SERIAL..."
INSTALL_APP_RC=0
run_cmd_bounded 60 "$ADB" -s "$RUNNING_SERIAL" install -r "$APP_APK" || INSTALL_APP_RC=$?
if [ $INSTALL_APP_RC -ne 0 ]; then
    echo "ERROR: App APK install failed on $RUNNING_SERIAL with status $INSTALL_APP_RC." >&2
    exit 1
fi

INSTALL_TEST_RC=0
run_cmd_bounded 60 "$ADB" -s "$RUNNING_SERIAL" install -r "$TEST_APK" || INSTALL_TEST_RC=$?
if [ $INSTALL_TEST_RC -ne 0 ]; then
    echo "ERROR: Test APK install failed on $RUNNING_SERIAL with status $INSTALL_TEST_RC." >&2
    exit 1
fi

# Pre-compile APKs to native code to eliminate ART cold-JIT overhead on first run.
# cmd package compile is available on API 24+; skip silently on API 23.
if [ "$EXPECTED_API" != "23" ]; then
    # Package names must match applicationId in app/build.gradle.kts.
    # Update both values here if applicationId is ever renamed.
    APP_PKG="com.rotiropi.pos_erpnext"
    TEST_PKG="com.rotiropi.pos_erpnext.test"

    echo "Pre-compiling $APP_PKG with speed profile on $RUNNING_SERIAL..."
    COMPILE_APP_START=$(python3 -c 'import time; print(time.monotonic())')
    COMPILE_APP_RC=0
    run_cmd_bounded 120 "$ADB" -s "$RUNNING_SERIAL" shell cmd package compile -m speed -f "$APP_PKG" || COMPILE_APP_RC=$?
    COMPILE_APP_END=$(python3 -c 'import time; print(time.monotonic())')
    COMPILE_APP_DUR=$(python3 -c "print(round($COMPILE_APP_END - $COMPILE_APP_START, 1))")
    if [ $COMPILE_APP_RC -ne 0 ]; then
        echo "ERROR: ART pre-compilation of $APP_PKG failed on $RUNNING_SERIAL with status $COMPILE_APP_RC." >&2
        exit 1
    fi
    echo "Pre-compiled $APP_PKG in ${COMPILE_APP_DUR}s (exit $COMPILE_APP_RC)."

    echo "Pre-compiling $TEST_PKG with speed profile on $RUNNING_SERIAL..."
    COMPILE_TEST_START=$(python3 -c 'import time; print(time.monotonic())')
    COMPILE_TEST_RC=0
    run_cmd_bounded 120 "$ADB" -s "$RUNNING_SERIAL" shell cmd package compile -m speed -f "$TEST_PKG" || COMPILE_TEST_RC=$?
    COMPILE_TEST_END=$(python3 -c 'import time; print(time.monotonic())')
    COMPILE_TEST_DUR=$(python3 -c "print(round($COMPILE_TEST_END - $COMPILE_TEST_START, 1))")
    if [ $COMPILE_TEST_RC -ne 0 ]; then
        echo "ERROR: ART pre-compilation of $TEST_PKG failed on $RUNNING_SERIAL with status $COMPILE_TEST_RC." >&2
        exit 1
    fi
    echo "Pre-compiled $TEST_PKG in ${COMPILE_TEST_DUR}s (exit $COMPILE_TEST_RC)."
fi

echo "Running instrumentation tests on $RUNNING_SERIAL..."
# Host-script-only setup/verification methods are annotated @SpecialHarnessOnly and
# excluded from the broad suite; tools/oauth-process-death.sh invokes them by name.
INSTRUMENTATION_ARGS=(-e notAnnotation com.rotiropi.pos_erpnext.test.SpecialHarnessOnly)
if [ -n "${INSTRUMENTATION_CLASS:-}" ]; then
    INSTRUMENTATION_ARGS+=(-e class "$INSTRUMENTATION_CLASS")
fi
INSTR_RC=0
run_cmd_bounded "$INSTRUMENTATION_TIMEOUT_SEC" "$ADB" -s "$RUNNING_SERIAL" shell am instrument -w -r "${INSTRUMENTATION_ARGS[@]}" com.rotiropi.pos_erpnext.test/androidx.test.runner.AndroidJUnitRunner > "$INSTRUMENTATION_FILE" 2>&1 || INSTR_RC=$?

cat "$INSTRUMENTATION_FILE"

# Required evidence copy failures fail
GRADLE_XML_DIR="app/build/outputs/androidTest-results/connected"
REPORT_COPY_DIR="$REPORT_DIR/report-copy"
if [ -d "$GRADLE_XML_DIR" ]; then
    cp -r "$GRADLE_XML_DIR" "$REPORT_COPY_DIR"
fi

# Finding 1: Nonzero host exit MUST fail before marker acceptance
if [ $INSTR_RC -ne 0 ]; then
    echo "ERROR: adb am instrument command failed with host exit status $INSTR_RC." >&2
    exit 1
fi

# Strict instrumentation parsing. Accept only the test count reported by this broad suite.
if grep -q "FAILURES!!!" "$INSTRUMENTATION_FILE" || grep -q "Process crashed" "$INSTRUMENTATION_FILE"; then
    echo "ERROR: Instrumentation output contains test failures or process crash." >&2
    exit 1
fi

CODE_MATCH_COUNT=$(grep -c "^INSTRUMENTATION_CODE: -1$" "$INSTRUMENTATION_FILE" || true)
if [ "$CODE_MATCH_COUNT" -ne 1 ]; then
    echo "ERROR: Expected exactly 1 'INSTRUMENTATION_CODE: -1', found $CODE_MATCH_COUNT." >&2
    exit 1
fi

EXPECTED_TESTS=$(grep "^INSTRUMENTATION_STATUS: numtests=" "$INSTRUMENTATION_FILE" | cut -d= -f2 | sort -u)
if ! [[ "$EXPECTED_TESTS" =~ ^[1-9][0-9]*$ ]]; then
    echo "ERROR: Expected one positive instrumentation test count, found '${EXPECTED_TESTS:-none}'." >&2
    exit 1
fi

if [ "$EXPECTED_TESTS" -eq 1 ]; then
    EXPECTED_SUMMARY="OK (1 test)"
else
    EXPECTED_SUMMARY="OK ($EXPECTED_TESTS tests)"
fi
OK_MATCH_COUNT=$(grep -Fxc "$EXPECTED_SUMMARY" "$INSTRUMENTATION_FILE" || true)
if [ "$OK_MATCH_COUNT" -ne 1 ]; then
    echo "ERROR: Expected exactly 1 anchored '$EXPECTED_SUMMARY', found $OK_MATCH_COUNT." >&2
    exit 1
fi

UNEXPECTED_SUMMARIES=$(grep -E "^(OK|FAILURES!|Tests run:)" "$INSTRUMENTATION_FILE" | grep -Fvx "$EXPECTED_SUMMARY" || true)
if [ -n "$UNEXPECTED_SUMMARIES" ]; then
    echo "ERROR: Unexpected extra summary lines found: $UNEXPECTED_SUMMARIES" >&2
    exit 1
fi

echo "Device tests PASSED on $TARGET_API ($RUNNING_SERIAL)."
