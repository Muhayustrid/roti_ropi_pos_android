#!/bin/sh

# Matches app and instrumentation package processes across Android 6+ ps layouts.
oauth_matching_processes_from_ps() {
    APP_PACKAGE="$1"
    TEST_PACKAGE="$2"
    awk -v app="$APP_PACKAGE" -v test="$TEST_PACKAGE" '
        NR > 1 {
            name = $NF
            if (name == app || index(name, app ":") == 1 ||
                name == test || index(name, test ":") == 1) {
                print $2 ":" name
            }
        }
    '
}

oauth_pidof_is_usable() {
    SENTINEL_OUTPUT=$(printf '%s' "$1" | tr -d '\r[:space:]')
    [ -z "$SENTINEL_OUTPUT" ]
}
