#!/usr/bin/env bash
#
# Provisions the Appellate Authority (AA) role model into the Keycloak `cms` realm.
#
# Idempotent: every create is preceded by an existence check, so re-running is safe and is the
# intended way to reconcile a realm after a Keycloak reinstall. Previously this setup existed only
# as prose inside Document/*.html, which meant a rebuilt realm silently lost every AA role and the
# whole AA E2E suite reported "skipped but passing".
#
# ORBIO: per the RBI ruling, "ORBIO" means the Ombudsman from the RBIO module — it is NOT a separate
# office or a separate role vocabulary. ORBIO Admin maps to RBIO_ADMIN and ORBIO officer maps to
# RBIO_OFFICER. We deliberately do NOT create ORBIO_* roles; a parallel vocabulary would let the two
# drift apart and give the same human two unrelated permission sets.
#
# Usage:
#   ./provision-aa-roles.sh                     # localhost:9090, realm cms, admin/admin
#   KEYCLOAK_URL=... KC_ADMIN=... KC_ADMIN_PASSWORD=... REALM=... ./provision-aa-roles.sh
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:9090}"
REALM="${REALM:-cms}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"
TEST_PASSWORD="${AA_TEST_PASSWORD:-test123}"

# kcadm ships as .bat on Windows and .sh elsewhere.
if [ -n "${KCADM:-}" ]; then
  :
elif command -v kcadm.sh >/dev/null 2>&1; then
  KCADM="kcadm.sh"
elif [ -x "${KEYCLOAK_HOME:-/opt/keycloak}/bin/kcadm.sh" ]; then
  KCADM="${KEYCLOAK_HOME:-/opt/keycloak}/bin/kcadm.sh"
elif [ -x "c:/tools/keycloak-26.0.0/bin/kcadm.bat" ]; then
  KCADM="c:/tools/keycloak-26.0.0/bin/kcadm.bat"
else
  echo "ERROR: kcadm not found. Set KCADM or KEYCLOAK_HOME." >&2
  exit 1
fi

echo "==> Authenticating to ${KEYCLOAK_URL} (realm ${REALM})"
"$KCADM" config credentials --server "$KEYCLOAK_URL" --realm master \
  --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD"

# ── Unmanaged attribute policy ──────────────────────────────────────────────
# Keycloak 26's declarative user profile DROPS unmanaged attributes SILENTLY when
# unmanagedAttributePolicy is unset: `create users -s attributes.entity_code=X` returns success,
# the attribute is never stored, and the JWT then carries no entity_code claim. Entity scoping would
# fall back to the request header — exactly the spoofable path we are removing — so this must be
# enabled before any user is created, and re-asserted on every run.
echo "==> Ensuring unmanaged user attributes are enabled"
PROFILE_JSON="$("$KCADM" get users/profile -r "$REALM")"
if printf '%s' "$PROFILE_JSON" | grep -q '"unmanagedAttributePolicy"'; then
  echo "    unmanagedAttributePolicy already set"
else
  TMP_PROFILE="$(mktemp)"
  printf '%s' "$PROFILE_JSON" \
    | python -c 'import sys,json; p=json.load(sys.stdin); p["unmanagedAttributePolicy"]="ENABLED"; json.dump(p,sys.stdout)' \
    > "$TMP_PROFILE"
  "$KCADM" update users/profile -r "$REALM" -f "$TMP_PROFILE"
  rm -f "$TMP_PROFILE"
  echo "    unmanagedAttributePolicy=ENABLED"
fi

