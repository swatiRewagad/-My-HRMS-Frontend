#!/usr/bin/env bash
#
# Slow, full-suite health pass for a screen review. Runs each e2e directory separately because a
# single 1944-test invocation buffers indefinitely through a pipe, and aggregates the results.
#
# Screenshots land in review-out/<dir>/, machine-readable results in review-json/<dir>.json.
#
# Usage:  e2e/review/run-full-suite.sh
# Env:    PW_SLOWMO (default 300), PW_HEADED=1 to watch, DIRS to override the directory list.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

# Sourced here, as capture-screens.sh already does. Without it the specs that SSO through Keycloak
# fall back to their hardcoded defaults and report a failure that looks like a broken redirect — the
# staff-dashboard specs were "failing" for exactly this reason while passing in isolation.
# shellcheck disable=SC1091
source e2e/review/credentials.env

export UI_BASE_URL="${UI_BASE_URL:-http://localhost:4202}"
export APP_BASE_URL="${APP_BASE_URL:-$UI_BASE_URL}"
export API_BASE_URL="${API_BASE_URL:-http://localhost:8092}"
export WORKFLOW_BASE_URL="${WORKFLOW_BASE_URL:-http://localhost:8094}"
export PW_SLOWMO="${PW_SLOWMO:-300}"
export PW_SCREENSHOT=on

DIRS="${DIRS:-i18n staff ui-homogenisation admin cepc re-portal rbio aa public}"

mkdir -p review-json review-out
SUMMARY=review-json/summary.txt
: > "$SUMMARY"

# ── Dependency probes ────────────────────────────────────────────────────────────
#
# This script used to export WORKFLOW_BASE_URL and then never start the service behind it, so
# e2e/admin/safe-deactivation.spec.ts test.skip()'d all 8 of its tests and the old grep-based summary
# reported them as "8 did not run" — indistinguishable from a crash. We PROBE instead of launching:
# cms-workflow-service is a long-lived JVM on its own port, and a run script that spawns and reaps one
# would race the ~6 parallel sessions that share this host. So the dependency stays external, but its
# absence is now STATED in the summary rather than silently degrading the results.
#
# Start it with:
#   cd ../cms-workflow-service && mvn -o spring-boot:run \
#     -Dspring-boot.run.profiles=dev-local -Dspring-boot.run.jvmArguments="-Dserver.port=8094"
# curl already WRITES 000 to stdout when it cannot connect, so a `|| echo 000` fallback would
# concatenate into "000000" and never equal "000". Swallow the non-zero exit instead and trust -w.
probe() {
  curl -s -o /dev/null -m 5 -w '%{http_code}' "$1" 2>/dev/null || true
}

DEPS_MISSING=""

backend_code="$(probe "$API_BASE_URL/api/v1/config/upload-limits")"
if [ "$backend_code" = "000" ]; then
  DEPS_MISSING="${DEPS_MISSING}cms-backend DOWN at $API_BASE_URL (most API tests will fail); "
fi

# A 2xx/4xx both prove the route is served; only a connection failure (000) means nothing is there.
workflow_code="$(probe "$WORKFLOW_BASE_URL/cms-workflow/api/v1/assignment/pool?roleGroup=PROBE")"
if [ "$workflow_code" = "000" ]; then
  DEPS_MISSING="${DEPS_MISSING}cms-workflow-service DOWN at $WORKFLOW_BASE_URL (e2e/admin/safe-deactivation.spec.ts will SKIP its 8 tests); "
fi

ui_code="$(probe "$UI_BASE_URL/")"
if [ "$ui_code" = "000" ]; then
  DEPS_MISSING="${DEPS_MISSING}Angular dev server DOWN at $UI_BASE_URL (every browser test will fail); "
fi

kc_code="$(probe "${KEYCLOAK_URL:-http://localhost:9090}/realms/${KEYCLOAK_REALM:-cms}")"
if [ "$kc_code" = "000" ]; then
  DEPS_MISSING="${DEPS_MISSING}Keycloak DOWN at ${KEYCLOAK_URL:-http://localhost:9090} (every SSO/token test will fail); "
fi

if [ -n "$DEPS_MISSING" ]; then
  echo "!! DEPENDENCIES MISSING: $DEPS_MISSING" | tee -a "$SUMMARY"
  echo "!! Results below are NOT a clean gate. Start the missing services and re-run." | tee -a "$SUMMARY"
  echo
fi

for d in $DIRS; do
  echo "==> $d  ($(date +%H:%M:%S))"
  PW_OUTPUT_DIR="review-out/$d" \
  PLAYWRIGHT_JSON_OUTPUT_NAME="review-json/$d.json" \
    npx playwright test "e2e/$d" \
      --config=playwright-review.config.ts \
      --project=chromium \
    > "review-json/$d.log" 2>&1
  # Counted from the JSON report, NOT scraped from the log.
  #
  # The old `grep -oE '[0-9]+ (passed|failed|skipped|flaky|did not run)'` conflated a cascade with a
  # defect: when a describe.serial beforeAll throws, Playwright emits ONE failure and marks the rest
  # of the file "did not run", so a single wrong password in a 16-test file read as a 16-test product
  # failure. The 2026-10-01 gate inflated three harness bugs into ~100 "failures" that way.
  # count-results.js walks the per-test results array and reports pass / fail / did-not-run / skipped
  # separately, distinguishing a deliberate test.skip() (service absent, honestly reported) from a
  # test that never ran because something upstream of it exploded.
  line="$(node e2e/review/count-results.js --tsv "review-json/$d.json" 2>/dev/null | cut -f2-)"
  pngs="$(find "review-out/$d" -name '*.png' 2>/dev/null | wc -l)"
  echo "$d: ${line:-NO JSON REPORT} | png=$pngs" | tee -a "$SUMMARY"
done

echo
echo "===== SUMMARY ====="
cat "$SUMMARY"
if [ -n "$DEPS_MISSING" ]; then
  echo
  echo "!! REMINDER — these results ran with missing dependencies: $DEPS_MISSING"
fi

echo
echo "Per-test failure and did-not-run detail:"
# shellcheck disable=SC2086
for d in $DIRS; do
  [ -f "review-json/$d.json" ] && node e2e/review/count-results.js "review-json/$d.json"
done
