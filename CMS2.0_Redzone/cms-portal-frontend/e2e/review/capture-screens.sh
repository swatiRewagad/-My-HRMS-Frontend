#!/usr/bin/env bash
#
# Captures every screen in screen-registry.ts and builds the review HTML + CSV template.
#
# Usage:  e2e/review/capture-screens.sh            # all screens
#         GREP='RBIO-|AA-' e2e/review/capture-screens.sh   # a subset
# Env:    PW_HEADED=1 to watch, PW_SLOWMO to slow it down, REVIEW_DIR to change the output location.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

# shellcheck disable=SC1091
source e2e/review/credentials.env

export UI_BASE_URL="${UI_BASE_URL:-http://localhost:4202}"
export APP_BASE_URL="${APP_BASE_URL:-$UI_BASE_URL}"
export API_BASE_URL="${API_BASE_URL:-http://localhost:8092}"
export KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:9090}"
export REVIEW_DIR="${REVIEW_DIR:-review-screens}"
export PW_SLOWMO="${PW_SLOWMO:-0}"
export PW_SCREENSHOT=off   # the crawler takes its own full-page shots

mkdir -p "$REVIEW_DIR"

ARGS=(e2e/review/capture-screens.spec.ts --config=playwright-review.config.ts --project=chromium --reporter=line)
[ -n "${GREP:-}" ] && ARGS+=(--grep "$GREP")

PW_OUTPUT_DIR="$REVIEW_DIR/pw" npx playwright test "${ARGS[@]}" 2>&1 | tee "$REVIEW_DIR/crawl.log"

node e2e/review/build-report.js "$REVIEW_DIR"