# ── Realm roles ─────────────────────────────────────────────────────────────
# AA_REVIEWER is ONE role. "Reviewer 1" and "Reviewer 2" are a tier ATTRIBUTE on the user, not two
# roles: the routing story needs to name a specific reviewer, which a role cannot express, and two
# roles would double every permission check for no gain.
#
# RBIO_ADMIN is included because it is referenced by production code
# (WorkflowController.getClosureClauses) but was missing from the realm.
AA_ROLES=(
  "AA_DO:Appellate Authority Dealing Officer - registers and processes appeals"
  "AA_REVIEWER:Appellate Authority Reviewer - first-level review (tier via reviewer_tier attribute)"
  "AA_SECRETARIAT:Appellate Authority Secretariat - second-level review, Register+Process milestones only"
  "AA_ADMIN:Appellate Authority Administrator - assignment, thresholds, overrides"
  "RE_PNO:Regulated Entity Principal Nodal Officer"
  "RBIO_ADMIN:RBIO Administrator - also acts as ORBIO Admin for AA routing"
  # ── RBIO rank ladder ──
  # These four are RANKS: a position in the escalation hierarchy
  # (DEALING_OFFICIAL -> REVIEWER -> DEPUTY_OMBUDSMAN -> OMBUDSMAN).
  #
  # RBIO_CONCILIATOR and RBIO_ADJUDICATOR are deliberately NOT part of this ladder. They are STAGES —
  # a function a ranked officer performs at a point in the process. Conflating the two is what the
  # existing hardcoded ladder does (OFFICER->SUPERVISOR->CONCILIATOR->ADJUDICATOR), which asserts that
  # being an adjudicator is senior to being a conciliator; it is not, they are different activities.
  # The frontend already assumes these names exist and 403s against them today.
  "RBIO_DEALING_OFFICIAL:RBIO Dealing Official - first-level case handling (rank)"
  "RBIO_REVIEWER:RBIO Reviewer - reviews the Dealing Official's assessment (rank)"
  "RBIO_DEPUTY_OMBUDSMAN:RBIO Deputy Ombudsman - decides within delegated authority (rank)"
  "RBIO_OMBUDSMAN:RBIO Ombudsman - final decision and appealable orders (rank)"
)

for entry in "${AA_ROLES[@]}"; do
  role="${entry%%:*}"
  desc="${entry#*:}"
  if "$KCADM" get "roles/${role}" -r "$REALM" >/dev/null 2>&1; then
    echo "    role ${role} already exists — skipping"
  else
    "$KCADM" create roles -r "$REALM" -s "name=${role}" -s "description=${desc}"
    echo "    role ${role} created"
  fi
done

# ── Test users ──────────────────────────────────────────────────────────────
# Format: username|role|extra kcadm -s args (SEMICOLON separated, may be empty).
# Semicolons, not spaces: an attribute value can legitimately contain a space
# ("HDFC Bank"), and splitting on whitespace would send kcadm a truncated value.
AA_USERS=(
  "aa_do_001|AA_DO|"
  "aa_reviewer_001|AA_REVIEWER|attributes.reviewer_tier=1"
  "aa_reviewer_002|AA_REVIEWER|attributes.reviewer_tier=2"
  "aa_secretariat_001|AA_SECRETARIAT|"
  "aa_admin_001|AA_ADMIN|"
  # entity_code must match what COMPLAINTS.entity_code actually stores, which is the NORMALISED
  # ENTITY NAME ("HDFC Bank"), not a code like HDFC0001 — RePortalService looks entities up by
  # normalised name. A code-shaped value here matches no complaint, so entity scoping would return an
  # empty queue and look like a working control while testing nothing.
  "re_pno_001|RE_PNO|attributes.entity_code=HDFC Bank"
  "orbio_admin_001|RBIO_ADMIN|"
  "orbio_officer_001|RBIO_OFFICER|"
  # RBIO rank ladder test users, one per rank, so an E2E test can log in AS a rank rather than
  # asserting against a role it granted itself with a header.
  "rbio_do_001|RBIO_DEALING_OFFICIAL|"
  "rbio_reviewer_001|RBIO_REVIEWER|"
  "rbio_dyombudsman_001|RBIO_DEPUTY_OMBUDSMAN|"
  "rbio_ombudsman_001|RBIO_OMBUDSMAN|"
)

