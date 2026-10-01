#!/usr/bin/env bash
#
# Gates e2e/public — 1103 tests in 54 files — by running it as PARALLEL SLICES.
#
# WHY SLICES AND NOT ONE INVOCATION
# playwright.config.ts pins `workers: 1`, so one invocation runs 1103 browser tests strictly
# serially. The 2026-10-01 attempt did exactly that and died at 368/1103 with 735 tests having no
# result at all. Slicing turns the directory into N independent Playwright processes, each with its
# own JSON report, so a slice that dies costs only its own slice and every other result survives.
#
# WHY THESE SLICE BOUNDARIES (they are not arbitrary)
#   * SLICE_AUTH is one slice on purpose. `clearCooloff` (helpers-auth-a.ts:189) deletes
#     `login_cooloffs WHERE mobile_number LIKE '987651%'` — the WHOLE session-A range, not one
#     number. Two concurrent processes both calling it would each wipe the other's lockout rows
#     mid-test, and a cooloff test whose row vanished reports a product defect that is not there.
#     Every spec importing helpers-auth-a therefore shares one process.
#   * The remaining specs key their data on a per-test random mobile (sessionBMobile /
#     sessionCMobile) or a literal phone of their own, so they do not collide and are packed by test
#     count into roughly equal slices for wall-clock.
#   * Slices are NOT given more than one worker each. A spec that mocks routes or asserts on a
#     cooloff is only safe against ITSELF being serial; raising workers inside a slice reintroduces
#     exactly the cross-talk the boundaries above exist to prevent.
#
# Each slice writes a FULL per-test JSON report. Counts must be walked with count-results.js, never
# grepped out of the summary line — the summary conflates a cascade-skip with a real failure and has
# already inflated one defect into "35 failures" once.
#
# Usage:  ./e2e/review/run-public-gate.sh            # all slices
#         ./e2e/review/run-public-gate.sh auth       # one named slice
set -uo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck disable=SC1091
source e2e/review/credentials.env

# BOTH of these are required. Omitting either sends the browser to the app's compiled 8082 while the
# fixtures seed 8092, which produced 47 fake failures before it was understood.
export API_BASE_URL="${API_BASE_URL:-http://localhost:8092}"
export UI_BASE_URL="${UI_BASE_URL:-http://localhost:4202}"
export APP_BASE_URL="${APP_BASE_URL:-$UI_BASE_URL}"

OUT=".gate-public"
mkdir -p "$OUT"

# ── Slice definitions ────────────────────────────────────────────────────────────────────────────
# auth: MUST stay together — shared 987651xxxx cooloff range (see header).
SLICE_auth="login-captcha login-consent-declaration login-mobile-field otp-field-validation
            complainant-name-fields complainant-category-age-gender"

SLICE_elig="eligibility-maintainability-questions eligibility-simplify-statutory eligibility-re-window
            eligibility-master eligibility-sub-questions eligibility-clause-interpolation
            eligibility-simplify"

SLICE_amount="amount-compensation-caps account-card-numbers complaint-transaction-account
              complaint-category-facts complaint-categories"

SLICE_wizard="non-maintainable-closure duplicate-detection-popup wizard-upload-documents
              wizard-step-navigation duplicate-check declaration-submission"

SLICE_entity="re-entity-search re-details-cascading-dropdowns review-document-preview
              complainant-address-state-district complainant-contact-fields complainant-pincode
              representative-validation"

SLICE_draft="draft-autosave-lifecycle draft-save filing-windows wallet-bc-reference
             form-tooltips-speech session-timeout"

SLICE_track="tracking-authz tracking-table tracking-field-validation otp-lifecycle
             citizen-appeal-filing pdf-signature"

SLICE_misc="withdrawal withdrawal-attachments withdrawal-notifications withdrawal-cancel-timeline
            feedback feedback-questionnaire consent-dpdp faq seo seo-headings"

SLICES="auth elig amount wizard entity draft track misc"

run_slice() {
  local name="$1"
  local var="SLICE_${name}"
  local specs=""
  for s in ${!var}; do specs="${specs} e2e/public/${s}.spec.ts"; done

  local started; started=$(date +%s)
  # shellcheck disable=SC2086
  npx playwright test $specs \
      --project=chromium \
      --reporter="json,list" \
      > "${OUT}/${name}.log" 2>&1
  local rc=$?
  local ended; ended=$(date +%s)
  echo "${name} rc=${rc} wall=$((ended - started))s" >> "${OUT}/wallclock.txt"
  return $rc
}

if [ $# -gt 0 ]; then
  SLICES="$*"
fi

: > "${OUT}/wallclock.txt"
GATE_START=$(date +%s)

for name in $SLICES; do
  # PLAYWRIGHT_JSON_OUTPUT_NAME is per-slice so slices cannot overwrite each other's report.
  PLAYWRIGHT_JSON_OUTPUT_NAME="${OUT}/${name}.json" \
  PLAYWRIGHT_HTML_OPEN=never \
  run_slice "$name" &
done
wait

GATE_END=$(date +%s)
echo
echo "==> all slices finished in $((GATE_END - GATE_START))s wall-clock"
cat "${OUT}/wallclock.txt"
echo
echo "==> per-slice counts (walked from JSON, not grepped)"
for name in $SLICES; do
  node e2e/review/count-results.js "${OUT}/${name}.json" || true
done
