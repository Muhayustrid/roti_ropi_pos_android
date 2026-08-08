#!/bin/bash
# Two-process durable mutation recovery harness.
set -eu

TARGET_API="${1:-}"
HARNESS="${2:-sale}"
case "$TARGET_API" in
    api23) EXPECTED_API=23 ;;
    api36) EXPECTED_API=36 ;;
    *) echo "Usage: $0 <api23|api36> [sale|return|closing]" >&2; exit 1 ;;
esac

ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
[ -x "$ADB" ] || { echo "ERROR: adb not found at $ADB" >&2; exit 1; }

APP_ID="com.rotiropi.pos_erpnext"
TEST_ID="$APP_ID.test"
RUNNER="$TEST_ID/androidx.test.runner.AndroidJUnitRunner"
case "$HARNESS" in
    sale)
        TEST_CLASS="com.rotiropi.pos_erpnext.recovery.ProcessDeathHarnessTest"
        REPORT_DIR="app/build/reports/mobile-pos-recovery-process-death/$TARGET_API"
        ;;
    return)
        TEST_CLASS="com.rotiropi.pos_erpnext.recovery.ReturnProcessDeathHarnessTest"
        REPORT_DIR="app/build/reports/mobile-pos-return-process-death/$TARGET_API"
        ;;
    closing)
        TEST_CLASS="com.rotiropi.pos_erpnext.recovery.ClosingProcessDeathHarnessTest"
        REPORT_DIR="app/build/reports/mobile-pos-closing-process-death/$TARGET_API"
        ;;
    *) echo "Usage: $0 <api23|api36> [sale|return|closing]" >&2; exit 1 ;;
esac
rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"
COMMANDS_FILE="$REPORT_DIR/commands.txt"

run_bounded() {
    TIMEOUT="$1"; shift
    CMD=""
    for ARG in "$@"; do CMD="$CMD $(printf '%q' "$ARG")"; done
    RC=0
    python3 - "$TIMEOUT" "$@" <<'PY' || RC=$?
import os, signal, subprocess, sys
p = subprocess.Popen(sys.argv[2:], start_new_session=True)
try:
    raise SystemExit(p.wait(timeout=float(sys.argv[1])))
except subprocess.TimeoutExpired:
    os.killpg(os.getpgid(p.pid), signal.SIGKILL)
    p.wait()
    raise SystemExit(124)
PY
    echo "[$RC]$CMD" >> "$COMMANDS_FILE"
    return "$RC"
}

DEVICES=$($ADB devices)
SERIAL=""
COUNT=0
for CANDIDATE in $(printf '%s\n' "$DEVICES" | awk 'NR>1 && $2=="device" {print $1}'); do
    API=$($ADB -s "$CANDIDATE" shell getprop ro.build.version.sdk | tr -d '\r')
    if [ "$API" = "$EXPECTED_API" ]; then SERIAL="$CANDIDATE"; COUNT=$((COUNT + 1)); fi
done
[ "$COUNT" -eq 1 ] || { echo "ERROR: expected exactly one attached API $EXPECTED_API device, found $COUNT" >&2; exit 1; }

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
run_bounded 600 ./gradlew assembleDebug assembleDebugAndroidTest
run_bounded 120 "$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
run_bounded 120 "$ADB" -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
run_bounded 30 "$ADB" -s "$SERIAL" shell pm clear "$APP_ID"

run_boundary() {
    METHOD="$1"; OUTPUT="$2"
    RC=0
    run_bounded 180 "$ADB" -s "$SERIAL" shell am instrument -w -r \
        -e class "$TEST_CLASS#$METHOD" "$RUNNER" > "$OUTPUT" 2>&1 || RC=$?
    cat "$OUTPUT"
    [ "$RC" -eq 0 ] || return "$RC"
    grep -Fq 'OK (1 test)' "$OUTPUT"
    ! grep -Eq 'FAILURES!!!|Process crashed' "$OUTPUT"
}

run_boundary persistMutationBeforeProcessDeath "$REPORT_DIR/boundary1-persist.txt"
run_bounded 30 "$ADB" -s "$SERIAL" shell am force-stop "$TEST_ID"
run_bounded 30 "$ADB" -s "$SERIAL" shell am force-stop "$APP_ID"
run_boundary verifyMutationAfterProcessDeath "$REPORT_DIR/boundary2-verify.txt"

{
    echo "RESULT: PASS"
    echo "SERIAL: $SERIAL"
    echo "API_LEVEL: $EXPECTED_API"
    echo "HARNESS: $HARNESS"
    echo "TEST_CLASS: $TEST_CLASS"
    echo "BOUNDARY_1: persistMutationBeforeProcessDeath"
    echo "BOUNDARY_2: verifyMutationAfterProcessDeath"
} | tee "$REPORT_DIR/summary.txt"
