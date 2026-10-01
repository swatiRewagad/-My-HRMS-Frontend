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

for d in $DIRS; do
  echo "==> $d  ($(date +%H:%M:%S))"
  PW_OUTPUT_DIR="review-out/$d" \
  PLAYWRIGHT_JSON_OUTPUT_NAME="review-json/$d.json" \
    npx playwright test "e2e/$d" \
      --config=playwright-review.config.ts \
      --project=chromium \
    > "review-json/$d.log" 2>&1
  line="$(grep -oE '[0-9]+ (passed|failed|skipped|flaky|did not run)' "review-json/$d.log" | tr '\n' ' ')"
  pngs="$(find "review-out/$d" -name '*.png' 2>/dev/null | wc -l)"
  echo "$d: ${line:-NO RESULT LINE} | png=$pngs" | tee -a "$SUMMARY"
done

echo
echo "===== SUMMARY ====="
cat "$SUMMARY"
