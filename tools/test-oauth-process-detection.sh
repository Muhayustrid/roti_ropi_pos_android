#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/oauth-process-detection.sh"

APP_ID="com.rotiropi.pos_erpnext"
TEST_ID="$APP_ID.test"

assert_eq() {
    EXPECTED="$1"
    ACTUAL="$2"
    LABEL="$3"
    if [ "$EXPECTED" != "$ACTUAL" ]; then
        printf 'FAIL: %s\nexpected: %s\nactual: %s\n' "$LABEL" "$EXPECTED" "$ACTUAL" >&2
        exit 1
    fi
}

API23_PS='USER      PID   PPID  VSIZE  RSS   WCHAN            PC  NAME
u0_a61    3522  1214  9876   1234  ffffffff 00000000 S com.rotiropi.pos_erpnext
u0_a62    3569  1214  9876   1234  ffffffff 00000000 S com.rotiropi.pos_erpnext.test
u0_a61    3570  1214  9876   1234  ffffffff 00000000 S com.rotiropi.pos_erpnext:worker
root      1214  1     9876   1234  ffffffff 00000000 S system_server'

assert_eq "" "$(printf '%s\n' "$API23_PS" | oauth_matching_processes_from_ps other.app other.app.test)" "process absent"
assert_eq "3522:$APP_ID" "$(printf '%s\n' "$API23_PS" | oauth_matching_processes_from_ps "$APP_ID" unrelated.test | head -n 1)" "app process present"
assert_eq "3569:$TEST_ID" "$(printf '%s\n' "$API23_PS" | oauth_matching_processes_from_ps unrelated.app "$TEST_ID")" "test process present"
assert_eq "3522:$APP_ID
3569:$TEST_ID
3570:$APP_ID:worker" "$(printf '%s\n' "$API23_PS" | oauth_matching_processes_from_ps "$APP_ID" "$TEST_ID")" "API 23 ps format"

oauth_pidof_is_usable "" || { echo "FAIL: empty pidof sentinel must be usable" >&2; exit 1; }
if oauth_pidof_is_usable "1 2 3 1214"; then
    echo "FAIL: pidof sentinel returning every PID must be unsupported" >&2
    exit 1
fi

printf 'PASS: oauth process detection helpers\n'
