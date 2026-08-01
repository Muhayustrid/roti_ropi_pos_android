#!/bin/bash
# Task 3 Step 5: OAuth process-death recovery.
#
# Drives two separate four-boundary sequences through explicit instrumentation
# class/method names (annotated @SpecialHarnessOnly so the broad suite skips them):
#   1. writePendingAttemptForConsumedBeforeTokenPersistence -> persist PENDING, force-stop
#   2. consumeAttemptBeforeTokenPersistence -> CONSUMED + exactly one exchange, no token, force-stop
#   3. recoverConsumedBeforeTokenPersistenceAfterDeath -> clear CONSUMED, stay unauthenticated
#   4. verifySecondConsumedBeforeTokenRelaunchDoesNotExchange -> prove no later exchange
#   5. writePendingAttempt -> persist PENDING, force-stop
#   6. persistTokenBeforeTerminalCleanup -> token + CONSUMED, force-stop
#   7. recoverPersistedTokenAfterDeath -> restore token + clear CONSUMED, force-stop
#   8. verifySecondRelaunchStillDoesNotExchange -> prove terminal cleanup remains stable
#
# Deterministic: resolves one serial for the requested API level, always uses
# `adb -s <serial>`, bounded waits only, fails on first nonzero status.
set -eu

TARGET_API="${1:-}"
case "$TARGET_API" in
    api23)
        EXPECTED_API="23"
        ;;
    api36)
        EXPECTED_API="36"
        ;;
    *)
        echo "Usage: $0 <api23|api36>" >&2
        exit 1
        ;;
esac

ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

