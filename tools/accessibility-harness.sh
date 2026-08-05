#!/usr/bin/env bash
set -euo pipefail

TARGET_API="${1:-}"
case "$TARGET_API" in
    api23|api36) ;;
    *)
        echo "Usage: $0 <api23|api36>" >&2
        exit 1
        ;;
esac

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="$ROOT_DIR/app/build/reports/mobile-pos-accessibility/$TARGET_API"
RUN_REPORT_DIR="$ROOT_DIR/app/build/reports/mobile-pos-devices/$TARGET_API"
RUN_LOG="$REPORT_DIR/run.log"
SUMMARY="$REPORT_DIR/summary.txt"

rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"

if ! (
    cd "$ROOT_DIR"
    INSTRUMENTATION_CLASS="com.rotiropi.pos_erpnext.ui.cashier.CatalogAccessibilityTest" \
        ./tools/run-device-tests.sh "$TARGET_API"
) >"$RUN_LOG" 2>&1; then
    cat "$RUN_LOG"
    echo "Catalog accessibility harness failed for $TARGET_API." >&2
    exit 1
fi

if ! grep -Fq "CatalogAccessibilityTest" "$RUN_LOG"; then
    cat "$RUN_LOG"
    echo "CatalogAccessibilityTest was not exercised for $TARGET_API." >&2
    exit 1
fi

if [ ! -f "$RUN_REPORT_DIR/instrumentation.txt" ]; then
    echo "Missing instrumentation evidence for $TARGET_API." >&2
    exit 1
fi

cp -R "$RUN_REPORT_DIR" "$REPORT_DIR/device-report"
{
    printf 'target_api=%s\n' "$TARGET_API"
    printf 'instrumentation_class=com.rotiropi.pos_erpnext.ui.cashier.CatalogAccessibilityTest\n'
    printf 'timeout_seconds=180\n'
    printf 'package_precompilation=api24_plus_speed_profile\n'
    printf 'automatic_retry=false\n'
    printf 'instrumentation=%s\n' "$RUN_REPORT_DIR/instrumentation.txt"
} > "$SUMMARY"

cat "$SUMMARY"
printf 'Accessibility artifacts written to %s\n' "$REPORT_DIR"