for entry in "${AA_USERS[@]}"; do
  IFS='|' read -r username role extra <<< "$entry"

  uid="$("$KCADM" get users -r "$REALM" -q "username=${username}" --fields id --format csv --noquotes 2>/dev/null | head -1 || true)"
  if [ -n "$uid" ]; then
    echo "    user ${username} already exists (${uid})"
    # Re-assert attributes on existing users. Users created before unmanagedAttributePolicy was
    # enabled had their attributes silently dropped, so existence alone does not imply correctness.
    if [ -n "$extra" ]; then
      update_args=(-r "$REALM")
      IFS=';' read -ra extra_kvs <<< "$extra"
      for kv in "${extra_kvs[@]}"; do [ -n "$kv" ] && update_args+=(-s "$kv"); done
      "$KCADM" update "users/${uid}" "${update_args[@]}" >/dev/null 2>&1 \
        && echo "      -> attributes re-asserted (${extra})" \
        || echo "      !! failed to set attributes (${extra})"
    fi
  else
    create_args=(-r "$REALM" -s "username=${username}" -s "enabled=true"
                 -s "email=${username}@rbi.org.in" -s "emailVerified=true"
                 -s "firstName=${username}" -s "lastName=Test")
    if [ -n "$extra" ]; then
      IFS=';' read -ra extra_kvs <<< "$extra"
      for kv in "${extra_kvs[@]}"; do [ -n "$kv" ] && create_args+=(-s "$kv"); done
    fi
    "$KCADM" create users "${create_args[@]}"
    uid="$("$KCADM" get users -r "$REALM" -q "username=${username}" --fields id --format csv --noquotes | head -1)"
    echo "    user ${username} created (${uid})"
  fi

  "$KCADM" set-password -r "$REALM" --username "$username" --new-password "$TEST_PASSWORD" >/dev/null 2>&1 || true
  "$KCADM" add-roles -r "$REALM" --uusername "$username" --rolename "$role" >/dev/null 2>&1 || true
  echo "      -> password set, role ${role} granted"
done

# ── Protocol mappers ────────────────────────────────────────────────────────
# entity_code must reach the backend as a JWT CLAIM, not a header. The backend resolves an RE
# caller's entity claim-first precisely so that a caller cannot name another entity's code and read
# its complaints; without this mapper there is no claim to prefer and scoping falls back to headers.
# reviewer_tier is mapped for the same reason: routing must not trust a client-declared tier.
add_user_attribute_mapper() {
  local client_id="$1" attr="$2" claim="$3"
  local cid
  cid="$("$KCADM" get clients -r "$REALM" -q "clientId=${client_id}" --fields id --format csv --noquotes 2>/dev/null | head -1 || true)"
  if [ -z "$cid" ]; then
    echo "    client ${client_id} not found — skipping ${claim} mapper"
    return 0
  fi
  if "$KCADM" get "clients/${cid}/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes 2>/dev/null | grep -qx "${claim}"; then
    echo "    mapper ${claim} already on ${client_id}"
    return 0
  fi
  "$KCADM" create "clients/${cid}/protocol-mappers/models" -r "$REALM" \
    -s "name=${claim}" \
    -s "protocol=openid-connect" \
    -s "protocolMapper=oidc-usermodel-attribute-mapper" \
    -s "config.\"user.attribute\"=${attr}" \
    -s "config.\"claim.name\"=${claim}" \
    -s "config.\"jsonType.label\"=String" \
    -s "config.\"id.token.claim\"=true" \
    -s "config.\"access.token.claim\"=true" \
    -s "config.\"userinfo.token.claim\"=true"
  echo "    mapper ${claim} created on ${client_id}"
}

# cms-frontend is the client the Angular apps actually authenticate with (see
# cms-portal-frontend/src/environments/environment.ts). It was absent here, so entity_code never
# reached a real token and every RE portal request was refused with "Your regulated entity could not
# be determined" — the mappers existed only on clients nothing signs in through.
for client in cms-frontend cms-portal cms-officer-portal; do
  add_user_attribute_mapper "$client" entity_code entity_code
  add_user_attribute_mapper "$client" reviewer_tier reviewer_tier
done

echo "==> AA role provisioning complete."