ADB=""
if [ -f "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
fi

if [ -z "$ADB" ]; then
    echo "ERROR: adb not found in $ANDROID_SDK_ROOT and not on PATH." >&2
    exit 1
fi

APP_ID="com.rotiropi.pos_erpnext"
TEST_ID="$APP_ID.test"
TEST_RUNNER="$TEST_ID/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.rotiropi.pos_erpnext.auth.OAuthAttemptProcessDeathTest"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/oauth-process-detection.sh"

REPORT_DIR="app/build/reports/mobile-pos-oauth-process-death/$TARGET_API"
rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"

COMMANDS_FILE="$REPORT_DIR/commands.txt"
CONSUMED_BOUNDARY1_FILE="$REPORT_DIR/consumed_boundary1_write_pending.txt"
CONSUMED_BOUNDARY2_FILE="$REPORT_DIR/consumed_boundary2_exchange_no_token.txt"
CONSUMED_BOUNDARY3_FILE="$REPORT_DIR/consumed_boundary3_recovered.txt"
CONSUMED_BOUNDARY4_FILE="$REPORT_DIR/consumed_boundary4_second_relaunch.txt"
BOUNDARY1_FILE="$REPORT_DIR/boundary1_write_pending.txt"
BOUNDARY2_FILE="$REPORT_DIR/boundary2_token_persisted.txt"
BOUNDARY3_FILE="$REPORT_DIR/boundary3_recovered.txt"
BOUNDARY4_FILE="$REPORT_DIR/boundary4_second_relaunch.txt"
SUMMARY_FILE="$REPORT_DIR/summary.txt"

run_cmd_bounded() {
    TIMEOUT_SEC="$1"
    shift
    RC=0
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

    CMD_QUOTED=""
    for ARG in "$@"; do
        CMD_QUOTED="$CMD_QUOTED $(printf '%q' "$ARG")"
    done
    echo "[$RC]${CMD_QUOTED}" >> "$COMMANDS_FILE"

    if [ -n "$OUT" ]; then
        echo "$OUT"
    fi
    return $RC
}

DEVICES_RC=0
DEVICES=$(run_cmd_bounded 15 "$ADB" devices) || DEVICES_RC=$?
if [ $DEVICES_RC -ne 0 ]; then
    echo "ERROR: 'adb devices' failed with status $DEVICES_RC." >&2
    exit 1
fi

MATCHING_SERIAL=""
MATCH_COUNT=0
for SERIAL in $(echo "$DEVICES" | awk 'NR>1 && $2=="device" {print $1}'); do
    SDK_RC=0
    SDK=$(run_cmd_bounded 10 "$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk) || SDK_RC=$?
    if [ $SDK_RC -ne 0 ]; then
        continue
    fi
    SDK=$(echo "$SDK" | head -n 1 | tr -d '\r')
    if [ "$SDK" = "$EXPECTED_API" ]; then
        MATCHING_SERIAL="$SERIAL"
        MATCH_COUNT=$((MATCH_COUNT + 1))
    fi
done

if [ -z "$MATCHING_SERIAL" ]; then
    {
        echo "RESULT: BLOCKED"
        echo "REASON: no attached device reports API $EXPECTED_API"
        echo "--- adb devices ---"
        echo "$DEVICES"
    } | tee "$SUMMARY_FILE" >&2
    exit 1
fi

if [ "$MATCH_COUNT" -ne 1 ]; then
    echo "ERROR: expected exactly 1 device at API $EXPECTED_API, found $MATCH_COUNT." >&2
    exit 1
fi

echo "Resolved serial $MATCHING_SERIAL (API $EXPECTED_API)"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
run_cmd_bounded 600 ./gradlew assembleDebug assembleDebugAndroidTest

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [ ! -f "$APP_APK" ] || [ ! -f "$TEST_APK" ]; then
    echo "ERROR: built APKs not found at $APP_APK or $TEST_APK" >&2
    exit 1
fi

run_cmd_bounded 120 "$ADB" -s "$MATCHING_SERIAL" install -r "$APP_APK"
run_cmd_bounded 120 "$ADB" -s "$MATCHING_SERIAL" install -r "$TEST_APK"

run_cmd_bounded 30 "$ADB" -s "$MATCHING_SERIAL" shell pm clear "$APP_ID"

PIDOF_SUPPORTED=""

matching_processes() {
    PROCESS_SERIAL="$1"
    PROCESS_PS_RC=0
    PROCESS_PS=$(run_cmd_bounded 10 "$ADB" -s "$PROCESS_SERIAL" shell ps) || PROCESS_PS_RC=$?
    if [ $PROCESS_PS_RC -ne 0 ]; then
        echo "ERROR: unable to inspect processes with API-compatible ps (status $PROCESS_PS_RC)." >&2
        return 1
    fi
    printf '%s\n' "$PROCESS_PS" | oauth_matching_processes_from_ps "$APP_ID" "$TEST_ID"
}

pidof_supported() {
    PIDOF_SERIAL="$1"
    if [ -n "$PIDOF_SUPPORTED" ]; then
        [ "$PIDOF_SUPPORTED" = "true" ]
        return
    fi

    PIDOF_SENTINEL_RC=0
    PIDOF_SENTINEL=$(run_cmd_bounded 10 "$ADB" -s "$PIDOF_SERIAL" shell pidof "oauth-process-sentinel-never-exists") || PIDOF_SENTINEL_RC=$?
    if [ $PIDOF_SENTINEL_RC -eq 124 ]; then
        echo "ERROR: pidof capability probe timed out." >&2
        return 1
    fi
    if oauth_pidof_is_usable "$PIDOF_SENTINEL"; then
        PIDOF_SUPPORTED="true"
        return 0
    fi

    PIDOF_SUPPORTED="false"
    echo "INFO: device pidof ignores process names; using API-compatible ps matching." >&2
    return 1
}

wait_for_process_gone() {
    WAIT_SERIAL="$1"
    DEADLINE=$(( $(date +%s) + 30 ))
    LAST_MATCHES=""
    LAST_APP_PIDOF="not-checked"
    LAST_TEST_PIDOF="not-checked"
    while [ "$(date +%s)" -lt "$DEADLINE" ]; do
        LAST_MATCHES=$(matching_processes "$WAIT_SERIAL") || return 1
        if [ -z "$LAST_MATCHES" ]; then
            if pidof_supported "$WAIT_SERIAL"; then
                APP_PIDOF_RC=0
                TEST_PIDOF_RC=0
                LAST_APP_PIDOF=$(run_cmd_bounded 10 "$ADB" -s "$WAIT_SERIAL" shell pidof "$APP_ID") || APP_PIDOF_RC=$?
                LAST_TEST_PIDOF=$(run_cmd_bounded 10 "$ADB" -s "$WAIT_SERIAL" shell pidof "$TEST_ID") || TEST_PIDOF_RC=$?
                LAST_APP_PIDOF=$(printf '%s' "$LAST_APP_PIDOF" | tr -d '\r[:space:]')
                LAST_TEST_PIDOF=$(printf '%s' "$LAST_TEST_PIDOF" | tr -d '\r[:space:]')
                if [ $APP_PIDOF_RC -ne 124 ] && [ $TEST_PIDOF_RC -ne 124 ] &&
                   [ -z "$LAST_APP_PIDOF" ] && [ -z "$LAST_TEST_PIDOF" ]; then
                    return 0
                fi
            else
                return 0
            fi
        fi
        sleep 1
    done

    APP_STOPPED=$(run_cmd_bounded 10 "$ADB" -s "$WAIT_SERIAL" shell dumpsys package "$APP_ID" 2>/dev/null | grep -E 'User 0:.*stopped=' | head -n 1 || true)
    TEST_STOPPED=$(run_cmd_bounded 10 "$ADB" -s "$WAIT_SERIAL" shell dumpsys package "$TEST_ID" 2>/dev/null | grep -E 'User 0:.*stopped=' | head -n 1 || true)
    echo "ERROR: app or instrumentation process still running after force-stop deadline." >&2
    echo "LAST_PROCESS_MATCHES: ${LAST_MATCHES:-none}" >&2
    echo "LAST_APP_PIDOF: ${LAST_APP_PIDOF:-none}" >&2
    echo "LAST_TEST_PIDOF: ${LAST_TEST_PIDOF:-none}" >&2
    echo "APP_STOPPED_STATE: ${APP_STOPPED:-unavailable}" >&2
    echo "TEST_STOPPED_STATE: ${TEST_STOPPED:-unavailable}" >&2
    return 1
}

force_stop_processes() {
    STOP_SERIAL="$1"
    run_cmd_bounded 30 "$ADB" -s "$STOP_SERIAL" shell am force-stop "$TEST_ID"
    run_cmd_bounded 30 "$ADB" -s "$STOP_SERIAL" shell am force-stop "$APP_ID"
    wait_for_process_gone "$STOP_SERIAL"
}

run_boundary() {
    METHOD="$1"
    OUT_FILE="$2"
    BOUNDARY_RC=0
    run_cmd_bounded 180 "$ADB" -s "$MATCHING_SERIAL" shell am instrument -w -r \
        -e class "$TEST_CLASS#$METHOD" \
        "$TEST_RUNNER" > "$OUT_FILE" 2>&1 || BOUNDARY_RC=$?

    cat "$OUT_FILE"

    if [ $BOUNDARY_RC -ne 0 ]; then
        echo "ERROR: instrumentation host exit $BOUNDARY_RC for $METHOD." >&2
        return 1
    fi
    if grep -q "FAILURES!!!" "$OUT_FILE" || grep -q "Process crashed" "$OUT_FILE"; then
        echo "ERROR: $METHOD reported failures or a process crash." >&2
        return 1
    fi
    if ! grep -Fxq "OK (1 test)" "$OUT_FILE"; then
        echo "ERROR: $METHOD did not report exactly 'OK (1 test)'." >&2
        return 1
    fi
    return 0
}

echo "Consumed boundary 1: persist PENDING attempt"
run_boundary writePendingAttemptForConsumedBeforeTokenPersistence "$CONSUMED_BOUNDARY1_FILE"

printf '%s\n' "Injecting process death before callback"
force_stop_processes "$MATCHING_SERIAL"

printf '%s\n' "Consumed boundary 2: CONSUMED plus one exchange without token persistence"
run_boundary consumeAttemptBeforeTokenPersistence "$CONSUMED_BOUNDARY2_FILE"

printf '%s\n' "Injecting process death immediately before token persistence"
force_stop_processes "$MATCHING_SERIAL"

printf '%s\n' "Consumed boundary 3: restart clears CONSUMED and stays unauthenticated"
run_boundary recoverConsumedBeforeTokenPersistenceAfterDeath "$CONSUMED_BOUNDARY3_FILE"

printf '%s\n' "Injecting process death after consumed cleanup"
force_stop_processes "$MATCHING_SERIAL"

printf '%s\n' "Consumed boundary 4: second relaunch stays non-replaying"
run_boundary verifySecondConsumedBeforeTokenRelaunchDoesNotExchange "$CONSUMED_BOUNDARY4_FILE"

printf '%s\n' "Boundary 1: persist PENDING attempt"
run_boundary writePendingAttempt "$BOUNDARY1_FILE"

printf '%s\n' "Injecting process death (force-stop)"
force_stop_processes "$MATCHING_SERIAL"

printf '%s\n' "Boundary 2: exchange once and persist token before terminal cleanup"
run_boundary persistTokenBeforeTerminalCleanup "$BOUNDARY2_FILE"

printf '%s\n' "Injecting process death immediately after token persistence"
force_stop_processes "$MATCHING_SERIAL"

printf '%s\n' "Boundary 3: restore persisted token and clear consumed attempt"
run_boundary recoverPersistedTokenAfterDeath "$BOUNDARY3_FILE"

printf '%s\n' "Injecting process death after terminal cleanup"
force_stop_processes "$MATCHING_SERIAL"

printf '%s\n' "Boundary 4: second relaunch remains authenticated without exchange"
run_boundary verifySecondRelaunchStillDoesNotExchange "$BOUNDARY4_FILE"

{
    echo "RESULT: PASS"
    echo "SERIAL: $MATCHING_SERIAL"
    echo "API_LEVEL: $EXPECTED_API"
    echo "TEST_CLASS: $TEST_CLASS"
    echo "CONSUMED_BOUNDARY_1: writePendingAttemptForConsumedBeforeTokenPersistence"
    echo "CONSUMED_BOUNDARY_2: consumeAttemptBeforeTokenPersistence"
    echo "CONSUMED_BOUNDARY_3: recoverConsumedBeforeTokenPersistenceAfterDeath"
    echo "CONSUMED_BOUNDARY_4: verifySecondConsumedBeforeTokenRelaunchDoesNotExchange"
    echo "BOUNDARY_1: writePendingAttempt"
    echo "BOUNDARY_2: persistTokenBeforeTerminalCleanup"
    echo "BOUNDARY_3: recoverPersistedTokenAfterDeath"
    echo "BOUNDARY_4: verifySecondRelaunchStillDoesNotExchange"
} | tee "$SUMMARY_FILE"

echo "Artifacts written to $REPORT_DIR"
